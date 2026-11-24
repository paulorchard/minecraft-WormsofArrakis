# Report — Prompt 08 Worms of Arrakis: mandibles, throat and mouth animation
Status: partly (all three parts built, 125 unit tests pass, one dedicated-server smoke start, jar deployed; NOTHING was seen on screen, the new renderer code has never run in a client; the reference file `mouth-previs-reference.html` was not found, see Surprises)   Commit / tag: head-2, teeth-1 and mouth-1 on one commit (see Surprises)

## What was built
- **Part 1, head geometry (head-2).**
  - `geometry/HeadMouth.java` (new, pure code, local head frame: ring at z = 0, +z forward) builds the mouth. `HeadSpec.CONE_HEIGHT` 0.6 → 1.0, `OPEN_DEGREES` 70 → 45.
  - Mandible flaps are shells: outer skin, inner skin (every outer vertex moved by −th along its averaged vertex normal, faces wound inward, grey-tinted), lip wall and two ridge walls per flap, so the shell is closed. Thickness `max(0.02, 0.05 + (W − 6) x 0.45 / 42) x HeadMouth.THICKNESS_MULTIPLIER` (the one named constant, 1.0).
  - Unrolled-fan UVs from the closed layout (apex at (0,0), d_i, cumulative apex angle centred on the middle, 1 block = 1 tile). They are computed from the closed layout every time, so they do not change with the angle (checked by a test).
  - The throat is the same three arcs with the apex at −0.5 R, flat, never animated, no thickness.
  - One uniform roll of every ring, `HeadMouth.roll(n) = −(sizes[0] / 2) x 2 pi / n`, carried by a new `ShingleMath.Options.roll` that is added to every ring's own angle (stagger and texture offset untouched).
  - `WormSkin` no longer appends flaps; the renderer draws the mouth from `HeadMouth` (see Part 3 for the cache). `HeadFlaps.append` stays as the prompt 04 reference function and is still what the prompt 04 hinge test exercises.
- **Part 2, teeth (teeth-1).** `HeadMouth.makeTeeth` is the previs's `buildTeeth` as written in the prompt: layers, bell, count per layer, jittered position, direction from `g` and the tilt, reach cut, locked constants (`M_*` mandible, `T_*` throat). Each bristle is two crossed triangles. Seed from the width and the flap index, so every build and every client gets the same teeth. Mandible teeth are rebuilt with the live angle; the throat (and its teeth) once. Key `teeth=on|off`.
- **Part 3, mouth animation (mouth-1).**
  - `path/MouthAnim.java`: `angle(t) = open x (1 − smoothstep((t − tStart) / closeTime))`, a pure function of the loop clock; `timing()` gives apex, start, end and the clamp.
  - `Moves.Move.apexTime()`: crest = the instant the head tip is highest (scanned every 5 ms of the rise); emerge, crash, engulf = the end of the rise.
  - Keys on all four moves, from the same table as before (`MoveOptions`): `mouth=anim|open|closed` (anim is the new default), `close_time=0.9`, `close_end=0` (crest 0.3), `open_deg=45`, `teeth=on|off`. Spec positions p21..p25, appended only when a key is not at its default (p8 = 2 means anim; old specs keep their length).
  - `/arrakis_worm_test mouthpose <0..1|off>` holds the closing at a fraction (0 = open at open_deg, 1 = closed). `freeze hold` and `freeze <t>` show the mouth of that instant because the angle is a function of the script time.
  - The cache key of the mouth includes the angle in tenths of a degree (`MouthAnim.angleKey`, 0 once closed), so nothing is rebuilt after the mouth has closed.
  - crashproj and crasharc keep a static open mouth (now 45°, was 70°).

