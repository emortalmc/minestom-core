package dev.emortal.minestom.core.utils.command.argument;

import dev.emortal.api.model.badge.Badge;
import dev.emortal.api.service.badges.BadgeService;
import io.grpc.StatusRuntimeException;
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

public final class ArgumentBadge {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArgumentBadge.class);

    private final @NotNull BadgeService badgeService;
    private final @NotNull String id;
    private final boolean onlyOwned;

    public ArgumentBadge(@NotNull BadgeService badgeService, @NotNull String id, boolean onlyOwned) {
        this.badgeService = badgeService;
        this.id = id;
        this.onlyOwned = onlyOwned;
    }

    public static @NotNull ArgumentWord create(@NotNull BadgeService badgeManager, @NotNull String id, boolean onlyOwned) {
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
            this.handleOwnedBadgesSuggestion((Player) sender, input, suggestion);
        } else {
            this.handleDefaultSuggestion(input, suggestion);
        }
    }

    private void handleDefaultSuggestion(@NotNull String input, @NotNull Suggestion suggestion) {
        List<Badge> badges;
        try {
            badges = this.badgeService.getAllBadges();
        } catch (StatusRuntimeException exception) {
            LOGGER.error("Failed to get badges", exception);
            return;
        }

        this.addBadgeSuggestions(suggestion, badges, input);
    }

    private void handleOwnedBadgesSuggestion(@NotNull Player sender, @NotNull String input, @NotNull Suggestion suggestion) {
        List<Badge> badges;
        try {
            badges = this.badgeService.getPlayerBadges(sender.getUuid()).getBadgesList();
        } catch (StatusRuntimeException exception) {
            LOGGER.error("Failed to get badges for '{}'", sender.getUsername(), exception);
            return;
        }

        this.addBadgeSuggestions(suggestion, badges, input);
    }

    private void addBadgeSuggestions(@NotNull Suggestion suggestion, @NotNull List<Badge> badges, @NotNull String filter) {
        badges.stream()
                .filter(badge -> badge.getId().toLowerCase().startsWith(filter.toLowerCase()))
                .map(badge -> new SuggestionEntry(badge.getId(), Component.text(badge.getFriendlyName() + ": " + badge.getChatString())))
                .forEach(suggestion::addEntry);
    }
}
