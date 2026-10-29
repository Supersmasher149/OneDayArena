# Testing

## Automated tests (`./gradlew :core:test`)

There are 49 tests, and all of them pass. They run headless: pure-Java rule tests, plus simulation tests that use
the real Bullet collision world, with no window or GL context.

| Area | Tests |
|---|---|
| Damage reduction and healing limits | `HealthTest`, `CombatRulesTest` (Weed reduction, Molly heals from actual damage and caps at max, protected and dead targets) |
| Power-up replace, refresh, expire and death clear | `PowerUpStateTest` (all six expire at their configured duration back to `Modifiers.NONE`), `CombatRulesTest`, `WorldIntegrationTest.pickupsReplaceRefreshAndRespawn` |
| Fire cadence, ammo, reloads, modifier restoration | `WeaponStateTest` (cadence, Addy speed-up then baseline again, reload math, reserve caps, falloff) |
| Score attribution, respawn, timer, results | `MatchTest` (kill limit, timer expiry, draws and shared ranks, ignored post-match kills), `CombatRulesTest` (one kill from many hits and attackers), `WorldIntegrationTest` (respawn state, full bot match where kill events equal scored kills) |
| Walls and piercing | `WorldIntegrationTest` (rifle and scattergun cannot hit through the lane wall; Acid pierces one body but not walls), `ShotResolverTest` |
| Movement | Coke speed and baseline after expiry, Mushrooms double jump, out-of-bounds recovery, no tunneling at 50 m/s with 0.25 s steps |
| Level, navigation, collision alignment | `LevelAndNavTest`: spawns and pickups stand on walkable ground with room; no pair of spawns faces each other with line of sight; nav covers ground, platform, terraces and slopes; A* routes between levels; the character controller physically walks every sloped nav edge and a random sample of flat ones |
| Restart hygiene | `restartingDoesNotLeakPhysicsWorlds` |
| Settings | `SettingsStoreTest` (round trip and clamping of corrupt values) |

## Graphical smoke tests (performed on macOS 15.6.1, Apple Silicon, JDK 21.0.11, 2560×1440 backbuffer)

These ran the real LWJGL3 build. A bot drove the player, so they exercise rendering, HUD, audio playback calls, the
game loop and screen flow, but **not human mouse and keyboard handling**.

- `--smoke --timescale=3` (run 3 times, last run on the final code): launch → 2 full matches ending at the kill
  limit (106 kills logged in the last run) → scoreboard → pause (sim time frozen) → settings panel → settings
  changed and saved → resume → death cam → results → **restart** (physics worlds alive stayed at 1) → second match
  → results → main menu with the live background match → clean exit. Screenshots of every step were reviewed. Bots
  were seen navigating all levels, fighting each other and the player, collecting health, ammo and all six
  power-ups, and respawning.
- `--tour` (from Gradle, from the fat JAR on JDK 21 and JDK 27, and from the packaged `Altered Arena.app`): all six
  power-ups collected through the real pickup code, each expiring after exactly its configured duration. Baseline
  modifiers (speed, fire rate, jumps, damage taken) returned to 1.0. Effect panel, icon, countdown and toast were
  checked in the screenshots.
- **Settings persistence across process restart** was seen directly: a value saved in one run (sensitivity 1.35,
  reduced effects on) was loaded at the start of the next launch.
- Exit is clean, with no exceptions and no double-dispose warnings.

## Manual checklist (still needs a human at the keyboard)

1. `./gradlew :lwjgl3:run`. The main menu shows a live bot match behind it. Hovering and clicking buttons make sounds.
2. Settings: move both sliders, toggle invert-Y, fullscreen (the window switches) and reduced effects. Quit and
   relaunch, and the values are kept.
3. Play: the mouse is captured, and WASD, jump and mouse look feel responsive. Walk up every ramp onto the central
   platform and both terraces. Cross both bridges. Vault the low window in a side wall.
4. Fire both weapons. Check muzzle flash, tracers, impact sparks, hitmarker, kill sound and recoil. Reload (R) and
   swap (1/2/Q/wheel).
5. Stand behind a side wall or the central pillar while a bot fires. You should take no damage through it.
6. Collect each power-up. Check the toast, HUD panel with countdown, and vignette tint; Mushrooms double jump, Coke
   speed, Addy faster fire. Let one expire, and pick up a different one to replace the current one.
7. Take damage. The red direction arc points at the attacker. Die, check the death cam, then respawn with the cyan
   spawn bubble.
8. Esc pauses and frees the mouse, and RESUME captures it again. Alt-tab or click outside the window: the game
   pauses. RESTART MATCH and MAIN MENU both work.
9. Finish a match (or wait out 5:00). Check the results, then PLAY AGAIN.
10. Turn on reduced effects. You should see no flashing, shaking, pulsing or FOV kick, but indicators still show.
