package cc.towerdefence.minestom.module.kubernetes;

import cc.towerdefence.api.service.PlayerTrackerGrpc;
import cc.towerdefence.api.service.PlayerTrackerProto;
import cc.towerdefence.api.utils.utils.FunctionalFutureCallback;
import cc.towerdefence.minestom.Environment;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerLoginEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

public class PlayerTrackerManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerTrackerManager.class);

    private final PlayerTrackerGrpc.PlayerTrackerFutureStub stub;

    public PlayerTrackerManager(EventNode<Event> eventNode, PlayerTrackerGrpc.PlayerTrackerFutureStub stub) {
        this.stub = stub;

        eventNode.addListener(PlayerLoginEvent.class, this::onPlayerJoin);
    }

    public void retrievePlayerServer(UUID uuid, Consumer<PlayerTrackerProto.OnlineServer> responseConsumer) {
        ListenableFuture< PlayerTrackerProto.GetPlayerServerResponse> serverResponseFuture = this.stub.getPlayerServer(PlayerTrackerProto.PlayerRequest.newBuilder()
                .setPlayerId(uuid.toString())
                .build());

        Futures.addCallback(serverResponseFuture, FunctionalFutureCallback.create(
                response -> responseConsumer.accept(response.getServer()),
                throwable -> LOGGER.error("Failed to retrieve player server", throwable)
        ), ForkJoinPool.commonPool());
    }

    private void onPlayerJoin(PlayerLoginEvent event) {
        Player player = event.getPlayer();

        this.stub.serverPlayerLogin(PlayerTrackerProto.PlayerLoginRequest.newBuilder()
                        .setPlayerId(player.getUuid().toString())
                        .setPlayerName(player.getUsername())
                        .setServerId(Environment.getHostname()).build());
    }
}
