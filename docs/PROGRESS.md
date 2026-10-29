# Progress

## v0.1 milestones
- [x] 1. Launchable libGDX/LWJGL3 project (gdx-liftoff layout, Gradle 9.8 wrapper, JDK 21 toolchain), arena from
      shared level data, first-person camera, kinematic capsule movement with Bullet sweeps, ramps, ground snap,
      fixed 120 Hz step with bounded catch-up, out-of-bounds recovery
- [x] 2. Hitscan shooting against Bullet geometry, damage, death, one bot
- [x] 3. Nav graph generated from walkable surfaces and validated against collision, A* bots (perception, reaction
      delay, aim error, strafing, pursuit, disengage to health, ammo seeking, stuck recovery), four bots,
      respawning, scoring, kill and time limits, draws
- [x] 4. All six power-ups, health and ammo pickups, replace/refresh/expire/clear rules, bot use
- [x] 5. Main menu, settings (persisted), controls, pause, results, restart, HUD, scoreboard, kill feed, damage
      indicators, procedural audio, reduced-effects mode, packaging (JAR and Construo macOS app)

## Multiplayer (branch `multiplayer`)
- [x] Phase 1: listen server for 2-4 players plus bots. Host/Join menus and lobby; the host runs the only
      simulation, clients send inputs (UDP, TCP until UDP is confirmed) and render 30 Hz snapshots one snapshot
      behind; events (sounds, kill feed, hit markers) go over TCP. Dropped players are taken over by a bot.
      Loopback tests in `core/src/test/java/com/alteredarena/net` and a two-window run on one Mac.
- [ ] Phase 2: client-side movement prediction and reconciliation, remote yaw interpolation, predicted weapon feel.
- [ ] Phase 3: lag compensation for hitscan.
- Phase 1 limits: your own movement and shots lag by your round-trip time; no joining mid-match; no NAT traversal
  (use Tailscale/ZeroTier, LAN, or forward port 27940 TCP+UDP); not yet tested between two real machines.

## Known defects and limitations
- Human input (mouse capture feel, sensitivity, keyboard play) has not been play-tested by a person. Only the
  bot-driven graphical runs above were observed.
- Only macOS 15 (Apple Silicon) was run. Windows, Linux and Intel macOS builds are configured but untested.
- The macOS app is unsigned and not notarized.
- Matches against bots are fast: about 2–2.5 minutes to 15 kills in bot-only runs. Bot accuracy is the main
  difficulty knob (`BotBrain.newAimError`), and there is no difficulty setting yet.
- Bots sometimes jump or strafe off ledges in fights, then re-route. They have no jump-down shortcuts in the nav graph.
- You can walk under the high ends of ramps and bridges. That is valid geometry, but it can look odd.
- No music. Positional audio is simple distance attenuation and panning.

## Backlog (not started, by design)
More maps, difficulty levels, campaign, progression or unlocks, controller support, music, key
rebinding, crafting, procedural levels.
