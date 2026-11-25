# ADR-013: libGDX core types are part of the public API

- Status: Accepted (new)
- Date: 2026-11-25
- Context: The original rule "LibGDX types must not leak through public engine APIs" would require wrapping `Vector2`, `Rectangle`, `Color`, `TextureRegion`, `Batch`, `Viewport` and key codes. That duplicates libGDX's vocabulary, adds conversion overhead and contradicts the principle of not forking libGDX.
- Decision: Public APIs may use libGDX core types: `math.*`, `graphics.Color`, `graphics.g2d.*`, `utils.*` collections and pools, `utils.viewport.*`, and Scene2D types. Hidden behind Ember2D APIs: Box2D types, the LWJGL3/GLFW backend, and file paths (games use asset IDs).
- Alternatives: Wrap everything — rejected (cost, no user benefit, libGDX is not being replaced).
- Consequences: Smaller API, existing libGDX knowledge transfers directly. libGDX math classes are plain Java and work in unit tests without a window.
- Risks: A future libGDX breaking change ripples through the API. Mitigation: pin the libGDX version; upgrades are explicit tasks.
- Impacted specifications: all; `CODING_GUIDELINES.md`.
