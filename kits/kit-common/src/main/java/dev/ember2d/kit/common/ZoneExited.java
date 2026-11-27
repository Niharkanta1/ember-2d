package dev.ember2d.kit.common;

import dev.ember2d.core.PublicApi;

/** An entity that passes a zone's filters left it (F-021). */
@PublicApi
public record ZoneExited(int zone, int entity, String name) {
}
