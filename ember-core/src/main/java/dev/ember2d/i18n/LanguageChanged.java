package dev.ember2d.i18n;

import dev.ember2d.core.PublicApi;

/** The language of context.strings() changed (F-025). */
@PublicApi
public record LanguageChanged(String language) {
}
