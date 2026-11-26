package dev.ember2d.save;

import dev.ember2d.core.PublicApi;

/**
 * A slot's metadata (F-013). For an empty or damaged slot the metadata fields are empty; {@code problem} is the
 * reason a slot is damaged or too new, otherwise empty. {@code timestamp} is in milliseconds since the epoch.
 */
@PublicApi
public record SlotInfo(int slot, SlotState state, int version, long timestamp, float playTimeSeconds, String scene,
        String summary, boolean backupAvailable, String problem) {

    /** An empty or damaged slot without metadata. */
    public static SlotInfo without(int slot, SlotState state, boolean backupAvailable, String problem) {
        return new SlotInfo(slot, state, 0, 0L, 0f, "", "", backupAvailable, problem);
    }
}
