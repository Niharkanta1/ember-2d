package dev.ember2d.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.app.EngineConfig;
import dev.ember2d.app.EngineContext;
import dev.ember2d.app.EngineSettingsAccess;
import dev.ember2d.assets.Assets;
import dev.ember2d.assets.AsyncLoad;
import dev.ember2d.event.internal.DefaultEventBus;
import dev.ember2d.save.internal.FileSaveStore;
import dev.ember2d.save.internal.MemorySaveFiles;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Localisation (F-025, I-440). */
class StringsTest {

    static final class FakeAssets implements Assets {
        final Map<String, JsonValue> files = new LinkedHashMap<>();
        int generation;

        void put(String id, String json) {
            files.put(id, new JsonReader().parse(json));
        }

        @Override
        public <T> T get(String id, Class<T> type) {
            return type.cast(files.get(id));
        }

        @Override
        public TextureRegion region(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AsyncLoad loadGroup(String group) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void unloadGroup(String group) {
        }

        @Override
        public boolean isLoaded(String id) {
            return files.containsKey(id);
        }

        @Override
        public boolean contains(String id) {
            return files.containsKey(id);
        }

        @Override
        public int generation() {
            return generation;
        }

        @Override
        public List<String> ids() {
            return new ArrayList<>(files.keySet());
        }
    }

    final FakeAssets assets = new FakeAssets();
    final DefaultEventBus events = new DefaultEventBus(false);
    final List<String> changed = new ArrayList<>();

    StringsTest() {
        assets.put("strings.en", "{ \"version\": 1, \"language\": \"en\", \"strings\": { \"menu.play\": \"Play\", "
                + "\"hud.score\": \"Score {0} of {1}\", \"only.en\": \"English only\", \"brace\": \"{{0} is {0}\" } }");
        assets.put("strings.de", "{ \"version\": 1, \"language\": \"de\", \"strings\": { \"menu.play\": \"Spielen\", "
                + "\"hud.score\": \"Punkte {0} von {1}\" } }");
        assets.put("ink.town", "{}");
        assets.put("ink.town.de", "{}");
        events.onGlobal(LanguageChanged.class, e -> changed.add(e.language()));
    }

    Strings strings() {
        return new Strings(assets, events, "en", true);
    }

    @Test
    void lookupsFallBackToTheDefaultLanguageThenMarkTheKey() {
        Strings s = strings();
        assertThat(s.languages()).containsExactly("en", "de");
        assertThat(s.get("menu.play")).isEqualTo("Play");
        s.setLanguage("de");
        assertThat(s.language()).isEqualTo("de");
        assertThat(s.get("menu.play")).isEqualTo("Spielen");
        assertThat(s.get("only.en")).as("falls back to the default language").isEqualTo("English only");
        assertThat(s.get("nowhere")).isEqualTo("[nowhere]");
        assertThat(s.has("only.en")).isTrue();
        assertThat(s.has("nowhere")).isFalse();
        s.setLanguage("de");   // the same: no event
        assertThat(changed).containsExactly("de");
        assertThatThrownBy(() -> s.setLanguage("fr")).hasMessage("No string table for language \"fr\"; "
                + "languages: [en, de]");
    }

    @Test
    void formatFillsPlaceholders() {
        Strings s = strings();
        assertThat(s.format("hud.score", 3, 10)).isEqualTo("Score 3 of 10");
        assertThat(s.format("hud.score", 3)).as("a missing argument stays").isEqualTo("Score 3 of {1}");
        assertThat(s.format("brace", "x")).isEqualTo("{0} is x");
        s.setLanguage("de");
        assertThat(s.format("hud.score", 1, 2)).isEqualTo("Punkte 1 von 2");
    }

    @Test
    void localisedAssetsAndGamesWithoutTables() {
        Strings s = strings();
        assertThat(s.localised("ink.town")).isEqualTo("ink.town");
        s.setLanguage("de");
        assertThat(s.localised("ink.town")).isEqualTo("ink.town.de");
        assertThat(s.localised("ink.castle")).isEqualTo("ink.castle");

        Strings none = new Strings(new FakeAssets(), null, "en", false);
        assertThat(none.get("menu.play")).as("no tables: the key itself").isEqualTo("menu.play");
        assertThat(none.languages()).isEmpty();
    }

    @Test
    void hotReloadReadsTablesAgainAndBadTablesAreReported() {
        Strings s = strings();
        assertThat(s.get("menu.play")).isEqualTo("Play");
        assets.put("strings.en", "{ \"version\": 1, \"strings\": { \"menu.play\": \"Start\" } }");
        assertThat(s.get("menu.play")).as("cached until a reload").isEqualTo("Play");
        assets.generation++;
        assertThat(s.get("menu.play")).isEqualTo("Start");

        assets.put("strings.en", "{ \"version\": 2, \"language\": \"de\", \"strings\": { \"a\": 1 } }");
        assets.generation++;
        assertThatThrownBy(() -> s.get("a")).hasMessageContaining("Bad string table")
                .hasMessageContaining("\"version\" must be 1")
                .hasMessageContaining("\"language\" is \"de\" but the asset is strings.en")
                .hasMessageContaining("\"a\" must be a string");
    }

    @Test
    void theLanguageIsASavedSetting() {
        FileSaveStore store = new FileSaveStore(new MemorySaveFiles(), 1, 1, () -> 0L);
        EngineContext ctx = new EngineContext.Builder(new EngineConfig()).assets(assets).saves(store).build();
        ctx.strings().setLanguage("de");
        assertThat(ctx.saveSettings().ok()).isTrue();
        assertThat(store.settings().getString("ui.language", "")).isEqualTo("de");

        EngineContext again = new EngineContext.Builder(new EngineConfig()).assets(assets).saves(store).build();
        EngineSettingsAccess.apply(again);
        assertThat(again.strings().language()).isEqualTo("de");

        store.settings().set("ui.language", "fr");
        EngineContext third = new EngineContext.Builder(new EngineConfig()).assets(assets).saves(store).build();
        EngineSettingsAccess.apply(third);   // unknown: logged and skipped
        assertThat(third.strings().language()).isEqualTo("en");
    }
}
