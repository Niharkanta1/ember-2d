package dev.ember2d.kit.common;

import dev.ember2d.core.PublicApi;

/** Game code called from an Ink {@code EXTERNAL} function (F-021); arguments and result are Ink values. */
@PublicApi
@FunctionalInterface
public interface DialogueFunction {

    /** @return the function's value in Ink (may be null for none) */
    Object call(Object... args);
}
