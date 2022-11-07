package cc.towerdefence.minestom.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.util.RGBLike;

public class ProgressBar {

    public static Component create(float percentage, int charCount, String character, RGBLike completeColour, RGBLike incompleteColour) {
        int completeCharacters = (int) Math.ceil((percentage * charCount));
        int incompleteCharacters = (int) Math.floor((1 - percentage) * charCount);

        return Component.text(character.repeat(completeCharacters), TextColor.color(completeColour))
                .append(Component.text(character.repeat(incompleteCharacters), TextColor.color(incompleteColour)));
    }
}