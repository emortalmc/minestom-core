package dev.emortal.minestom.core.module.matchmaker;

import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeCollection;
import dev.emortal.api.modules.ModuleData;
import dev.emortal.api.modules.env.ModuleEnvironment;
import dev.emortal.api.service.matchmaker.MatchmakerService;
import dev.emortal.api.utils.GrpcStubCollection;
import dev.emortal.minestom.core.module.MinestomModule;
import dev.emortal.minestom.core.module.liveconfig.LiveConfigModule;
import dev.emortal.minestom.core.module.matchmaker.commands.DequeueCommand;
import dev.emortal.minestom.core.module.matchmaker.commands.QueueCommand;
import dev.emortal.minestom.core.module.matchmaker.session.DefaultMatchmakingSessionImpl;
import dev.emortal.minestom.core.module.matchmaker.session.MatchmakingSession;
import dev.emortal.minestom.core.module.matchmaker.session.MatchmakingSessionManager;
import dev.emortal.minestom.core.module.messaging.MessagingModule;
import net.minestom.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ModuleData(name = "matchmaker", required = false, softDependencies = {MessagingModule.class, LiveConfigModule.class})
public final class MatchmakerModule extends MinestomModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(MatchmakerModule.class);

    private final MatchmakingSession.Creator sessionCreator;

    private MatchmakerService matchmaker;

    public MatchmakerModule(@NotNull ModuleEnvironment environment, @NotNull MatchmakingSession.Creator sessionCreator) {
        super(environment);
        this.sessionCreator = sessionCreator;
    }

    public MatchmakerModule(@NotNull ModuleEnvironment environment) {
        this(environment, DefaultMatchmakingSessionImpl::new);
    }

    // If you can access this method, we assume the module was loaded and the matchmaker is not null.
    public @NotNull MatchmakerService getMatchmaker() {
        return this.matchmaker;
    }

    @Override
    public boolean onLoad() {
        MessagingModule messaging = this.getModule(MessagingModule.class);
        if (messaging == null) {
            LOGGER.warn("Messaging unavailable. Matchmaking will not work.");
            return false;
        }

        LiveConfigModule liveConfig = this.getModule(LiveConfigModule.class);
        if (liveConfig == null || liveConfig.getConfigCollection().gameModes() == null) {
            LOGGER.warn("Live config parser unavailable. Matchmaking will not work.");
            return false;
        }
        GameModeCollection gameModes = liveConfig.getConfigCollection().gameModes();

        MatchmakerService matchmaker = GrpcStubCollection.getMatchmakerService().orElse(null);
        if (matchmaker == null) {
            LOGGER.warn("Matchmaker service unavailable. Matchmaking will not work.");
            return false;
        }
        this.matchmaker = matchmaker;

        var commandManager = MinecraftServer.getCommandManager();
        commandManager.register(new QueueCommand(matchmaker, gameModes));
        commandManager.register(new DequeueCommand(matchmaker));

        new MatchmakingSessionManager(this.eventNode, matchmaker, messaging, gameModes, this.sessionCreator);

        return true;
    }

    @Override
    public void onUnload() {
    }
}
