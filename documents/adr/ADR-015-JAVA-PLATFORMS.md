# ADR-015: Java 17 language level; desktop and Android targets

- Status: Accepted (new; amended 2026-11-25 by ADR-019)
- Date: 2026-11-25
- Context: The plan said "desktop-first, keep the architecture portable" without choosing a Java version or naming targets. Android is now an official target (ADR-019). Android supports Java 17 language features through D8/R8 desugaring (records included), but not every Java library API. The GWT web backend cannot use modern Java features at all.
- Decision:
  - Java 17 language level for all modules (build with a current LTS JDK).
  - Targets: desktop (LWJGL3) and Android. iOS is not a target (ADR-019). If web is added later, use TeaVM; GWT is out of scope.
  - Engine and kit code may use only Java library APIs available on Android's minimum supported API level (with core library desugaring enabled). Not allowed in `ember-core` or kits: `java.nio.file`, `java.awt`, `javax.*`, `ProcessBuilder`, `java.lang.management`, threads other than documented loader threads.
  - Desktop-only code (`ember-desktop`, `ember-devtools`) may use any Java API.
  - Minimum Android API level is chosen in I-015 (Android smoke task) based on current Google Play requirements and libGDX support.
- Alternatives: (1) Java 8 for maximum portability — rejected, loses records, switch expressions, text blocks. (2) Java 21 language level — rejected for now; Android desugaring support for newer features lags.
- Consequences: Records and modern syntax may be used; records used through reflection need R8 keep rules (generated, ADR-019). An Android lint/build job in CI catches unsupported APIs.
- Risks: An allowed-looking API missing on older Android versions. Mitigation: CI builds and runs the Android smoke test on the minimum API level emulator image.
- Impacted specifications: `CODING_GUIDELINES.md`, F-020, F-022.
