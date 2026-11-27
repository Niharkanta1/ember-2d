package dev.ember2d.kit.common;

import dev.ember2d.core.PublicApi;

/** An entity that passes a zone's filters entered it (F-021); name is the zone entity's name. */
@PublicApi
public record ZoneEntered(int zone, int entity, String name) {
}
