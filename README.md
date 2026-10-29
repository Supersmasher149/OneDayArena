# Altered Arena (v0.1)

A fast, neon first-person arena shooter built with libGDX. You fight four bots in free-for-all deathmatch inside
"The Reactor", an underground arena with a glowing central pillar, two raised terraces, ramps, bridges and side lanes.
Six fictional arcade power-ups (Weed, Molly, Acid, Mushrooms, Coke, Addy) sit at contested spots around the map.

Everything ships in the repo: the models, textures and sounds are generated in code. The only external asset is the
OFL-licensed Chakra Petch font.

## Requirements

- **JDK 21 or newer** to build and run. Gradle runs its daemon on JDK 21 (`gradle/gradle-daemon-jvm.properties`)
  and compiles with a Java 21 toolchain. If no JDK 21 is installed, Gradle downloads one through foojay.
- An OpenGL 2.1+ GPU. Tested on macOS 15.6 (Apple Silicon).
- Internet access on the first build (Gradle 9.8.0 and Maven Central dependencies).

## Run

```sh
./gradlew :lwjgl3:run              # play from source (macOS/Linux)
gradlew.bat :lwjgl3:run            # Windows
```

## Build and package

```sh
./gradlew :core:test               # unit and simulation tests (49 tests, headless, no window)
./gradlew :lwjgl3:jar              # cross-platform runnable JAR -> lwjgl3/build/libs/AlteredArena-0.1.0.jar
java -jar lwjgl3/build/libs/AlteredArena-0.1.0.jar

./gradlew :lwjgl3:packageMacM1     # macOS Apple Silicon app + bundled JRE -> lwjgl3/build/construo/dist/AlteredArena-macM1.zip
./gradlew :lwjgl3:packageMacX64    # macOS Intel
./gradlew :lwjgl3:packageWinX64    # Windows x64 (.exe + bundled JRE)
./gradlew :lwjgl3:packageLinuxX64  # Linux x64
```

