package cc.towerdefence.minestom.module.kubernetes;

import cc.towerdefence.api.service.PlayerTrackerGrpc;
import cc.towerdefence.api.service.PlayerTrackerProto;
import cc.towerdefence.api.utils.utils.FunctionalFutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

public class PlayerTrackerManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerTrackerManager.class);

    private final PlayerTrackerGrpc.PlayerTrackerFutureStub stub;

    public PlayerTrackerManager(PlayerTrackerGrpc.PlayerTrackerFutureStub stub) {
        this.stub = stub;
    }

    public void retrievePlayerServer(UUID uuid, Consumer<PlayerTrackerProto.OnlineServer> responseConsumer) {
        ListenableFuture<PlayerTrackerProto.GetPlayerServerResponse> serverResponseFuture = this.stub.getPlayerServer(PlayerTrackerProto.PlayerRequest.newBuilder()
                .setPlayerId(uuid.toString())
                .build());

        Futures.addCallback(serverResponseFuture, FunctionalFutureCallback.create(
                response -> responseConsumer.accept(response.getServer()),
                throwable -> LOGGER.error("Failed to retrieve player server", throwable)
        ), ForkJoinPool.commonPool());
    }
}
