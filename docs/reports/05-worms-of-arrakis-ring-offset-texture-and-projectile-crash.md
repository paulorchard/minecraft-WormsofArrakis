# Report — Prompt 05 Worms of Arrakis: ring offset texture, lip and liner fixes, projectile crash
Status: partly (Parts 0-2 built, unit tested (71 tests), one dedicated-server smoke start, jar deployed; nothing seen on screen because no client was run)   Commit / tag: fix-2, tex-1, proj-1 (one commit; the three parts share the skin and path code)

## What was built
- Part 0 (fix-2): every failure in `/arrakis_worm_test` goes through one `fail()` that logs and sends it to the command source (chat): crest height above 3.0, crash apex above 6.0, bad or unknown keys (`crest ... foo=1`, `crash ... foo=1`), bad `mouth`, bad `closer`, `freeze` out of range, `offset`/`stagger`/`base`/`freeze` argument errors, crash/crasharc/emerge "too short" and every `Moves.Rejected`. `run()` is wrapped, so any unexpected exception is also reported in chat instead of vanishing. Bend-limit and radius/angle clamps use `notice()` (chat + log). `clear` and `stop` say how many worms they removed, including zero. Lip and rim UVs fixed (answer 2).
- Part 1 (tex-1): per-face tile orientation removed (`tiles` is now an alias that prints "replaced by offset"). Geometric ring rotation (`stagger`) default 0 and kept behind `stagger <fraction|off> [seed]`. One integer pixel texture offset per ring (`Stagger.ringOffsetsPx`), `offset on|off|seed <n>|<n> [seed]`. The liner uses one vertex ring per joint, shared by the two neighbouring liner segments, and is never rotated.
- Part 2 (proj-1): `PlanarPath.parabola(k)` (exact arc length), `Moves.projectile`, command `crash <width> <segments> [apex=2.5] [angle=40] [gravity=12] [mouth=open|closed] [force]` (keyed, bare number = apex). The old crash is `crasharc` with the old syntax, unchanged.
- Jar: `IslandCraft-WormsOfArrakis-0.1.0.jar` (in the ArrakisTest01 `mods` folder, older jar deleted, GeckoMesh stays `.disabled`, no PNG in the jar).

## Confirmed   (tested, or read from the code or the jar; say which)
- 71 unit tests pass: all of prompts 02 to 04 (updated only where the head, tiles and liner changed) plus `Prompt05Test`.
- Read from the 26.1.2 sources jar: `AbstractTexture.sampler` is `getSampler(AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.NEAREST, FilterMode.LINEAR, false)`; `RenderTypes.ENTITY_CUTOUT_CULL` builds its render setup with `withTexture("Sampler0", texture)` and no sampler override (`RenderSetup` uses `texture.getSampler()` when no override is given). The worm texture is the standalone `minecraft:textures/block/dripstone_block.png`, so UVs above 1 repeat.
- Names used compile against the 26.1.2 / NeoForge / GeckoLib 5.5.3 jars (as before; nothing new on the client side in this prompt).
- Smoke start (dedicated server, console as command source): the chat feedback of the command source is echoed by the server log lines tagged `[minecraft/MinecraftServer]`, so each refusal and clamp was seen reaching the source: crest height 4, unknown keys, `crash 16 200` too short (needs 214), `crash 16 260 angle=70` clamped to 57.7, `apex=7` refused, `mouth=wide` refused, `crash 48 400` too short (needs 636), `crasharc 16 80 10` refused, `curve 16 40 1` clamped, `freeze 2` refused, `offset maybe` refused, `clear` / `stop` with "Removed 0" / "Stopped 0".
- Parts of the maths proven by test: offset pattern, one-tile faces, lip/rim UV scale, shared liner joints (bit-identical), coverage with offsets, parabola continuity and unit speed, apex, range, headings, speed profile, apex radius, clamp, bend limit on the flight, holes, short-worm refusal.

## Inferred    (not verified)
- Nothing here has been seen on screen: how the offset rings look, whether the lips are no longer stretched, whether the specks are gone, how the crash falls.
- The cause of the blue specks (answer 3) is inferred, not confirmed.
- `freeze`, `speed`, `clear` and `stop` found no entity in the smoke run (no loaded chunk at the console spawn point); they work through the same entity/spec mechanism as before.
- Texture offsets rely on the REPEAT sampler read from the jar; a shader or mod that changes the entity sampler would break the wrap (the contingency, splitting quads at the tile edge, was not built).

