# Report — Prompt 02 Worms of Arrakis: a worm skin that stays closed when it bends
Status: partly (all three parts built, unit tested and deployed; nothing seen on screen: no client was run, as required)   Commit / tag: skin-1, skin-2, skin-3 (one commit: the three parts share `ShingleMath`, so they landed together)

## What was built
- `geometry/ShingleMath` (pure, no Minecraft classes): builds the whole shingle skin of a `ChainMath` chain: dark liner + plates + telescoping lips + rims, as one float buffer (x y z u v nx ny nz per vertex, 4 per quad, one kind byte per quad).
- Part 1, liner: the closed mitre-ring loft (`ChainMath.miterRing`, new `ChainMath.withRadius`) at radius `r0 - inset`, one quad per face per segment, drawn with vertex colour `0xFF404040` on the vanilla dripstone texture. `inset` = max(0.25, depth needed to stay behind every plate/lip/rim edge + 0.02).
- Part 2, telescoping lip: per ring vertex and joint, the gap from the actual 3D frames: `gap_i = (Q - P) . axis(k-1)`, P = ring point of segment k-1 at the joint, Q = ring point of segment k at the joint (same angle). gap > 0: lip = min(max(base, gap + 0.05), 0.6 x pitch). gap < 0: lip = max(0.02, base + gap) and the skin's trailing edge is cut by min(-gap, 0.3 x skin). Lips are trapezoids (their own length at each ring vertex). Same quad count as before. Only the last plate of a segment gets the telescoping lip (plates inside one rigid segment keep the base lip).
- Rims (`rim_px` = 1): per face, a 1 px strip hanging inward from the skin's leading edge (faces the head) and a 1 px strip continuing the lip downward (faces the tail), same texture and tint as the plates.
- Part 3: `/arrakis_worm_test curve <width> <segments> <radius_widths> [horizontal|vertical|diagonal] [per]`; `/arrakis_worm_test stats on|off`; new `chain ... shingle [taper] [per] [lip] [liner on|off]` closer (older closers none, lip, loft, scale and all prompt 01 commands remain). Geometry cache: the built buffer is reused while the bone angles and spec are unchanged.
- `build.gradle` now compiles against the instance's GeckoLib 5.5.3 (the owner replaced 5.5.2; same interface-injection file; the mods.toml range `[5.5.2,6)` already covers it).
- Tests: `ShingleTest` (straight chain, pure yaw/pitch, diagonal, lip coverage table, liner inside for every bend/plane/width/per, normals, quad counts and build time).

## Confirmed   (tested, or read from the code or the jar; say which)
- Unit test: straight chain, gap 0 at every vertex, every lip equals the base 0.132, no cut, liner inset stays 0.25.
- Unit test: pure yaw and pure pitch: the outside gap is within 8% of r tan(angle) (e.g. width 16: 0.277 vs 0.278 at 2 deg, 1.38 vs 1.404 at 10 deg, 2.72 vs 2.90 at 20 deg; the measured gap is r sin(angle)-like because it is taken along the axis between the real ring points); the inside overlap mirrors it; outside and inside are 180 degrees apart.
- Unit test: diagonal bend (8 deg yaw + 8 deg pitch, n 150): the largest gap sits at the direction the bend points outward, within 1.5 faces, at every joint.
- Unit test: liner stays inside the plates (liner radius below the deepest plate, lip and rim vertex by at least 0.02) for widths 16 and 48, 0/2/5/10/20 deg per joint, yaw, pitch and diagonal, 1 and 4 scales per bone.
- Unit test: normals: stored normal agrees with the quad winding for every quad; skin and liner normals are radial (dot >= cos 5.25 deg); lip and lip rim normals point to the tail (nz < -0.99); leading rim points to the head.
- Read from the client jar `entity.vsh`/`entity.fsh` (26.1.2): the entity shader takes the per-vertex `Color` attribute (`vertexColor`) and mixes the two light directions with the vertex `Normal`. That is why the lower half is darker: it is directional lighting by normal.
- Dedicated-server smoke start: the mod loads with GeckoLib 5.5.3, no errors, "Done". No client class is touched on the server.
- Jar assets: only the animation JSON, no PNG.

