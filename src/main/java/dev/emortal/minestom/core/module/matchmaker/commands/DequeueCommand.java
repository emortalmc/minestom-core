package dev.emortal.minestom.core.module.matchmaker.commands;

import dev.emortal.api.service.matchmaker.DequeuePlayerResult;
import dev.emortal.api.service.matchmaker.MatchmakerService;
import dev.emortal.minestom.core.module.matchmaker.CommonMatchmakerError;
import io.grpc.StatusRuntimeException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
            LOGGER.error("Failed to dequeue '{}'", player.getUsername(), exception);
            player.sendMessage(Component.text("An unknown error occurred", NamedTextColor.RED));
            return;
        }

        switch (result) {
            case SUCCESS -> player.sendMessage(CommonMatchmakerError.DEQUEUE_SUCCESS);
            case NOT_IN_QUEUE -> player.sendMessage(CommonMatchmakerError.DEQUEUE_ERR_NOT_IN_QUEUE);
            case NO_PERMISSION -> player.sendMessage(CommonMatchmakerError.DEQUEUE_ERR_NO_PERMISSION);
            case ALREADY_MARKED_FOR_DEQUEUE -> player.sendMessage(CommonMatchmakerError.DEQUEUE_ERR_ALREADY_MARKED);
        }
    }
}
