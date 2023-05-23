package dev.emortal.minestom.core.module.matchmaker.commands;

import com.google.common.util.concurrent.Futures;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.rpc.Status;
import dev.emortal.api.kurushimi.MatchmakerGrpc;
import dev.emortal.api.kurushimi.QueueByPlayerErrorResponse;
import dev.emortal.api.kurushimi.QueueByPlayerRequest;
import dev.emortal.api.liveconfigparser.configs.ConfigUpdate;
import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeCollection;
import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeConfig;
import dev.emortal.api.utils.callback.FunctionalFutureCallback;
import dev.emortal.minestom.core.module.matchmaker.CommonMatchmakerError;
import io.grpc.protobuf.StatusProto;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.command.builder.arguments.ArgumentStringArray;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Stream;

public class QueueCommand extends Command {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final MatchmakerGrpc.MatchmakerFutureStub matchmaker;
    private final List<GameModeConfig> configs;

    public QueueCommand(@NotNull MatchmakerGrpc.MatchmakerFutureStub matchmaker, @NotNull GameModeCollection gameModeCollection) {
        super("play", "queue");

        this.matchmaker = matchmaker;

        // Load the available gamemodes
        this.configs = gameModeCollection.getAllConfigs(this::handleUpdate);

        this.setCondition((sender, commandString) -> sender instanceof Player);

        ArgumentStringArray modeArg = new ArgumentStringArray("mode");
        modeArg.setSuggestionCallback((sender, context, suggestion) -> {
            String inputLower = context.getRaw("mode").toLowerCase();

            Stream<String> stream = this.configs.stream()
                    .filter(GameModeConfig::isEnabled)
                    .map(GameModeConfig::getFriendlyName);

            if (!inputLower.isEmpty() && !inputLower.isBlank() && inputLower.charAt(0) != 0) {
                stream = stream.filter(name -> name.toLowerCase().startsWith(inputLower));
            }

            stream.map(SuggestionEntry::new).forEach(suggestion::addEntry);
        });

        this.addSyntax(this::execute, modeArg);
    }

    private void execute(@NotNull CommandSender sender, @NotNull CommandContext context) {
        Player player = (Player) sender;
        String[] modeArg = context.get("mode");
        String modeName = String.join(" ", modeArg);

        GameModeConfig mode = this.configs.stream()
                .filter(GameModeConfig::isEnabled)
                .filter(config -> config.getFriendlyName().equalsIgnoreCase(modeName))
                .findFirst()
                .orElse(null);

        if (mode == null) {
            sender.sendMessage("Invalid mode " + modeName + "!");
            return;
        }

        var queueReqFuture = this.matchmaker.queueByPlayer(QueueByPlayerRequest.newBuilder()
                .setPlayerId(player.getUuid().toString())
                .setGameModeId(mode.getId())
                .build());

        Futures.addCallback(queueReqFuture, FunctionalFutureCallback.create(
                response -> sender.sendMessage(
                        MINI_MESSAGE.deserialize(CommonMatchmakerError.QUEUE_SUCCESS, Placeholder.unparsed("mode", mode.getFriendlyName()))
                ),
                throwable -> {
                    Status status = StatusProto.fromThrowable(throwable);
                    if (status == null || status.getDetailsCount() == 0) {
                        sender.sendMessage("An unknown error occurred while queuing for " + mode.getFriendlyName());
                        LOGGER.error("An unknown error occurred while queuing for " + mode.getFriendlyName(), throwable);
                        return;
                    }

                    try {
                        QueueByPlayerErrorResponse errorResponse = status.getDetails(0).unpack(QueueByPlayerErrorResponse.class);

                        player.sendMessage(switch (errorResponse.getReason()) {
                            case ALREADY_IN_QUEUE -> CommonMatchmakerError.QUEUE_ERR_ALREADY_IN_QUEUE;
                            case INVALID_MAP -> {
                                LOGGER.error("Invalid map for gamemode " + mode.getFriendlyName());
                                yield MINI_MESSAGE.deserialize(CommonMatchmakerError.QUEUE_ERR_UNKNOWN, Placeholder.unparsed("mode", mode.getFriendlyName()));
                            }
                            case PARTY_TOO_LARGE -> MINI_MESSAGE.deserialize(CommonMatchmakerError.QUEUE_ERR_PARTY_TOO_LARGE,
                                    Placeholder.unparsed("mode", mode.getFriendlyName()),
                                    Placeholder.unparsed("max", String.valueOf(mode.getPartyRestrictions().getMaxSize()))
                            );
                            case INVALID_GAME_MODE -> {
                                LOGGER.error("Invalid gamemode " + mode.getFriendlyName());
                                yield MINI_MESSAGE.deserialize(CommonMatchmakerError.QUEUE_ERR_UNKNOWN, Placeholder.unparsed("mode", mode.getFriendlyName()));
                            }
                            case GAME_MODE_DISABLED -> {
                                LOGGER.error("Gamemode " + mode.getFriendlyName() + " is disabled");
                                yield MINI_MESSAGE.deserialize(CommonMatchmakerError.QUEUE_ERR_UNKNOWN, Placeholder.unparsed("mode", mode.getFriendlyName()));
                            }
                            default -> {
                                LOGGER.error("An unknown error occurred while queuing for " + mode.getFriendlyName(), throwable);
                                yield MINI_MESSAGE.deserialize(CommonMatchmakerError.QUEUE_ERR_UNKNOWN, Placeholder.unparsed("mode", mode.getFriendlyName()));
                            }
                        });
                    } catch (InvalidProtocolBufferException e) {
                        sender.sendMessage("An unknown error occurred while queuing for " + mode.getFriendlyName());
                        LOGGER.error("An unknown error occurred while queuing for " + mode.getFriendlyName(), throwable);
                    }
                }
        ), ForkJoinPool.commonPool());
    }

    private void handleUpdate(@NotNull ConfigUpdate<GameModeConfig> update) {
        switch (update.getType()) {
            case CREATE -> this.configs.add(update.getConfig());
            case DELETE -> this.configs.removeIf(config -> config.getFileName().equals(update.getFileName()));
            case MODIFY -> {
                this.configs.removeIf(config -> config.getFileName().equals(update.getFileName()));
                this.configs.add(update.getConfig());
            }
        }
    }
}
