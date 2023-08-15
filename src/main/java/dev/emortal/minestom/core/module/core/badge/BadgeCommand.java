package dev.emortal.minestom.core.module.core.badge;

import dev.emortal.api.service.badges.BadgeService;
import dev.emortal.api.service.badges.SetActiveBadgeResult;
import dev.emortal.api.service.mcplayer.McPlayerService;
import dev.emortal.api.utils.GrpcStubCollection;
import dev.emortal.minestom.core.utils.command.argument.ArgumentBadge;
import io.grpc.StatusRuntimeException;
import net.kyori.adventure.text.Component;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.command.builder.arguments.ArgumentLiteral;
import net.minestom.server.command.builder.condition.Conditions;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BadgeCommand extends Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(BadgeCommand.class);

    private final BadgeService badgeService;

    public BadgeCommand(@NotNull BadgeService badgeService) {
        super("badge");
        this.badgeService = badgeService;

        this.setCondition(Conditions::playerOnly);
        this.setDefaultExecutor((sender, context) -> new BadgeGui((Player) sender, badgeService));

        var setArgument = new ArgumentLiteral("set");
        var badgeArgument = ArgumentBadge.create(this.badgeService, "badge", true);
        this.addConditionalSyntax(Conditions::playerOnly, this::executeSetCurrentBadge, setArgument, badgeArgument);

        McPlayerService mcPlayerService = GrpcStubCollection.getPlayerService().orElse(null);
        if (mcPlayerService != null) {
            this.addSubcommand(new BadgeAdminSubcommand(this.badgeService, mcPlayerService));
        } else {
            LOGGER.warn("MC player service unavailable. Badge admin command will not be registered.");
        }
    }

    private void executeSetCurrentBadge(CommandSender sender, CommandContext context) {
        String badgeId = context.get("badge");
        Player player = (Player) sender;

        SetActiveBadgeResult result;
        try {
            result = this.badgeService.setActiveBadge(player.getUuid(), badgeId);
        } catch (StatusRuntimeException exception) {
            LOGGER.error("Failed to set badge", exception);
            sender.sendMessage(Component.text("Failed to set badge"));
            return;
        }

        switch (result) {
            case SUCCESS -> sender.sendMessage(Component.text("Set your badge to " + badgeId));
            case PLAYER_DOESNT_HAVE_BADGE -> sender.sendMessage(Component.text("You don't have that badge"));
        }
    }
}
