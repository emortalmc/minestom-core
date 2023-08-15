package dev.emortal.minestom.core.module.core.badge;

import dev.emortal.api.grpc.mcplayer.McPlayerProto;
import dev.emortal.api.model.mcplayer.McPlayer;
import dev.emortal.api.service.badges.AddBadgeToPlayerResult;
import dev.emortal.api.service.badges.BadgeService;
import dev.emortal.api.service.badges.RemoveBadgeFromPlayerResult;
import dev.emortal.api.service.mcplayer.McPlayerService;
import dev.emortal.minestom.core.utils.command.argument.ArgumentBadge;
import dev.emortal.minestom.core.utils.command.argument.ArgumentMcPlayer;
import io.grpc.StatusRuntimeException;
import net.kyori.adventure.text.Component;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.command.builder.arguments.ArgumentLiteral;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.function.Supplier;

final class BadgeAdminSubcommand extends Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(BadgeAdminSubcommand.class);

    private final BadgeService badgeService;

    BadgeAdminSubcommand(@NotNull BadgeService badgeService, @NotNull McPlayerService mcPlayerService) {
        super("admin");
        this.badgeService = badgeService;

        this.setCondition((sender, $) -> sender.hasPermission("command.badge.admin"));

        var addArgument = new ArgumentLiteral("add");
        var removeArgument = new ArgumentLiteral("remove");
        var playerArgument = ArgumentMcPlayer.create("player", mcPlayerService, McPlayerProto.SearchPlayersByUsernameRequest.FilterMethod.NONE);
        var badgeArgument = ArgumentBadge.create(badgeService, "badge", false);

        this.addSyntax(this::executeAddBadgeToPlayer, addArgument, playerArgument, badgeArgument);
        this.addSyntax(this::executeRemoveBadgeFromPlayer, removeArgument, playerArgument, badgeArgument);
    }

    private void executeAddBadgeToPlayer(@NotNull CommandSender sender, @NotNull CommandContext context) {
        McPlayer player = getPlayer(context);
        String badgeId = context.get("badge");

        AddBadgeToPlayerResult result;
        try {
            result = this.badgeService.addBadgeToPlayer(UUID.fromString(player.getId()), badgeId);
        } catch (StatusRuntimeException exception) {
            LOGGER.error("Failed to add badge to player", exception);
            sender.sendMessage(Component.text("Failed to add badge to player"));
            return;
        }

        switch (result) {
            case SUCCESS -> sender.sendMessage(Component.text("Added badge to player"));
            case PLAYER_ALREADY_HAS_BADGE -> sender.sendMessage(Component.text("Player already has that badge"));
        }
    }

    private void executeRemoveBadgeFromPlayer(@NotNull CommandSender sender, @NotNull CommandContext context) {
        McPlayer player = getPlayer(context);
        String badgeId = context.get("badge");

        RemoveBadgeFromPlayerResult result;
        try {
            result = this.badgeService.removeBadgeFromPlayer(UUID.fromString(player.getId()), badgeId);
        } catch (StatusRuntimeException exception) {
            LOGGER.error("Failed to remove badge from player", exception);
            sender.sendMessage(Component.text("Failed to remove badge from player"));
            return;
        }

        switch (result) {
            case SUCCESS -> sender.sendMessage(Component.text("Removed badge from player"));
            case PLAYER_DOESNT_HAVE_BADGE -> sender.sendMessage(Component.text("Player doesn't have that badge"));
        }
    }

    private static @NotNull McPlayer getPlayer(@NotNull CommandContext context) {
        Supplier<McPlayer> supplier = context.get("player");
        return supplier.get();
    }
}
