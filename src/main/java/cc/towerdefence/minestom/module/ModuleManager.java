package cc.towerdefence.minestom.module;

import cc.towerdefence.minestom.MinestomServer;
import cc.towerdefence.minestom.module.kubernetes.KubernetesModule;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ModuleManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModuleManager.class);

    private final @NotNull Map<Class<? extends Module>, Module> modules = new ConcurrentHashMap<>();
    private final @NotNull EventNode<Event> modulesNode = EventNode.all("modules");

    public ModuleManager(MinestomServer.Builder builder) {
        for (MinestomServer.Builder.LoadableModule loadableModule : builder.getModules()) {
            ModuleData moduleData = loadableModule.clazz().getDeclaredAnnotation(ModuleData.class);

            for (Class<? extends Module> moduleClazz : moduleData.dependencies()) {
                if (!this.modules.containsKey(moduleClazz)) {
                    LOGGER.error("Module {} requires module {} to be loaded first.", moduleData.name(), moduleClazz.getName());
                    // todo failure handling?
                    continue;
                }
            }

            EventNode<Event> eventNode = EventNode.all(moduleData.name());
            this.modulesNode.addChild(eventNode);

            Module module = loadableModule.creator().apply(new ModuleEnvironment(eventNode, this));

            Instant loadStart = Instant.now();
            boolean loadResult = module.onLoad();
            Duration loadDuration = Duration.between(loadStart, Instant.now());

            if (loadResult) {
                this.modules.put(loadableModule.clazz(), module);
                LOGGER.info("Loaded module {} in {}ms with status {} (required: {})", moduleData.name(), loadDuration.toMillis(), loadResult, moduleData.required());
            }
        }

        if (!this.modules.containsKey(KubernetesModule.class)) {
            LOGGER.warn("""
                    Kubernetes is not enabled, this server will not be able to connect to Agones
                    Other features such as [player-tracking] will also be disabled
                    """);
        }
    }

    public void onReady() {
        for (Module module : this.modules.values()) {
            Instant readyStart = Instant.now();
            module.onReady();
            Duration readyDuration = Duration.between(readyStart, Instant.now());

            LOGGER.info("Fired onReady for module {} in {}ms", module.getClass().getName(), readyDuration.toMillis());
        }
    }

    public @NotNull Map<Class<? extends Module>, Module> getModules() {
        return this.modules;
    }

    public <T> T getModule(Class<T> clazz) {
        return clazz.cast(this.modules.get(clazz));
    }
}
