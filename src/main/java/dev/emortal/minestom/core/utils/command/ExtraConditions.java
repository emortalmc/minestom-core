package dev.emortal.minestom.core.utils.command;

import net.minestom.server.command.builder.condition.CommandCondition;
import org.jetbrains.annotations.NotNull;

public final class ExtraConditions {

    public static @NotNull CommandCondition hasPermission(@NotNull String permission) {
        return (sender, commandName) -> sender.hasPermission(permission);
    }

    private ExtraConditions() {
        throw new AssertionError("This class cannot be instantiated.");
    }
}
