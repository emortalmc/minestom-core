package dev.emortal.minestom.core.module.matchmaker.commands;

import dev.emortal.api.service.matchmaker.DequeuePlayerResult;
import dev.emortal.api.service.matchmaker.MatchmakerService;
import dev.emortal.minestom.core.module.matchmaker.CommonMatchmakerError;
import io.grpc.StatusRuntimeException;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.command.builder.condition.Conditions;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DequeueCommand extends Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(DequeueCommand.class);

    private final MatchmakerService matchmaker;

    public DequeueCommand(@NotNull MatchmakerService matchmaker) {
        super("dequeue");
        this.matchmaker = matchmaker;

        this.setCondition(Conditions::playerOnly);
        this.setDefaultExecutor(this::execute);
    }

    private void execute(CommandSender sender, CommandContext context) {
        Player player = (Player) sender;

        DequeuePlayerResult result;
        try {
            result = this.matchmaker.dequeuePlayer(player.getUuid());
        } catch (StatusRuntimeException exception) {
            LOGGER.error("Failed to dequeue player for unknown reason (id: {})", player.getUuid(), exception);
            player.sendMessage(CommonMatchmakerError.DEQUEUE_ERR_UNKNOWN);
            return;
        }

        var message = switch (result) {
            case SUCCESS -> CommonMatchmakerError.DEQUEUE_SUCCESS;
            case NOT_IN_QUEUE -> CommonMatchmakerError.DEQUEUE_ERR_NOT_IN_QUEUE;
            case NO_PERMISSION -> CommonMatchmakerError.DEQUEUE_ERR_NO_PERMISSION;
            case ALREADY_MARKED_FOR_DEQUEUE -> CommonMatchmakerError.DEQUEUE_ERR_ALREADY_MARKED;
        };
        player.sendMessage(message);
    }
}