## Confirmed   (tested, or read from the code or the jar; say which)
- Tested (125 tests, 0 failures: 105 earlier + 20 new in `Prompt08Test`): HEAD_LENGTH, open angle, roll sign and upper jaw on top, uniform roll added to the stagger, seam vertices, UV area ratio and no UV swim, thickness, watertightness at 0°, 22.5°, 45°, throat apex and tint, teeth determinism, counts, base on the inner skin, directions, reach cut, no NaN, mouth law per move, clamp, key bounds, static modes, held pose, freeze, no rebuild after closure, moves text.
- Read from the 26.1.2 jar (`minecraft-patched-26.1.2.129-sources.jar`): `RenderTypes.entityCutout(Identifier)` uses `RenderPipelines.ENTITY_CUTOUT`, which is built with `.withCull(false)` (and `PER_FACE_LIGHTING`); `RenderTypes.entityCutoutCull(Identifier)` uses the pipeline without it. `SubmitNodeCollector.CustomGeometryRenderer.render(PoseStack.Pose, VertexConsumer)` is the callback. `assets/minecraft/textures/block/white_concrete.png` and `bone_block_side.png` exist (16x16, no alpha).
- Read from the PNGs: white_concrete mean texel 207/213/214 (all pixels opaque); bone_block_side mean 229/225/207.
- Dedicated-server smoke start: the `moves` text with the new keys, the phase and mouth log lines, the clamp notes (`close_time=100 close_end=99 open_deg=90` give three notes), refusals (`mouth=wide`, `teeth=maybe`, `mouthpose 2`), crashproj and crasharc with the static mouth, `mouthpose` and `freeze` answering. The server ticks no worms (no player, no loaded chunks), so the effect of `mouthpose` and `freeze` was not seen there (0 worms), only in unit tests.
- Jar: `IslandCraft-WormsOfArrakis-0.1.0.jar` (169,060 bytes, no PNG) copied to the test instance; older WormsOfArrakis jars removed; every other mod untouched; `geckomesh-...jar.disabled` stays disabled.

## Inferred    (not verified)
- That the new renderer code draws correctly. It compiles and the server start does not load it; it was never run in a client (hard rule). Risks I cannot exclude without looking: the local-to-world matrices (`Matrix4f`/`Matrix3f` built column-major from `ChainMath.frame[0]` and `center[0]`), winding/lighting of the double-sided pass (`PER_FACE_LIGHTING` shades a triangle by its normal, so a bristle seen from behind may be lit slightly differently), z-fighting of the inner skin or walls with the ring lip at the hinge.
- That `entityCutout` (no cull) shows the back of a single triangle. Read from the pipeline definition; not seen.
- That the bone-block switch (`HeadMouth.BONE_TEETH = true`) looks right: the code path is the same, the texture exists in the jar, the tint then is the shade only. Not seen.
- Frame time of the animated mouth: not measured (no client run).
- That `FLAT_TEXEL` (the texel the tints are divided by) is uniform: the mean was read from the PNG, not every pixel.

## Answers to the prompt's questions
1. **HEAD_LENGTH, tip heights, segments needed, changed tests.**
   - HEAD_LENGTH W3 / W16 / W48: old 1.97 / 5.78 / 15.30, new 2.62 / 8.96 / 24.87.
   - Engulf, exposed 2: head tip height at the hold old 3.94 / 7.72 / 17.20, new 4.58 / 10.88 / 26.67. Segments needed (exposed + 2) 4, unchanged by the longer head. Rise 2.18 / 5.01 / 12.87 s (W3 was 2.05 s: see Surprises, the tunnel is deeper), drop 1.17 / 1.86 / 2.96 s (was 1.07 / 1.55 / 2.36).
   - Emerge W16 n = 10 at 90°: head top height 18.96 (was 15.78), crash W16 default: segments needed 98, tower tip 2.5 W, rise 7.46 s, fall 2.89 s (it was 7.72 s and 2.79 s on the old head).
   - Tests whose expected numbers I changed (none weakened beyond the numbers):
     - `Prompt04Test.flapSplitAndHeadLength`: `PITCH + 0.6 R` → `PITCH + 1.0 R` (1.97/5.78/15.3 → 2.62/8.96/24.9).
     - `Prompt04Test.headTipLiesOnThePath`: the allowance `0.05 W + 0.1` grows by `(cone² − 0.6²R²) / (2 W)` (the tip is the straight extension of the head ring over one cone and leaves the underground arc of radius 1 W by cone²/2W; emerge W16 measured 2.09, the new allowance is 2.18).
     - `PathTest.crestApexTopEqualsHeightTimesWidth`: the tip-on-path allowance `0.05 W + 0.1` → `0.05 W + 0.1 + 0.1 R` (W3 crest height 0.2 radius 2: measured 0.343 against the old 0.25).
     - `Prompt05Test.parabolaIsContinuousWithUnitSpeedAndCorrectGeometry`: tolerance of the head tip apex `0.06 W` → `0.1 W` (W3 apex 1.0 W: tip 3.235 against 3.0; the old head gave 0.06 W = 0.18).
     - `Prompt04Test` hinge test and the coverage test: the open angle 70 → `HeadSpec.OPEN_DEGREES` (45); the call into `WormSkin.build` keeps its parameter, which is now ignored.
     - `Prompt07Test.theOldPositionalFormsGiveTheSameSpecsAsBefore`: the default mouth is now the animation (p8 = 2) so the expected old specs go through `withMouth(..., MOUTH_ANIM, ...)`; the explicit `open` / `closed` forms stay the static p8 = 1 / 0.
