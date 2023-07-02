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

public final class ArgumentBadge {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArgumentBadge.class);

    private final BadgeManagerGrpc.BadgeManagerFutureStub badgeManager;

    private final String id;
    private final boolean onlyOwned;

    public ArgumentBadge(@NotNull BadgeManagerGrpc.BadgeManagerFutureStub badgeManager, @NotNull String id, boolean onlyOwned) {
        this.badgeManager = badgeManager;
        this.id = id;
        this.onlyOwned = onlyOwned;
    }

    public static @NotNull ArgumentWord create(@NotNull BadgeManagerGrpc.BadgeManagerFutureStub badgeManager, @NotNull String id, boolean onlyOwned) {
        var handlerArgument = new ArgumentBadge(badgeManager, id, onlyOwned);
        var createdArgument = new ArgumentWord(id);

        createdArgument.setSuggestionCallback(handlerArgument::suggestionCallback);
        return createdArgument;
    }

    public void suggestionCallback(@NotNull CommandSender sender, @NotNull CommandContext context, @NotNull Suggestion suggestion) {
        String input = context.getRaw(this.id);

        if (this.onlyOwned && !(sender instanceof Player)) {
            LOGGER.error("Validation misconfiguration: onlyOwned is true but sender is not a player");
            return;
        }

        if (input.length() == 1 && input.charAt(0) == 0) {
            input = "";
        }

        if (this.onlyOwned) {
            handleOwnedBadgesSuggestion((Player) sender, input, suggestion);
        } else {
            handleDefaultSuggestion(input, suggestion);
        }
    }

    private void handleDefaultSuggestion(@NotNull String input, @NotNull Suggestion suggestion) {
        try {
            var response = this.badgeManager.getBadges(BadgeManagerProto.GetBadgesRequest.getDefaultInstance()).get();
            addBadgeSuggestions(suggestion, response.getBadgesList(), input);
        } catch (InterruptedException | ExecutionException exception) {
            LOGGER.error("Failed to get badges", exception);
        }
    }

    private void handleOwnedBadgesSuggestion(@NotNull Player sender, @NotNull String input, @NotNull Suggestion suggestion) {
        try {
            var request = BadgeManagerProto.GetPlayerBadgesRequest.newBuilder().setPlayerId(sender.getUuid().toString()).build();
            addBadgeSuggestions(suggestion, this.badgeManager.getPlayerBadges(request).get().getBadgesList(), input);
        } catch (InterruptedException | ExecutionException exception) {
            LOGGER.error("Failed to get badges", exception);
        }
    }

    private void addBadgeSuggestions(@NotNull Suggestion suggestion, @NotNull List<Badge> badges, @NotNull String filter) {
        badges.stream()
                .filter(badge -> badge.getId().toLowerCase().startsWith(filter.toLowerCase()))
                .map(badge -> new SuggestionEntry(badge.getId(), Component.text(badge.getFriendlyName() + ": " + badge.getChatString())))
                .forEach(suggestion::addEntry);
    }
}
