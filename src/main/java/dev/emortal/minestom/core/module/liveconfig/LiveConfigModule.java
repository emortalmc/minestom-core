package dev.emortal.minestom.core.module.liveconfig;

import dev.emortal.api.liveconfigparser.configs.LiveConfigCollection;
import dev.emortal.api.modules.Module;
import dev.emortal.api.modules.ModuleData;
import dev.emortal.api.modules.env.ModuleEnvironment;
import dev.emortal.minestom.core.module.kubernetes.KubernetesModule;
import io.kubernetes.client.openapi.ApiClient;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@ModuleData(name = "liveconfig", required = false, softDependencies = {KubernetesModule.class})
public final class LiveConfigModule extends Module {
    private static final Logger LOGGER = LoggerFactory.getLogger(LiveConfigModule.class);

    private LiveConfigCollection configCollection;

    public LiveConfigModule(@NotNull ModuleEnvironment environment) {
        super(environment);
    }

    public @NotNull LiveConfigCollection getConfigCollection() {
        return this.configCollection;
    }

    @Override
    public boolean onLoad() {
        var kubernetesModule = this.getModule(KubernetesModule.class);
        ApiClient apiClient = kubernetesModule != null ? kubernetesModule.getApiClient() : null;

        try {
            this.configCollection = new LiveConfigCollection(apiClient);
        } catch (IOException exception) {
            LOGGER.error("Failed to load LiveConfigCollection", exception);
            return false;
        }
        return true;
    }

    @Override
    public void onUnload() {
        try {
            this.configCollection.close();
        } catch (IOException exception) {
            LOGGER.error("Failed to close LiveConfigCollection", exception);
        }
    }
}