2. **Roll sign and the upper jaw.** The roll is **negative**: `delta = −(sizes[0] / 2) x 2 pi / n`. In the mod ring vertex i is at `a = i step + delta` with `(x, y) = (R sin a, R cos a)`, so a = 0 is +y and a grows toward +x. The larger flap covers vertices 0..sizes[0], its middle is at `a = (sizes[0] / 2) step`, so the roll has to be negative to bring it to a = 0 (the previs's `+rotation.z` is the same turn seen with three.js's axis). Verified by numbers, not assumed: centroid of the larger flap's arc vertices x / y, with the roll, W3 (n = 10): 0.0000 / 1.0472; W16 (n = 50): 0.0000 / 6.3749; W48 (n = 150, divisible by 3): 0.0000 / 19.5885; the smaller flaps are at y < 0 in every case (W3 −0.73 / −0.73, W16 −3.42 / −3.15, W48 −9.79 / −9.79). Also tested at W4, 5, 6, 7 and 10 (n divisible by 3 and not). Without the roll the larger flap is off to the side (a test checks that too).
3. **UV area ratio and thickness.** UV area / 3D area, closed, every flap triangle: 1.0000 .. 1.0000 at W3, 16, 48 (an unrolled fan is exact). Thickness (blocks): W3 0.020 (floor), W6 0.050, W16 0.157, W48 0.500, W96 1.014 (the prompt says "about 1.0"); the measured outer-to-inner distance equals it at every vertex at 0°, 22.5° and 45°.
4. **Where the previs and this prompt disagree.**
   - I could not read the previs: `mouth-previs-reference.html` is not in the repo or anywhere I could search (repo, Downloads, Desktop, Documents). Everything is implemented from the formulas written in the prompt. Where the prompt is silent I chose: arcs on the ring (only the apex moves, as in prompt 04); the outward normal sign is read once from the closed layout; the inner apex is offset along the average of all the flap's triangle normals; wall UVs are (length, thickness) in blocks. If the previs does any of these differently, tell me.
   - Per-vertex colour: the prompt says tip 0.78, base 1.0. The mesh container stores one colour per quad, so each blade has one colour, the average (0.89 x per-tooth shade). A gradient would need a per-vertex colour array in the container; not done.
   - "The throat teeth point toward the ring side": with `g = normalize(apex − lip)` the tooth points down the throat and leans out of the surface into the cavity (`dot(dir, nin) > 0`, `dot(dir, toward apex) > 0`). I implemented the formula and tested those two properties; the sentence as written is ambiguous.
   - The old `crest` default mouth was closed; `mouth=anim` is now the default for all four moves, as the prompt says.
   - crasharc and crashproj keep their static open mouth, now 45° (the new maximum) instead of 70°.
5. **Teeth.**
   - Counts (mandible / throat): W3 127 / 68, W16 **629 / 346** (the previs numbers exactly, in spite of the different random generator), W48 1872 / 1032. Longest W16 tooth 6.385 blocks (0.7 R = 5.574, limit x 1.15 = 6.410).
   - Render type: `RenderTypes.entityCutoutCull` for the shell (outer, inner and walls are separate faces wound for their own side), `RenderTypes.entityCutout(texture)` (the pipeline with `withCull(false)`) for the throat and the bristles, so no triangle was doubled. Read from the jar, not seen.
   - Flat ivory is the default (vertex colour `0xFFFFF0` x shade, divided by the white_concrete texel). The one-line switch is `HeadMouth.BONE_TEETH = true` (bone_block_side, tint = shade only). The switch is complete in code and the texture exists; I did not see it.
   - Quads for one build (shell + teeth quads): W3 36 + 254, W16 156 + 1258, W48 456 + 3744; throat (+ teeth): W3 10 + 136, W16 50 + 692, W48 150 + 2064.
   - Build time of one mandible rebuild (shell and teeth, warmed up, in the unit test JVM): W3 0.13 ms, W16 0.39 ms, W48 2.43 ms (the teeth part 0.07 / 0.30 / 0.85 ms). The throat once: 0.07 / 0.21 / 0.60 ms. Frame time of the animated part: not measured (no client).
