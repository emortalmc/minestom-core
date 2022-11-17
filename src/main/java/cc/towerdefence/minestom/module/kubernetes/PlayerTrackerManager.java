package cc.towerdefence.minestom.module.kubernetes;

import cc.towerdefence.api.agonessdk.EmptyStreamObserver;
import cc.towerdefence.api.service.PlayerTrackerGrpc;
import cc.towerdefence.api.service.PlayerTrackerProto;
import cc.towerdefence.minestom.Environment;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerLoginEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.function.Consumer;

public class PlayerTrackerManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerTrackerManager.class);

    private static final String ADDRESS;
    private static final int PORT;

    static {
        String portString = System.getenv("PLAYER_TRACKER_SVC_PORT");

        ADDRESS = Environment.isProduction() ? "player-tracker.towerdefence.svc" : "localhost";
        PORT = portString == null ? 9090 : Integer.parseInt(portString);
    }

    private final PlayerTrackerGrpc.PlayerTrackerStub stub;

    public PlayerTrackerManager(EventNode<Event> eventNode) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(ADDRESS, PORT)
                .defaultLoadBalancingPolicy("round_robin")
                .usePlaintext()
                .build();

        this.stub = PlayerTrackerGrpc.newStub(channel);

        eventNode.addListener(PlayerLoginEvent.class, this::onPlayerJoin);
    }

    public void retrievePlayerServer(UUID uuid, Consumer<PlayerTrackerProto.OnlineServer> responseConsumer) {
        // todo async
        this.stub.getPlayerServer(PlayerTrackerProto.PlayerRequest.newBuilder()
                .setPlayerId(uuid.toString())
                .build(), new StreamObserver<>() {
            @Override
            public void onNext(PlayerTrackerProto.GetPlayerServerResponse value) {
                responseConsumer.accept(value.getServer());
            }

            @Override
            public void onError(Throwable t) {
                LOGGER.error("Error while getting player server", t);
            }

            @Override
            public void onCompleted() {
                // ignored
            }
        });
    }

    private void onPlayerJoin(PlayerLoginEvent event) {
        Player player = event.getPlayer();

        this.stub.serverPlayerLogin(PlayerTrackerProto.PlayerLoginRequest.newBuilder()
                        .setPlayerId(player.getUuid().toString())
                        .setPlayerName(player.getUsername())
                        .setServerId(Environment.getHostname()).build()
                , new EmptyStreamObserver<>());
    }
}
