package dev.emortal.minestom.core.module.matchmaker;

import dev.emortal.api.kurushimi.KurushimiStubCollection;
import dev.emortal.api.kurushimi.KurushimiUtils;
import dev.emortal.api.kurushimi.MatchmakerGrpc;
import dev.emortal.api.kurushimi.Ticket;
import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeCollection;
import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeConfig;
import dev.emortal.api.modules.ModuleData;
import dev.emortal.api.modules.ModuleEnvironment;
import dev.emortal.minestom.core.module.MinestomModule;
import dev.emortal.minestom.core.module.liveconfig.LiveConfigModule;
import dev.emortal.minestom.core.module.matchmaker.commands.DequeueCommand;
import dev.emortal.minestom.core.module.matchmaker.commands.QueueCommand;
import dev.emortal.minestom.core.module.matchmaker.session.DefaultMatchmakingSessionImpl;
import dev.emortal.minestom.core.module.matchmaker.session.MatchmakingSession;
import dev.emortal.minestom.core.module.matchmaker.session.MatchmakingSessionManager;
import dev.emortal.minestom.core.module.messaging.MessagingModule;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandManager;
import net.minestom.server.entity.Player;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@ModuleData(name = "matchmaker", required = false, softDependencies = {MessagingModule.class, LiveConfigModule.class})
public final class MatchmakerModule extends MinestomModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(MatchmakerModule.class);

    private final MatchmakerGrpc.MatchmakerFutureStub matchmaker = KurushimiStubCollection.getFutureStub().orElse(null);

    private final MessagingModule messaging;
    private final LiveConfigModule liveConfig;

    private final TriFunction<Player, GameModeConfig, Ticket, MatchmakingSession> sessionCreator;

    public MatchmakerModule(@NotNull ModuleEnvironment environment) {
        this(environment, DefaultMatchmakingSessionImpl::new);
    }

    public MatchmakerModule(@NotNull ModuleEnvironment environment, @NotNull TriFunction<Player, GameModeConfig, Ticket, MatchmakingSession> sessionCreator) {
        super(environment);
        this.sessionCreator = sessionCreator;

        KurushimiUtils.registerParserRegistry();
        this.messaging = getModule(MessagingModule.class);
        this.liveConfig = getModule(LiveConfigModule.class);
    }

    @Override
    public boolean onLoad() {
        if (matchmaker == null) {
            LOGGER.error("Matchmaker gRPC stub is not present but is required for MatchmakerModule");
            return false;
        }

        final Optional<GameModeCollection> gameModeCollection = liveConfig.getConfigCollection().gameModes();
        if (gameModeCollection.isEmpty()) {
            LOGGER.error("GameModeCollection is not present in LiveConfigModule but is required for MatchmakerModule");
            return false;
        }

        final CommandManager commandManager = MinecraftServer.getCommandManager();
        commandManager.register(new QueueCommand(matchmaker, gameModeCollection.get()));
        commandManager.register(new DequeueCommand(matchmaker));

        new MatchmakingSessionManager(eventNode, matchmaker, messaging, gameModeCollection.get(), sessionCreator);

        return true;
    }

    @Override
    public void onUnload() {

    }
}
