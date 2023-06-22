package dev.emortal.minestom.core.module.kubernetes;

import com.google.common.util.concurrent.Futures;
import dev.emortal.api.grpc.playertracker.PlayerTrackerGrpc;
import dev.emortal.api.grpc.playertracker.PlayerTrackerProto;
import dev.emortal.api.model.playertracker.PlayerLocation;
import dev.emortal.api.utils.callback.FunctionalFutureCallback;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

public final class PlayerTrackerManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerTrackerManager.class);

    private final PlayerTrackerGrpc.PlayerTrackerFutureStub service;

    public PlayerTrackerManager(@NotNull PlayerTrackerGrpc.PlayerTrackerFutureStub service) {
        this.service = service;
    }

    public void retrievePlayerServer(UUID uuid, Consumer<PlayerLocation> responseConsumer) {
        final var request = PlayerTrackerProto.GetPlayerServerRequest.newBuilder().setPlayerId(uuid.toString()).build();

        Futures.addCallback(service.getPlayerServer(request), FunctionalFutureCallback.create(
                response -> responseConsumer.accept(response.getServer()),
                throwable -> LOGGER.error("Failed to retrieve player server", throwable)
        ), ForkJoinPool.commonPool());
    }
}
