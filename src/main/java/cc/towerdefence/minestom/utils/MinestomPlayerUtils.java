package cc.towerdefence.minestom.utils;

import cc.towerdefence.api.service.PlayerTransporterProto;
import cc.towerdefence.api.utils.GrpcStubCollection;
import cc.towerdefence.api.utils.utils.FunctionalFutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.Empty;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.timer.ExecutionType;
import net.minestom.server.timer.TaskSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

public class MinestomPlayerUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(MinestomPlayerUtils.class);

    public static void sendAllToLobby(Runnable callback) {
        sendAllToLobby(callback, 1);
    }

    private static void sendAllToLobby(Runnable callback, int iteration) {
        if (MinecraftServer.getConnectionManager().getOnlinePlayers().size() == 0) {
            LOGGER.info("No players online, skipping lobby server (iteration {})", iteration);
            callback.run();
            return;
        }

        if (iteration > 3) {
            LOGGER.error("Failed to send all players to the lobby after 3 attempts. Remaining online ({}): {}",
                    MinecraftServer.getConnectionManager().getOnlinePlayers().size(),
                    MinecraftServer.getConnectionManager().getOnlinePlayers().stream()
                            .map(Player::getUsername)
                            .collect(Collectors.joining(", ")));
            callback.run();
            return;
        }

        GrpcStubCollection.getPlayerTransporterService().ifPresent(service -> {
            Set<String> playerIds = MinecraftServer.getConnectionManager().getOnlinePlayers().stream()
                    .map(Player::getUuid)
                    .map(UUID::toString)
                    .collect(Collectors.toUnmodifiableSet());

            ListenableFuture<Empty> response = service.commonMovePlayer(PlayerTransporterProto.MoveRequest.newBuilder()
                    .addAllPlayerIds(playerIds)
                    .setServerType(PlayerTransporterProto.RestrictedServerType.LOBBY)
                    .build());

            Futures.addCallback(response, FunctionalFutureCallback.create(
                    empty -> checkAndRetry(callback, iteration),
                    throwable -> checkAndRetry(callback, iteration)
            ), ForkJoinPool.commonPool());
        });
    }

    private static void checkAndRetry(Runnable callback, int iteration) {
        delayExecution(() -> {
            if (MinecraftServer.getConnectionManager().getOnlinePlayers().size() > 0) {
                LOGGER.warn("Failed to send all players to the lobby, retrying (iteration {})", iteration);
                sendAllToLobby(callback, iteration + 1);
            } else {
                LOGGER.info("Successfully sent all players to the lobby (iteration {})", iteration);
                callback.run();
            }
        });
    }

    private static void delayExecution(Runnable runnable) {
        MinecraftServer.getSchedulerManager().scheduleTask(runnable, TaskSchedule.seconds(2), TaskSchedule.stop(), ExecutionType.SYNC);
    }
}
