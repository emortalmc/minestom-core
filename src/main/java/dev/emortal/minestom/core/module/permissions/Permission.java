package dev.emortal.minestom.core.module.permissions;

import org.jetbrains.annotations.NotNull;

public record Permission(@NotNull String permission, boolean state) {

    public Permission(@NotNull String permission) {
        this(permission, true);
    }

}
