package dev.emortal.minestom.core.module.permissions;

import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.regex.Pattern;

public interface PermissionHolder {

    @NotNull Set<Permission> getPermissions();

    default void addPermission(@NotNull Permission permission) {
        this.getPermissions().add(permission);
    }

    default void removePermission(@NotNull Permission permission) {
        this.getPermissions().remove(permission);
    }

    default void removePermission(@NotNull String permission) {
        this.getPermissions().removeIf(p -> p.permission().equals(permission));
    }

    default boolean hasPermission(@NotNull Permission permission) {
        for (Permission permissionLoop : this.getPermissions()) {
            if (permissionLoop.equals(permission)) {
                return true;
            }
            String permissionLoopName = permissionLoop.permission();
            if (permissionLoopName.contains("*")) {
                // Sanitize permissionLoopName
                String regexSanitized = Pattern.quote(permissionLoopName).replace("*", "\\E(.*)\\Q"); // Replace * with regex
                // pattern matching for wildcards, where foo.b*r.baz matches foo.baaaar.baz or foo.bar.baz
                if (permission.permission().matches(regexSanitized)) {
                    return true;
                }
            }
        }
        return false;
    }

    default boolean hasPermission(@NotNull String permission) {
        return this.hasPermission(new Permission(permission, true));
    }

    default Permission getPermission(@NotNull String permission) {
        for (Permission permissionLoop : this.getPermissions()) {
            if (permissionLoop.permission().equals(permission)) {
                return permissionLoop;
            }
        }

        return null;
    }
}
