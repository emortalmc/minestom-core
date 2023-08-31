package dev.emortal.minestom.core.utils.resolver;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record LocalMcPlayer(@NotNull UUID uuid, @NotNull String username, boolean online) {
}
