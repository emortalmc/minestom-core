package dev.emortal.minestom.core.module.core.badge;

import dev.emortal.api.grpc.badge.BadgeManagerProto;
import dev.emortal.api.model.badge.Badge;
import dev.emortal.api.service.badges.BadgeService;
import io.grpc.StatusRuntimeException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minestom.server.entity.Player;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.inventory.click.ClickType;
import net.minestom.server.inventory.condition.InventoryConditionResult;
import net.minestom.server.item.ItemComponent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.AttributeList;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class BadgeGui {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Logger LOGGER = LoggerFactory.getLogger(BadgeGui.class);

    private static final Component TITLE = Component.text("Your Badges", NamedTextColor.DARK_GREEN);
    private static final String UNLOCKED_LINE = "<i:false><gray>Unlocked: <green>Yes";
    private static final String NOT_UNLOCKED_LINE = "<i:false><gray>Unlocked: <red>No";
    private static final String ACTIVE_LINE = "<i:false><gray>Active: <green>Yes";
    private static final String NOT_ACTIVE_LINE = "<i:false><gray>Active: <red>No";
    private static final String REQUIRED_LINE = "<i><red>This badge is required. You cannot disable it.";

    private static final Component CLICK_CANNOT_CHANGE_ACTIVE = MINI_MESSAGE.deserialize("<red>You cannot change your active badge as one of your badges is required.");

    private static final Tag<String> BADGE_ID_TAG = Tag.String("badge_id");
    private static final Tag<String> BADGE_NAME_TAG = Tag.String("badge_name");
    private static final Tag<Boolean> BADGE_ACTIVE_TAG = Tag.Boolean("badge_active");
    private static final Tag<Boolean> BADGE_UNLOCKED_TAG = Tag.Boolean("badge_unlocked");

    private final @NotNull BadgeService badgeService;
    private final @NotNull Inventory inventory;
    private final boolean canChangeActive;

    public BadgeGui(@NotNull BadgeService badgeService, @NotNull Player player) {
        this.badgeService = badgeService;
        this.inventory = new Inventory(InventoryType.CHEST_4_ROW, TITLE);

        List<Badge> allBadges;
        try {
            allBadges = this.badgeService.getAllBadges();
        } catch (StatusRuntimeException exception) {
            LOGGER.error("Failed to get all badges", exception);
            allBadges = List.of();
        }

        List<Badge> playerBadges;
        @Nullable String activeBadgeId;
        try {
            BadgeManagerProto.GetPlayerBadgesResponse response = this.badgeService.getPlayerBadges(player.getUuid());
            playerBadges = response.getBadgesList();
            activeBadgeId = response.getActiveBadgeId();
        } catch (StatusRuntimeException exception) {
            LOGGER.error("Failed to get badges for '{}'", player.getUsername(), exception);
            playerBadges = List.of();
            activeBadgeId = null;
        }

        Set<String> ownedBadgeIds = playerBadges.stream()
                .map(Badge::getId)
                .collect(Collectors.toUnmodifiableSet());

        boolean canChangeActive = true;
        for (Badge badge : allBadges) {
            if (badge.getRequired() && ownedBadgeIds.contains(badge.getId())) { // badge is required and player owns it
                canChangeActive = false;
                break;
            }
        }
        this.canChangeActive = canChangeActive;

        this.drawInventory(allBadges, ownedBadgeIds, activeBadgeId);
        this.inventory.addInventoryCondition(this::onClick);
        player.openInventory(this.inventory);
    }

    private void drawInventory(@NotNull List<Badge> allBadges, @NotNull Set<String> ownedBadgeIds, @Nullable String activeBadgeId) {
        List<Badge> badges = allBadges.stream()
                .filter(badge -> !badge.getGuiItem().getMaterial().equals("minecraft:air"))
                .sorted(Comparator.comparingLong(Badge::getPriority))
                .toList();

        for (int i = 0; i < badges.size(); i++) {
            Badge badge = badges.get(i);

            boolean isOwned = ownedBadgeIds.contains(badge.getId());
            boolean isActive = badge.getId().equals(activeBadgeId);

            this.inventory.setItemStack(i, this.createItemStack(badge, isOwned, isActive));
        }
    }

    private void onClick(@NotNull Player clicker, int slot, @NotNull ClickType clickType, @NotNull InventoryConditionResult result) {
        if (slot < 0 || slot >= this.inventory.getSize()) return;

        ItemStack clickedItem = this.inventory.getItemStack(slot);
        if (clickedItem.isAir()) return;

        result.setCancel(true);

        if (!this.canChangeActive) {
            clicker.sendMessage(CLICK_CANNOT_CHANGE_ACTIVE);
            return;
        }


        boolean isUnlocked = clickedItem.getTag(BADGE_UNLOCKED_TAG);
        boolean isActive = clickedItem.getTag(BADGE_ACTIVE_TAG);
        if (!isUnlocked || isActive) return;

        String badgeId = clickedItem.getTag(BADGE_ID_TAG);
        try {
            this.badgeService.setActiveBadge(clicker.getUuid(), badgeId);
        } catch (StatusRuntimeException exception) {
            LOGGER.error("Failed to set active badge for '{}' to '{}'", clicker.getUsername(), badgeId);
            clicker.sendMessage(Component.text("An unknown error occurred", NamedTextColor.RED));
            return;
        }

        String badgeName = clickedItem.getTag(BADGE_NAME_TAG);
        clicker.sendMessage(Component.text("Set active badge to " + badgeName, NamedTextColor.GREEN));
        new BadgeGui(this.badgeService, clicker); // Reopen the gui
    }

    private @NotNull ItemStack createItemStack(@NotNull Badge badge, boolean isOwned, boolean isActive) {
        Badge.GuiItem guiItem = badge.getGuiItem();

        List<Component> lore = new ArrayList<>();

        // Unlocked: Yes/No
        lore.add(MINI_MESSAGE.deserialize(isOwned ? UNLOCKED_LINE : NOT_UNLOCKED_LINE));

        if (isOwned) {
            // Active: Yes/No
            lore.add(MINI_MESSAGE.deserialize(isActive ? ACTIVE_LINE : NOT_ACTIVE_LINE));
        }

        lore.add(Component.empty());

        for (String line : guiItem.getLoreList()) {
            lore.add(MINI_MESSAGE.deserialize(line));
        }

        if (isActive && badge.getRequired()) {
            lore.add(Component.empty());
            lore.add(MINI_MESSAGE.deserialize(REQUIRED_LINE));
        }

        return ItemStack.builder(Material.fromNamespaceId(guiItem.getMaterial()))
                .set(ItemComponent.ITEM_NAME, MINI_MESSAGE.deserialize(guiItem.getDisplayName()))
                .set(ItemComponent.LORE, lore)
                .set(BADGE_ID_TAG, badge.getId())
                .set(BADGE_NAME_TAG, badge.getFriendlyName())
                .set(BADGE_ACTIVE_TAG, isActive)
                .set(BADGE_UNLOCKED_TAG, isOwned)
                .set(ItemComponent.ATTRIBUTE_MODIFIERS, new AttributeList(List.of(), false)
                ).build();
    }
}
