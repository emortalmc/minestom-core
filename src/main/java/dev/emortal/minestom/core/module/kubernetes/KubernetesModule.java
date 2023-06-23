package dev.emortal.minestom.core.module.kubernetes;

import dev.agones.sdk.AgonesSDKProto;
import dev.agones.sdk.SDKGrpc;
import dev.emortal.api.agonessdk.AgonesUtils;
import dev.emortal.api.agonessdk.IgnoredStreamObserver;
import dev.emortal.api.modules.Module;
import dev.emortal.api.modules.ModuleData;
import dev.emortal.api.modules.ModuleEnvironment;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@ModuleData(name = "kubernetes", required = true)
public final class KubernetesModule extends Module {
    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesModule.class);

    private static final boolean KUBERNETES_ENABLED = Environment.isProduction(); // Kubernetes support can only be enabled if run in-cluster

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
                this.apiClient = Config.defaultClient();
                Configuration.setDefaultApiClient(apiClient);

                this.protoClient = new ProtoClient(apiClient);
            } catch (final IOException exception) {
                LOGGER.error("Failed to initialise Kubernetes client", exception);
                return false;
            }
        }

        // player tracker
        MinecraftServer.getCommandManager().register(new CurrentServerCommand());

        // agones
        if (AGONES_SDK_ENABLED) loadAgones();

        return true;
    }

    private void loadAgones() {
        this.sdk = SDKGrpc.newStub(ManagedChannelBuilder.forAddress(AGONES_ADDRESS, AGONES_GRPC_PORT).usePlaintext().build());

        MinecraftServer.getCommandManager().register(new AgonesCommand(this));

        for (final AgonesSDKProto.KeyValue label : additionalLabels) {
            sdk.setLabel(label, new IgnoredStreamObserver<>());
            LOGGER.info("Set Agones label {} to {}", label.getKey(), label.getValue());
        }

        final var protocolVersion = AgonesSDKProto.KeyValue.newBuilder()
                .setKey("emc-protocol-version")
                .setValue(String.valueOf(MinecraftServer.PROTOCOL_VERSION))
                .build();
        final var versionName = AgonesSDKProto.KeyValue.newBuilder()
                .setKey("emc-version-name")
                .setValue(MinecraftServer.VERSION_NAME)
                .build();

        sdk.setAnnotation(protocolVersion, new IgnoredStreamObserver<>());
        sdk.setAnnotation(versionName, new IgnoredStreamObserver<>());

        LOGGER.info("Set Agones version annotations. [protocol={}, version={}]", MinecraftServer.PROTOCOL_VERSION, MinecraftServer.VERSION_NAME);
    }

    @Override
    public void onUnload() {
        if (AGONES_SDK_ENABLED) unloadAgones();
    }

    private void unloadAgones() {
        LOGGER.info("Marking server as shutdown for Agones");
        AgonesUtils.shutdownHealthTask();
        sdk.shutdown(AgonesSDKProto.Empty.getDefaultInstance(), new IgnoredStreamObserver<>());
    }

    @Override
    public void onReady() {
        if (AGONES_SDK_ENABLED) readyAgones();
    }

    private void readyAgones() {
        LOGGER.info("Marking server as READY for Agones");

        AgonesUtils.startHealthTask(sdk, 10, TimeUnit.SECONDS);
        sdk.ready(AgonesSDKProto.Empty.getDefaultInstance(), new IgnoredStreamObserver<>());
    }

    public ApiClient getApiClient() {
        return this.apiClient;
    }

    public ProtoClient getProtoClient() {
        return this.protoClient;
    }

    public @NotNull SDKGrpc.SDKStub getSdk() {
        return this.sdk;
    }
}
