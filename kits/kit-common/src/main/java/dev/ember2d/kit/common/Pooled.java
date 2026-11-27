package dev.ember2d.kit.common;

import dev.ember2d.core.PublicApi;

/** Marks an entity made by an {@link EntityPool}, so {@link Despawn} returns it there (F-021). Not settable from data. */
@PublicApi
public final class Pooled {
    final EntityPool pool;
    boolean inUse;

    Pooled(EntityPool pool) {
        this.pool = pool;
    }

    /** The pool the entity belongs to. */
    public EntityPool pool() {
        return pool;
    }
}
