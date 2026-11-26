package dev.ember2d.demo.platformer;

/**
 * The player reached the goal (F-015, I-170).
 *
 * @param seconds game time from the level start
 * @param deaths  how often the player was hurt and respawned
 */
public record LevelCompleted(float seconds, int deaths) {
}
