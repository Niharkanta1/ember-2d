package dev.ember2d.core;

/** Reads a float property without boxing; used by {@link Tweens}. */
@PublicApi
@FunctionalInterface
public interface FloatGetter {

    float get();
}