Packaging uses [Construo](https://github.com/fourlastor-alexandria/construo), which downloads a Temurin 21 JDK for
each target and builds a trimmed runtime with jlink. The macOS app is unsigned, so on another Mac you may need to
right-click it and choose **Open** the first time.

Regenerating the procedural assets (optional; the results are committed):

```sh
./gradlew :tools:generateSounds    # assets/sounds/*.wav
./gradlew :tools:generateIcons     # window and packaging icons
```

## Controls

| Input | Action |
|---|---|
| W A S D / arrow keys | Move |
| Mouse | Look (sensitivity and invert-Y in Settings) |
| Space | Jump (press again in the air for a double jump while on Mushrooms) |
| Left mouse | Fire |
| R | Reload (reload is automatic when the magazine runs dry) |
| 1 / 2, Q, mouse wheel | Pulse Rifle / Scattergun / swap |
| Tab (hold) | Scoreboard |
| Esc or P | Pause and release the mouse |

The mouse is captured during play. It is released when you pause, open the results screen, or switch away from
the window (losing focus pauses the game).

## Rules

- Free-for-all deathmatch: you against 4 bots, and the bots also fight each other.
- The match ends when someone reaches **15 kills** or after **5:00**. Most kills wins. If several players tie for
  the top score, the match is a **draw** and is shown as one. Tied players share a rank on the scoreboard.
- You respawn 3 s after dying at the spawn point farthest from opponents and out of their sight. You get 2 s of
  spawn protection (a cyan bubble and a HUD countdown), which ends early if you fire.
- Falling out of the arena returns you to a spawn point.

### Weapons

| Weapon | Damage | Fire rate | Magazine / reserve | Notes |
|---|---|---|---|---|
| Pulse Rifle | 8 per shot, falls to 60% from 30 m to 70 m | ~10.5 shots/s, automatic | 30 / 90 (max 180) | Accurate hitscan, wins at medium and long range |
| Scattergun | 9 pellets × 8, falls to 25% from 7 m to 22 m | 1 shot / 0.8 s | 6 / 18 (max 36) | Up to 72 damage up close |

Shots are raycasts against the same Bullet collision geometry the characters walk on, so walls always stop them.
Moving or jumping widens spread. All pellets from one shot are added up before damage is applied, so a victim can
only be killed (and credited) once.

### Power-ups

You can hold one power-up at a time. Taking a different one replaces your current effect, and taking the same one
refreshes its timer without stacking. Dying clears your effect. Pickups respawn 30 s after collection. Bots collect
and use power-ups too, and a bot with an active power-up shows a colored ring at its feet and the effect name on its
name tag.

| Pickup | Shape | Effect | Duration |
|---|---|---|---|
| WEED (green) | fanned leaf | Take 35% less incoming damage | 15 s |
| MOLLY (pink) | heart | Heal for 25% of damage dealt, capped at max health | 12 s |
| ACID (yellow) | blotter tab with a smiley | Shots pierce one extra opponent, never walls | 12 s |
| MUSHROOMS (violet) | mushroom | Double jump, 25% higher jumps | 15 s |
| COKE (ice white) | crystal | 40% faster movement, sharper acceleration | 10 s |
| ADDY (orange) | capsule pill | 30% faster fire rate, 50% less spread | 12 s |

These are fictional arcade abilities and do not represent real substance effects. You can tune them in one table:
`core/src/main/java/com/alteredarena/rules/PowerUpType.java`. Weapons are tuned in `rules/WeaponType.java`, and
movement, match and pickup values in `rules/Tuning.java`.

Health packs (+50, every 20 s) and ammo crates (+60 rifle / +12 shells, every 15 s) are spread around the lower level.

## Settings

Settings has master volume, mouse sensitivity, invert-Y, fullscreen/windowed and **reduced effects**. Reduced
effects turns off screen shake, damage flashes, the pulsing and color-cycling vignettes and the Coke FOV kick, and
softens weapon bob. You still get directional damage indicators, hitmarkers and effect colors. Settings save
immediately to libGDX preferences (`~/.prefs/AlteredArena` on macOS/Linux, `%USERPROFILE%\.prefs\AlteredArena` on
Windows).

## Project layout

```
core/      game code (no desktop dependencies)
  rules/     pure gameplay rules: power-ups, weapons, health, tuning tables
  sim/       simulation: GameWorld (fixed-step), Bullet collision, character motor, shots, match, nav builder
  ai/        BotBrain (perception, aim, A* navigation, combat movement, stuck recovery)
  level/     ArenaLayout: the single level definition feeding collision, rendering and nav
  render/    procedural models/textures, world renderer, effects, first-person weapon
  ui/ screens/ audio/ settings/
lwjgl3/    desktop launcher (gdx-liftoff layout), packaging config
tools/     generators for the bundled sounds and icons
assets/    fonts and generated sounds
docs/      TESTING.md (checklist and verification record), PROGRESS.md (milestones and backlog)
```

The simulation runs at a fixed 120 Hz. Each frame can simulate at most 0.1 s (up to 12 steps), and any time beyond
that is dropped. This prevents large jumps after window drags, stalls or focus changes. Mouse look is applied every
rendered frame. Movement and buttons are sampled per tick, and presses are queued so a quick tap is never missed.

## Developer smoke modes

```sh
./gradlew :lwjgl3:run --args="--smoke --timescale=3"   # bot-driven player: 2 matches, pause, settings, restart, menu, quit
./gradlew :lwjgl3:run --args="--tour --timescale=4"    # walks onto all six power-ups and waits for each to expire
./gradlew :lwjgl3:run --args="--autoplay"              # watch the bot-driven player
```

Screenshots go to `smoke-output/` (gitignored). Note that `--smoke` deliberately flips two saved settings (reduced
effects and sensitivity) to check that they persist, so running it again flips them back.

## Troubleshooting

- **macOS: window never appears / `-XstartOnFirstThread` errors.** The launcher restarts the JVM with that flag
  automatically. If you run a custom JRE, pass `-XstartOnFirstThread` yourself.
- **Gradle error about the daemon JVM.** Install JDK 21, or allow Gradle to provision one (it needs network access).
- **jlink "cannot find the build signature" while packaging.** The Gradle daemon is not running on JDK 21. Keep
  `gradle/gradle-daemon-jvm.properties` and run `./gradlew --stop`.
- **Mouse stuck or released unexpectedly.** Press Esc to pause and click RESUME to capture the mouse again.
  Alt-tabbing pauses on purpose.
- **Low frame rate.** Play windowed, or update GPU drivers. The game uses simple forward rendering without shadows.
- **"restricted method" warnings on JDK 24+.** These are harmless. The launcher passes `--enable-native-access` to suppress them.

## Licenses

The code in this repository is the project's own. See `CREDITS.md` for third-party libraries, the font and the
adapted template code.
