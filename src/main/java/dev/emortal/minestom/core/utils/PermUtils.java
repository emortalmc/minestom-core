package dev.emortal.minestom.core.utils;

import dev.emortal.minestom.core.module.permissions.PermissionHolder;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.ConsoleSender;

public class PermUtils {

    public static boolean hasPermission(CommandSender sender, String permission) {
        if (sender instanceof ConsoleSender) return false; // We don't allow console use
        if (!(sender instanceof PermissionHolder permHolder)) throw new IllegalArgumentException("Player must be a PermissionHolder");

        return permHolder.hasPermission(permission);
    }
}
