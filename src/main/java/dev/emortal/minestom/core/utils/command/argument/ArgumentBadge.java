package dev.emortal.minestom.core.utils.command.argument;

import dev.emortal.api.model.badge.Badge;
import dev.emortal.api.service.badges.BadgeService;
import net.kyori.adventure.text.Component;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.command.builder.arguments.Argument;
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

    public static @NotNull Argument<String> create(@NotNull BadgeService badgeService, @NotNull String id, boolean onlyOwned) {
        var handler = new ArgumentBadge(badgeService, id, onlyOwned);
        return new ArgumentWord(id).setSuggestionCallback(handler::suggestionCallback);
    }

    private final BadgeService badgeService;
    private final String id;
    private final boolean onlyOwned;

    private ArgumentBadge(@NotNull BadgeService badgeService, @NotNull String id, boolean onlyOwned) {
        this.badgeService = badgeService;
        this.id = id;
        this.onlyOwned = onlyOwned;
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
        List<Badge> badges = this.badgeService.getAllBadges();
        this.addBadgeSuggestions(suggestion, badges, input);
    }

    private void handleOwnedBadgesSuggestion(@NotNull Player sender, @NotNull String input, @NotNull Suggestion suggestion) {
        this.addBadgeSuggestions(suggestion, this.badgeService.getPlayerBadges(sender.getUuid()).getBadgesList(), input);
    }

    private void addBadgeSuggestions(@NotNull Suggestion suggestion, @NotNull List<Badge> badges, @NotNull String filter) {
        badges.stream()
                .filter(badge -> badge.getId().toLowerCase().startsWith(filter.toLowerCase()))
                .map(badge -> new SuggestionEntry(badge.getId(), Component.text(badge.getFriendlyName() + ": " + badge.getChatString())))
                .forEach(suggestion::addEntry);
    }
}
