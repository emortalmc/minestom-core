package dev.emortal.minestom.core.module.matchmaker.session;

import dev.emortal.api.kurushimi.PendingMatch;
import dev.emortal.api.kurushimi.Ticket;
import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeConfig;
import dev.emortal.api.utils.ProtoTimestampConverter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class DefaultMatchmakingSessionImpl extends MatchmakingSession {
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    // TODO let's have a 3, 2, 1 countdown before they get teleported.
    private static final String MATCH_FOUND_MESSAGE = "<green><mode> match found! Teleporting in <time> seconds...</green>";
    private static final String MATCH_CANCELLED_MESSAGE = "<mode> match cancelled.";

    private final @NotNull ScheduledFuture<?> future;
    private final @NotNull GameModeConfig gameMode;

    public DefaultMatchmakingSessionImpl(@NotNull Player player, @NotNull GameModeConfig gameMode, @NotNull Ticket ticket) {
        super(player, ticket);

        this.future = SCHEDULER.scheduleAtFixedRate(this::notifyPlayer, 30, 30, TimeUnit.SECONDS);
        this.gameMode = gameMode;

        /* TODO: We experimented with notifications, not good right now because we can't get rid of the
         text saying "Challenge Completed!", etc.. We can get rid of it when we have a custom resource pack

        for (FrameType frameType : FrameType.values()) {
            NotificationCenter.send(new Notification(
                    Component.text("Test").append(Component.newline()).append(Component.text("Test2")),
                    frameType,
                    ItemStack.of(Material.DIAMOND_SWORD)
            ), this.player);
        }*/
    }

    @Override
    void onPendingMatchCreate(@NotNull PendingMatch match) {
        Instant teleportTime = ProtoTimestampConverter.fromProto(match.getTeleportTime());
        int secondsToTeleport = (int) (teleportTime.getEpochSecond() - Instant.now().getEpochSecond());

        this.player.sendMessage(MINI_MESSAGE.deserialize(MATCH_FOUND_MESSAGE,
                Placeholder.unparsed("mode", this.gameMode.getFriendlyName()),
                Placeholder.unparsed("time", String.valueOf(secondsToTeleport))
        ));
    }

    @Override
    void onPendingMatchUpdate(@NotNull PendingMatch match) {
        // do nothing
    }

    @Override
    void onPendingMatchCancelled(@NotNull PendingMatch match) {
        this.player.sendMessage(MINI_MESSAGE.deserialize(MATCH_CANCELLED_MESSAGE,
                Placeholder.unparsed("mode", this.gameMode.getFriendlyName())
        ));
    }

    private void notifyPlayer() {
        this.player.sendMessage(Component.text("You are in queue for %s...".formatted(this.gameMode.getFriendlyName()), NamedTextColor.GREEN));
    }

    @Override
    void notifyDeletion(@NotNull DeleteReason reason) {
        switch (reason) {
            case MANUAL_DEQUEUE ->
                    this.player.sendMessage(Component.text("You have been removed from the queue.", NamedTextColor.RED));
            case GAME_MODE_DELETED ->
                    this.player.sendMessage(Component.text("The game mode you were in queue for has been disabled.", NamedTextColor.RED));
            case MATCH_CREATED, UNKNOWN -> {
            } // do nothing
        }
    }

    @Override
    void destroy() {
        this.future.cancel(false);
    }

    public void setTicket(@NotNull Ticket ticket) {
        this.ticket = ticket;
    }
}