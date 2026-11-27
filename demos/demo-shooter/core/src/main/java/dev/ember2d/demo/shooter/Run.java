package dev.ember2d.demo.shooter;

/** One play-through: carried from the room to the boss. */
final class Run {
    /** Global-data key of the best score. */
    static final String BEST_KEY = "best.score";

    int score;
    /** Player health when entering the next stage; -1 starts full. */
    int health = -1;
    float seconds;
}
