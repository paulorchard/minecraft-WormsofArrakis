# Report — Prompt 03 Worms of Arrakis: liner texture, bend limit and the head-leading path engine
Status: partly (Parts 0-3 built, unit tested, dedicated-server smoke run, deployed; nothing seen on screen because no client was run; world-surface mode untested)   Commit / tag: fix-1 (Part 0), path-1, path-2, path-3 (Parts 1-3 share one commit because they share the path engine)

## What was built
- Part 0 (tag fix-1):
  - Liner texture: the liner now uses the same dripstone tile per face as the plates, tinted `liner_tint` = `0xFFC0C0C0` (`ShingleMath.LINER_TINT`), with its ring turned half a face (pi/n) so its seams fall in the middle of the plates. Command flag `liner dark` restores the old `0xFF404040` tint (`ShingleMath.LINER_DARK`). Nothing is copied into the jar.
  - `geometry/BendLimit`: `MIN_BEND_RADIUS_WIDTHS = 2.0`, `minRadius(width)`, `maxJointAngle(width, pitch)` (= pitch / (2 x width)), `visibleCurvatureOk`, `visibleJointOk`, `clampDegrees`, `clampRadius`.
  - Commands `chain`, `wave`, `curve` clamp the requested bend to the limit and say so in chat and in the log, unless the command carries the word `force` (for `curve` it can take the place of the plane word: `curve 16 40 1 force`). `curve` below 2 widths is clamped to 2.
- Part 1 (path-1): path engine in the new package `path` (no Minecraft classes):
  - `PlanarPath` (lines and arcs in one vertical plane, exact arc-length parameter, straight tails before and after), `WormPose` (head-leading pose), `Moves` (crest, emerge, crash builders with the curvature rule and speed scripts), `SurfaceProvider` (plane; world implementation in the client renderer), `WormSkin` (builds the skin for the drawn segments only), `HeadSpec` (head length).
  - Test glue in `test/MoveFactory` (spec to move, script time) and the renderer path `TestWormRenderer.submitMove`.
  - Commands: `crest <width> <segments> [crest_fraction] [speed] [force]`, `stop`, `speed <value>`, `surface plane|world`.
- Part 2 (path-2): `emerge <width> <segments> <n> [angle=90] [speed=12] [hold=2] [mouth=open|closed] [force]`.
- Part 3 (path-3): `crash <width> <segments> <n> [angle=70] [speed=12] [gravity=12] [top_speed=4] [mouth=open|closed] [ballistic on|off] [force]`.
- Jar: `IslandCraft-WormsOfArrakis-0.1.0.jar` (in the instance `mods` folder; GeckoLib 5.5.3 there; GeckoMesh stays `.disabled`).

## Confirmed   (tested, or read from the code or the jar; say which)
- Unit tests, all passing (`BendLimitTest`, `PathTest`, plus the earlier ones):
  - limit table: 1.79 / 0.60 / 9.55 degrees for widths 16 / 48 / 3 at pitch 1; the clamp never exceeds the limit (widths 3/16/48, pitch 1/2/4, yaw/pitch combinations);
  - at the limit radius (one scale per bone, widths 3/16/48, yaw/pitch/diagonal) there are no uncovered ring vertices and no intersections left;
  - liner seams are exactly half a face from the plate edges;
  - path arc-length: `|dP/ds| = 1` within 1e-3 over all moves, continuous at every primitive boundary;
  - joints from the path equal the `ChainMath` joint positions within 1e-4 (crest, emerge 90 and 60, crash, widths 3/16/48, many s), and the spacing is exactly one pitch;
  - crest: the highest point of the plates equals crest_fraction x width within 0.06 (W16: 6.399 for 0.4, 7.999 for 0.5; W48: 19.199, 23.999; W3: 1.195, 1.476); the head tip lies on the path, so never above it; fraction above 0.5 is rejected;
  - no visible joint exceeds the limit (widths 3/16/48, all four sample moves, sampled along the whole move);
  - buried segments are skipped and no visible one is: every skipped segment, built alone, has its highest vertex at or below y = -0.9, and every segment with a vertex above y = 0 is drawn;
  - emerge: exposed count at the hold equals n for n = 1 to 40 at widths 3, 16, 48 (angle 90); base height is within one pitch of n; retreat positions equal the emergence positions at the same s (1e-4); the path is straight for one body width on each side of the surface crossing;
  - crash: arcs tighter than the limit are all buried (axis at or below -r0 - 0.5); the top arc radius is the limit; the head re-enters at the mirror angle; the path length between the crossings matches the sampled polyline (0.05); every joint crossing the surface does so within one pitch of the entry or exit hole; the ballistic speed at the apex is 4.00 blocks/s with top_speed 4; crash with a short worm or a top radius factor below 1 is refused.
