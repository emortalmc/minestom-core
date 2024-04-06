package dev.emortal.minestom.core.module.core.profile;

import dev.emortal.api.grpc.mcplayer.McPlayerProto;
import dev.emortal.api.model.mcplayer.McPlayer;
import dev.emortal.api.service.mcplayer.McPlayerService;
import dev.emortal.minestom.core.utils.command.argument.ArgumentMcPlayer;
import dev.emortal.minestom.core.utils.resolver.LocalMcPlayer;
import dev.emortal.minestom.core.utils.resolver.PlayerResolver;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentString;
import net.minestom.server.entity.Player;
import net.minestom.server.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

            this.execute(player, player.getUsername());
        });

        this.addSyntax((commandSender, context) -> {
            if (!(commandSender instanceof Player player)) {
                LOGGER.error("Command sender is not a player");
                return;
            }

            LocalMcPlayer target = context.<Supplier<LocalMcPlayer>>get("player").get();
            this.execute(player, target.username());
        }, playerArgument);
    }

    private void execute(@NotNull Player executor, @NotNull String targetUsername) {
        McPlayer player = this.playerService.getPlayerByUsername(targetUsername);

        Inventory inventory = new ProfileGui(player);
        executor.openInventory(inventory);
    }
}
