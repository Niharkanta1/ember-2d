# ADR-019: Android is an official target; iOS kept possible but not committed

- Status: Accepted (new)
- Date: 2026-11-25
- Context: The developer intends to ship games on Android. iOS is undecided. libGDX's Android backend is mature; its iOS backend depends on the community-maintained MobiVM (RoboVM fork) and has a history of breaking on new iOS/Xcode releases. Problems found late in a port are expensive, so Android must be exercised from early phases.
- Decision:
  - Add an `ember-android` launcher module (libGDX Android backend). Android builds are produced and smoke-tested from Phase 1 onward; the Phase 3 MVP gate includes an Android build of `demo-platformer`.
  - Engine and kit code must be Android-safe: Java 17 language features are allowed (D8/R8 desugaring), but only Java library APIs available on Android (with core library desugaring) may be used; file access goes through libGDX `Gdx.files`.
  - Touch input and on-screen controls become part of the engine (F-022).
  - Graphics context loss on Android resume is handled explicitly (frame buffers and any GL objects not managed by `AssetManager` are recreated).
  - Reflection use (prefabs, saves, inspector) is confined to `ember.data` and a component registry; R8 keep rules are generated from that registry.
  - iOS: no iOS module, no iOS testing. The same rules (no desktop-only code in `ember-core`, contained reflection) keep a later MobiVM port feasible; that port would need its own ADR and a spike first.
- Alternatives: (1) Desktop only — rejected, Android is a goal. (2) Android and iOS together — rejected for now; iOS toolchain risk and Mac requirement without a confirmed need.
- Consequences: One more launcher module and an Android CI job. `ember-devtools` (ImGui) remains desktop-only; Android builds never include it. Construo stays desktop-only; Android uses the standard Gradle APK/AAB pipeline.
- Risks: libGDX regressions specific to Android (example: libGDX 1.13.5 was not recommended for Android because of an R8 issue). Mitigation: pinned versions; Android smoke test in CI before any libGDX upgrade.
- Impacted specifications: F-001, F-004, F-005, F-012, F-013, F-020, F-022; `CODING_GUIDELINES.md`; ADR-015.
