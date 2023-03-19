package dev.emortal.minestom.core.module.liveconfig;

import dev.emortal.api.liveconfigparser.configs.ConfigCollection;
import dev.emortal.minestom.core.module.Module;
import dev.emortal.minestom.core.module.ModuleData;
import dev.emortal.minestom.core.module.ModuleEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@ModuleData(name = "liveconfig", required = false)
public class LiveConfigModule extends Module {
    private static final Logger LOGGER = LoggerFactory.getLogger(LiveConfigModule.class);

    private final ConfigCollection configCollection;

    public LiveConfigModule(ModuleEnvironment environment) throws IOException {
        super(environment);
        this.configCollection = new ConfigCollection();
    }

    public ConfigCollection getConfigCollection() {
        return this.configCollection;
    }

    @Override
    public boolean onLoad() {
        // do nothing
        return true;
    }

    @Override
    public void onUnload() {

    }
}