- Dedicated-server smoke start: crest, emerge (90, 60), crash (refused with the length message, accepted at 120 and with force), curve (clamped, and with force), chain shingle (clamped) all parse and log; no errors; no client class loaded on the server.
- GeckoLib 5.5.3 compiles and the injection file is unchanged. `submitCustomGeometry`, `RenderTypes.entityCutoutCull(Identifier)`, `adjustRenderPose`, `adjustModelBonesForRender` are used as in prompts 01 and 02 and compile against the 26.1.2 and GeckoLib jars.

## Inferred    (not verified)
- Nothing in this prompt has been seen on screen: how the head sits on the joint, the liner tint against the plates, the hump, the column and the arc as drawn.
- `/arrakis_worm_test surface world` (client height map through `Level.getHeight(MOTION_BLOCKING, x, z)`) compiles but is untested; `EntityRenderState.x/y/z` are used for the entity position.
- The `speed` command works through a time ratio (script time = tau0 + ratio x (age - age0) / 20); the client and server entity ages can differ by a tick or two, which may give a small jump.
- Vertex colour on `entityCutoutCull` (inferred from the shader in prompt 02) is still not seen.
- Entity Culling, Sodium and Iris effects on the box are untested.

## Answers to the prompt's questions
1. Liner texture: the same dripstone texture per face as the plates, tint `0xFFC0C0C0` (about 75% grey), offset half a face along the ring (the ring is turned pi/n, so the seam is not a UV shift but a geometric one, and every liner face is still one whole tile). Whether the plates still read against it is not seen (plates stand 0.25 or more above the liner; tint 75% keeps a brightness step). Flag `liner dark` returns to `0xFF404040`; `liner off` removes it.
2. Bend limit: `geometry/BendLimit` (`MIN_BEND_RADIUS_WIDTHS = 2.0`). `curve`, `chain`, `wave` clamp (chat message plus log line starting `bend limit:` or `curve:`) unless `force`. Max joint angle at pitch 1: width 3 = 9.55 deg, width 16 = 1.79 deg, width 48 = 0.60 deg. For longer bones (`per` > 1) the same formula applies with the bone length, but the gap grows with the bone length, so coverage at the limit is only proven for one scale per bone.
3. Path engine: a `PlanarPath` is a list of lines and arcs in one vertical plane, parametrised by exact arc length; `pos(s)` and `theta(s)` are closed-form, with straight tails before and after. The worm pose is a function of the head's s only: joint 0 is the path point at s, joint k+1 is the path point behind joint k at Euclidean distance one pitch (found by bisection), so every segment is a chord of exactly one pitch and the positions equal the `ChainMath` positions. Joint angles: the elevation phi_k of each chord (head-to-tail axis); `ChainMath` takes `pitch[0] = phi_0` and `pitch[k] = phi_k - phi_(k-1)` (wrapped), yaw 0; a new `ChainMath` constructor takes a start point and applies the first segment's angle too. Non-planar paths are refused by construction (the type has no second horizontal axis, so no yaw or roll can arise); a future 3D path would need yaw from the projected heading plus a roll rule.
4. Underground skipping: a segment is skipped only when its highest ring point is at least one block below the sand at both its joints: `max(joint y) + r sqrt(1 - ay^2) + 0.2 (plates) + r x tilt/2 (mitre) <= surface - 1`, where ay is the vertical part of the chord axis. The built range is the drawn range plus one segment each side. Quads for a 100-segment worm with 10 exposed (emerge 90 degrees, with the liner and rims): width 16: 13 segments, 3,250 quads (whole worm 25,000); width 48: 13 segments, 9,750 quads (whole worm 75,000). Proof that no visible segment is skipped: `PathTest.buriedSegmentsAreSkippedAndNoVisibleSegmentIs` (every skipped segment alone stays below y = -0.9; every segment with a vertex above y = 0 is drawn) for widths 3, 16, 48 and the four moves.
5. Crest: measured top of the hump 6.399 against 6.4 (W16, fraction 0.4), 7.999 against 8.0 (0.5); W48 19.199 against 19.2 and 23.999 against 24.0; W3 1.195 against 1.2 and 1.476 against 1.5. Arch radius = the limit (6 / 32 / 96 blocks at widths 3 / 16 / 48); the arch is three arcs of that radius (rise, over the top, descent). The length over which the body is above the sand: W16 41.9 blocks (fraction 0.4) and 47.4 (0.5); W48 139.3 and 157.1; W3 6.9 and 7.7.
6. Emerge (W16, n = 10; head base height and top of the mouth): angle 90: 10.00 and 22.78; angle 75: 9.66 and 22.00; angle 60: 8.66 and 19.73. Formula: the head stops where exactly n segment centres are above the sand (midway between the n-th and (n+1)-th centre crossing the surface), so base height = n x sin(angle) on the straight stretch (the curved part is below the sand), and mouth top = base + HEAD_LENGTH x sin(angle). `HEAD_LENGTH`: body part `HeadSpec.BODY_LENGTH = 8.0` (4 body segments of 2) plus the closed flaps `CONE_HEIGHT x R = 0.6 R`; total 12.78 blocks at width 16 (`HeadSpec.headLength(width)`); change `HeadSpec` when the mouth is reworked.
7. Crash: `crash 16 80 10` is refused because the exposed path is 98.2 blocks, so the worm must be at least 99 segments; with force it exposes all 80 segments (maxExposed 80), span 67.0 between the two holes, apex 30.5 blocks, top radius 32. `crash 16 120 10`: maxExposed 98, span 67.0, apex 30.5. `crash 48 150 20` is refused (needs about 275 segments; exposed path 274.6); with force: maxExposed 150, span 194.1, apex 82.0, top radius 96. Other cases (W16, n = 10 and 600 segments): 45 degrees: maxExposed 71, span 59.4, apex 16.4; W48 at 70: maxExposed 255, span 187.3, apex 72.6. Owner estimates (W16, minimum radius 32): they do not match my arcs: for a launch angle of 30 / 45 / 60 degrees my arc apex above the start of the turn is 4.3 / 9.4 / 16.0 blocks (whole apex height with n = 1: 4.8 / 10.1 / 16.9) and the span between the holes is 33.7 / 46.7 / 56.4, against the estimates 5 / 16 / 48 and 37 / 64 / 111. The estimates correspond to R tan(angle) instead of R sin(angle) for the span and a taller apex; my numbers are from the actual path code (turn of 2 x angle with radius 32, mirrored). The two example commands in the prompt (`crash 16 80 10`, `crash 16 100 25`) need a longer worm or `force`: roughly 2n + 78 segments at 70 degrees (99 for n = 10; about 129 for n = 25).
8. Blocks for the next prompt: nothing in the geometry. Sand displacement and particles need the hole positions (path crossings: `Move.sCross1`, `sCross2`, `path.pos`) and the head crossing times, which the move already has. The mouth rework only needs `HeadSpec` and `MouthModel` (the head is the prompt 01 mouth model, posed on joint 0). Peristaltic crawling needs a different s-to-time script (the pose is already a function of the head s); not started.

