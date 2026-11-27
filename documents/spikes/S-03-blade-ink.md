# S-03 — blade-ink: compiled stories and saved state

- Time box: 0.5 day (used: about an hour)
- Phase: 5
- Related spec/ADR: F-021 (DialogueRunner), F-023 (kit-novel), ADR-017 (Ink)
- Spike code: branch `spike/s-03-ink` (pushed for reference, never merged)
- Date: 2026-11-27

## Question
Does blade-ink load an Inky-compiled story and save and restore its state, on desktop and Android?

## Downloads (approved by the developer, 2026-11-27)
- **blade-ink 1.2.1:** `com.bladecoder.ink:blade-ink`, from Maven Central, about 0.4 MB. It is in the dependency register.
- **inklecate v1.2.1 for Windows:** inkle's official compiler, `inklecate_windows.zip` from github.com/inkle/ink/releases. The zip is 26.5 MB because it bundles its own .NET runtime. It is unpacked into the session scratchpad, outside the repository, and is never committed.
- **Why inklecate is needed:** blade-ink is a runtime only. Its jar has no compiler, and no compiler artifact was found on Maven. Inky uses the same compiler, so a story compiled by inklecate is an "Inky-compiled story".

## Approach
- **The story:** `s03.ink` has spoken lines with tags (`#speaker:guard #mood:stern`), two choices (one shows a variable in its text), a global variable changed by a choice, an `EXTERNAL` function with an ink fallback, and a gather.
- **Compiling:** `inklecate -o s03.ink.json s03.ink`. The output is ink JSON format version 21, 1 KB.
- **The JUnit test** (`kit-common`):
  - It loads the JSON with `new Story(json)` and binds the external function.
  - It checks the lines, the tags, the choice texts, `chooseChoiceIndex` and the variables.
  - It saves `story.getState().toJson()` at the choice, loads it into a fresh `Story`, takes the other branch and checks the variables.

## Results
- **Desktop:**
  - Both tests pass.
  - The external function binding replaces the ink fallback.
  - Tags come per line.
  - The saved state is 937 characters for this story and restores exactly at the choice, with the variables.
- **The byte-order mark (corrected in I-420):** an earlier version of this report said inklecate writes a UTF-8 byte-order mark that `new Story(...)` fails on. That was not checked, and it is wrong: inklecate v1.2.1 writes no byte-order mark. The spike test stripped one only as a precaution. `DialogueRunner` still strips a leading mark, since an editor may add one when a file is saved again.
- **Android (static check only):**
  - The jar is Java 8 bytecode (class version 52).
  - None of its 157 runtime classes use streams, `java.nio.file`, reflection or newer `String` methods (checked with `javap`).
  - It should run on API 23 without desugaring problems and without keep rules.
  - An actual run on the API 23 emulator happens with the first demo that uses dialogue (I-450). That is the remaining part of this question.

## Decision
- **Use blade-ink 1.2.1 behind `DialogueRunner`:** it lives in `kit-common` as an `implementation` dependency, so no Ink type leaks.
- **Story files:**
  - Sources are `.ink` files under `assets-src/<game>/ink/`.
  - A dev-only Gradle task compiles them with inklecate. Its path comes from the Gradle property `ember.inklecate`, so it is never assumed or downloaded by the build.
  - The compiled `.ink.json` files are committed into the game's assets, so normal builds and CI need no compiler.
- **Saving:** the story state is saved as its JSON string inside a save slot.

## Follow-ups
- I-420: `DialogueRunner` (strips a leading byte-order mark if present; lines, tags, choices, variables, external functions, state into `SaveData`), and the inklecate Gradle task.
- I-450: the first run on the emulator; confirm R8 needs no keep rules for blade-ink.
