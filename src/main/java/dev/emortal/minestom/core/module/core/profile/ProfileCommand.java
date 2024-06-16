package dev.emortal.minestom.core.module.core.profile;

import dev.emortal.api.grpc.mcplayer.McPlayerProto;
import dev.emortal.api.model.mcplayer.McPlayer;
import dev.emortal.api.service.mcplayer.McPlayerService;
import dev.emortal.minestom.core.utils.command.argument.ArgumentMcPlayer;
import dev.emortal.minestom.core.utils.resolver.LocalMcPlayer;
import dev.emortal.minestom.core.utils.resolver.PlayerResolver;
import io.grpc.StatusRuntimeException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentString;
import net.minestom.server.entity.Player;
import net.minestom.server.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.function.Supplier;

public class ProfileCommand extends Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileCommand.class);

    private final @NotNull McPlayerService playerService;

    public ProfileCommand(@NotNull McPlayerService playerService, @NotNull PlayerResolver playerResolver) {
        super("profile");

        this.playerService = playerService;

        var playerArgument = ArgumentMcPlayer.create("player", playerService, playerResolver, McPlayerProto.SearchPlayersByUsernameRequest.FilterMethod.NONE);

        this.setCondition((executor, context) -> {
            if (!(executor instanceof Player player)) {
                LOGGER.error("Command sender is not a player");
                return false;
            }

            return player.hasPermission("flag.command.profile");
        });

        this.setDefaultExecutor((commandSender, context) -> {
            if (!(commandSender instanceof Player player)) {
                LOGGER.error("Command sender is not a player");
                return;
            }

            this.execute(player, player.getUuid());
        });

        this.addSyntax((commandSender, context) -> {
            if (!(commandSender instanceof Player player)) {
                LOGGER.error("Command sender is not a player");
                return;
            }

            LocalMcPlayer target = context.<Supplier<LocalMcPlayer>>get("player").get();
            if (target == null) {
                player.sendMessage(Component.text("Player '%s' not found".formatted(context.getRaw("player")), NamedTextColor.RED));
                return;
            }

            this.execute(player, target.uuid());
        }, playerArgument);
    }

    private void execute(@NotNull Player executor, @NotNull UUID targetId) {
        McPlayer player;
        try {
            player = this.playerService.getPlayerById(targetId);
        } catch (StatusRuntimeException exception) {
            LOGGER.error("Failed to get player to open profile of target {}", targetId, exception);
            return;
        }

        if (player == null) {
            LOGGER.error("Player not found for target (is null) {}", targetId);
            return;
        }

        Inventory inventory = new ProfileGui(player);
        executor.openInventory(inventory);
    }
}
