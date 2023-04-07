package dev.emortal.minestom.core.module.matchmaker.session;

import dev.emortal.api.kurushimi.PendingMatch;
import dev.emortal.api.kurushimi.Ticket;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;

public abstract class MatchmakingSession {
    protected final @NotNull Player player;
    protected @NotNull Ticket ticket;

    protected MatchmakingSession(@NotNull Player player, @NotNull Ticket ticket) {
        this.player = player;
        this.ticket = ticket;
    }

    public abstract void onPendingMatchCreate(@NotNull PendingMatch match);

    public abstract void onPendingMatchUpdate(@NotNull PendingMatch match);

    public abstract void onPendingMatchCancelled(@NotNull PendingMatch match);

    /**
     * Notifies the player that they have been removed from the queue.
     *
     * @param reason The reason for the deletion
     */
    public abstract void notifyDeletion(@NotNull DeleteReason reason);

    /**
     * Destroy the matchmaking session. This is called either after {@link #notifyDeletion(DeleteReason)}
     * or when the player disconnects.
     */
    public abstract void destroy();

    public enum DeleteReason {
        GAME_MODE_DELETED,
        MANUAL_DEQUEUE,
        MATCH_CREATED,
        UNKNOWN // triggered when a player is removed from a ticket (e.g. they left a party, disconnected)
    }

    public @NotNull Player getPlayer() {
        return this.player;
    }

    public void setTicket(@NotNull Ticket ticket) {
        this.ticket = ticket;
    }
}
