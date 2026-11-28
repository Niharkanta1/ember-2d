package dev.ember2d.demo.gold;

import dev.ember2d.save.SaveData;

/**
 * One play-through of the three levels (F-029), like the original's {@code GameSession} and {@code ScoreBoard}: the
 * level being played, the gold collected (the score, carried across levels), kills, and the time of the current
 * level. The best score, the level reached and the gold banked at its start are kept in the global save data
 * ({@code saves().global()}), so Continue and the title screen can use them.
 */
final class Campaign {

    static final int LEVELS = 3;
    static final String BEST = "gold.best";
    static final String REACHED = "gold.reached";
    static final String SAVED_GOLD = "gold.savedGold";

    int level = 1;
    int gold;
    int kills;
    float levelTime;
    /** Gold and kills when the current level started, for the level summary and for restarting after death. */
    int levelStartGold;
    int levelStartKills;

    /** A new campaign from level 1. */
    static Campaign fresh() {
        return new Campaign();
    }

    /** Continues from the level reached last time, with the gold banked at its start. */
    static Campaign resume(SaveData saved) {
        Campaign c = new Campaign();
        c.level = Math.max(1, Math.min(LEVELS, saved.getInt(REACHED, 1)));
        c.gold = Math.max(0, saved.getInt(SAVED_GOLD, 0));
        c.levelStartGold = c.gold;
        return c;
    }

    static boolean canResume(SaveData saved) {
        return saved.getInt(REACHED, 0) > 1;
    }

    static int best(SaveData saved) {
        return saved.getInt(BEST, 0);
    }

    void startLevel() {
        levelTime = 0f;
        levelStartGold = gold;
        levelStartKills = kills;
    }

    /** After a death: the level starts again with the gold and kills it started with. */
    void retryLevel() {
        gold = levelStartGold;
        kills = levelStartKills;
        startLevel();
    }

    int levelGold() {
        return gold - levelStartGold;
    }

    int levelKills() {
        return kills - levelStartKills;
    }

    boolean isLastLevel() {
        return level >= LEVELS;
    }

    /** Moves on after a cleared level and remembers it for Continue; returns false after the last one. */
    boolean advance(SaveData saved) {
        recordBest(saved);
        if (isLastLevel()) {
            saved.set(REACHED, 1);
            saved.set(SAVED_GOLD, 0);
            return false;
        }
        level++;
        saved.set(REACHED, level);
        saved.set(SAVED_GOLD, gold);
        return true;
    }

    /** Keeps the best score; returns true if this one is a new best. */
    boolean recordBest(SaveData saved) {
        if (gold > best(saved)) {
            saved.set(BEST, gold);
            return true;
        }
        return false;
    }
}
