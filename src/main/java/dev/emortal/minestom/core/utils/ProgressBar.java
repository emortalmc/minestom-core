package dev.emortal.minestom.core.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.util.RGBLike;
import org.jetbrains.annotations.NotNull;

public final class ProgressBar {

    public static @NotNull Component create(float percentage, int charCount, @NotNull String character, @NotNull RGBLike completeColour,
                                            @NotNull RGBLike incompleteColour) {
        int completeCharacters = (int) Math.ceil((percentage * charCount));
        int incompleteCharacters = (int) Math.floor((1 - percentage) * charCount);

        return Component.text()
                .append(Component.text(character.repeat(completeCharacters), TextColor.color(completeColour)))
                .append(Component.text(character.repeat(incompleteCharacters), TextColor.color(incompleteColour)))
                .build();
    }

    private ProgressBar() {
        throw new AssertionError("This class cannot be instantiated.");
    }
}