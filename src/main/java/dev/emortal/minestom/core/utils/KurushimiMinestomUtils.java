package dev.emortal.minestom.core.utils;

import dev.emortal.api.service.matchmaker.MatchmakerService;
import dev.emortal.api.utils.GrpcStubCollection;
import io.grpc.StatusRuntimeException;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.timer.Task;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NonBlocking;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class KurushimiMinestomUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(KurushimiMinestomUtils.class);
    private static final EventNode<PlayerEvent> EVENT_NODE = EventNode.type("kurushimi-utils", EventFilter.PLAYER);

    private static final MatchmakerService MATCHMAKER = GrpcStubCollection.getMatchmakerService().orElse(null);

    static {
        MinecraftServer.getGlobalEventHandler().addChild(EVENT_NODE);
    }

    /**
     * Note: The failure runnable is only run at the end of the time if players are not sent.
     * If there are other errors, they may only affect one player and resolve with retries.
     *
     * @param players         The player ids to queue for a lobby
     * @param successRunnable A runnable to run when all players are connected to the lobby.
     * @param failureRunnable A runnable to run when the sender gives up sending players.
     * @param retries         The amount of retries to send players to the lobby before giving up.
     */
    // todo retries
    // todo store player tickets so if we assume a cancellation, we delete the ticket
    @NonBlocking
    public static void sendToLobby(@NotNull Collection<? extends Player> players, @NotNull Runnable successRunnable,
                                   @NotNull Runnable failureRunnable, int retries) {
        if (MATCHMAKER == null) throw new IllegalStateException("Kurushimi stub is not present.");

        Set<? extends Player> remainingPlayers = new HashSet<>(players);
        AtomicBoolean finished = new AtomicBoolean(false);

        EventNode<PlayerEvent> localNode = EventNode.type(UUID.randomUUID().toString(), EventFilter.PLAYER,
                (event, player) -> players.contains(player));
        EVENT_NODE.addChild(localNode);

        Task task = MinecraftServer.getSchedulerManager().buildTask(() -> {
            boolean shouldRun = finished.compareAndSet(false, true);
            if (shouldRun) {
                failureRunnable.run();
                EVENT_NODE.removeChild(localNode);
            }
        }).delay(10, ChronoUnit.SECONDS).schedule();

        localNode.addListener(PlayerDisconnectEvent.class, event -> {
            remainingPlayers.remove(event.getPlayer());
            if (remainingPlayers.isEmpty()) {
                task.cancel();
                EVENT_NODE.removeChild(localNode);
                successRunnable.run();
            }
        });

        for (Player player : players) {
            Thread.startVirtualThread(() -> sendToLobby(player, () -> LOGGER.warn("Failed to create ticket to send '{}' to lobby", player.getUsername())));
        }
    }

    @NonBlocking
    public static void sendToLobby(@NotNull Collection<? extends Player> players, @NotNull Runnable successRunnable, @NotNull Runnable failureRunnable) {
        sendToLobby(players, successRunnable, failureRunnable, 1);
    }

    @Blocking
    private static void sendToLobby(@NotNull Player player, @NotNull Runnable failureRunnable) {
        try {
            MATCHMAKER.sendPlayerToLobby(player.getUuid(), false);
        } catch (StatusRuntimeException exception) {
            failureRunnable.run();
        }
    }
}
