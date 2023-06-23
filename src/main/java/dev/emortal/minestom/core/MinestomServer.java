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
import java.util.ArrayList;
import java.util.List;
import net.minestom.server.MinecraftServer;
import net.minestom.server.extras.MojangAuth;
import net.minestom.server.extras.velocity.VelocityProxy;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MinestomServer {
    private static final Logger LOGGER;

    static {
        // I'd rather have this all done when this class is loaded rather than when it is constructed, which could be after other module classes
        // that need the logger are loaded
        final String loggerConfigFile = Environment.isProduction() ? "logback-prod.xml" : "logback-dev.xml";
        System.setProperty("logback.configurationFile", loggerConfigFile);

        LOGGER = LoggerFactory.getLogger(MinestomServer.class);
    }

    private static final String DEFAULT_ADDRESS = "0.0.0.0";
    private static final String DEFAULT_PORT = "25565";

    private MinestomServer(Builder builder) {
        final MinecraftServer server = MinecraftServer.init();
        MinecraftServer.setCompressionThreshold(0);
        tryEnableVelocity();

        if (builder.mojangAuth) {
            LOGGER.info("Enabling Mojang authentication");
            MojangAuth.init();
        }

        LOGGER.info("Starting server at {}:{}", builder.address, builder.port);

        final ModuleManager moduleManager = new ModuleManager(builder.modules);
        MinecraftServer.getSchedulerManager().buildShutdownTask(moduleManager::onUnload);

        server.start(builder.address, builder.port);
        moduleManager.onReady();
    }

    private void tryEnableVelocity() {
        final String forwardingSecret = System.getenv("VELOCITY_FORWARDING_SECRET");
        if (forwardingSecret == null) {
            LOGGER.warn("Not enabling Velocity forwarding, no secret was provided");
            return;
        }

        LOGGER.info("Enabling Velocity forwarding");
        VelocityProxy.enable(forwardingSecret);
    }

    public static final class Builder {

        private String address = getValue("minestom.address", DEFAULT_ADDRESS);
        private int port = Integer.parseInt(getValue("minestom.port", DEFAULT_PORT));
        private boolean mojangAuth = false;

        private final List<LoadableModule> modules = new ArrayList<>();

        public Builder() {
            // we do this because env variables in dockerfiles break k8s env variables?
            // So we can't add system properties in the dockerfile, but we can add them at runtime
            for (final var entry : System.getenv().entrySet()) {
                if (System.getProperty(entry.getKey()) == null) {
                    System.setProperty(entry.getKey(), entry.getValue());
                }
            }
        }

        public Builder address(String address) {
            this.address = address;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder mojangAuth(boolean mojangAuth) {
            this.mojangAuth = mojangAuth;
            return this;
        }

        public Builder commonModules() {
            return module(KubernetesModule.class, KubernetesModule::new)
                    .module(CoreModule.class, CoreModule::new)
                    .module(PermissionModule.class, PermissionModule::new)
                    .module(ChatModule.class, ChatModule::new)
                    .module(LiveConfigModule.class, LiveConfigModule::new)
                    .module(MessagingModule.class, MessagingModule::new)
                    .module(MatchmakerModule.class, MatchmakerModule::new);
        }

        public Builder module(@NotNull Class<? extends Module> clazz, @NotNull LoadableModule.Creator moduleCreator) {
            modules.add(new LoadableModule(clazz, moduleCreator));
            return this;
        }

        public MinestomServer build() {
            return new MinestomServer(this);
        }

        private static String getValue(String key, String defaultValue) {
            String value = System.getProperty(key);
            if (value != null && !value.isEmpty()) return value;

            value = System.getenv(key);
            if (value != null && !value.isEmpty()) return value;

            return defaultValue;
        }
    }
}