## Answers to the prompt's questions
1. Chat messages: all of the list in "What was built" (Part 0). Tested in the dedicated-server smoke run: the console command source prints the feedback of `sendFailure` / `sendSuccess` as `[minecraft/MinecraftServer]` lines (see Confirmed). The earlier silent `crest height=4` could not be reproduced in the smoke run (it printed in prompt 04's run too); the new wrapper makes any exception, not only a rejection, reach chat.
2. Lip and rim UVs: a lip takes the slice v = 0 .. L of the tile, where L is that vertex's own lip length (0.132 on a straight chain, up to 0.6 where the telescope opens), measured from the skin's trailing edge: the rows continue the skin's texture cyclically (rows 0 to L follow row 1 of the skin). The rim lip continues at v = L .. L + 1/16, the leading rim takes v = 0 .. 1/16. Along the strip, u runs for the real length of that edge in tiles (one block = one tile), per edge, so lips and rims keep tile scale in both directions. Unit test (`lipAndRimSampleOnlyTheirOwnSliceAtTileScale`, widths 3/16/48, straight and at the limit, three planes): across the strip the UV extent equals the strip height within 1e-3 for every lip, rim lip and rim skin quad; along the strip it equals the edge length within 1e-3. Deviation: the prompt asked for the along-strip extent to equal the strip's length "in tiles", and for the skin faces themselves the extent stays exactly one tile (the face chord is 1.011 blocks at the rear edge at W16), so lips and rims are at tile scale per block while skin faces are at one tile per face.
3. The liner seams: not confirmed, and partly not reproducible in geometry. In prompt 04 the liner of each ring was turned by its own stagger and, independent of that, the two sides of a joint were built by two different mitre calculations that agree only in pure yaw or pure pitch bends (they differ by a second-order amount in a mixed bend). Both leave slivers at the joints (the first is up to the polygon sagitta, under 0.02 blocks at n >= 50, about 0.1 at width 3). The lengthwise seams the owner saw, between faces of the same ring, are watertight in the geometry (the faces share their vertex arrays; the prompt 04 test proves it), so those specks are most probably light coming through the joint slivers where a plate slit lines up with them, which is what the owner suspected. Now there is one ring of liner vertices per joint (`ShingleMath.jointRing`, a circle in the mitre plane), used by both neighbouring liner segments, and no per-ring rotation. Test `linerIsWatertightAtEveryJoint` (widths 16 and 48, yaw / pitch / diagonal, straight and at the 2-width limit, stagger 0 and 1): the vertices of the liner polygon at the end of segment j-1 and at the start of segment j are bit-identical to the joint ring. Whether the specks are gone needs your eyes.
4. The texture offset: sampler address mode REPEAT (AbstractTexture default, no override for `entityCutoutCull`), so the method is a UV shift, no split quads. Every face shows exactly one tile (u0 = offset / 16, u1 = u0 + 1, v 0..1; no split, so UV may exceed 1 and wraps). Pattern: even rings (head ring = ring 0) offset 0; each odd ring a seeded 1 to 15 differing from the previous odd ring by 2 to 14 mod 16 (e.g. seed 42: 0, 13, 0, 9, 0, 2, 0, 15, 0, 9, 0, 1). All quads of a ring (skin, lips, rims) take the ring's offset; the liner of ring k takes (offset + 8) mod 16 (half a tile away from its plates, so its seams never line up with their texture lines; liner on even rings is therefore at 8). Deterministic from the seed and the fixed ring index, same ring keeps its offset in a longer worm. Extra quads per odd ring: 0 (10,000 quads for 40 rings at W16 with and without offsets). Build time with offsets, 100 segments: W16 3.0 ms (25,000 quads), W48 9.4 ms (75,000 quads).
5. Projectile crash, defaults (apex 2.5, angle 40, gravity 12): W16: v0 48.20 blocks/s, flight time 5.16 s, range 11.92 W (190.7 blocks), R_apex 7.10 W, exposedMax 211 body rings, segments needed 214 (flight 211.1 blocks). W48: v0 83.49, flight 8.94 s, range 11.92 W, R_apex 7.10 W, exposedMax 634, segments needed 636. Largest angle by apex: 45.00 degrees (apex 1.0), 57.69 (2.5), 63.43 (4.0); at that angle R_apex is exactly 2 widths (tested to 1e-6). Speed: v0 underground and at the surface, v0 cos(a) = 36.93 at the apex for the default (measured 36.93). The prompt's examples `crash 16 200` and `crash 48 400` are therefore refused ("needs at least 214 segments" and "636"); use 214 or more, or `force`.
6. Frame table for `crash 16 260 angle=34` (t = 0, 0.25, 0.5 apex, 0.75, 1.0): tip height 0.00 / 1.89 / 2.50 / 1.86 / -0.01 W; heading +34 / +18 / 0 / -19 / -34 deg; dx 0 / 3.74 / 7.47 / 11.17 / 14.85 W. Defaults (angle 40, 260 segments): 0.01 / 1.90 / 2.51 / 1.85 / -0.02 W; +40 / +22 / -1 / -23 / -40 deg; dx 0 / 3.03 / 6.04 / 9.00 / 11.94 W. Reference strip: heights 2.5, 2.0, 1.5, 0.4 W; headings +85, -15, -30, -80 deg; dx 0, 2.3, 4.3, 7.1 W. If the strip's first frame is the apex, then from the apex of the angle-34 table the heights are 2.50, 1.86 (at +3.7 W), 0 (at +7.4 W): the strip's 2.0 W at 2.3 W and 1.5 W at 4.3 W are steeper than a parabola of apex 2.5 W and range 7.5 W from the apex (this one reaches 1.86 W at 3.7 W and 0.9 W at about 5.6 W). The strip's -80 degrees at 0.4 W is not a projectile heading (it is -34 at landing); that needs the stiff pole behaviour that is not in this prompt. Knobs: `angle` sets the dive angle and the horizontal scale (range = 4 H / tan a), `apex` sets the height and the scale of everything, `gravity` changes only the speed profile and flight time (v0 and the time), not the shape.
7. Blockers for the next prompt: none in the maths. Stiffness / hose behaviour needs the head-tip trajectory to be bent by a body constraint, which the head-leading path (pose = function of the head's path position) does not allow yet: the body only follows the trail. Breach location needs the two hole positions (`sCross1`, `sCross2`) and the surface provider (the world one exists, untested). The mouth rework only touches `HeadSpec` and `HeadFlaps`.

## Numbers
- Offsets: seed 42, rings 0..11: 0, 13, 0, 9, 0, 2, 0, 15, 0, 9, 0, 1. Build time W16/100: 3.0 ms (25,000 quads), W48/100: 9.4 ms (75,000 quads); 0 extra quads.
- Crash defaults: see answer 5. Crash 16 260 at angle 70 (clamped to 57.7): v0 36.66, range 6.32 W, apexRadius 2.00 W, exposedMax 135, segmentsNeeded 137.
- `crash 16 200 force`: exposedMax 199 (whole worm in the air, tail visible).
- Head tip distance from the path at the apex: below 0.06 W (test), as the tip lies on the tangent.

## Surprises, problems, deviations from the prompt
- The example commands `crash 16 200` and `crash 48 400` are refused: the default flight is 211 blocks (W16) and 633 blocks (W48) long, so the worm must be 214 and 636 segments long to keep its tail under the sand at both holes. The argument maximum for segments is now 1000 (crest, crash, crasharc).
- The head path used for the parabola is the path of joint 0 (the front of the head ring); the flap tip lies on the tangent ahead of it, so the tip height equals the apex at the top and the base-level crossing of the tip is a little earlier or later than the joint's (the frame table is measured on the tip).
- The sagitta-level difference between a lip's chord and a tile means lips and rims run at their own edge length per edge in u (see answer 2), not exactly one tile.
- The quad split contingency for a clamping sampler was not built (the sampler repeats).
- `exposedMax` is a scan around the flight and the landing, not every pose.

## How the owner can see it
Jar: `IslandCraft-WormsOfArrakis-0.1.0.jar`. Creative world; run `/arrakis_worm_test base here` on non-sea-level ground; stand back about 40 blocks.
```
/arrakis_worm_test chain 16 12 10 0 1 shingle
/arrakis_worm_test offset off
/arrakis_worm_test offset on
/arrakis_worm_test offset seed 7
/arrakis_worm_test jitter 0
/arrakis_worm_test stagger 0.5
/arrakis_worm_test stagger off
/arrakis_worm_test crest 16 80 height=4
/arrakis_worm_test clear
/arrakis_worm_test crash 16 220
/arrakis_worm_test crash 16 220 angle=34
/arrakis_worm_test crash 16 220 apex=4
/arrakis_worm_test crash 16 220 angle=70
/arrakis_worm_test crash 48 650
/arrakis_worm_test crash 16 200 force
/arrakis_worm_test freeze 0
/arrakis_worm_test freeze 0.25
/arrakis_worm_test freeze 0.5
/arrakis_worm_test freeze 0.75
/arrakis_worm_test freeze 1.0
/arrakis_worm_test freeze off
/arrakis_worm_test crasharc 16 150 25
/arrakis_worm_test stop
```
What to look for: even rings normal, odd rings shifted, no stretched lips, no blue specks or visible liner seams; `crest ... height=4`, `crash ... apex=7` and `crash 16 200` print a message in chat; `clear` and `stop` print a count; the crash as a fountain that slows at the top and falls with the body following the head; `angle=70` prints a clamp message.
Log lines: `path crash: width= segments= apex=...W angle=... (clamped from ...) gravity= v0= flightTime= range=...W apexRadius=...W exposedMax= segmentsNeeded=`, `crash frame t=...: headTipHeight=...W headHeading=...deg headDx=...W exposedRings=...` (t = 0, 1/3, 2/3, 1 at spawn; `freeze crash frame ...` at each freeze), `refused: ...`, `offset ...`, `stagger ...`, `path <mode>: ... offset=... stagger=...` (client).

## Suggested next steps (your view)
- Look at the offset texture, the lips and the liner first; if the specks remain, the next suspect is the lip telescope, not the liner.
- Then compare the projectile crash with the strip; the missing steepness near the end is the stiff-pole part (the head going nose-down faster than a parabola), so the next prompt could add a stiffness term to the head trajectory only near the landing.
- Then breach location and the world surface provider.

## Owner observations (human)
