package dev.emortal.minestom.core.module.core.badge;

import dev.emortal.api.service.badges.BadgeService;
import dev.emortal.api.service.badges.SetActiveBadgeResult;
import dev.emortal.api.service.mcplayer.McPlayerService;
import dev.emortal.minestom.core.utils.command.argument.ArgumentBadge;
import dev.emortal.minestom.core.utils.resolver.PlayerResolver;
import io.grpc.StatusRuntimeException;
import net.kyori.adventure.text.Component;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.command.builder.arguments.ArgumentLiteral;
import net.minestom.server.command.builder.condition.Conditions;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BadgeCommand extends Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(BadgeCommand.class);

    private final BadgeService badgeService;

    public BadgeCommand(@Nullable McPlayerService playerService, @NotNull PlayerResolver playerResolver, @NotNull BadgeService badgeService) {
        super("badge");
        this.badgeService = badgeService;

        this.setCondition(Conditions::playerOnly);
        this.setDefaultExecutor((sender, context) -> new BadgeGui(badgeService, (Player) sender));

        var setArgument = new ArgumentLiteral("set");
        var badgeArgument = ArgumentBadge.create(this.badgeService, "badge", true);

        this.addConditionalSyntax(Conditions::playerOnly, this::executeSetCurrentBadge, setArgument, badgeArgument);
        this.addSubcommand(new BadgeAdminSubcommand(playerService, playerResolver, this.badgeService));
    }

    private void executeSetCurrentBadge(@NotNull CommandSender sender, @NotNull CommandContext context) {
        Player player = (Player) sender;
        String badgeId = context.get("badge");

        SetActiveBadgeResult result;
        try {
            result = this.badgeService.setActiveBadge(player.getUuid(), badgeId);
        } catch (StatusRuntimeException exception) {
            LOGGER.error("Failed to set current badge for '{}' to '{}'", player.getUsername(), badgeId, exception);
            return;
        }

        switch (result) {
            case SUCCESS -> sender.sendMessage(Component.text("Set your badge to " + badgeId));
            case PLAYER_DOESNT_HAVE_BADGE -> sender.sendMessage(Component.text("You don't have that badge"));
        }
    }
}
