package dev.emortal.minestom.core.module.matchmaker;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class CommonMatchmakerError {
    // Queue
    public static final String QUEUE_SUCCESS = "<green>Queued for <mode>!</green>";
    public static final String QUEUE_ERR_UNKNOWN = "<red>An unknown error occurred while trying to queue you for <mode>. Please try again later.</red>";
    public static final Component QUEUE_ERR_ALREADY_IN_QUEUE = Component.text("You are already queued!", NamedTextColor.RED);
    public static final String QUEUE_ERR_PARTY_TOO_LARGE = "<red>Your party is too large for <mode>. The maximum party size is <max> players.</red>";
    public static final String PLAYER_PERMISSION_DENIED = "<red>You do not have permission to queue your party.</red>";

    // Dequeue
    public static final Component DEQUEUE_SUCCESS = Component.text("You have been dequeued", NamedTextColor.GREEN);

    public static final Component DEQUEUE_ERR_NOT_IN_QUEUE = Component.text("You are not queued for a game", NamedTextColor.RED);
    public static final Component DEQUEUE_ERR_NO_PERMISSION = Component.text("You do not have permission to dequeue", NamedTextColor.RED);
    public static final Component DEQUEUE_ERR_ALREADY_MARKED = Component.text("You are already marked for dequeue", NamedTextColor.RED);
    public static final Component DEQUEUE_ERR_UNKNOWN = Component.text("An unknown error occurred. Please report this to a staff member", NamedTextColor.RED);

    private CommonMatchmakerError() {
        throw new AssertionError("This class cannot be instantiated.");
    }
}
