package dev.ember2d.event;

import dev.ember2d.core.PublicApi;

/** Published immediately when the platform suspends the app (window minimised, Android app in the background). */
@PublicApi
public record AppSuspended() {
}
