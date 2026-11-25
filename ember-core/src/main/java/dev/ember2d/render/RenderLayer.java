package dev.ember2d.render;

import dev.ember2d.core.PublicApi;
import java.util.Objects;

/** A named render layer and its sort mode; layers are drawn in list order (F-004). */
@PublicApi
public record RenderLayer(String name, SortMode sort) {

    public RenderLayer {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(sort, "sort");
    }
}
