package dev.emortal.minestom.core.module;

import dev.emortal.minestom.core.MinestomServer;
import dev.emortal.minestom.core.module.kubernetes.KubernetesModule;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import org.jetbrains.annotations.NotNull;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ModuleManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModuleManager.class);

    private final @NotNull Map<Class<? extends Module>, Module> modules = new ConcurrentHashMap<>();

    private final @NotNull EventNode<Event> modulesNode;

    public ModuleManager(MinestomServer.Builder builder, @NotNull EventNode<Event> modulesNode) {
        this.modulesNode = modulesNode;

        List<MinestomServer.Builder.LoadableModule> sortedModules = this.sortModules(builder.getModules().values());

        for (MinestomServer.Builder.LoadableModule loadableModule : sortedModules) {
            ModuleData moduleData = loadableModule.clazz().getDeclaredAnnotation(ModuleData.class);

            EventNode<Event> eventNode = EventNode.all(moduleData.name());
            this.modulesNode.addChild(eventNode);

            Module module;
            try {
                module = loadableModule.creator().create(new ModuleEnvironment(eventNode, this));
            } catch (Exception e) {
                LOGGER.error("Failed to create module {}", moduleData.name(), e);
                continue;
            }

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

            LOGGER.info("Fired onReady for module {} in {}ms", module.getClass().getSimpleName(), readyDuration.toMillis());
        }
    }

    /**
     * Sorts the modules in the order they should be loaded in
     *
     * @param modules The modules to sort
     * @return The sorted modules
     * @throws IllegalArgumentException If a module has a cyclic dependency
     */
    private List<MinestomServer.Builder.LoadableModule> sortModules(Collection<MinestomServer.Builder.LoadableModule> modules) throws IllegalArgumentException {
        Graph<MinestomServer.Builder.LoadableModule, DefaultEdge> graph = new DirectedAcyclicGraph<>(DefaultEdge.class);

        for (MinestomServer.Builder.LoadableModule module : modules) {
            graph.addVertex(module);

            for (Class<? extends Module> dependency : module.clazz().getDeclaredAnnotation(ModuleData.class).softDependencies()) {
                // find the LoadableModule for the dependency's Class
                MinestomServer.Builder.LoadableModule dependencyModule = modules.stream()
                        .filter(targetModule -> targetModule.clazz().equals(dependency))
                        .findFirst()
                        .orElse(null);

                if (dependencyModule == null) {
                    LOGGER.error("Module {} requires module {} to be loaded first.", module.clazz().getSimpleName(), dependency.getSimpleName());
                    continue;
                }
                graph.addVertex(dependencyModule);
                graph.addEdge(dependencyModule, module);
            }
        }

        TopologicalOrderIterator<MinestomServer.Builder.LoadableModule, DefaultEdge> sortedIterator = new TopologicalOrderIterator<>(graph);
        List<MinestomServer.Builder.LoadableModule> sorted = new java.util.ArrayList<>();

        sortedIterator.forEachRemaining(sorted::add);

        LOGGER.info("Loading modules: [{}]", sorted.stream().map(module -> module.clazz().getSimpleName()).collect(Collectors.joining(", ")));

        return sorted;
    }

    public @NotNull Map<Class<? extends Module>, Module> getModules() {
        return this.modules;
    }

    public <T> T getModule(Class<T> clazz) {
        return clazz.cast(this.modules.get(clazz));
    }
}
