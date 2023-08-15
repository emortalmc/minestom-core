package dev.emortal.minestom.core.module.core;

import dev.emortal.api.modules.ModuleData;
import dev.emortal.api.modules.env.ModuleEnvironment;
import dev.emortal.api.service.badges.BadgeService;
import dev.emortal.api.utils.GrpcStubCollection;
import dev.emortal.api.utils.resolvers.PlayerResolver;
import dev.emortal.minestom.core.module.MinestomModule;
import dev.emortal.minestom.core.module.core.badge.BadgeCommand;
import dev.emortal.minestom.core.module.core.performance.PerformanceCommand;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandManager;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ModuleData(name = "core", required = false)
public final class CoreModule extends MinestomModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoreModule.class);

    public CoreModule(@NotNull ModuleEnvironment environment) {
        super(environment);
    }

    @Override
    public boolean onLoad() {
        PlayerResolver.setPlatformUsernameResolver(username -> {
            Player player = MinecraftServer.getConnectionManager().getPlayer(username);
            if (player == null) return null;

            return new PlayerResolver.CachedMcPlayer(player.getUuid(), player.getUsername(), player.isOnline());
        });

        CommandManager commandManager = MinecraftServer.getCommandManager();
        commandManager.register(new PerformanceCommand(this.eventNode));

        BadgeService badgeService = GrpcStubCollection.getBadgeManagerService().orElse(null);
        if (badgeService != null) {
            commandManager.register(new BadgeCommand(badgeService));
        } else {
            LOGGER.warn("Badge service unavailable. Badges will not work.");
        }

        return true;
    }

    @Override
    public void onUnload() {
    }
}
