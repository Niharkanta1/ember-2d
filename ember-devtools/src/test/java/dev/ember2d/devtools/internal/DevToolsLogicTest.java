package dev.ember2d.devtools.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import dev.ember2d.assets.AssetWatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** The window-free parts of the dev tools (F-018, I-160). */
class DevToolsLogicTest {

    enum Mood { CALM, ANGRY }

    static class Base {
        public int inherited = 1;
    }

    /** A component with one field of every kind. */
    static final class Sample extends Base {
        public int i = 2;
        public long l = 3L;
        public float f = 4f;
        public double d = 5.0;
        public boolean b;
        public String s = "x";
        public Mood mood = Mood.CALM;
        public final Color color = new Color(Color.WHITE);
        public final Vector2 v = new Vector2();
        public final Rectangle r = new Rectangle();
        public final float[] path = {1f};
        public final int fixed = 9;
        public Object other;
        public static int ignoredStatic;
        int notPublic;
    }

    private static Map<String, FieldAccess.FieldView> byName(List<FieldAccess.FieldView> fields) {
        return fields.stream().collect(Collectors.toMap(FieldAccess.FieldView::name, Function.identity()));
    }

    @Test
    void fieldsAreListedWithKindsAndEditability() {
        Sample sample = new Sample();
        List<FieldAccess.FieldView> fields = FieldAccess.fields(sample, null);

        assertThat(fields).extracting(FieldAccess.FieldView::name).containsExactly("inherited", "i", "l", "f", "d",
                "b", "s", "mood", "color", "v", "r", "path", "fixed", "other");
        Map<String, FieldAccess.FieldView> f = byName(fields);
        assertThat(f.get("mood").kind()).isEqualTo(FieldAccess.Kind.ENUM);
        assertThat(f.get("color").kind()).isEqualTo(FieldAccess.Kind.COLOR);
        assertThat(f.get("color").editable()).as("final but changed in place").isTrue();
        assertThat(f.get("fixed").editable()).as("final int").isFalse();
        assertThat(f.get("path").editable()).as("arrays are read-only").isFalse();
        assertThat(f.get("other").kind()).isEqualTo(FieldAccess.Kind.OTHER);
        assertThat(f.get("other").editable()).isFalse();
    }

    @Test
    void valuesAreReadAndWritten() {
        Sample sample = new Sample();
        Map<String, FieldAccess.FieldView> f = byName(FieldAccess.fields(sample, null));

        f.get("i").set(sample, 7);
        f.get("l").set(sample, 8L);
        f.get("f").set(sample, 1.5f);
        f.get("d").set(sample, 2.5);
        f.get("b").set(sample, true);
        f.get("s").set(sample, "y");
        f.get("mood").set(sample, Mood.ANGRY);
        ((Vector2) f.get("v").get(sample)).set(3f, 4f);

        assertThat(sample.i).isEqualTo(7);
        assertThat(sample.l).isEqualTo(8L);
        assertThat(sample.f).isEqualTo(1.5f);
        assertThat(sample.d).isEqualTo(2.5);
        assertThat(sample.b).isTrue();
        assertThat(sample.s).isEqualTo("y");
        assertThat(sample.mood).isEqualTo(Mood.ANGRY);
        assertThat(sample.v).isEqualTo(new Vector2(3f, 4f));
        assertThat(f.get("inherited").get(sample)).isEqualTo(1);
        assertThatThrownBy(() -> f.get("fixed").set(sample, 1)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> f.get("color").set(sample, Color.RED)).as("final: change it in place")
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void onlyTheRegistrysSettableFieldsAreEditableForARegisteredComponent() {
        Map<String, FieldAccess.FieldView> f = byName(FieldAccess.fields(new Sample(), Set.of("i", "color")));

        assertThat(f.get("i").editable()).isTrue();
        assertThat(f.get("color").editable()).isTrue();
        assertThat(f.get("f").editable()).isFalse();
    }

    @Test
    void theEventLogKeepsTheNewestFirstAndFilters() {
        EventLog log = new EventLog(3);
        record Hit(int n) {
        }
        record Heal(int n) {
        }
        log.record(1, new Hit(1));
        log.record(2, new Heal(2));
        log.record(3, new Hit(3));
        log.record(4, new Hit(4));   // pushes out Hit(1)

        assertThat(log.entries("")).extracting(EventLog.Entry::text)
                .containsExactly("Hit[n=4]", "Hit[n=3]", "Heal[n=2]");
        assertThat(log.entries("HE")).extracting(EventLog.Entry::frame).containsExactly(2L);
        log.setPaused(true);
        log.record(5, new Hit(5));
        assertThat(log.entries("")).hasSize(3).first().extracting(EventLog.Entry::frame).isEqualTo(4L);
        log.setPaused(false);
        log.record(6, "x".repeat(300));
        assertThat(log.entries("").get(0).text()).hasSize(EventLog.MAX_TEXT + 3).endsWith("...");
        log.clear();
        assertThat(log.entries("")).isEmpty();
    }

    @Test
    void frameStatsKeepTheLastFramesInOrder() {
        FrameStats stats = new FrameStats(3);
        assertThat(stats.averageFrame()).isZero();
        stats.add(10f, 1f);
        stats.add(20f, 2f);
        stats.add(30f, 3f);
        stats.add(40f, 4f);

        assertThat(stats.size()).isEqualTo(3);
        float[] times = stats.frameTimes();
        assertThat(new float[] {times[0], times[1], times[2]}).containsExactly(20f, 30f, 40f);
        assertThat(stats.averageFrame()).isEqualTo(30f);
        assertThat(stats.averageEngine()).isEqualTo(3f);
        assertThat(stats.maxFrame()).isEqualTo(40f);
    }

    @Test
    void reloadRequestsJoinTheFileChanges() {
        List<String> fileChanges = new ArrayList<>(List.of("maps/a.tmx"));
        boolean[] closed = {false};
        ManualReloadWatcher watcher = new ManualReloadWatcher(new AssetWatcher() {
            @Override
            public void drainChanges(Consumer<String> changedPath) {
                fileChanges.forEach(changedPath);
                fileChanges.clear();
            }

            @Override
            public void close() {
                closed[0] = true;
            }
        });
        watcher.request("anim\\hero.clips.json");
        watcher.request("anim/hero.clips.json");   // same file once

        List<String> got = new ArrayList<>();
        watcher.drainChanges(got::add);
        assertThat(got).containsExactly("maps/a.tmx", "anim/hero.clips.json");
        got.clear();
        watcher.drainChanges(got::add);
        assertThat(got).isEmpty();
        watcher.close();
        assertThat(closed[0]).isTrue();
    }
}
