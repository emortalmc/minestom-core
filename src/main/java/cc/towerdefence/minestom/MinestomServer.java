package cc.towerdefence.minestom;

import cc.towerdefence.minestom.module.Module;
import cc.towerdefence.minestom.module.ModuleData;
import cc.towerdefence.minestom.module.kubernetes.KubernetesModule;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.extras.velocity.VelocityProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class MinestomServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MinestomServer.class);

    private static final String DEFAULT_ADDRESS = "0.0.0.0";
    private static final String DEFAULT_PORT = "25565";

    public static final boolean DEV_ENVIRONMENT = System.getenv("AGONES_SDK_GRPC_PORT") == null;
    public static final int MAX_PLAYERS = System.getenv("MAX_PLAYERS") == null ? 100 : Integer.parseInt(System.getenv("MAX_PLAYERS"));
    public static final String SERVER_ID = DEV_ENVIRONMENT ? "local" : System.getenv("HOSTNAME");

    private static Map<Class<? extends Module>, Module> MODULES;

    public MinestomServer(Builder builder) {
        MinecraftServer server = MinecraftServer.init();
        MinecraftServer.setCompressionThreshold(0);
        this.tryEnableVelocity();

        LOGGER.info("Starting server at {}:{}", builder.address, builder.port);

        EventNode<Event> modulesNode = EventNode.all("modules");
        MinecraftServer.getGlobalEventHandler().addChild(modulesNode);

        Map<Class<? extends Module>, Module> modules = new LinkedHashMap<>();
        for (Builder.LoadableModule loadableModule : builder.modules) {
            ModuleData moduleData = loadableModule.clazz().getDeclaredAnnotation(ModuleData.class);
            if (DEV_ENVIRONMENT && moduleData.productionOnly()) continue;

            for (Class<? extends Module> moduleClazz : moduleData.dependencies()) {
                if (!modules.containsKey(moduleClazz)) {
                    LOGGER.error("Module {} requires module {} to be loaded first.", moduleData.name(), moduleClazz.getName());
                    // todo failure handling?
                    continue;
                }
            }

            EventNode<Event> eventNode = EventNode.all(moduleData.name());
            modulesNode.addChild(eventNode);

            Module module = loadableModule.creator().apply(eventNode);

            Instant loadStart = Instant.now();
            boolean loadResult = module.onLoad();
            Duration loadDuration = Duration.between(loadStart, Instant.now());


            if (loadResult) {
                modules.put(module.getClass(), module);
                LOGGER.info("Loaded module {} in {}ms with status {} (required: {})", moduleData.name(), loadDuration.toMillis(), loadResult, moduleData.required());
            }
        }
        MODULES = Collections.unmodifiableMap(modules);

        if (!MODULES.containsKey(KubernetesModule.class)) {
            LOGGER.warn("""
                    Kubernetes is not enabled, this server will not be able to connect to Agones
                    Other features such as [player-tracking] will also be disabled
                    """);
        }

        server.start(builder.address, builder.port);
        for (Module module : MODULES.values()) module.onReady();
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

    public static Map<Class<? extends Module>, Module> getModules() {
        return MODULES;
    }

    public static <T> T getModule(Class<T> clazz) {
        return (T) MODULES.get(clazz);
    }

    public static final class Builder {
        private String address = getValue("minestom.address", DEFAULT_ADDRESS);
        private int port = Integer.parseInt(getValue("minestom.port", DEFAULT_PORT));

        private final Set<LoadableModule> modules = new HashSet<>();

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

        public Builder module(Class<? extends Module> clazz, Function<EventNode<Event>, Module> moduleCreator) {
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

        private record LoadableModule(Class<? extends Module> clazz, Function<EventNode<Event>, Module> creator) {
        }
    }
}