## Numbers
- Limit table (pitch 1): 9.55 / 1.79 / 0.60 degrees at widths 3 / 16 / 48; minimum radius 6 / 32 / 96 blocks.
- Crest tunnel depth: axis at -(r0 + 1.5) (W16: -9.46), peak axis at crest_fraction x width - r0 - 0.0915.
- Emerge tunnel depth: the deeper of the straight-stretch rule ((width + r0) x sin(angle) + curve rise) and -(r0 + 2).
- Quads: see answer 4. Per frame the head is drawn through GeckoLib (4 body segments plus 3 flaps, a few hundred quads).
- Build time of the exposed part was not measured in this prompt (13 segments are built instead of 100; prompt 02 measured about 1 ms for a whole worm on the client); read it from `stats on`.
- Dedicated-server smoke: loads in 0.18 s, no errors.

## Surprises, problems, deviations from the prompt
- The two example commands `crash 16 80 10` and `crash 16 100 25` are refused as the worm is too short for the arc (the prompt asks for this rejection). Use `crash 16 120 10`, `crash 16 150 25` or `force`.
- The owner's apex and span estimates for the crash differ from the path code (answer 7).
- With more than one scale per bone the limit is not enough to close the skin (the gap grows with the bone length). The path engine uses one scale per bone.
- The head is the prompt 01 mouth model (own frustum skin), not the shingle skin; it is rigid and straight and lies along the chord from joint 0 to the path point HEAD_LENGTH ahead, so its tip is on the path. There is a small angle step at joint 0 between the head and the first body segment.
- Safety margins added to the curvature rule: the straight stretch below the crossing is (width + r0) long, the tunnel axis is at least r0 + 1.5 (crest) or r0 + 2 (emerge, crash) below the sand, so the ring is clear of the sand where the underground radius is tight.
- The liner offset is a geometric half-face turn, not a UV shift (a UV shift needs a wrapping sampler).
- `speed` changes the time ratio of a running move; moves loop (crest continuously, emerge with a 1 s pause, crash with a 2 s pause) until `stop` or `clear`.
- Nothing was viewed on screen in this prompt; the dev client was not run.
- Dedicated-server console spawns do not load the entity (no chunk at the spawn), so `stop` and `speed` found nothing in that smoke run; in the owner's world the entity is added.
- The hold log line `path emerge hold: exposed=... headBaseHeight=... headTopHeight=...` comes from the client renderer; the spawn-time log line carries the same numbers computed by the pure code.

