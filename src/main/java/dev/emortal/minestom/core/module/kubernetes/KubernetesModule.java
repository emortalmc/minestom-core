package dev.emortal.minestom.core.module.kubernetes;

import dev.agones.sdk.AgonesSDKProto;
import dev.agones.sdk.SDKGrpc;
import dev.agones.sdk.alpha.AlphaAgonesSDKProto;
import dev.emortal.api.agonessdk.AgonesUtils;
import dev.emortal.api.agonessdk.IgnoredStreamObserver;
import dev.emortal.api.utils.GrpcStubCollection;
import dev.emortal.minestom.core.Environment;
import dev.emortal.minestom.core.MinestomServer;
import dev.emortal.minestom.core.module.Module;
import dev.emortal.minestom.core.module.ModuleData;
import dev.emortal.minestom.core.module.ModuleEnvironment;
import dev.emortal.minestom.core.module.kubernetes.command.agones.AgonesCommand;
import dev.emortal.minestom.core.module.kubernetes.command.currentserver.CurrentServerCommand;
import dev.emortal.minestom.core.module.kubernetes.rabbitmq.RabbitMqCore;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.kubernetes.client.ProtoClient;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.util.Config;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerLoginEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@ModuleData(name = "kubernetes", required = true)
public class KubernetesModule extends Module {
    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesModule.class);

    private static final boolean KUBERNETES_ENABLED = Environment.isProduction(); // Kubernetes support can only be enabled if run in-cluster

    private static final boolean AGONES_SDK_ENABLED;
    private static final String AGONES_ADDRESS = "localhost"; // SDK runs as a sidecar in production so address is always localhost
    private static final int AGONES_GRPC_PORT;

    static {
        String agonesPortString = System.getenv("AGONES_SDK_GRPC_PORT");
        AGONES_SDK_ENABLED = Environment.isProduction() || agonesPortString != null;

        AGONES_GRPC_PORT = AGONES_SDK_ENABLED ? Integer.parseInt(agonesPortString) : Integer.MIN_VALUE;
    }

    private final AgonesSDKProto.KeyValue[] additionalLabels;

    private ApiClient apiClient;
    private ProtoClient protoClient;

    private SDKGrpc.SDKStub sdk;
    private dev.agones.sdk.beta.SDKGrpc.SDKFutureStub betaSdk;
    private dev.agones.sdk.alpha.SDKGrpc.SDKFutureStub alphaSdk;

    public KubernetesModule(ModuleEnvironment environment, AgonesSDKProto.KeyValue... additionalLabels) {
        super(environment);

        this.additionalLabels = additionalLabels;
    }

    @Override
    public boolean onLoad() {
        // kubernetes
        if (KUBERNETES_ENABLED) {
            try {
                this.apiClient = Config.defaultClient();
                Configuration.setDefaultApiClient(this.apiClient);

                this.protoClient = new ProtoClient(this.apiClient);
            } catch (IOException e) {
                LOGGER.error("Failed to initialise Kubernetes client", e);
                return false;
            }
        }

        // player tracker
        GrpcStubCollection.getPlayerTrackerService().ifPresent(playerTracker -> {
            PlayerTrackerManager playerTrackerManager = new PlayerTrackerManager(playerTracker);
            MinecraftServer.getCommandManager().register(new CurrentServerCommand(playerTrackerManager));
        });
        new RabbitMqCore(this.eventNode);

        // agones
        if (AGONES_SDK_ENABLED) {
            ManagedChannel agonesChannel = ManagedChannelBuilder.forAddress(AGONES_ADDRESS, AGONES_GRPC_PORT).usePlaintext().build();
            this.sdk = SDKGrpc.newStub(agonesChannel);
            this.betaSdk = dev.agones.sdk.beta.SDKGrpc.newFutureStub(agonesChannel);
            this.alphaSdk = dev.agones.sdk.alpha.SDKGrpc.newFutureStub(agonesChannel);
            MinecraftServer.getCommandManager().register(new AgonesCommand(this));

            for (AgonesSDKProto.KeyValue label : this.additionalLabels) {
                this.sdk.setLabel(label, new IgnoredStreamObserver<>());
                LOGGER.info("Set Agones label {} to {}", label.getKey(), label.getValue());
            }
        }
        return true;
    }

    @Override
    public void onUnload() {
        if (AGONES_SDK_ENABLED)
            AgonesUtils.shutdownHealthTask();
    }

    @Override
    public void onReady() {
        if (AGONES_SDK_ENABLED) {
            LOGGER.info("Marking server as READY for Agones with a capacity of {} players", MinestomServer.MAX_PLAYERS);

            AgonesUtils.startHealthTask(this.sdk, 10, TimeUnit.SECONDS);
            this.sdk.ready(AgonesSDKProto.Empty.getDefaultInstance(), new IgnoredStreamObserver<>());

            this.eventNode.addListener(PlayerLoginEvent.class, this::onConnect)
                    .addListener(PlayerDisconnectEvent.class, this::onDisconnect);

            this.alphaSdk.setPlayerCapacity(AlphaAgonesSDKProto.Count.newBuilder().setCount(MinestomServer.MAX_PLAYERS).build());
        }
    }

    private void onConnect(PlayerLoginEvent event) {
        this.alphaSdk.playerConnect(
                AlphaAgonesSDKProto.PlayerID.newBuilder().setPlayerID(event.getPlayer().getUuid().toString()).build()
        );
    }

    private void onDisconnect(PlayerDisconnectEvent event) {
        this.alphaSdk.playerDisconnect(
                AlphaAgonesSDKProto.PlayerID.newBuilder().setPlayerID(event.getPlayer().getUuid().toString()).build()
        );
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

    public @NotNull dev.agones.sdk.beta.SDKGrpc.SDKFutureStub getBetaSdk() {
        return this.betaSdk;
    }

    public @NotNull dev.agones.sdk.alpha.SDKGrpc.SDKFutureStub getAlphaSdk() {
        return this.alphaSdk;
    }
}
