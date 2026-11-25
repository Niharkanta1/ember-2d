package dev.ember2d.core;

/** Writes a float property without boxing; used by {@link Tweens}. */
@PublicApi
@FunctionalInterface
public interface FloatSetter {

    void set(float value);
}