## Inferred    (not verified)
- Vertex colour is multiplied into the texture by `entityCutoutCull` (the shader passes `Color` through; not seen on screen). If the liner does not look dark, the render type ignores it and the fallback is plain dripstone.
- The skin renders as intended: the new geometry was never drawn in a client this prompt.
- Frame-time numbers below are build times in a JVM test; the real cost on the owner's client (vertex submission, GPU) was not measured.
- Entity Culling / Sodium / Iris effects on the large entity are untested.

## Answers to the prompt's questions
1. Tint: read from the shader that vertex colour is honoured; not yet seen. Liner colour 0xFF404040 (dark grey multiplier on dripstone).
2. `liner_inset`: default 0.25, raised automatically per frame to (r0 - deepest edge radius + 0.02) when telescoped lips reach deeper (0.288 at 2 body widths radius, about 0.535 at 1, 0.588 at 0.5). Proof: `ShingleTest.linerStaysInsideForEveryBendAndPlane` and `lipsCoverTheGap...` assert liner radius <= deepest plate/lip/rim radius - 0.02 for all tested bends. Straight chain: lip reach is 0.04 + rim, so 0.25 is enough.
3. Gap and lip (width 16 / width 48, horizontal bend, per = 1, from the test table):

| radius (body widths) | deg/joint W16 | max gap (outside) | min gap (inside) | longest lip |
|---|---|---|---|---|
| 8 | 0.45 / 0.15 | 0.062 | -0.062 | 0.132 (base) |
| 4 | 0.90 / 0.30 | 0.124 | -0.124 | 0.174 |
| 2 | 1.79 / 0.60 | 0.248 | -0.248 | 0.298 |
| 1 | 3.58 / 1.19 | 0.496 | -0.496 | 0.546 |
| 0.5 | 7.16 / 2.39 | 0.991 | -0.991 | 0.600 (cap) |

   Gaps are nearly the same for both widths because gap = r x pitch / Rc and Rc scales with width. Per-joint bends from prompt 01's fixed-angle tests (pure yaw, per = 1, width 16): outside gap 0.28 / 0.69 / 1.38 / 2.72 at 2 / 5 / 10 / 20 deg.
4. Tightest fully closed by the lips: 1 body width radius (gap 0.50 + margin 0.05 fits under the 0.6 cap). At 0.5 widths the outside gap (0.99) exceeds the cap: 112 ring vertices (width 16) and 336 (width 48) per tested chain are not covered by a lip; the liner closes them (dark, no sky). On the inside of the bend, plates still intersect once the overlap exceeds the 0.3 cut: from 1 body width radius on (98 vertices at width 16, 308 at width 48 in the test chain), and from 5 deg per joint at per = 1 in the straight-angle tests (overlap 0.69 > 0.3). Intersections are visible only as plates crossing on the inside of the bend. The liner always closes the sky.
5. Quads: per scale 4n (skin n, lip n, 2 rims n each) = 200 at W16, 600 at W48; the liner adds n per segment. Per worm (per = 1): W16 60 segments 15,000 (liner 3,000 / plates 3,000 / lips 3,000 / rims 6,000), W16 100 segments 25,000, W48 60 segments 45,000, W48 100 segments 75,000. Build time of the pure geometry in a JVM test (bent chain, average of 5 after warm-up): 2.1 ms (W16/60), 3.9 ms (W16/100), 8.1 ms (W48/60), 10.5 ms (W48/100). Cache: a static worm builds once and then reuses, so the build cost per frame drops to about 0 (only the per-frame vertex submission remains, which can only be measured on the client with `stats on`). A waving worm rebuilds every frame.
6. Normals: skin, lip, liner and rims were checked in a unit test (above): radial outward, lip toward the tail; the stored normal matches the winding. The dark underside is the entity shader's directional lighting by normal, not a normal bug.
7. Owner decisions next: inset (0.25 default, auto-raised), tint (0x404040), rim width (1 px), max lip (0.6 x pitch; at 0.5 body widths radius it is exceeded), whether to accept plate intersection on the inside of very tight bends or give plates a 0.3+ cut, and how many scales per bone. Blocks for prompt 03: nothing in this code; the skin takes a pose array (`yaw`/`pitch` per joint) so a head-leading path can drive it; but I measured build time only in tests, not on the client, so check `stats` first.

