# kit-platformer

Module `kits/kit-platformer`, package `kit.platformer`. Spec F-015. Side-view platformer movement with the details that make it feel good, plus patrolling enemies and moving platforms. Register with `PlatformerKit.register(registry)`, which covers `PlatformerBody`, `Patroller`, `MovingPlatform` and `Ladder`.

## 1. Systems and order
```java
Collision collision = new Collision(world, context.events());
collision.addTileGrid(map.collision(), 0);
world.addSystem(new MovingPlatformSystem(world, collision));                    // solids first: riders see where they are
world.addSystem(new PatrollerSystem(world, collision));
world.addSystem(new PlatformerSystem(world, collision, input, context.events()));
world.addSystem(new CollisionSystem(world, collision));                         // after all movement
```
All four run in `FIXED`.

## 2. `PlatformerBody`
A player is an `Actor` with a `PlatformerBody`. Every field has a default, so `"PlatformerBody": {}` works. Units are tiles and seconds; "ticks" are fixed steps (1/60 s).

| Field | Default | Effect |
| --- | --- | --- |
| `moveAxis`, `jumpAction`, `dashAction` | `move`, `jump`, `dash` | input names |
| `maxSpeed` | 8 | top running speed |
| `groundAccel` / `groundDecel` | 60 / 70 | speeding up and slowing down on the ground |
| `airAccel` / `airDecel` | 35 / 20 | the same in the air |
| `gravity`, `fallMultiplier`, `maxFall` | 40, 1.6, 25 | falling is faster than rising |
| `jumpSpeed` | 16 | about 3.2 tiles high when held |
| `jumpCut` | 0.45 | releasing jump while rising cuts the speed: a short hop |
| `coyoteTicks` | 6 | a jump still works 0.1 s after running off a ledge |
| `bufferTicks` | 6 | a jump pressed up to 0.1 s before landing starts on landing |
| `airJumps`, `airJumpSpeed` | 0, 14 | extra jumps in the air |
| `dash`, `dashSpeed`, `dashTicks`, `dashCooldownTicks`, `airDashes` | false, 20, 10, 30, 1 | a dash on the dash action |
| `wallSlide`, `wallSlideSpeed` | false, 3 | slide down walls |
| `wallJump`, `wallJumpX`, `wallJumpY`, `wallJumpLockTicks` | false, 9, 15, 8 | jump off walls |
| `ladders`, `climbSpeed` | false, 5 | climb `Ladder` areas with up and down |
| `inheritPlatformVelocity` | false | keep a moving platform's speed when jumping off |
| `vx`, `vy`, `locked` | runtime | velocity; `locked` ignores input |

Controls:
- left and right move;
- **down + jump** drops through a one-way plank;
- up and down climb ladders.

### Events and animation
- Events: `Jumped` (with a `JumpKind`), `Landed` and `Dashed`.
- The system writes these state machine parameters when your definition declares them: `grounded`, `speedX`, `velY`, `wallSliding`, `dashing`, `climbing`, and the triggers `airJump` and `wallJump`. See [Animation](Animation).

### Switching mechanics on per level
Each mechanic is off by default and can be switched on in the map. In Tiled, edit the player object's `PlatformerBody` class property (available after `tiledTypes`), or add plain properties such as `PlatformerBody.airJumps = 1`. The demo's level 2 turns on wall jumps, and level 3 an air jump and a dash.

## 3. `Patroller`
| Field | Default | Meaning |
| --- | --- | --- |
| `speed` | 6 | walking speed |
| `minX`, `maxX` or `range` | -12, 12 / 0 | the walking limits, or `range` tiles each way from the start |
| `gravity` | 40 | it falls |
| `turnAtLedges` | false | turn around instead of walking off |
| `artFacesLeft` | false | the art faces left (flip logic) |
| `direction`, `vy` | runtime | |

## 4. `MovingPlatform`
`speed` (2), `distance` (6 tiles), `vertical`. Put it on a `Solid` entity; riders move with it, and actors it pushes into walls get `ActorSquished`.

## 5. `Ladder`
A marker on a trigger-collider area. With `PlatformerBody.ladders` on, the body can climb inside it.

## 6. Typical prefabs
```json
{ "version": 1, "components": {
    "Sprite":   { "regionId": "tex.frog_jump", "origin": [16, 0], "z": 2 },
    "Collider": { "box": { "x": -0.4375, "y": 0, "width": 0.875, "height": 1.375 } },
    "Actor": {},
    "PlatformerBody": {},
    "Animator": { "clipSet": "anim.frog", "clip": "idle" },
    "AnimationStateMachine": { "definition": "asm.frog" } } }
```
```json
{ "version": 1, "components": {
    "Sprite":   { "regionId": "tex.pig_walk", "origin": [18, 0], "z": 1 },
    "Collider": { "box": { "x": -0.75, "y": 0, "width": 1.5, "height": 1.5 } },
    "Actor": {},
    "Patroller": { "speed": 2.5, "range": 3, "artFacesLeft": true, "turnAtLedges": true },
    "Animator": { "clipSet": "anim.pig", "clip": "walk" } } }
```
The full game is in [Guide: Platformer](Guide-Platformer).
