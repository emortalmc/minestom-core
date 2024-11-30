package dev.emortal.minestom.core.utils.command;

import dev.emortal.minestom.core.module.permissions.PermissionHolder;
import net.minestom.server.command.builder.condition.CommandCondition;
import org.jetbrains.annotations.NotNull;

public final class ExtraConditions {

    public static @NotNull CommandCondition hasPermission(@NotNull String permission) {
        return (sender, commandName) -> {
            if (!(sender instanceof PermissionHolder permHolder)) return false;
            return permHolder.hasPermission(permission);
        };
    }

    private ExtraConditions() {
        throw new AssertionError("This class cannot be instantiated.");
    }
}
