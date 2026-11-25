package dev.ember2d.demo.platformer.android;

import android.util.Log;
import com.badlogic.gdx.utils.Json;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * SPIKE S-04 (throwaway, never merged): Java 17 language features, newer Java library methods, reflection and libGDX
 * Json under D8/R8 on the minimum API level. Logs "PASS name" or "FAIL name: error" with tag S04.
 */
final class SpikeChecks {

    private static final String TAG = "S04";
    private static int passed;
    private static int failed;

    record Point(int x, int y) {
    }

    enum Kind { SOLID, ONE_WAY }

    sealed interface Shape permits Circle, Box {
    }

    record Circle(float radius) implements Shape {
    }

    record Box(float width, float height) implements Shape {
    }

    private SpikeChecks() {
    }

    static void run() {
        passed = 0;
        failed = 0;
        Log.i(TAG, "S-04 checks on Android API " + android.os.Build.VERSION.SDK_INT);

        // Java 17 language features
        check("record equals/hashCode", () -> new Point(1, 2).equals(new Point(1, 2))
                && new Point(1, 2).hashCode() == new Point(1, 2).hashCode() && !new Point(1, 2).equals(new Point(2, 1)));
        check("record toString", () -> {
            String text = new Point(1, 2).toString();
            Log.i(TAG, "  record toString = " + text);
            return text.contains("1") && text.contains("2");
        });
        check("record accessors", () -> new Point(4, 5).x() == 4 && new Point(4, 5).y() == 5);
        check("pattern matching instanceof", () -> {
            Object o = new Point(3, 4);
            return o instanceof Point p && p.x() == 3;
        });
        check("switch expression on String", () -> (switch ("oneWay") {
            case "oneWay" -> "one way";
            default -> "other";
        }).equals("one way"));
        check("switch expression on enum", () -> (switch (Kind.ONE_WAY) {
            case SOLID -> 1;
            case ONE_WAY -> 2;
        }) == 2);
        check("sealed interface + records", () -> {
            Shape shape = new Box(2f, 3f);
            float area = shape instanceof Box b ? b.width() * b.height() : shape instanceof Circle c ? c.radius() : 0f;
            return area == 6f;
        });
        check("text block", () -> """
                line one
                line two
                """.lines().count() == 2);
        check("var and lambdas", () -> {
            var list = List.of(1, 2, 3);
            Callable<Integer> sum = () -> list.stream().mapToInt(Integer::intValue).sum();
            return sum.call() == 6;
        });

        // Newer Java library methods (Java 9-16): backported by D8 or supplied by core library desugaring
        check("String.isBlank", () -> "   ".isBlank() && !" a ".isBlank());
        check("String.strip", () -> " a ".strip().equals("a"));
        check("String.repeat", () -> "ab".repeat(3).equals("ababab"));
        check("String.lines", () -> "a\nb\nc".lines().count() == 3);
        check("List.of / Map.of / Set.copyOf", () -> List.of(1, 2).size() == 2 && Map.of("a", 1).get("a") == 1
                && Set.copyOf(List.of(1, 1, 2)).size() == 2);
        check("Optional.isEmpty", () -> Optional.ofNullable(null).isEmpty());
        check("Stream.toList (Java 16)", () -> Stream.of(1, 2, 3).toList().size() == 3);
        check("IntStream + Collectors.joining", () -> IntStream.range(0, 3).mapToObj(Integer::toString)
                .collect(Collectors.joining(",")).equals("0,1,2"));
        check("java.time.LocalDate", () -> LocalDate.of(2026, 11, 26).plusDays(5).getDayOfMonth() == 1);

        // Reflection like a component registry: create by class name, set fields by name
        check("reflection, kept component", () -> reflect("Kept"));
        expectFailure("reflection, component without keep rule", () -> reflect("Unkept"));

        // libGDX Json filling a component by field name
        check("libGDX Json, kept component", () -> {
            SpikeKeptJson c = new Json().fromJson(SpikeKeptJson.class, "{health:7,name:slime}");
            return c.health == 7 && "slime".equals(c.name);
        });
        expectFailure("libGDX Json, component without keep rule", () -> {
            SpikeUnkeptJson c = new Json().fromJson(SpikeUnkeptJson.class, "{health:7,name:slime}");
            return c.health == 7 && "slime".equals(c.name);
        });

        // Record reflection (Class.isRecord is API 34)
        expectFailure("Class.isRecord (API 34)", () -> Point.class.isRecord());
        check("getPackage() of an app class", () -> {
            Package pkg = SpikeChecks.class.getPackage();
            Log.i(TAG, "  SpikeChecks is " + SpikeChecks.class.getName() + ", package " + pkg);
            return pkg != null;
        });

        Log.i(TAG, "S-04 summary: " + passed + " as expected, " + failed + " unexpected");
    }

    private static boolean reflect(String which) throws Exception {
        // Built at run time so R8 cannot see which class is meant (like names read from prefab JSON).
        String name = new StringBuilder("dev.ember2d.demo.platformer.android.Spike").append(which)
                .append(System.nanoTime() > 0 ? "Component" : "").toString();
        Class<?> type = Class.forName(name);
        Object component = type.getDeclaredConstructor().newInstance();
        Field health = type.getDeclaredField("health");
        health.setInt(component, 9);
        Field label = type.getDeclaredField("name");
        label.set(component, "boss");
        return health.getInt(component) == 9 && "boss".equals(label.get(component));
    }

    /** Expected to pass. */
    private static void check(String name, Callable<Boolean> body) {
        try {
            if (Boolean.TRUE.equals(body.call())) {
                passed++;
                Log.i(TAG, "PASS " + name);
            } else {
                failed++;
                Log.e(TAG, "FAIL " + name + ": wrong result");
            }
        } catch (Throwable t) {
            failed++;
            Log.e(TAG, "FAIL " + name + ": " + t);
        }
    }

    /** Expected to fail in a minified build (or on an old API level); reports what actually happened. */
    private static void expectFailure(String name, Callable<Boolean> body) {
        try {
            boolean result = Boolean.TRUE.equals(body.call());
            Log.w(TAG, "WORKS " + name + " (result " + result + ")");
        } catch (Throwable t) {
            Log.w(TAG, "BREAKS " + name + ": " + t);
        }
        passed++;
    }
}
