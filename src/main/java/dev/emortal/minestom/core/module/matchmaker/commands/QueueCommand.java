package dev.emortal.minestom.core.module.matchmaker.commands;

import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeCollection;
import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeConfig;
import dev.emortal.api.service.matchmaker.MatchmakerService;
import dev.emortal.api.service.matchmaker.QueuePlayerResult;
import dev.emortal.minestom.core.module.matchmaker.CommonMatchmakerError;
import io.grpc.StatusRuntimeException;
import java.util.Collection;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.command.builder.arguments.ArgumentStringArray;
import net.minestom.server.command.builder.condition.Conditions;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Stream;

public class QueueCommand extends Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueueCommand.class);
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final MatchmakerService matchmaker;
    private final Collection<GameModeConfig> configs;

    public QueueCommand(@NotNull MatchmakerService matchmaker, @NotNull GameModeCollection gameModeCollection) {
        super("play", "queue");
        this.matchmaker = matchmaker;
        this.configs = gameModeCollection.getAllConfigs();

        this.setCondition(Conditions::playerOnly);

        var modeArg = new ArgumentStringArray("mode");
        modeArg.setSuggestionCallback((sender, context, suggestion) -> {
            String inputLower = context.getRaw("mode").toLowerCase();

            Stream<String> configNames = this.configs.stream()
                    .filter(GameModeConfig::enabled)
                    .map(GameModeConfig::friendlyName);

            if (!inputLower.isEmpty() && !inputLower.isBlank() && inputLower.charAt(0) != 0) {
                configNames = configNames.filter(name -> name.toLowerCase().startsWith(inputLower));
            }

            configNames.map(SuggestionEntry::new).forEach(suggestion::addEntry);
        });

        this.addSyntax(this::execute, modeArg);
    }

    private void execute(@NotNull CommandSender sender, @NotNull CommandContext context) {
        Player player = (Player) sender;
        String[] modeArg = context.get("mode");
        String modeName = String.join(" ", modeArg);

        GameModeConfig mode = this.configs.stream()
                .filter(GameModeConfig::enabled)
                .filter(config -> config.friendlyName().equalsIgnoreCase(modeName))
                .findFirst()
                .orElse(null);

        if (mode == null) {
            sender.sendMessage("Invalid mode " + modeName + "!");
            return;
        }

        var modeNamePlaceholder = Placeholder.unparsed("mode", mode.friendlyName());

        QueuePlayerResult result;
        try {
            result = this.matchmaker.queuePlayer(mode.id(), player.getUuid());
        } catch (StatusRuntimeException exception) {
            LOGGER.error("An unknown error occurred while queuing for " + mode.friendlyName(), exception);
            player.sendMessage(MINI_MESSAGE.deserialize(CommonMatchmakerError.QUEUE_ERR_UNKNOWN, modeNamePlaceholder));
            return;
        }

        var message = switch (result) {
            case SUCCESS -> MINI_MESSAGE.deserialize(CommonMatchmakerError.QUEUE_SUCCESS, modeNamePlaceholder);
            case ALREADY_IN_QUEUE -> CommonMatchmakerError.QUEUE_ERR_ALREADY_IN_QUEUE;
            case INVALID_GAME_MODE -> {
                LOGGER.error("Invalid gamemode " + mode.friendlyName());
                yield MINI_MESSAGE.deserialize(CommonMatchmakerError.QUEUE_ERR_UNKNOWN, modeNamePlaceholder);
            }
            case GAME_MODE_DISABLED -> {
                LOGGER.error("Gamemode " + mode.friendlyName() + " is disabled");
                yield MINI_MESSAGE.deserialize(CommonMatchmakerError.QUEUE_ERR_UNKNOWN, modeNamePlaceholder);
            }
            case INVALID_MAP -> {
                LOGGER.error("Invalid map for gamemode " + mode.friendlyName());
                yield MINI_MESSAGE.deserialize(CommonMatchmakerError.QUEUE_ERR_UNKNOWN, modeNamePlaceholder);
            }
            case PARTY_TOO_LARGE -> {
                final var max = Placeholder.unparsed("max", String.valueOf(mode.partyRestrictions().maxSize()));
                yield MINI_MESSAGE.deserialize(CommonMatchmakerError.QUEUE_ERR_PARTY_TOO_LARGE, modeNamePlaceholder, max);
            }
            case NO_PERMISSION -> MINI_MESSAGE.deserialize(CommonMatchmakerError.PLAYER_PERMISSION_DENIED);
        };
        player.sendMessage(message);
    }
}
