package dev.emortal.minestom.core;

import dev.emortal.minestom.core.utils.EnvUtils;
import org.jetbrains.annotations.NotNull;

public class Environment {
    private static final boolean DEVELOPMENT = System.getenv("KUBERNETES_SERVICE_HOST") == null;
    private static final @NotNull String HOSTNAME = EnvUtils.getOrDefault("HOSTNAME", "unknown");

    public static boolean isProduction() {
        return !DEVELOPMENT;
    }

    public static @NotNull String getHostname() {
        return HOSTNAME;
    }
}
