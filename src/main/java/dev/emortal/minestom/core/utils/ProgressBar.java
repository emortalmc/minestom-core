package dev.emortal.minestom.core.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.util.RGBLike;
import org.jetbrains.annotations.NotNull;

public final class ProgressBar {

    public static Component create(float percentage, int charCount, @NotNull String character, @NotNull RGBLike completeColour,
                                   @NotNull RGBLike incompleteColour) {
        final int completeCharacters = (int) Math.ceil((percentage * charCount));
        final int incompleteCharacters = (int) Math.floor((1 - percentage) * charCount);

        return Component.text(character.repeat(completeCharacters), TextColor.color(completeColour))
                .append(Component.text(character.repeat(incompleteCharacters), TextColor.color(incompleteColour)));
    }

    private ProgressBar() {
        throw new AssertionError("This class cannot be instantiated.");
    }
}