## How the owner can see it
Jar: `IslandCraft-WormsOfArrakis-0.1.0.jar`. Creative world, flat ground, fly or stand back about 40 blocks. The ground at the spawn point is the sand surface (plane).
```
/arrakis_worm_test clear
/arrakis_worm_test chain 16 12 10 0 1 shingle
/arrakis_worm_test curve 16 40 1
/arrakis_worm_test curve 16 40 1 force
/arrakis_worm_test chain 16 12 10 0 1 shingle 1 1 0.132 dark
/arrakis_worm_test crest 16 60
/arrakis_worm_test crest 16 60 0.5 12
/arrakis_worm_test emerge 16 60 10
/arrakis_worm_test emerge 16 60 10 60
/arrakis_worm_test emerge 16 60 25
/arrakis_worm_test crash 16 120 10
/arrakis_worm_test crash 16 150 25
/arrakis_worm_test crash 48 300 20
/arrakis_worm_test stats on
/arrakis_worm_test speed 5
/arrakis_worm_test stop
```
Log lines (`logs\latest.log`): `[WormsOfArrakis] bend limit: ...`, `curve: radius ... clamped`, `path crest: ... peakTop=...`, `path emerge: ... exposed=... headBaseHeight=... headTopHeight=...`, `path emerge hold: exposed=...` (once per hold, from the client), `path crash: ... maxExposed=... span=... apexHeight=...`, `path <mode>: width=... built segments a..b quads=... HEAD_LENGTH ...` (client, when the exposed range changes mode), `crash refused: ...`, `stats: ...`.
What to look for: the liner shows dripstone tiles with the plates proud (try `liner dark` to compare); `curve 16 40 1` is clamped with a message and `force` builds it as in prompt 02; `crest 16 60`: a low smooth hump with less than half the body thickness above the sand, then it sinks; `emerge 16 60 10`: 10 segments out of the sand under the head, hold, retreat; `crash`: the head rises, turns over, falls in, the body follows through the hole.

## Suggested next steps (your view)
- Look first at the head joint on `emerge` and `crash` and at the plates against the liner, then decide on the liner tint.
- Then the mouth rework (HeadSpec), so the head can use the shingle skin and bend with the path.
- Then sand displacement and particles at the two path crossings, driven by `Move`.
- Decide whether crash worms need a minimum length warning only, or an automatic length.

## Owner observations (human)
