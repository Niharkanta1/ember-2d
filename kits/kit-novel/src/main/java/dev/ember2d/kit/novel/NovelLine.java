package dev.ember2d.kit.novel;

import com.badlogic.gdx.graphics.Color;
import dev.ember2d.core.PublicApi;
import java.util.List;

/**
 * One line to show (F-023).
 *
 * @param speakerId the character ID, or null for narration or an unknown speaker
 * @param name the name for the name plate (empty for narration)
 * @param color the name's colour (white when unknown)
 * @param voice the voice asset ID from a {@code voice:} tag, or null
 * @param tags the line's tags that are not staging commands (for the game)
 * @param read whether the reader has seen this line before
 * @param delay seconds of {@code wait:} before the line
 */
@PublicApi
public record NovelLine(String speakerId, String name, Color color, String text, String voice, List<String> tags,
        boolean read, float delay) {
}
