package dev.ember2d.demo.platformer;

/** The player was hurt (spikes, an enemy, or falling) and will respawn (I-170, I-210). */
public record PlayerHurt(int player) {
}
