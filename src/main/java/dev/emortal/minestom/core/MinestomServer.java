package dev.emortal.minestom.core;

import dev.emortal.api.modules.LoadableModule;
import dev.emortal.api.modules.Module;
import dev.emortal.api.modules.ModuleManager;
import dev.emortal.minestom.core.module.chat.ChatModule;
import dev.emortal.minestom.core.module.core.CoreModule;
import dev.emortal.minestom.core.module.kubernetes.KubernetesModule;
import dev.emortal.minestom.core.module.liveconfig.LiveConfigModule;
import dev.emortal.minestom.core.module.matchmaker.MatchmakerModule;
import dev.emortal.minestom.core.module.messaging.MessagingModule;
import dev.emortal.minestom.core.module.permissions.PermissionModule;
import net.minestom.server.MinecraftServer;
import net.minestom.server.extras.MojangAuth;
import net.minestom.server.extras.velocity.VelocityProxy;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public final class MinestomServer {
    private static final Logger LOGGER;

    static {
        // I'd rather have this all done when this class is loaded rather than when it is constructed, which could be after other module classes
        // that need the logger are loaded
        String loggerConfigFile = Environment.isProduction() ? "logback-prod.xml" : "logback-dev.xml";
        System.setProperty("logback.configurationFile", loggerConfigFile);

        LOGGER = LoggerFactory.getLogger(MinestomServer.class);
    }

    private static final String DEFAULT_ADDRESS = "0.0.0.0";
    private static final String DEFAULT_PORT = "25565";

    public static @NotNull Builder builder() {
        return new Builder();
    }

    private final String address;
    private final int port;

    private final MinecraftServer server;
    private final ModuleManager moduleManager;

    private MinestomServer(@NotNull Builder builder) {
        this.address = builder.address;
        this.port = builder.port;

        this.server = MinecraftServer.init();
        MinecraftServer.setCompressionThreshold(0);
        this.tryEnableVelocity();

        if (builder.mojangAuth) {
            LOGGER.info("Enabling Mojang authentication");
            MojangAuth.init();
        }

        this.moduleManager = builder.moduleManagerBuilder.build();
        MinecraftServer.getSchedulerManager().buildShutdownTask(this.moduleManager::onUnload);
    }

    private void tryEnableVelocity() {
        String forwardingSecret = System.getenv("VELOCITY_FORWARDING_SECRET");
        if (forwardingSecret == null) {
            LOGGER.warn("Not enabling Velocity forwarding, no secret was provided");

            if (Environment.isProduction()) System.exit(1); // If in prod, kill the server
            return;
        }

        LOGGER.info("Enabling Velocity forwarding");
        VelocityProxy.enable(forwardingSecret);
    }

    public void start() {
        LOGGER.info("Starting server at {}:{}", this.address, this.port);
        this.server.start(this.address, this.port);
        this.moduleManager.onReady();
    }

    public @NotNull ModuleManager getModuleManager() {
        return this.moduleManager;
    }

    public static final class Builder {

        private @NotNull String address = getValue("minestom.address", DEFAULT_ADDRESS);
        private int port = Integer.parseInt(getValue("minestom.port", DEFAULT_PORT));
        private boolean mojangAuth = false;

        private final ModuleManager.Builder moduleManagerBuilder = ModuleManager.builder();

        private Builder() {
            // we do this because env variables in dockerfiles break k8s env variables?
            // So we can't add system properties in the dockerfile, but we can add them at runtime
            for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
                if (System.getProperty(entry.getKey()) == null) {
                    System.setProperty(entry.getKey(), entry.getValue());
                }
            }
        }

        public @NotNull Builder address(@NotNull String address) {
            this.address = address;
            return this;
        }

        public @NotNull Builder port(int port) {
            this.port = port;
            return this;
        }

        public @NotNull Builder mojangAuth(boolean mojangAuth) {
            this.mojangAuth = mojangAuth;
            return this;
        }

        public @NotNull Builder commonModules() {
            return this.module(KubernetesModule.class, KubernetesModule::new)
                    .module(CoreModule.class, CoreModule::new)
                    .module(PermissionModule.class, PermissionModule::new)
                    .module(ChatModule.class, ChatModule::new)
                    .module(LiveConfigModule.class, LiveConfigModule::new)
                    .module(MessagingModule.class, MessagingModule::new)
                    .module(MatchmakerModule.class, MatchmakerModule::new);
        }

        public @NotNull Builder module(@NotNull Class<? extends Module> clazz, @NotNull LoadableModule.Creator moduleCreator) {
            this.moduleManagerBuilder.module(clazz, moduleCreator);
            return this;
        }

        public @NotNull MinestomServer build() {
            return new MinestomServer(this);
        }

        public void buildAndStart() {
            this.build().start();
        }

        private static @NotNull String getValue(@NotNull String key, @NotNull String defaultValue) {
            String value = System.getProperty(key);
            if (value != null && !value.isEmpty()) return value;

            value = System.getenv(key);
            if (value != null && !value.isEmpty()) return value;

            return defaultValue;
        }
    }
}
