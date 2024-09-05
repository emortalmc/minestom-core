package dev.emortal.minestom.core.module.core.profile;

import dev.emortal.api.model.mcplayer.LoginSession;
import dev.emortal.api.model.mcplayer.McPlayer;
import dev.emortal.api.utils.EmortalXP;
import dev.emortal.api.utils.ProtoDurationConverter;
import dev.emortal.api.utils.ProtoTimestampConverter;
import dev.emortal.minestom.core.utils.DurationFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.entity.PlayerSkin;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemComponent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.HeadProfile;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

public class ProfileGui extends Inventory {
    private static final int[] BLOCKED_SLOTS = new int[]{0, 1, 2, 3, 5, 6, 7, 8,
            9, 10, 16, 17,
            18, 26,
            27, 28, 34, 35, 36, 37, 38, 39, 41, 42, 43, 44};
    private static final ItemStack BLOCKED_ITEM = ItemStack.builder(Material.BLACK_STAINED_GLASS_PANE)
            .set(ItemComponent.CUSTOM_NAME, Component.text(" "))
            .build();

    private static final int PLAYER_ICON_SLOT = 4;
    private static final int ACHIEVEMENTS_SLOT = 21;
    private static final int GAME_HISTORY_SLOT = 23;

    private static final SimpleDateFormat FIRST_LOGIN_FORMAT = new SimpleDateFormat("yyyy-MM-dd hh:mm");

    private final @NotNull McPlayer targetPlayer;

    public ProfileGui(@NotNull McPlayer targetPlayer) {
        super(InventoryType.CHEST_5_ROW, "Profile");
        this.targetPlayer = targetPlayer;

        super.addInventoryCondition((player, i, clickType, inventoryConditionResult) -> inventoryConditionResult.setCancel(true));

        for (int slot : BLOCKED_SLOTS) {
            super.setItemStack(slot, BLOCKED_ITEM);
        }

        super.setItemStack(PLAYER_ICON_SLOT, this.createPlayerIcon());
        super.setItemStack(ACHIEVEMENTS_SLOT, this.createAchievementsIcon());
        super.setItemStack(GAME_HISTORY_SLOT, this.createGameHistoryIcon());
    }

    private ItemStack createPlayerIcon() {
        dev.emortal.api.model.common.PlayerSkin skin = this.targetPlayer.getCurrentSkin();
        HeadProfile headProfile = new HeadProfile(new PlayerSkin(skin.getTexture(), skin.getSignature()));

        return ItemStack.builder(Material.PLAYER_HEAD)
                .set(ItemComponent.PROFILE, headProfile)
                .set(ItemComponent.CUSTOM_NAME, Component.text(this.targetPlayer.getCurrentUsername(), NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false))
                .lore(
                        Component.empty(),
                        this.levelLine().decoration(TextDecoration.ITALIC, false),
                        this.firstLoginLine().decoration(TextDecoration.ITALIC, false),
                        this.lastOnlineLine().decoration(TextDecoration.ITALIC, false),
                        this.playtimeLine().decoration(TextDecoration.ITALIC, false)
                )
                .build();
    }

    private Component levelLine() {
        long xp = this.targetPlayer.getExperience();
        int level = EmortalXP.toLevel(xp);
        long xpOfLevel = EmortalXP.toXp(level);

        long xpOfNextLevel = EmortalXP.toXp(level + 1);
        long xpIntoNextLevel = xp - xpOfLevel;
        float progress = (float) xpIntoNextLevel / (xpOfNextLevel - xpOfLevel);

        return Component.text("Level: ", NamedTextColor.GRAY)
                .append(Component.text("%.2f".formatted(level + progress), NamedTextColor.GOLD));
    }

    private Component playtimeLine() {
        Duration playtime = ProtoDurationConverter.fromProto(this.targetPlayer.getHistoricPlayTime());

        LoginSession currentSession = this.targetPlayer.getCurrentSession();
        Duration currentSessionDuration = Duration.between(
                ProtoTimestampConverter.fromProto(currentSession.getLoginTime()),
                Instant.now()
        );
        playtime = playtime.plus(currentSessionDuration);

        String playtimeFormatted = DurationFormatter.ofGreatestUnits(playtime, 3, ChronoUnit.SECONDS);

        return Component.text("Playtime: ", NamedTextColor.GRAY)
                .append(Component.text(playtimeFormatted, NamedTextColor.GOLD));
    }

    private Component firstLoginLine() {
        Instant firstLogin = ProtoTimestampConverter.fromProto(this.targetPlayer.getFirstLogin());

        return Component.text("First Login: ", NamedTextColor.GRAY)
                .append(Component.text(FIRST_LOGIN_FORMAT.format(Date.from(firstLogin)), NamedTextColor.GOLD));
    }

    private Component lastOnlineLine() {
        TextComponent.Builder builder = Component.text()
                .append(Component.text("Last Online: ", NamedTextColor.GRAY));

        if (this.targetPlayer.hasCurrentServer()) {
            return builder.append(Component.text("Online", NamedTextColor.GREEN)).build();
        } else {
            Instant lastOnline = ProtoTimestampConverter.fromProto(this.targetPlayer.getLastOnline());
            Duration duration = Duration.between(lastOnline, Instant.now());
            String lastOnlineFormatted = DurationFormatter.ofGreatestUnits(duration, 2, ChronoUnit.SECONDS);

            return builder.append(Component.text(lastOnlineFormatted + " ago", NamedTextColor.GOLD)).build();
        }
    }

    private ItemStack createAchievementsIcon() {
        return ItemStack.builder(Material.DIAMOND)
                .set(ItemComponent.CUSTOM_NAME, Component.text("Achievements", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false))
                .lore(
                        Component.empty(),
                        Component.text("Coming Soon...", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                )
                .build();
    }

    private ItemStack createGameHistoryIcon() {
        return ItemStack.builder(Material.BOOK)
                .set(ItemComponent.CUSTOM_NAME, Component.text("Game History", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false))
                .lore(
                        Component.empty(),
                        Component.text("Coming Soon...", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                )
                .build();
    }
}
