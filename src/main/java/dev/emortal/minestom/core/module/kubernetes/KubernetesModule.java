package dev.emortal.minestom.core.module.kubernetes;

import dev.agones.sdk.AgonesSDKProto;
import dev.agones.sdk.SDKGrpc;
import dev.emortal.api.agonessdk.AgonesUtils;
import dev.emortal.api.agonessdk.IgnoredStreamObserver;
import dev.emortal.api.modules.Module;
import dev.emortal.api.modules.annotation.ModuleData;
import dev.emortal.api.modules.env.ModuleEnvironment;
import dev.emortal.api.utils.GrpcStubCollection;
import dev.emortal.minestom.core.Environment;
import dev.emortal.minestom.core.module.kubernetes.command.agones.AgonesCommand;
import dev.emortal.minestom.core.module.kubernetes.command.currentserver.CurrentServerCommand;
import io.grpc.ManagedChannelBuilder;
import io.kubernetes.client.ProtoClient;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.util.Config;
import net.minestom.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@ModuleData(name = "kubernetes")
public final class KubernetesModule extends Module {
    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesModule.class);

    private static final boolean KUBERNETES_ENABLED = Environment.isProduction(); // Kubernetes support can only be enabled if run in-cluster

    private static final boolean KUBE_CLIENT_DEBUG = Boolean.parseBoolean(System.getenv("KUBE_CLIENT_DEBUG"));
    private static final boolean AGONES_SDK_ENABLED;
    private static final String AGONES_ADDRESS = "localhost"; // SDK runs as a sidecar in production so address is always localhost
    private static final int AGONES_GRPC_PORT;

    static {
        final String agonesPortString = System.getenv("AGONES_SDK_GRPC_PORT");
        AGONES_SDK_ENABLED = Environment.isProduction() || agonesPortString != null;
        AGONES_GRPC_PORT = AGONES_SDK_ENABLED ? Integer.parseInt(agonesPortString) : Integer.MIN_VALUE;
    }

    private final AgonesSDKProto.KeyValue[] additionalLabels;

    private ApiClient apiClient;
    private ProtoClient protoClient;

    private SDKGrpc.SDKStub sdk;

    public KubernetesModule(@NotNull ModuleEnvironment environment, @NotNull AgonesSDKProto.KeyValue... additionalLabels) {
        super(environment);
        this.additionalLabels = additionalLabels;
    }

    @Override
    public boolean onLoad() {
        // kubernetes
        if (KUBERNETES_ENABLED) {
            try {
                this.apiClient = Config.defaultClient().setDebugging(KUBE_CLIENT_DEBUG);
                Configuration.setDefaultApiClient(this.apiClient);

                this.protoClient = new ProtoClient(this.apiClient);
            } catch (IOException exception) {
                LOGGER.error("Failed to initialise Kubernetes client", exception);
                return false;
            }
        }

        // player tracker
        GrpcStubCollection.getPlayerTrackerService()
                .ifPresent(playerTracker -> MinecraftServer.getCommandManager().register(new CurrentServerCommand(playerTracker)));

        // agones
        if (AGONES_SDK_ENABLED) this.loadAgones();

        return true;
    }

    private void loadAgones() {
        this.sdk = SDKGrpc.newStub(ManagedChannelBuilder.forAddress(AGONES_ADDRESS, AGONES_GRPC_PORT).usePlaintext().build());

        MinecraftServer.getCommandManager().register(new AgonesCommand(this.sdk));

        for (AgonesSDKProto.KeyValue label : this.additionalLabels) {
            this.sdk.setLabel(label, new IgnoredStreamObserver<>());
            LOGGER.info("Set Agones label agones.dev/sdk-{}={}", label.getKey(), label.getValue());
        }

        LOGGER.info("Setting agones label: agones.dev/sdk-emc-protocol-version={}", MinecraftServer.PROTOCOL_VERSION);
        AgonesSDKProto.KeyValue protocolVersion = AgonesSDKProto.KeyValue.newBuilder()
                .setKey("emc-protocol-version")
                .setValue(String.valueOf(MinecraftServer.PROTOCOL_VERSION))
                .build();

        LOGGER.info("Setting agones label: agones.dev/sdk-emc-version-name={}", MinecraftServer.VERSION_NAME);
        AgonesSDKProto.KeyValue versionName = AgonesSDKProto.KeyValue.newBuilder()
                .setKey("emc-version-name")
                .setValue(MinecraftServer.VERSION_NAME)
                .build();

        this.sdk.setAnnotation(protocolVersion, new IgnoredStreamObserver<>());
        this.sdk.setAnnotation(versionName, new IgnoredStreamObserver<>());
    }

    @Override
    public void onUnload() {
        if (AGONES_SDK_ENABLED) this.unloadAgones();
    }

    private void unloadAgones() {
        LOGGER.info("Marking server as shutdown for Agones");

        AgonesUtils.shutdownHealthTask();
        this.sdk.shutdown(AgonesSDKProto.Empty.getDefaultInstance(), new IgnoredStreamObserver<>());
    }

    @Override
    public void onReady() {
        if (AGONES_SDK_ENABLED) this.readyAgones();
    }

    private void readyAgones() {
        LOGGER.info("Marking server as READY for Agones");

        AgonesUtils.startHealthTask(this.sdk, 10, TimeUnit.SECONDS);
        this.sdk.ready(AgonesSDKProto.Empty.getDefaultInstance(), new IgnoredStreamObserver<>());
    }

    public @Nullable ApiClient getApiClient() {
        return this.apiClient;
    }

    public @Nullable ProtoClient getProtoClient() {
        return this.protoClient;
    }

    public @Nullable SDKGrpc.SDKStub getSdk() {
        return this.sdk;
    }
}
