package dev.ember2d.input;

import dev.ember2d.core.PublicApi;

/** Published immediately when the input context stack changes (F-005, F-019); {@code top} is the new top context. */
@PublicApi
public record InputContextChanged(String top) {
}