## Numbers
- Straight chain: gap 0, lip 0.132, liner inset 0.25.
- Quads: see answer 5. Max telescoped lip 0.6 (cap); liner inset up to 0.588 at 0.5 body widths radius.
- Build time (JVM test): 2.1 / 3.9 / 8.1 / 10.5 ms for W16/60, W16/100, W48/60, W48/100 segments.
- Client numbers (submit time, frame time) need `stats on` in the game.

## Surprises, problems, deviations from the prompt
- The owner's instance now has GeckoLib 5.5.3 (5.5.2 was removed), so the first compile failed; the build now points at 5.5.3.
- Coverage test (d) in the prompt asks that the skin and lip reach the next plate's front ring along the axis. The lip is a radial wall, so it cannot reach axially; I test instead that the lip drop is at least gap + margin (or the cap) at every ring vertex, and I report where the cap leaves a gap.
- Auto-raised liner inset: a fixed 0.25 would let telescoped lips (up to 0.6) hang below the liner; the inset is raised per frame to stay behind them. This is a deviation from the plain knob and changes the liner depth with the bend.
- Rims are my reading of "a thin strip on the lip's lower edge and the skin's leading edge" (a strip continuing the lip, a strip hanging from the leading edge); not seen on screen.
- Skin trailing edge is cut by the whole overlap (up to 0.3) wherever the gap is negative, not only when plates intersect.
- Diagonal `curve` uses equal yaw and pitch per joint (a helix-like arc, not a planar arc).
- `shingle` ignores taper (constant radius).
- The stats chat line uses `LocalPlayer.sendSystemMessage`; it compiled but was never run.
- Nothing was viewed on screen in this prompt (no client was run). Prompt 01's screenshots came from repeated dev-client runs, which the owner asked not to repeat.

## How the owner can see it
Jar: `IslandCraft-WormsOfArrakis-0.1.0.jar` in `ArrakisTest01\mods` (GeckoLib 5.5.3 there; GeckoMesh stays disabled). Creative world, flat ground, fly around.
- `/arrakis_worm_test chain 16 12 10 0 1 shingle`, then the same with `... shingle 1 1 0.132 off` (liner off): with the liner on there are no see-through slits.
- `/arrakis_worm_test curve 16 40 1` (radius one body width, the tightest crest) and `curve 16 40 4`: closed, shingled; look at the outside and the inside of the bend. Also `curve 16 40 0.5` to see the case beyond the lip cap.
- `/arrakis_worm_test curve 48 60 2` for the big worm. Plane option: `curve 16 40 2 vertical` or `diagonal`.
- `/arrakis_worm_test stats on`: a line every 5 seconds in `logs\latest.log` (and in chat): segments, faces per ring, quads (liner/plates/lips/rims), build and submit ms, frames rebuilt and reused.
- Log lines: `[WormsOfArrakis] skin shingle: segments=... liner=... (colour ..., inset ...) quads ... maxGap=... minGap=... uncoveredVertices=... cutVertices=... intersectionsLeft=...`, `[WormsOfArrakis] curve: ... => ... deg per joint`, `[WormsOfArrakis] stats: ...`.

## Suggested next steps (your view)
- Look at `curve 16 40 1` and `curve 16 40 0.5` first: they show whether the dark liner, lip cap and inside intersections are acceptable.
- Read the `stats` line for `chain 48 100 ... shingle` and `wave ... scale` to decide whether the build time (about 10 ms for a 75,000-quad worm) is fine, or whether a lower-resolution mesh for far worms is needed before prompt 03.
- If the tint does not show, use the unlit/dark variant of the render type or plain dripstone.
- Consider a longer cap (0.8) or two-ring lips for 0.5 body widths radius.

## Owner observations (human)