6. **The flat grey of the throat.** The vanilla texture `minecraft:textures/block/white_concrete.png` (read from the jar: 16x16, opaque, mean 207/213/214), tinted by vertex colour `HeadMouth.flatTint(0x3A3A3A, 1.0)` = 0xFF474545 (target / texel, so the result is #3A3A3A within 2 levels; a test checks it). No PNG is shipped. This is inferred to look right: the tint maths is tested, the look is not seen.
7. **Mouth timing at the defaults, W16** (loop clock, seconds; the closing is 0.9 s):

   | Move | apex | t_start | t_end | closing | clamp |
   |---|---|---|---|---|---|
   | emerge | 5.84 | 4.94 | 5.84 | 0.90 | no |
   | crash | 7.46 | 6.56 | 7.46 | 0.90 | no |
   | engulf | 5.01 | 4.11 | 5.01 | 0.90 | no |
   | crest (close_end 0.3) | 7.94 | 7.35 | 8.25 | 0.90 | no |

   t_end of emerge, crash and engulf is the start of the hold (checked within 1 ms). The clamp is needed when the rise is shorter than 0.9 s: `engulf 3 20 exposed=1 speed=64` (rise 0.39 s) closes in 0.39 s and says so in chat and log.
8. **Nothing rebuilds after closure.** Test over a full loop at 60 frames per second: the number of distinct mouth cache keys (= mandible builds) is 54 for each of emerge, crash, engulf and crest (the closing takes 0.9 s) and **0 after the closed instant**; the loop ends on key 0. The renderer logs `mouth closed t=...` once per loop.
9. **Overlap and clipping.** The outer skin never reaches the throat (the mandibles are in front of the ring plane, z > 0 at 0°, 22.5° and 45°; the throat is behind it). The inner skin dips behind the throat surface only at the hinge, and only when the mouth is nearly closed: deepest, in blocks, closed / 22.5° / 45°: W3 0.007 / 0.003 / 0.001, W16 0.056 / 0.026 / 0.003, W48 0.177 / 0.082 / 0.011 (thickness 0.02 / 0.157 / 0.5). That is at most 0.35 of a thickness, inside the lip wall; I expect it to be invisible but have not seen it. Seam vertices of ring and flaps coincide within 1e-4 at 0°, 22.5° and 45° (W3, 16, 48). The ring's lead plates and the shell lip are different surfaces at the same hinge; z-fighting there is not excluded.
10. **What blocks the next prompt.** Nothing in the code. What the owner's first test must settle before sand displacement, particles, sounds or the breach location build on this head: (a) the renderer works in a client (matrices, double-sided pass), (b) the mouth reads well at the three angles, (c) the cost of ~6,000 quads per frame at W48 (3744 + 456 + 2064 + 150 per frame, resubmitted every frame even when closed, only not rebuilt).

## Numbers
- HEAD_LENGTH: W3 2.62, W16 8.96, W48 24.87 (cone 1.0 R). Open angle 45°.
- Teeth counts, quads and build times: see answer 5. Thickness: see answer 3.
- Closed mouth at W16, quads: shell 156 (50 outer + 50 inner + 50 lip walls + 6 ridge walls), throat 50, mandible teeth 1258, throat teeth 692.
- Phase lengths at W16 defaults (new head): emerge rise 5.84 / hold 2 / retreat 5.84 / pause 1; crash 7.46 / 1.5 / 2.89 / 2; engulf 5.01 / 0.5 / 1.86 / 1.5; loop engulf 8.88 s, crest 25.45 s.
- Tests: 125, 0 failures.

## Surprises, problems, deviations from the prompt
- **The reference file is missing** (see answer 4). I worked from the formulas in the prompt; the counts at W16 matching the previs exactly (629 and 346) is a good sign that the teeth algorithm is the same.
- **One build, one jar, one commit for all three parts** (as in prompt 07); the three tags `head-2`, `teeth-1`, `mouth-1` sit on the same commit. The jar after Part 1 and Part 2 alone was never deployed.
- **Engulf's tunnel is deeper.** The straight stretch below the crossing was sized with `2 r0 + 2`, which the longer head outgrew (the old test "tunnel still straight 3 blocks behind the head at the end" failed at W3). It is now written from the end rule: `HEAD_LENGTH + 1 + (cone + 3) sin(angle) + 0.5` (comment in `Moves.engulf`). The visible part of the move does not change; the rise starts earlier underground (W3 rise 2.05 s → 2.18 s; W16 and W48 unchanged).
- Because HEAD_LENGTH moved, the drop of engulf and the rise and fall of crash changed in length (answer 1); the speeds and the paths above the sand are the ones of the old prompts.
- `WormSkin` no longer contains the flaps, so `WormSkin.flapQuads` is 0 and the `path ...: ... of which head flaps` log line says 0; the mouth has its own `mouth build:` line.
- The legacy `HeadFlaps.append` is no longer used by the game, only by the prompt 04 test.
- The clamp message for a short rise reads "the rise (x s until the closing ends) is shorter than close_time=..."; the time is the end of the closing, which is what the rise has to hold.
- Not run on screen: everything about the look.

## How the owner can see it   (commands, log lines, the "you look" list)
Creative world; `/arrakis_worm_test base here` on non-sea-level ground; stand back about 30 blocks to the side; `/arrakis_worm_test clear` between tests.
- `/arrakis_worm_test moves` (the new keys are in every line).
- `/arrakis_worm_test engulf 16 60`: the mouth is open as the head comes out, closes smoothly and is fully closed as it stops; no more mouth movement through the drop; pause; open again next time, under the sand.
- `/arrakis_worm_test emerge 16 60`, `/arrakis_worm_test crash 16 120`, `/arrakis_worm_test crest 16 80 height=1.3` (try `close_end=0`, `0.3`, `0.6`).
- `/arrakis_worm_test mouthpose 0`, `0.5`, `1`, `off`: the shell, the inner skin, the throat and the teeth at three angles; `freeze hold` shows the closed mouth, `freeze 0.5` the drop (closed).
- The upper (larger) jaw is on top; the two smaller below (look from the side at W16).
- `engulf 3 20` and `engulf 48 120`: shell thickness and teeth at the smallest and the big scale.
- `mouth=open`, `mouth=closed`, `teeth=off`, `open_deg=20`, `close_time=2`.
- Log lines (`logs\latest.log`): `[WormsOfArrakis] <move> mouth: anim openDeg=.. apex=.. closeStart=.. closeEnd=.. closeTime=..` (at the command), `[WormsOfArrakis] mouth build: width=.. angle=.. thickness=.. shell quads=.. mandible teeth=.. throat quads=.. throat teeth=.. teeth=on|off` (client, at the first build of a configuration and when the mouth reaches 0), `[WormsOfArrakis] mouth closed t=..s (... mandible rebuilds since it opened ...)` (client, once per loop), `[WormsOfArrakis] <move> phase <name> t=.. mouth=..deg` (client, each phase change), `[WormsOfArrakis] mouthpose ...`.
- What to report back: does the mouth look like the previs (shell, throat grey, ivory bristles); the timing of the closing against the rise; any popping at the hinge or the ring; any flicker or missing faces; the frame rate at `engulf 48 120`.

## Suggested next steps (your view)
- Look at it before anything is built on it; the first thing to check is the double-sided pass (the bristles from both sides, the throat from the front).
- If the bristles look too uniform, a per-vertex colour (base 1.0 to tip 0.78) is a small change of the mesh container (one colour per quad today).
- If the per-frame resubmission of the teeth at big widths is a problem, a closed mouth can be cached as a vertex buffer; not needed until a frame time says so.
- The mouth is not yet tied to the real move of the head through the sand (breach location, displacement); that comes after the owner has seen this.

## Owner observations (human)
