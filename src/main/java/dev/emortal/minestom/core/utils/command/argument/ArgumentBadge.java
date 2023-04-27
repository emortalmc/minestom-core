package dev.emortal.minestom.core.utils.command.argument;

import dev.emortal.api.grpc.badge.BadgeManagerGrpc;
import dev.emortal.api.grpc.badge.BadgeManagerProto;
import dev.emortal.api.model.badge.Badge;
import net.kyori.adventure.text.Component;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.command.builder.arguments.ArgumentWord;
import net.minestom.server.command.builder.suggestion.Suggestion;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class ArgumentBadge {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArgumentBadge.class);

    private final BadgeManagerGrpc.BadgeManagerFutureStub badgeManager;

    private final @NotNull String id;
    private final boolean onlyOwned;

    public ArgumentBadge(BadgeManagerGrpc.BadgeManagerFutureStub badgeManager, @NotNull String id, boolean onlyOwned) {
        this.badgeManager = badgeManager;
        this.id = id;
        this.onlyOwned = onlyOwned;
    }

    public static @NotNull ArgumentWord create(BadgeManagerGrpc.BadgeManagerFutureStub badgeManager, String id, boolean onlyOwned) {
        ArgumentBadge handlerArgument = new ArgumentBadge(badgeManager, id, onlyOwned);
        ArgumentWord createdArgument = new ArgumentWord(id);

        createdArgument.setSuggestionCallback(handlerArgument::suggestionCallback);

        return createdArgument;
    }

    public void suggestionCallback(@NotNull CommandSender sender, @NotNull CommandContext context, @NotNull Suggestion suggestion) {
        String input = context.getRaw(this.id);

        if (this.onlyOwned && !(sender instanceof Player)) {
            LOGGER.error("Validation misconfiguration: onlyOwned is true but sender is not a player");
            return;
        }

        if (this.onlyOwned) {
            Player player = (Player) sender;
            handleOwnedBadgesSuggestion(player, input, suggestion);
        } else {
            handleDefaultSuggestion(input, suggestion);
        }
    }

    private void handleDefaultSuggestion(@NotNull String input, @NotNull Suggestion suggestion) {
        try {
            BadgeManagerProto.GetBadgesResponse response = this.badgeManager
                    .getBadges(BadgeManagerProto.GetBadgesRequest.getDefaultInstance()).get();

            this.addBadgeSuggestions(suggestion, response.getBadgesList(), input);
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.error("Failed to get badges", e);
        }
    }

    private void handleOwnedBadgesSuggestion(@NotNull Player sender, @NotNull String input, @NotNull Suggestion suggestion) {
        try {
            BadgeManagerProto.GetPlayerBadgesResponse response = this.badgeManager.getPlayerBadges(
                    BadgeManagerProto.GetPlayerBadgesRequest.newBuilder()
                            .setPlayerId(sender.getUuid().toString()).build()
            ).get();

            this.addBadgeSuggestions(suggestion, response.getBadgesList(), input);
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.error("Failed to get badges", e);
        }
    }

    private void addBadgeSuggestions(@NotNull Suggestion suggestion, @NotNull List<Badge> badges, @NotNull String filter) {
        badges.stream()
                .filter(badge -> badge.getId().toLowerCase().startsWith(filter.toLowerCase()))
                .map(badge -> new SuggestionEntry(badge.getId(),
                        Component.text(badge.getFriendlyName() + ": " + badge.getChatCharacter())))
                .forEach(suggestion::addEntry);
    }
}
