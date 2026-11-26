package dev.ember2d.data;

import dev.ember2d.core.PublicApi;

/** A component field that data can set (F-010), with its value on a new instance. */
@PublicApi
public record FieldInfo(String name, Class<?> type, Object defaultValue) {
}
