package dev.ember2d.demo.gold;

/**
 * The original's difficulty levels (F-029), with health counted in quarter hearts so kit-common's whole-number
 * {@code Health.damageMultiplier} can express the original's damage factors: Easy 6 hearts and half damage, Normal
 * 5 hearts, Hard 4 hearts and 1.5 times the damage. One point of enemy damage is half a heart on Normal.
 */
enum Difficulty {
    EASY("Easy", 24, 1),
    NORMAL("Normal", 20, 2),
    HARD("Hard", 16, 3);

    final String label;
    /** Maximum health in quarter hearts. */
    final int maxHealth;
    /** Quarter hearts lost per point of damage. */
    final int damageMultiplier;

    Difficulty(String label, int maxHealth, int damageMultiplier) {
        this.label = label;
        this.maxHealth = maxHealth;
        this.damageMultiplier = damageMultiplier;
    }

    Difficulty next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
