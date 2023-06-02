package dev.emortal.minestom.core.module.core.badge;

import com.google.common.util.concurrent.Futures;
import dev.emortal.api.grpc.badge.BadgeManagerGrpc;
import dev.emortal.api.grpc.badge.BadgeManagerProto;
import dev.emortal.api.model.badge.Badge;
import dev.emortal.api.utils.GrpcStubCollection;
import dev.emortal.api.utils.callback.FunctionalFutureCallback;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minestom.server.entity.Player;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemHideFlag;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

public class BadgeGui {
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

    private final BadgeManagerGrpc.BadgeManagerFutureStub badgeService = GrpcStubCollection.getBadgeManagerService().orElse(null);

    private final @NotNull Inventory inventory;
    private final boolean canChangeActive;

    public BadgeGui(@NotNull Player player) {
        this.inventory = new Inventory(InventoryType.CHEST_4_ROW, TITLE);

        var badgesReqFuture = this.badgeService.getBadges(BadgeManagerProto.GetBadgesRequest.newBuilder().build());
        var playerBadgesReqFuture = this.badgeService.getPlayerBadges(BadgeManagerProto.GetPlayerBadgesRequest.newBuilder()
                .setPlayerId(player.getUuid().toString()).build());

        BadgeManagerProto.GetBadgesResponse badgesResp;
        BadgeManagerProto.GetPlayerBadgesResponse playerBadgesResp;
        try {
            badgesResp = badgesReqFuture.get();
            playerBadgesResp = playerBadgesReqFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

        Set<String> ownedBadgeIds = playerBadgesResp.getBadgesList().stream().map(Badge::getId)
                .collect(Collectors.toUnmodifiableSet());

        boolean canChangeActive = true;
        for (Badge badge : badgesResp.getBadgesList()) {
            if (badge.getRequired() && ownedBadgeIds.contains(badge.getId())) { // badge is required and player owns it
                canChangeActive = false;
                break;
            }
        }
        this.canChangeActive = canChangeActive;

        this.drawInventory(badgesResp, ownedBadgeIds, playerBadgesResp.getActiveBadgeId());

        this.inventory.addInventoryCondition((clicker, slot, clickType, inventoryConditionResult) -> {
            if (slot < 0 || slot >= this.inventory.getSize()) return;

            ItemStack clickedItem = this.inventory.getItemStack(slot);
            if (clickedItem.isAir()) return;

            inventoryConditionResult.setCancel(true);

            if (!this.canChangeActive) {
                clicker.sendMessage(CLICK_CANNOT_CHANGE_ACTIVE);
                return;
            }

            boolean isOwned = clickedItem.meta().getTag(BADGE_UNLOCKED_TAG);
            if (!isOwned) return;

            boolean isActive = clickedItem.meta().getTag(BADGE_ACTIVE_TAG);
            if (isActive) return;

            String badgeId = clickedItem.meta().getTag(BADGE_ID_TAG);
            var setBadgeReqFuture = this.badgeService.setActivePlayerBadge(BadgeManagerProto.SetActivePlayerBadgeRequest.newBuilder()
                    .setPlayerId(player.getUuid().toString())
                    .setBadgeId(badgeId)
                    .build());

            Futures.addCallback(setBadgeReqFuture, FunctionalFutureCallback.create(
                    unused -> {
                        String badgeName = clickedItem.meta().getTag(BADGE_NAME_TAG);
                        clicker.sendMessage(Component.text("Set active badge to " + badgeName, NamedTextColor.GREEN));
                        new BadgeGui(clicker); // Reopen the gui
                    },
                    throwable -> {
                        LOGGER.error("Failed to set active badge for {}: {}", clicker.getUsername(), throwable);
                        clicker.sendMessage(Component.text("Failed to set active badge", NamedTextColor.RED));
                    }
            ), ForkJoinPool.commonPool());
        });

        player.openInventory(this.inventory);
    }

    private void drawInventory(@NotNull BadgeManagerProto.GetBadgesResponse badgesResp,
                               @NotNull Set<String> ownedBadgeIds, String activeBadgeId) {

        List<Badge> badges = badgesResp.getBadgesList().stream()
                .sorted(Comparator.comparingLong(Badge::getPriority))
                .toList();

        for (int i = 0; i < badges.size(); i++) {
            Badge badge = badges.get(i);

            boolean isOwned = ownedBadgeIds.contains(badge.getId());
            boolean isActive = badge.getId().equals(activeBadgeId);

            this.inventory.setItemStack(i, this.createItemStack(badge, isOwned, isActive));
        }
    }

    private @NotNull ItemStack createItemStack(@NotNull Badge badge, boolean isOwned, boolean isActive) {
        Badge.GuiItem guiItem = badge.getGuiItem();

        List<Component> lore = new ArrayList<>();
        lore.add(MINI_MESSAGE.deserialize(isOwned ? UNLOCKED_LINE : NOT_UNLOCKED_LINE));
        if (isOwned) lore.add(MINI_MESSAGE.deserialize(isActive ? ACTIVE_LINE : NOT_ACTIVE_LINE));
        lore.add(Component.empty());

        guiItem.getLoreList().stream().map(MINI_MESSAGE::deserialize).forEach(lore::add);

        if (isActive && badge.getRequired()) {
            lore.add(Component.empty());
            lore.add(MINI_MESSAGE.deserialize(REQUIRED_LINE));
        }

        return ItemStack.builder(Material.fromNamespaceId(guiItem.getMaterial()))
                .displayName(MINI_MESSAGE.deserialize(guiItem.getDisplayName()))
                .lore(lore)
                .meta(builder -> builder.hideFlag(ItemHideFlag.HIDE_ATTRIBUTES)
                        .set(BADGE_ID_TAG, badge.getId())
                        .set(BADGE_NAME_TAG, badge.getFriendlyName())
                        .set(BADGE_ACTIVE_TAG, isActive)
                        .set(BADGE_UNLOCKED_TAG, isOwned)
                )
                .build();
    }
}