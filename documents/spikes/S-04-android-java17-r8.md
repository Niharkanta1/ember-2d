# S-04 — Java 17, reflection and R8 on the minimum Android API level

- Time box: 1 day (used: about half)
- Phase: 1
- Related spec/ADR: ADR-015, ADR-019, F-003, F-010 (component registry), F-020, F-022
- Spike code: branch `spike/s-04-android-r8` (pushed for reference, never merged)
- Date: 2026-11-26

## Question
Does an Android build with Java 17 features (records, switch expressions), reflection-loaded components and R8 minification run correctly on the minimum API level (23)?

## Approach
- The platformer demo built as a **minified release APK** (R8, Android Gradle plugin 9.5.2 defaults, `proguard-android-optimize.txt`, libGDX rules adapted from the gdx-liftoff template, debug signing) next to the normal debug APK.
- Both installed on the **API 23 emulator** (`default` x86_64 image). The real engine boots in both (records, lambdas, switch expressions, `String.isBlank`, libGDX JSON, scenes, ECS, rendering), plus a spike-only check class logging PASS/FAIL per item:
  - Java 17 language: records, pattern matching for `instanceof`, switch expressions, sealed interfaces, text blocks, `var`, lambdas.
  - Java 9–16 library methods that API 23 does not have natively.
  - Reflection like the future component registry: class looked up by a name built at run time, created with its no-argument constructor, fields set by name — once for a class **with** the keep rule a registry would generate, once **without**.
  - libGDX `Json` filling a component by field name, with and without the keep rule.
  - `Class.isRecord()` (Android API 34).

## Out of scope
The real component registry and rule generation; signed release builds and the AAB; API levels above 23; real devices (none connected).

## Finding
**Answer: yes, with three rules to follow.** Java 17 language features and most newer library methods work on API 23, debug and minified. Reflection-loaded components work under R8 exactly when keep rules exist for them. A few library methods are missing on API 23 and are not supplied by desugaring.

**Evidence** (logcat tag `S04`, both builds unless noted):

| Check | Debug | Release (R8) |
| --- | --- | --- |
| Records: `equals`, `hashCode`, accessors | pass | pass |
| Record `toString` | `Point[x=1, y=2]` | `sa[b=1, c=2]` (minified names) |
| Pattern matching `instanceof`, switch expressions (String, enum), sealed interfaces, `var`, lambdas | pass | pass |
| `String.isBlank`, `strip`, `repeat`; `List.of`, `Map.of`, `Set.copyOf`; `Optional.isEmpty`; `Stream.toList`; streams; `java.time.LocalDate` | pass | pass |
| **`String.lines()`** (also used by the text-block check) | **`NoSuchMethodError`** | **`NoSuchMethodError`** |
| **`Class.isRecord()`** | **`NoSuchMethodError`** | **`NoSuchMethodError`** |
| Reflection, component **with** keep rule | pass | pass |
| Reflection, component **without** keep rule | works | **`ClassNotFoundException`** (class renamed) |
| libGDX `Json`, component **with** keep rule | pass | pass |
| libGDX `Json`, component **without** keep rule | works | **"Class cannot be created (missing no-arg constructor)"** (class renamed, constructor removed) |
| `getPackage()` of an app class | `dev.ember2d.demo.platformer.android` | **null** (R8 moved the class to the top level as `ta`) |
| Engine boot and demo scene | runs | runs (screenshot: scene renders; overlay hidden, not debuggable) |

- APK size: debug 5.33 MB, minified release **1.17 MB**.
- The keep rule that works, per component (what the registry should generate):
  ```
  -keep class <component class> { <init>(); <fields>; }
  ```
- Engine log lines that print class names are minified in release: `Entered y2 (stack depth 1)` instead of `Entered DemoScene`.

**Surprises**
- `String.lines()` is neither backported by D8 nor supplied by core library desugaring, while `isBlank`, `strip`, `repeat`, `List.of` and `Stream.toList` are. Missing methods compile fine (compileSdk 36) and only fail at run time on old devices.
- Without keep rules, reflection works in debug and breaks only in the minified build — so problems surface late unless a minified build is tested.
- R8 repackages classes, so `getPackage()` returns null and class names change; code must not build class names from packages or rely on them at run time.

## Consequences (proposed; each needs approval)
Status: 2 (lint part) and 5 done in I-016; the minified smoke test of 2 and items 1, 3, 4 are open (I-130, I-255).

1. **Component registry (I-130):** generate `-keep class X { <init>(); <fields>; }` for every registered component into the release build's rules, and test prefab/JSON loading in a minified build (F-010 acceptance already asks for this). Components stay plain classes, never records accessed by reflection (`Class.isRecord` is API 34) — matches F-003.
2. **Guard against Java APIs missing on API 23** in `ember-core`, kits and demo cores. Android lint does not check plain Java modules, so propose an architecture-rule denylist of known-missing methods (`String.lines`, `String.indent`, `String.transform`, `Class.isRecord`, `Class.getRecordComponents`, `Class.isSealed`, `Class.getPermittedSubclasses`, …), plus running a minified demo smoke test on the API 23 emulator before releases. A small task (for example I-016) or part of I-255.
3. **Release logs and errors (I-255):** keep the R8 mapping file with every release build (for retrace), and consider `-keepnames` for `Scene` and `EntitySystem` subclasses so engine log lines stay readable.
4. **Release R8 rules (I-255):** start from the libGDX rules used here; they were sufficient for the backend, Scene2D-free demo, `Json` and the engine.
5. **Coding guideline:** no run-time use of package or class names of app classes (R8 renames and repackages them); record `toString` is for debugging only.
