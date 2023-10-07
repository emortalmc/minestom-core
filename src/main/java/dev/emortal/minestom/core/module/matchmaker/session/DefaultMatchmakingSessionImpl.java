package dev.emortal.minestom.core.module.matchmaker.session;

import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeConfig;
import dev.emortal.api.model.matchmaker.PendingMatch;
import dev.emortal.api.model.matchmaker.Ticket;
import dev.emortal.api.utils.ProtoTimestampConverter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minestom.server.entity.Player;
import net.minestom.server.timer.TaskSchedule;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class DefaultMatchmakingSessionImpl extends MatchmakingSession {
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    // TODO let's have a 3, 2, 1 countdown before they get teleported.
    private static final String MATCH_FOUND_MESSAGE = "<green><mode> match found!";
    private static final String TELEPORTING_IN_MESSAGE = "<green>Teleporting in <time> seconds...</green>";
    private static final Component TELEPORTING_MESSAGE = Component.text("Teleporting...", NamedTextColor.GREEN);
    private static final String MATCH_CANCELLED_MESSAGE = "<mode> match cancelled.";

    private final @NotNull ScheduledFuture<?> notificationTask;
    private final @NotNull GameModeConfig gameMode;

    public DefaultMatchmakingSessionImpl(@NotNull Player player, @NotNull GameModeConfig gameMode, @NotNull Ticket ticket) {
        super(player, ticket);

        this.notificationTask = SCHEDULER.scheduleAtFixedRate(this::notifyPlayer, 30, 30, TimeUnit.SECONDS);
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
    public void onPendingMatchCreate(@NotNull PendingMatch match) {
        this.player.sendMessage(MINI_MESSAGE.deserialize(MATCH_FOUND_MESSAGE, Placeholder.unparsed("mode", this.gameMode.friendlyName())));
        this.player.scheduler().submitTask(new NotifyTeleportTimeTask(this.player, match));
    }

    @Override
    public void onPendingMatchUpdate(@NotNull PendingMatch match) {
        // do nothing
    }

    @Override
    public void onPendingMatchCancelled(@NotNull PendingMatch match) {
        var modeName = Placeholder.unparsed("mode", this.gameMode.friendlyName());
        this.player.sendMessage(MINI_MESSAGE.deserialize(MATCH_CANCELLED_MESSAGE, modeName));
    }

    private void notifyPlayer() {
        this.player.sendMessage(Component.text("You are in queue for %s...".formatted(this.gameMode.friendlyName()), NamedTextColor.GREEN));
    }

    @Override
    public void notifyDeletion(@NotNull DeleteReason reason) {
        switch (reason) {
            case MANUAL_DEQUEUE -> this.player.sendMessage(Component.text("You have been removed from the queue.", NamedTextColor.RED));
            case GAME_MODE_DELETED -> this.player.sendMessage(Component.text("The game mode you were in queue for has been disabled.", NamedTextColor.RED));
        }
    }

    @Override
    public void destroy() {
        this.notificationTask.cancel(false);
    }

    private static final class NotifyTeleportTimeTask implements Supplier<TaskSchedule> {

        private final @NotNull Player player;
        private final int teleportSeconds;

        private int count = 0;

        NotifyTeleportTimeTask(@NotNull Player player, @NotNull PendingMatch match) {
            this.player = player;
            this.teleportSeconds = this.calculateSecondsToTeleport(match);
        }

        @Override
        public @NotNull TaskSchedule get() {
            if (this.count >= this.teleportSeconds) {
                this.player.sendMessage(TELEPORTING_MESSAGE);
                return TaskSchedule.stop();
            }

            int secondsLeft = this.teleportSeconds - this.count;
            this.player.sendMessage(MINI_MESSAGE.deserialize(TELEPORTING_IN_MESSAGE, Placeholder.unparsed("time", String.valueOf(secondsLeft))));

            this.count++;
            return TaskSchedule.seconds(1);
        }

        private int calculateSecondsToTeleport(@NotNull PendingMatch match) {
            Instant now = Instant.now();
            Instant teleportTime = ProtoTimestampConverter.fromProto(match.getTeleportTime());
            return (int) (teleportTime.getEpochSecond() - now.getEpochSecond());
        }
    }
}
