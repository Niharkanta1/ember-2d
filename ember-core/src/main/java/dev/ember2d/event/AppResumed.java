package dev.ember2d.event;

import dev.ember2d.core.PublicApi;

/** Published immediately when the app runs again after {@link AppSuspended}. */
@PublicApi
public record AppResumed() {
}
