# Report — Prompt 18 Worms of Arrakis: mouth and throat discs and camera framing
Status: partly (all three parts are built, 385 unit tests pass and the jar is deployed; the disc crossing, the swallow and the camera framing were never seen on a real client, and fake players cannot be touched by the worm, so the server smoke start could not show a crossing)   Commit / tag: see the last commit on master; tags discs-1, discs-2, cam-2 all sit on that one commit (see Deviations)

## What was built

**Part 1 — the two discs (discs-1)**
- `hazard/Discs`: two thin cylinders in the head frame (origin the head ring centre, +z the way the worm travels). Throat disc: plane z = 0 (the rim of the throat funnel), radius `ThroatDiscRadiusFraction` × R (0.95). Mouth disc: plane z = `MouthDiscFraction` × HL (0.5), radius `MouthDiscRadiusFraction` × R (0.8). Thickness `DiscThickness` 0.5, centred on the plane.
- `WormHazard.discContact(box, tPrev, tNow, disc)`: at `HazardSubsteps` instants a sample (8 corners, 6 face centres, centre, a vertical line every 0.3 block) meets the thin cylinder, or the box centre passes from one side of the plane to the other between two instants inside the radius (a fast worm cannot skip a thin disc). The pose is `WormHazard.poseAt`, the same pose function as prompt 16 (no second pose). Only the part of the box above the sand level of the move counts (prompt 17 clipping kept). The prompt 16 prefilter, entity budget and pose cache are reused unchanged.
- `hazard/DiscRules` (engine-free): what each disc does to whom. Mouth disc: players only, starts the camera. Throat disc: swallows a player (prompt 17 swallowed state), kills a mob on touch, no camera for a mob. Off Dune Sand: spared (each kind by its own key, unchanged). The body, head, mandible shell and funnel trigger nothing: `HazardRunner` now tests only the two discs; the old contact shapes stay in `WormHazard` as geometry (debug draw, camera clearance, `nearestSurface`).
- `ThroatDiscEnabled` and `MouthDiscEnabled` (default true) replace `HazardParts`, `VictimParts` and `OthersParts`.
- `hunt/DiscRecord` (engine-free): per player and strike, `mouthAt`, `throatAt`, `backupAt` and the gap; one log line per player: `[WormsOfArrakis] discs: <name> mouth at t=<s> throat at t=<s> gap=<s> (<move> W<n>)` (or "throat only", "mouth only", "neither disc crossed"). `hunt status` shows it under each player. `hunt/DiscListener` has `onMouth(event, player, t)` and `onThroat(event, player, t)` with empty defaults; `HuntAdapter.addDiscListener` subscribes a future detector.
- `hazard/DiscTimes`: the instants at which each disc first meets a 0.6 × 1.8 box at the entry crossing, from the move's own frames; logged at start-up for the 16 combinations.
- `strike hazarddebug on|off` now draws the two discs as rings of dust particles (mouth disc green, throat disc magenta), the camera orbit target as a column of end_rod particles, and the body/head/funnel outlines (marked non-lethal: the red skin particles are gone).
- Worms of `/arrakis-worm` and the test commands act through the discs only with `HazardOnTestWorms` (default false); the worm of `camera preview <size> <move>` is never hazardous.

**Part 2 — the camera starts at a disc (discs-2)**
- The camera no longer starts at the freeze end: `GroupRunner.onChoice` starts none (a unit test checks the source). One method, `startCamera`, starts it, called at the first of: mouth disc crossing, throat disc crossing, the victim's backup swallow. Per player, once. The camera entity is created at that moment. A member the worm never reaches never gets one and is released at the end with an unchanged view; a member released at the choice never had one. Restore paths are those of prompts 16 and 17 (release alive, stop, fizzle, nokill end, respawn, log-off, dimension, server stop, timeout). One fix: a player whose kill was refused (fake players) now gets their view back (before, the camera stayed until server stop).
- A player who crosses the mouth disc without being a member (a bystander, or a member released at the choice) is held at once as a late member, as for the throat.

