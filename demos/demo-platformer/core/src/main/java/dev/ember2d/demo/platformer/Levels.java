package dev.ember2d.demo.platformer;

import java.util.List;

/** The demo's levels (I-250): each one adds a mechanic of kit-platformer. */
final class Levels {

    /** One level: its map is {@code map.level<N>} in the asset group {@code level<N>}. */
    record Level(int number, String title) {

        String mapId() {
            return "map.level" + number;
        }

        String group() {
            return "level" + number;
        }

        /** Global-data key of the best time in seconds (I-230). */
        String bestTimeKey() {
            return "best.level" + number;
        }
    }

    static final List<Level> ALL = List.of(new Level(1, "Meadow"), new Level(2, "Cliffs"), new Level(3, "Sky"));

    private Levels() {
    }

    static int count() {
        return ALL.size();
    }

    /**
     * @throws IllegalArgumentException for a number outside 1 to {@link #count()}
     */
    static Level get(int number) {
        if (number < 1 || number > ALL.size()) {
            throw new IllegalArgumentException("level must be from 1 to " + ALL.size() + " (was " + number + ")");
        }
        return ALL.get(number - 1);
    }
}
