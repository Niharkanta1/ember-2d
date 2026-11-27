package dev.ember2d.kit.topdown.rpg;

import dev.ember2d.core.PublicApi;

/** An entity took count of item from a pickup (F-017). */
@PublicApi
public record ItemPickedUp(int entity, int pickup, String item, int count) {
}
