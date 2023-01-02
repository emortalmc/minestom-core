package dev.emortal.minestom.core;

import dev.emortal.minestom.core.module.Module;
import dev.emortal.minestom.core.module.ModuleEnvironment;
import dev.emortal.minestom.core.module.ModuleManager;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.extras.velocity.VelocityProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class MinestomServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MinestomServer.class);

    private static final String DEFAULT_ADDRESS = "0.0.0.0";
    private static final String DEFAULT_PORT = "25565";

    public static final int MAX_PLAYERS = System.getenv("MAX_PLAYERS") == null ? 100 : Integer.parseInt(System.getenv("MAX_PLAYERS"));

    public MinestomServer(Builder builder) {
        MinecraftServer server = MinecraftServer.init();
        MinecraftServer.setCompressionThreshold(0);
        this.tryEnableVelocity();

        LOGGER.info("Starting server at {}:{}", builder.address, builder.port);

        EventNode<Event> modulesNode = EventNode.all("modules");
        MinecraftServer.getGlobalEventHandler().addChild(modulesNode);

        ModuleManager moduleManager = new ModuleManager(builder, modulesNode);

        server.start(builder.address, builder.port);
        moduleManager.onReady();
    }

    private void tryEnableVelocity() {
        String forwardingSecret = Builder.getValue("minestom.velocity-forwarding-secret", null);
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

        private final List<LoadableModule> modules = new ArrayList<>();

        public Builder() {
            // we do this because env variables in dockerfiles break k8s env variables?
            // So we can't add system properties in the dockerfile, but we can add them at runtime
            for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
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

        public Builder module(Class<? extends Module> clazz, Function<ModuleEnvironment, Module> moduleCreator) {
            this.modules.add(new LoadableModule(clazz, moduleCreator));
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

        public record LoadableModule(Class<? extends Module> clazz, Function<ModuleEnvironment, Module> creator) {
        }

        public List<LoadableModule> getModules() {
            return this.modules;
        }

        public int getPort() {
            return this.port;
        }

        public String getAddress() {
            return this.address;
        }
    }
}
