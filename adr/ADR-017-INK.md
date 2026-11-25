# ADR-017: Ink (blade-ink) for dialogue and visual-novel scripts

- Status: Accepted (new; updated 2026-11-25 — runner moved to `kit-common`, used by RPG and visual novels)
- Date: 2026-11-25
- Context: F-017 called for a custom dialogue data format and runtime. Branching dialogue with variables, conditions and save/restore is a solved problem. Visual novels (F-023) need the same runtime plus tag-driven staging commands.
- Decision: Write dialogue and novel scripts in Ink (authored with Inky, compiled to JSON) and run them with blade-ink, a Java port of the Ink runtime. A small `DialogueRunner` in `kit-common` exposes lines, speaker, choices and tags, binds external functions to game actions, and stores story state in the save file. The RPG package (F-017) and `kit-novel` (F-023) both use it; `kit-novel` adds a tag-command layer (`# bg: park`, `# show: alice happy left`).
- Alternatives: (1) Custom format — rejected, large scope. (2) Yarn Spinner — its official runtimes target other engines; a Java runtime was not evaluated.
- Consequences: Writers use established tools; tags drive staging and simple cutscenes. blade-ink is pure Java and runs on desktop and Android.
- Risks: blade-ink is a small community project. Mitigation: it sits behind `DialogueRunner`; spike S-03 confirms story loading and state save/restore.
- Impacted specifications: F-017, F-021, F-023.