**Part 3 — the camera is framed outside the worm (cam-2)**
- `camera/StrikeCam` (engine-free): base distance `D_base = R + FlareFactor × HL + n`, `n = StrikeCamClearanceFactor × R`; `D_fit = peak / (StrikeCamFill × 2 tan(StrikeCamFov / 2))`, `peak` = the highest point of the worm above the sand over the move (joint centre + radius, flap vertices, from the move's own frames); `D = max(D_base, D_fit)`, capped by `StrikeCamMaxDistance` and the loaded area `16 × (view distance − 1)`. Height above the ground = `max(StrikeCamMinHeight, StrikeCamHeightFactor × peak)`. Look-at = the held spot + `StrikeCamLookHeight`.
- Clearance: every tick the camera is checked against the worm's outer surface (`WormHazard.nearestSurface`: tapered body capsules, mandible shell edges at the live opening, the funnel rim, at the renderer's pose). If nearer than n it is pushed away (horizontal first, then up), the push changing by at most `StrikeCamPushPerTick` (1.5) per tick, the camera moving at most `StrikeCamMaxSpeed` (2.0) per tick. The first position is solved by the same rule before the first packet (at most 20 pushes; logged if it fails). Never inside terrain (heightmap of the loaded column).
- Orbit: a symmetric sweep of `StrikeCamSweepDegrees` (120, i.e. ±60°) about side-on, smoothstep ends, spread over the remaining run time of the move from the camera start; side and direction from the event's seeded random per player; 0 = locked; the camera holds after the move.
- `camera preview [size] [seconds]` (no worm: base distance, height and sweep round the caller, using the emerge peak of the size), `camera preview <size> <move>` (the real move plays at the caller's feet as a harmless worm; the camera starts when the caller's box crosses the mouth disc; the caller is not frozen, not swallowed; refused with a chat message off Dune Sand), `camera stop`, `camera status` all work.
- All numbers are `StrikeCam*` keys; the old `DeathCam*` keys are superseded.

New files: `Discs`, `DiscRules`, `DiscTimes`, `StrikeCam`, `DiscRecord`, `DiscListener`, `WormSurface`, `CameraMovePreview`, `Prompt18Test`.

## Confirmed
Tested (unit tests, engine-free):
- Disc planes and radii for the four sizes; a box on a disc's centre crosses it; a box outside the radius or off the plane does not; the thin-plane crossing catches the tiny box with one instant per tick within a tick or two of the 4-instant result; the rim of the funnel is on the plane z = 0; the same cached pose as the renderer's function.
- Triggers: mouth disc players only; throat disc swallows a player and kills a mob; off Dune Sand spares each kind by its own key; each switch; the body, head, mandibles and funnel trigger nothing (a box in the body, one on a mandible vertex, one beside the funnel rim are untouched while the old geometry still sees the body).
- The record keeps both times and the gap; the listener fires; the defaults do nothing.
- Camera: the 16 distance rows, the caps, the height rule, the 16-move clearance simulation, the symmetric sweep, smoothstep ends, locked at 0, same seed same side and direction.
- No camera at the freeze end (a source check) and one starter method for the three triggers.
Read from the code and the jars (names unchanged from prompts 14 to 17, no new Minecraft or NeoForge class was needed): `ClientboundSetCameraPacket`, `TestWormEntity.onRemoval`, `ServerChunkCache.addTicketWithRadius`, the NeoForge events of prompt 17. Seen in the server smoke start: start-up logs of the config, the disc blocks and the expected times; the group at the freeze start; no camera at the strike start; the camera starting at the victim's backup swallow; the camera and chunk tickets cleaned up; `camera preview <size> <move>` refused politely on the console.

