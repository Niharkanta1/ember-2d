package dev.ember2d.save;

import dev.ember2d.core.PublicApi;

/** The outcome of loading a slot (F-013): {@code data} is set only when the state is {@link SlotState#OK}. */
@PublicApi
public record LoadResult(SlotState state, SaveData data, SlotInfo info) {

    public boolean ok() {
        return state == SlotState.OK;
    }
}
