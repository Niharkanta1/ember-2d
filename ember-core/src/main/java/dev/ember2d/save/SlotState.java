package dev.ember2d.save;

import dev.ember2d.core.PublicApi;

/** The state of a save slot (F-013). */
@PublicApi
public enum SlotState {
    /** No save in the slot. */
    EMPTY,
    /** A save that loads. */
    OK,
    /** The save cannot be read (see {@link SlotInfo#problem()}); a backup may be offered. */
    DAMAGED,
    /** The save was written by a newer version of the game. */
    TOO_NEW
}