## Inferred
- How the disc geometry looks and where it sits on a real worm (the owner's `hazarddebug` check); whether 0.5 of the head length is the right place for the mouth disc.
- What the first-person view does for the one tick before the camera packet arrives, and how the cut looks.
- That the camera never clips on a real client: the simulation is of one player standing still at the entry crossing on flat sand with the renderer's pose; a moving player, slopes, the server pose lag (`HazardTimeOffsetSeconds`) and the client's own view of the worm are not in it.
- The FOV: the server cannot set or read it; 75 is assumed.

## Answers to the prompt's questions

1. **Disc geometry (blocks) and the pose.** The pose is `WormHazard.poseAt` (the prompt 16 function that calls the renderer's `Moves.pose`, `WormSkin.bodyChain`, `HeadMouth.flaps`); `headC` and `headF` are the head ring centre and frame. HL = `HeadSpec.headLength(width)`.

   | size | R | HL | throat disc radius (0.95 R) | mouth disc z (0.5 HL) | mouth disc radius (0.8 R) | thickness |
   |---|---|---|---|---|---|---|
   | small | 3.99 | 4.99 | 3.79 | 2.49 | 3.19 | 0.5 |
   | medium | 7.96 | 8.96 | 7.56 | 4.48 | 6.37 | 0.5 |
   | large | 15.92 | 16.92 | 15.12 | 8.46 | 12.73 | 0.5 |
   | huge | 23.87 | 24.87 | 22.68 | 12.44 | 19.10 | 0.5 |

2. **Expected disc times** for a 0.6 × 1.8 box at the entry crossing (seconds after the move started; unit test and the start-up log agree). The mouth disc comes before the throat disc in all 16 combinations; the gap grows with size.

   | move | size | mouth | throat | gap |
   |---|---|---|---|---|
   | crest | small | 2.60 | 2.80 | 0.20 |
   | crest | medium | 4.10 | 4.50 | 0.40 |
   | crest | large | 7.10 | 7.80 | 0.70 |
   | crest | huge | 10.10 | 11.15 | 1.05 |
   | crash | small | 2.60 | 2.80 | 0.20 |
   | crash | medium | 4.40 | 4.75 | 0.35 |
   | crash | large | 8.00 | 8.70 | 0.70 |
   | crash | huge | 11.60 | 12.60 | 1.00 |
   | emerge | small | 2.70 | 2.90 | 0.20 |
   | emerge | medium | 4.55 | 4.95 | 0.40 |
   | emerge | large | 8.30 | 9.00 | 0.70 |
   | emerge | huge | 12.05 | 13.10 | 1.05 |
   | engulf | small | 2.70 | 2.95 | 0.25 |
   | engulf | medium | 4.40 | 4.75 | 0.35 |
   | engulf | large | 8.00 | 8.70 | 0.70 |
   | engulf | huge | 11.60 | 12.60 | 1.00 |

   The throat disc time is earlier than the old devour table for the crash and a little earlier for the others (e.g. crash small 2.80 vs 4.14; crest small 2.80 vs 2.92), so with the discs the player is swallowed earlier than before in the crash; the victim's backup (DevourSeconds + ThroatBackupSeconds) is unchanged and still comes later.
3. **First-person view for the tick before the packet.** The camera entity is created at the crossing and `ClientboundSetCameraPacket` goes one tick (50 ms) later, as in prompt 16, so the player stays in their own first-person view for one more tick, inside the mouth. This was not seen on a client; I expect it to be one frame of the normal view, then a cut (no transition) to the external view looking at the player's spot with the player visible until the throat disc hides them. Unverified.
4. **Distances** (view distance 10 chunks, so the loaded-area limit 144 never applies; FOV 75, fill 0.85):

   | move | size | peak | D_base | D_fit | D |
   |---|---|---|---|---|---|
   | crest | small / medium / large / huge | 5.5 / 11.2 / 22.5 / 33.7 | 9.5 / 18.2 / 35.7 / 53.2 | 4.2 / 8.6 / 17.2 / 25.9 | 9.5 / 18.2 / 35.7 / 53.2 |
   | crash | small / medium / large / huge | 20.0 / 40.1 / 80.1 / 120.2 | 9.5 / 18.2 / 35.7 / 53.2 | 15.4 / 30.7 / 61.4 / 92.2 | 15.4 / 30.7 / 61.4 / 92.2 |
   | emerge | small / medium / large / huge | 15.0 / 19.0 / 26.9 / 34.9 | 9.5 / 18.2 / 35.7 / 53.2 | 11.5 / 14.5 / 20.6 / 26.7 | 11.5 / 18.2 / 35.7 / 53.2 |
   | engulf | small / medium / large / huge | 7.0 / 10.9 / 18.9 / 26.9 | 9.5 / 18.2 / 35.7 / 53.2 | 5.3 / 8.4 / 14.5 / 20.6 | 9.5 / 18.2 / 35.7 / 53.2 |

   Cap and clamp: `StrikeCamMaxDistance` 120 never applies (the largest D is 92.2, the crash huge); the loaded-area clamp applies only to a player whose view distance is below 7 chunks for the crash huge (16 × (view distance − 1) < 92) and below 5 for the 53-block moves; it is logged when it happens. The crash peaks (20 / 40 / 80 / 120) are much higher than the prompt's emerge numbers (15 / 19 / 27 / 35, which the emerge rows match).
5. **Clearance results** (simulated from the camera start to 1 s after the move's end, a player standing still at the entry crossing, flat sand; the first position was already clear in all 16, with 0 pushes):

   | move | size | min distance to the surface | n | max push per tick | max step per tick |
   |---|---|---|---|---|---|
   | crest | small / medium / large / huge | 2.00 / 3.98 / 7.96 / 11.94 | 1.99 / 3.98 / 7.96 / 11.94 | 0.29 / 0.31 / 0.29 / 1.00 | 0.32 / 0.33 / 0.32 / 0.96 |
   | crash | small / medium / large / huge | 10.27 / 21.30 / 42.84 / 65.16 | 1.99 / 3.98 / 7.96 / 11.94 | 0 | 0.39 / 0.57 / 0.77 / 0.87 |
   | emerge | small / medium / large / huge | 7.36 / 9.99 / 19.45 / 29.54 | 1.99 / 3.98 / 7.96 / 11.94 | 0 | 0.23 / 0.28 / 0.39 / 0.44 |
   | engulf | small / medium / large / huge | 5.11 / 9.90 / 20.22 / 30.08 | 1.99 / 3.98 / 7.96 / 11.94 | 0 | 0.43 / 0.72 / 1.14 / 1.45 |

   Every move keeps the camera at least n − 0.25 from the surface; the maximum speed is 1.45 blocks per tick (cap 2.0). Only the crest needs pushing (its long body lies along the ground close to the side-on camera; the push holds the camera exactly at n); the push never exceeded 1.0 per tick. Nothing could not be kept clear in the simulation. Not simulated: a player who moves, uneven terrain, other players' cameras.
6. **Config keys.** Retired (no longer read by any code): `HazardParts`, `VictimParts`, `OthersParts`. Superseded: `DeathCamDistance.<size>`, `DeathCamTurnDegrees` (by `StrikeCamSweepDegrees`), `DeathCamMaxSeconds` (by `StrikeCamMaxSeconds`), `LookHeight` (by `StrikeCamLookHeight`), `DeathCamHeightFactor`, `DeathCamMinHeight`, `DeathCamSandClearance`. Migration: the old keys stay in the toml (NeoForge would otherwise drop them silently); at start-up a warning line `config: the key <name> no longer does anything (prompt 18)` is logged for each old key whose value differs from its default. The new keys appear with their defaults the first time the server or client starts. The `[WormsOfArrakis] config:` lines at start-up list every new key.
7. **Tests changed.** None of the 372 existing tests had to be changed. Added `Prompt18Test` (13 tests); total 385, 0 failures. The prompt's test "the pose used is the renderer's (compare a known point with a mesh vertex within 1e-4)" was covered by round-tripping the rim point through the same pose function and frames, not by comparing with an actual mesh vertex (the mesh needs the client); the prompt's "cancel paths" test checks the end-path table of `CameraLifecycle` and that only one method starts a camera, not a running entity (that needs a server).
8. **Blocking later work.** Nothing blocks. The multiplayer detector can subscribe with `HuntAdapter.addDiscListener` (the gap is 0.2 to 1.05 s for a standing player, in this geometry; a second player arriving off the entry crossing will show other values). Rock clipping of the worm (the worm still goes through rock; only the sand-only immunity protects players on rock). Thumpers, rideable worms and camera shake: the discs only trigger and push nothing, so a rider or a thumper would need a new rule on top; the camera is a fixed-orbit entity (no shake, no free look; a client-side mouse look would need a client mod: not done).

## Numbers
- Tests: 385, 0 failures (13 new).
- Start-up cost of the new tables (peak, expected disc times, distances for the 16 combinations): about 3.6 s in the smoke start (once at server start).
- Camera: max speed 1.45 blocks per tick, max push step 1.0 per tick over the 16 simulated moves.
- Disc times and D tables: in the answers above.

## Surprises, problems, deviations from the prompt
- **Fake players cannot show a crossing** (report 17: they are not in the level's entity list). The smoke start (victim WormFake and Near on sand, Near not reached) showed: the start-up tables; `group:` lines; no camera at the strike start; the camera starting at the victim's backup swallow (t=5.30 s, devour table 3.79 s) with `camera: WormFake starts at t=5.30 ... because the victim's backup swallow` and the packet sent; `discs: WormFake neither disc crossed; swallowed by the backup at t=5.30`; the strike ended DONE; the kill was refused for the fake player (so it was released) and 12 chunk tickets released. A real client is needed to see a crossing, the hide at the throat disc, the cut, the framing and `strike hazarddebug` (that command, like `camera preview <size> <move>`, needs a player, so the console could only be refused).
- Two log-format bugs of mine were found in the smoke start (wrong argument count in the `camera: framing` and `group: ... chose` lines) and fixed afterwards; the fixed lines were not run again in a server (the unit tests do not cover log text).
- A player whose kill was refused kept the camera until server stop: fixed (the view is released).
- The crash peak is 4 to 8 times the emerge peak, so the crash camera is far out (15 to 92 blocks) to keep the worm in a 75° frame; the worm may fill little of the screen at the start of a crash. `StrikeCamFill` and the FOV control it.
- The throat disc meets a standing player earlier than the old devour table, most for the crash (1.4 to 8 s earlier): the crash swallow comes earlier than in prompt 17. The owner may want `MouthDiscFraction` or `ThroatDiscRadiusFraction` changed after the `hazarddebug` look.
- The clearance simulation treats the whole body including the buried part as surface, which is conservative; the camera is always above the sand.
- Tags: the work was done in one working tree, so `discs-1`, `discs-2` and `cam-2` all point at one combined commit.
- Not done: the first-person tick before the packet could not be improved on the server; the camera has no mouse look.

## How the owner can see it
Commands (`/arrakis_worm_test ...`):
- `hunt trigger self medium strike emerge [nokill]`, `hunt status` (shows each player's discs line), `strike hazarddebug on|off`, `camera preview <size> [seconds]`, `camera preview <size> <move>`, `camera stop`, `camera status`.

Log lines to look for: `discs: thickness ... small: R ... throat disc z 0 radius ...` and `discs: expected times for a player at the entry crossing, <move>: ...` (start-up); `camera: framing <move> ... D_base ... D_fit ... D ...` (start-up); `config: the key ... no longer does anything`; `discs: <name> crossed the mouth disc at t=...`; `camera: <name> starts at t=... because the mouth disc was crossed | the throat disc was crossed | the victim's backup swallow`; `camera: start for ... distance ... (D_base ..., D_fit ..., clearance n ..., peak ...) ... first position is clear`; `swallowed: <name> by throat at t=...`; `hazard: <name> is not on sand, spared`; `discs: <name> mouth at t=<s> throat at t=<s> gap=<s> (<move> W<n>)`; `camera: restored for ...`.

You look and listen (survival world with Dunes, the test instance, ideally a friend; FOV 75 to 90):
- `hunt trigger self medium strike emerge`: you stay in first person while frozen and while the worm comes up; the camera cuts to the external view when you are inside the mouth (about 4.5 s after the move starts), not when the worm spawns; you vanish at the throat (about 0.4 s later); you die at the end and then the death screen shows.
- `strike hazarddebug on`: you see the two discs on the worm (green: the mouth disc, magenta: the throat disc). Do they sit where the mouth and the throat begin? Tell me which disc moves you would want (`MouthDiscFraction`, `ThroatDiscRadiusFraction`).
- The camera: is it outside the worm with no clipping at every size (small, medium, large, huge), at about half the worm's height, with you in the middle of the shot? Say the FOV you use and whether you want it tighter or looser (`StrikeCamClearanceFactor`, `StrikeCamFill`). Also try `camera preview medium crash` (the real move, you are not hurt).
- Walk through the worm's body and along a mandible before the mouth reaches you (use nokill or a second account): nothing happens to you.
- Stand on rock inside the mouth: nothing happens (immune), no camera.
- With two players: the log line `discs: ... gap=` for each; do the gaps make sense (0.2 to 1.05 s for someone at the entry crossing)?

## Suggested next steps (your view)
- Owner test of the discs and the framing with `hazarddebug`; then tune the disc fractions and `StrikeCamFill` from what you see.
- If the crash camera is too far out, cap it with `StrikeCamMaxDistance` or accept a looser fill for the crash only (a per-move fill would be a small change).
- A fake-player helper that puts the player in the level's entity list would let the server smoke start show a real crossing.
- The multiplayer detector on top of `DiscListener` once the real gaps of two players are known.

## Owner observations (human)

