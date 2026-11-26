package dev.ember2d.collision;

import dev.ember2d.core.PublicApi;

/** A box that blocks actors (F-006); move it only with {@link Collision#moveSolid}. One-way: blocks only from above. */
@PublicApi
public final class Solid {
    public boolean oneWay;
}
