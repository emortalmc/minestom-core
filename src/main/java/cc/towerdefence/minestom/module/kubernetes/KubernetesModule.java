package cc.towerdefence.minestom.module.kubernetes;

import cc.towerdefence.api.agonessdk.AgonesUtils;
import cc.towerdefence.api.agonessdk.IgnoredStreamObserver;
import cc.towerdefence.minestom.MinestomServer;
import cc.towerdefence.minestom.module.Module;
import cc.towerdefence.minestom.module.ModuleData;
import cc.towerdefence.minestom.module.core.PlayerTrackerManager;
import cc.towerdefence.minestom.module.kubernetes.command.agones.AgonesCommand;
import cc.towerdefence.minestom.module.kubernetes.command.currentserver.CurrentServerCommand;
import dev.agones.sdk.AgonesSDKProto;
import dev.agones.sdk.SDKGrpc;
import dev.agones.sdk.alpha.AlphaAgonesSDKProto;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.kubernetes.client.ProtoClient;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.util.Config;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerLoginEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@ModuleData(name = "kubernetes", required = true, productionOnly = true)
public class KubernetesModule extends Module {
    private static final boolean ENABLED = !MinestomServer.DEV_ENVIRONMENT || System.getenv("ENABLE_K8S_DEV") != null;
    private static final int AGONES_GRPC_PORT = MinestomServer.DEV_ENVIRONMENT ? 9357 : Integer.parseInt(System.getenv("AGONES_SDK_GRPC_PORT"));

    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesModule.class);

    private final AgonesSDKProto.KeyValue[] additionalLabels;

    private ApiClient apiClient;
    private ProtoClient protoClient;

    private SDKGrpc.SDKStub sdk;
    private dev.agones.sdk.beta.SDKGrpc.SDKFutureStub betaSdk;
    private dev.agones.sdk.alpha.SDKGrpc.SDKFutureStub alphaSdk;

    public KubernetesModule(EventNode<Event> eventNode, AgonesSDKProto.KeyValue... additionalLabels) {
        super(eventNode);

        this.additionalLabels = additionalLabels;
    }

    public KubernetesModule(EventNode<Event> eventNode) {
        this(eventNode, new AgonesSDKProto.KeyValue[]{});
    }

    @Override
    public boolean onLoad() {
        try {
            this.apiClient = Config.defaultClient();
            Configuration.setDefaultApiClient(this.apiClient);

            this.protoClient = new ProtoClient(this.apiClient);

            ManagedChannel agonesChannel = ManagedChannelBuilder.forAddress("localhost", AGONES_GRPC_PORT).usePlaintext().build();
            this.sdk = SDKGrpc.newStub(agonesChannel);
            this.betaSdk = dev.agones.sdk.beta.SDKGrpc.newFutureStub(agonesChannel);
            this.alphaSdk = dev.agones.sdk.alpha.SDKGrpc.newFutureStub(agonesChannel);

            if (!MinestomServer.DEV_ENVIRONMENT) {
                PlayerTrackerManager playerTrackerManager = new PlayerTrackerManager(this.eventNode);
                MinecraftServer.getCommandManager().register(new CurrentServerCommand(playerTrackerManager));
                MinecraftServer.getCommandManager().register(new AgonesCommand(this));
            }

            for (AgonesSDKProto.KeyValue label : this.additionalLabels) {
                this.sdk.setLabel(label, new IgnoredStreamObserver<>());
                LOGGER.info("Set Agones label {} to {}", label.getKey(), label.getValue());
            }
        } catch (IOException e) {
            LOGGER.error("Failed to initialise Kubernetes client", e);
            return false;
        }
        return true;
    }

    @Override
    public void onUnload() {
        AgonesUtils.shutdownHealthTask();
    }

    @Override
    public void onReady() {
        LOGGER.info("Marking server as READY for Agones with a capacity of {} players", MinestomServer.MAX_PLAYERS);

        AgonesUtils.startHealthTask(this.sdk, 10, TimeUnit.SECONDS);
        this.sdk.ready(AgonesSDKProto.Empty.getDefaultInstance(), new IgnoredStreamObserver<>());

        this.eventNode.addListener(PlayerLoginEvent.class, this::onConnect)
                .addListener(PlayerDisconnectEvent.class, this::onDisconnect);

        this.alphaSdk.setPlayerCapacity(AlphaAgonesSDKProto.Count.newBuilder().setCount(MinestomServer.MAX_PLAYERS).build());
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

    public @NotNull SDKGrpc.SDKStub getSdk() {
        return sdk;
    }

    public @NotNull dev.agones.sdk.beta.SDKGrpc.SDKFutureStub getBetaSdk() {
        return betaSdk;
    }

    public @NotNull dev.agones.sdk.alpha.SDKGrpc.SDKFutureStub getAlphaSdk() {
        return alphaSdk;
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    public ApiClient getApiClient() {
        return this.apiClient;
    }

    public ProtoClient getProtoClient() {
        return this.protoClient;
    }
}
