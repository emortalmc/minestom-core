package dev.emortal.minestom.core.module.liveconfig;

import dev.emortal.api.liveconfigparser.configs.LiveConfigCollection;
import dev.emortal.api.modules.Module;
import dev.emortal.api.modules.ModuleData;
import dev.emortal.api.modules.ModuleEnvironment;
import dev.emortal.minestom.core.module.kubernetes.KubernetesModule;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import okhttp3.OkHttpClient;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.logging.Level;

@ModuleData(name = "liveconfig", required = false, softDependencies = {KubernetesModule.class})
public final class LiveConfigModule extends Module {
    private static final Logger LOGGER = LoggerFactory.getLogger(LiveConfigModule.class);

    private LiveConfigCollection configCollection;

    public LiveConfigModule(@NotNull ModuleEnvironment environment) {
        super(environment);
    }

    public LiveConfigCollection getConfigCollection() {
        return this.configCollection;
    }

    @Override
    public boolean onLoad() {
        KubernetesModule kubernetesModule = this.moduleManager.getModule(KubernetesModule.class);
        ApiClient apiClient = null;
        if (kubernetesModule != null) {
            apiClient = kubernetesModule.getApiClient();
        }

        // todo remove
        java.util.logging.Logger.getLogger(OkHttpClient.class.getName()).setLevel(Level.FINE);

        try {
            this.configCollection = new LiveConfigCollection(apiClient);
        } catch (ApiException e) {
            LOGGER.error("Failed to load LiveConfigCollection\nbody: {}\nheaders: {}", e.getResponseBody(), e.getResponseHeaders(), e);
            return false;
        } catch (IOException e) {
            LOGGER.error("Failed to load LiveConfigCollection", e);
            return false;
        }
        return true;
    }

    @Override
    public void onUnload() {

    }
}
