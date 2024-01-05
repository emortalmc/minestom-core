package dev.emortal.minestom.core.module.core.badge;

import dev.emortal.api.grpc.mcplayer.McPlayerProto.SearchPlayersByUsernameRequest.FilterMethod;
import dev.emortal.api.service.badges.AddBadgeToPlayerResult;
import dev.emortal.api.service.badges.BadgeService;
import dev.emortal.api.service.badges.RemoveBadgeFromPlayerResult;
import dev.emortal.api.service.mcplayer.McPlayerService;
import dev.emortal.minestom.core.utils.command.ExtraConditions;
import dev.emortal.minestom.core.utils.command.argument.ArgumentBadge;
import dev.emortal.minestom.core.utils.command.argument.ArgumentMcPlayer;
import dev.emortal.minestom.core.utils.resolver.LocalMcPlayer;
import dev.emortal.minestom.core.utils.resolver.PlayerResolver;
import io.grpc.StatusRuntimeException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.command.builder.arguments.ArgumentLiteral;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

public final class BadgeAdminSubcommand extends Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(BadgeAdminSubcommand.class);

    private final BadgeService badgeService;

    public BadgeAdminSubcommand(@Nullable McPlayerService playerService, @NotNull PlayerResolver playerResolver, @NotNull BadgeService badgeService) {
        super("admin");
        this.badgeService = badgeService;

        this.setCondition(ExtraConditions.hasPermission("command.badge.admin"));

        var playerArgument = ArgumentMcPlayer.create("player", playerService, playerResolver, FilterMethod.NONE);
        var badgeArgument = ArgumentBadge.create(badgeService, "badge", false);

        this.addSyntax(this::executeAddBadgeToPlayer, new ArgumentLiteral("add"), playerArgument, badgeArgument);
        this.addSyntax(this::executeRemoveBadgeFromPlayer, new ArgumentLiteral("remove"), playerArgument, badgeArgument);
    }

    private void executeAddBadgeToPlayer(@NotNull CommandSender sender, @NotNull CommandContext context) {
        LocalMcPlayer player = context.<Supplier<LocalMcPlayer>>get("player").get();
        String badgeId = context.get("badge");

        if (player == null) {
            sender.sendMessage(Component.text("Player %s not found".formatted(context.getRaw("player")), NamedTextColor.RED));
            return;
        }

        AddBadgeToPlayerResult result;
        try {
            result = this.badgeService.addBadgeToPlayer(player.uuid(), badgeId);
        } catch (StatusRuntimeException exception) {
            LOGGER.error("Failed to add badge '{}' to '{}'", badgeId, player.username(), exception);
            return;
        }

        switch (result) {
            case SUCCESS -> sender.sendMessage(Component.text("Added badge to player"));
            case PLAYER_ALREADY_HAS_BADGE -> sender.sendMessage(Component.text("Player already has that badge"));
        }
    }

    private void executeRemoveBadgeFromPlayer(@NotNull CommandSender sender, @NotNull CommandContext context) {
        LocalMcPlayer player = context.<Supplier<LocalMcPlayer>>get("player").get();
        String badgeId = context.get("badge");

        RemoveBadgeFromPlayerResult result;
        try {
            result = this.badgeService.removeBadgeFromPlayer(player.uuid(), badgeId);
        } catch (StatusRuntimeException exception) {
            LOGGER.error("Failed to remove badge '{}' from '{}'", badgeId, player.username(), exception);
            return;
        }

        switch (result) {
            case SUCCESS -> sender.sendMessage(Component.text("Removed badge from player"));
            case PLAYER_DOESNT_HAVE_BADGE -> sender.sendMessage(Component.text("Player doesn't have that badge"));
        }
    }
}
