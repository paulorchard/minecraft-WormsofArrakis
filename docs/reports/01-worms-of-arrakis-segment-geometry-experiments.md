# Report — Prompt 01 Worms of Arrakis: worm segment geometry experiments with GeckoLib and GeckoMesh
Status: partly (GeckoLib only: GeckoMesh does not load on NeoForge 26.1.2, owner chose "continue without GeckoMesh". Experiments 1–5 all built and checked in a dev client)   Commit / tag: exp-1, exp-2, exp-3 on commit 3f066da; exp-4, exp-5 on the commit that carries this report (see `git tag`)

## What was built
- Test entity `arrakis_worm_test` (no physics, not saved, not summonable, 1024-block render distance), rendered by a `GeoEntityRenderer` with the vanilla `minecraft:textures/block/dripstone_block.png` by reference (nothing copied into the jar; the jar holds only classes, the toml and one animation JSON).
- Command `/arrakis_worm_test` (op level 2): `ring <width> [faces]`, `segment <width> <length> [taper]`, `chain <width> <segments> <bend> [pitch] [seglen] [closer] [taper]`, `wave ...`, `mouth <width> open|close|cycle`, `clear`.
- Exp 1: a ring of zero-thickness GeckoLib cubes, one per face, generated as Bedrock JSON and baked by GeckoLib's own `Geometry` baker.
- Exp 2: one lofted frustum segment as a `GeoBone` subclass (`LoftBone`) holding hand-built `GeoQuad`s.
- Exp 3: `testpack/ArrakisWormTestPack.zip` (64x64 numbered grid replacing only `dripstone_block.png`), copied to the instance `resourcepacks`, not enabled.
- Exp 4: chain of nested bones with three bend closers (`none` = plain hoops, `lip` = overlap sleeve, `loft` = code-generated mitre skin) and a procedural travelling wave.
- Exp 5: mouth = 4 body segments + 3 triangle-fan flaps, opened and closed by a GeckoLib animation file.
- Dev-only screenshot harness `AutoTest` (active only with `-Darrakis.autotest=true`, never in the instance), unit tests (geometry, bake, chain), `tools/MakeTestPack.java`.

## Confirmed   (tested, or read from the code or the jar; say which)
- GeckoLib 5.5.2 (`com.geckolib`, MIT) loads on a dedicated server and in a dev client on NeoForge 26.1.2.129 (tested). GeckoMesh 1.2.0 (MIT) fails to load: needs NeoForge `[26.2.0.1-beta,)` (tested, server). The instance runs 26.1.2.127.
- Per-face geometry via GeckoLib (tested, unit test + screenshots): zero-thickness cubes are accepted; a cube with only an `up` UV gives exactly one quad; the 7.2° face rotation survives baking; arbitrary rotation (any angle, not just 22.5° steps) works because GeckoLib bakes cube rotation as radians.
- Face counts (unit test): n=10 → 10 cubes/quads, n=50 → 50, n=150 → 150, each with one non-null quad.
- Winding: my frustum quads use the same convention as GeckoLib's baked cubes (`(v1-v0)×(v3-v0)` = stored normal), checked by unit test. With `RenderTypes.entityCutoutCull` the outside is visible and the inside is culled (screenshots: from inside a ring or segment nothing is drawn).
- The vanilla block texture path works as an entity texture: the dripstone texture appears on the rings, segments and flaps (screenshots).
- Per-frame bone hook: `GeoRenderer.adjustModelBonesForRender(RenderPassInfo, BoneSnapshots)` with `BoneSnapshot.setRotation(x,y,z)` in radians (tested: the chain and the mouth frames move every frame).
- GeckoLib bone pivots are in 1/16 block units (pixels), while `GeoQuad`/`GeoVertex` positions are in blocks (found by debugging: rotating about a pivot given in blocks acted as if the pivot were 16x too close to the origin). `LoftBone` now multiplies pivots by 16.
- Bend closers (screenshots from above, width 16, 12 segments of length 2, no taper):
  - `none` leaves visible slits on the outer side from 2° per joint onward; at 10° the outer side is a fan of separate slabs.
  - `lip` (sleeve radius 0.96 R) hides the slits at 5° and 10° per joint, the outer edge becomes a sawtooth (visible at 10°).
  - `loft` is a clean closed elbow at 2°, 5°, 10° and 20° (the mitre rings of neighbouring segments coincide exactly in a pure yaw or pitch bend, by unit test).
- Wave (tested, screenshots): a smooth closed S-bend with the `loft` closer; with the crest travelling toward the tail.
- Mouth (screenshots): closed = three-petal cone; open = flaps hinged outward; `cycle` loops open/close.
- Dev server log shows no errors, and no client class is touched on the server.

## Inferred    (not verified)
- Entity Culling, Sodium and Iris are in the instance and may hide or alter the huge entity; I could not test them in the dev client.
- The effective tracking range is capped by the server's view distance and entity-broadcast-range-percentage; not measured.
- Mixed body-yaw plus bone-pitch bends at once (diagonal bends) were not screenshotted; the mitre rings are exact only in pure yaw or pure pitch.
- Frame time was not measured (no profiler run); only quad and bone counts below.

## Answers to the prompt's questions
1. GeckoLib 5.5.2, MIT: loads on 26.1.2 — yes. GeckoMesh 1.2.0, MIT licence text: loads on 26.1.2 — NO (requires NeoForge 26.2.0.1-beta or newer). The owner decided to continue without it.
2. Exp 1: arbitrary face rotation works (radians, Z then Y then X). The vanilla texture path is accepted. One 16 px tile per face via `uv: {up: [0,0,16,16]}` on a zero-height cube, which gives one quad per face. From inside the ring nothing is drawn with `entityCutoutCull` (use `entityCutout` for two-sided). Quad counts: n=50 → 50 quads, n=150 → 150 quads. Face count rule that reproduces the prompt's 10/50/150: `floor(πW/s)` with minimum 10 (see deviations).
3. Exp 2: GeckoMesh could not be used (does not load). Instead a `GeoBone` subclass draws pre-built `GeoQuad`s; the frustum is drawn closed and tapered. For the code-generated skin: `SubmitNodeCollector.submitCustomGeometry(PoseStack, RenderType, CustomGeometryRenderer)` where the renderer implements `render(PoseStack.Pose, VertexConsumer)`; RenderType `RenderTypes.entityCutoutCull(Identifier)`; vertices via `VertexConsumer.addVertex(x,y,z,color,u,v,overlay,light,nx,ny,nz)`.
4. Exp 3: the pack replaces the vanilla texture when enabled, because the worm references `minecraft:textures/block/dripstone_block.png` by name, so the standard resource loader resolves it through the active packs. I confirmed the vanilla texture displays; I did not enable the pack in a game run, so the pack path itself is by design, not by screenshot. The pack is `pack.mcmeta` format 84 with a 64x64 grid.
5. Exp 4: largest per-joint bend with a closed skin: `loft` closes at any bend I tried (up to 20°, mathematically any pure yaw/pitch bend); with bones only (`lip`) it stays closed to about 10° per joint at width 16, length 2 (sawtooth edge); plain hoops are open from 2°. Closer chosen: (c) code-generated loft skin, mitre rings. Hook for per-frame bones: `adjustModelBonesForRender` (snapshot rotations); the loft skin is rebuilt per frame in `submit`. Culling box for 100 segments × 48 wide: reach ≈ 100·2 + 2·23.87 + 2 ≈ 250 blocks from the head in every horizontal direction (`getBoundingBoxForCulling` overridden to ±reach). Tracking range needed: at least about 250 blocks; the entity is registered with `clientTrackingRange(64)` chunks (1024 blocks), subject to server caps.
6. Exp 5: each flap is a fan of round(n/3) triangles (each a `GeoQuad` with a repeated vertex) from an apex on the axis 0.6 R in front of the ring to a 120° arc of the rim. Three flaps close into a cone. Each flap hangs under a frame bone (pivot on the axis, turned 0°/120°/240° about Z by the renderer every frame) and hinges on its own bone whose pivot is on the hinge chord (R/2 from the axis). The GeckoLib animation file rotates the flap bones about X by 0 ↔ −70° (`mouth_open`, `mouth_close`, looping `mouth_cycle`); the same animation drives all three flaps because the frames orient them.
7. Owner decisions next: (a) accept the code-generated loft skin as the worm body renderer (bones only for the head/mouth and pose), or ask for a cheaper static-mesh path; (b) the mouth: keep three flaps and the 70° opening, or change flap count/shape; (c) how long a worm is wanted (it sets culling box and tracking range); (d) whether to keep GeckoLib as a hard dependency or ask for a GeckoMesh build for 26.1.2; (e) enable the test pack and check the grid on the worm yourself.

## Numbers
- Rings: n=10 (W3), 50 (W16), 150 (W48); R = 1.62, 7.97, 23.87 blocks; one quad per face.
- Plain-hoop wedge gap on the outer side, `r·tan(bend)`: width 16: 0.278 / 0.697 / 1.404 / 2.898 blocks at 2 / 5 / 10 / 20°; width 48: 0.834 / 2.089 / 4.210 / 8.690.
- Loft skin cost: segments × n quads rebuilt every frame: 100 × 150 = 15,000 quads, 100 bones' snapshots, no per-quad objects kept. Bone path: same quad count, plus 100 bone transforms.
- Mouth: 4 body segments + 3 frame + 3 flap bones; 17 triangles per flap at width 16 (n=50).
- Culling box ±250 blocks for 100 segments × length 2 at width 48.

## Surprises, problems, deviations from the prompt
- GeckoMesh does not load on 26.1.2 (needs NeoForge 26.2). I stopped and the owner chose option A (GeckoLib only), so exp 2 used a `GeoBone` subclass and the `submitCustomGeometry` route, not GeckoMesh's mesh.
- GeckoLib 5.5.2 parses `poly_mesh` but does not render it (that is GeckoMesh's job), so I did not use it.
- The prompt's face formula `round(πW/s)` gives 9 for W=3 and 151 for W=48 but the prompt states 10, 50, 150. I used `floor(πW/s)` with a minimum of 10, which gives 10, 50, 150.
- Pivot units (above): the first chain screenshots were taken before this was found and were wrong; all chain and mouth results in this report are from after the fix.
- Experiments 1, 2 and 3 share one commit (3f066da) and its three tags.
- The project lives in `C:\Apps\IslandCraft-MC\WormsofArrakis` (the existing skeleton), not in a new `WormsOfArrakis\WormsOfArrakis` folder.
- The owner must remove `geckomesh-neoforge-26.1-1.2.0.jar` from the instance `mods` folder or the game will not start (I did not touch it).
- Entity Culling, Sodium and Iris are in the instance and may affect how the large entity looks.
- Debug logging remains in `TestCommand` (spawn lines), which is intended for the owner's check.

## How the owner can see it
1. Remove `geckomesh-neoforge-26.1-1.2.0.jar` from `C:\Users\paulo\curseforge\minecraft\Instances\ArrakisTest01\mods`. The mod jar there is `IslandCraft-WormsOfArrakis-0.1.0.jar` (GeckoLib 5.5.2 is already there).
2. Start the instance, make a flat creative world with cheats, stand on flat ground, and use spectator or fly to look around.
3. Try: `/arrakis_worm_test ring 16`, `segment 16 8 0.8`, `chain 16 12 10 0 2 none` (then `lip`, `loft`), `wave 16 12 10 2 loft`, `mouth 16 cycle`, `clear`.
4. To check the texture path: enable `ArrakisWormTestPack.zip` in Resource Packs; the worm then shows the numbered grid.
5. Log lines (latest.log): `[WormsOfArrakis] loaded: prompt 01 geometry experiments ...`, `test content registered`, `client: test worm renderer registered`, `spawned test worm: spec=...`, `exp1 ring: faces=...`, `exp2 segment: ...`, `exp4 chain/wave: ...`, `exp4 loft skin: submitCustomGeometry, ...`, `exp5 mouth: ...`.

## Suggested next steps
- Decide the worm body path (loft skin per frame vs a cached mesh rebuilt only on change) and measure frame time with a 100-segment worm.
- Test with Sodium, Iris and Entity Culling enabled, at distance, for the big culling box.
- Add real texture mapping (V along the length, per-face U) and a separate head model.
- Check diagonal bends and extreme per-joint angles on the loft skin.

## Revision 2 (2026-11-23): L-shaped scale, per the revised prompt
The first pass (above) used plain frustum segments. The revised prompt adds the L-shaped scale (sloped skin cone plus a lip ring), `scale_direction`, and the `scale` / `scales` commands. This revision adds them.
- New pure code: `geometry/ScaleMath` (skin 1.0 long, tilt 5.25 deg, lip 0.132, pitch 1.0, tailward/headward), `geometry/ScaleGeoJson` (2a: zero-thickness cuboids, skin + lip per angular position, one bone per angular position). Unit tests in `ScaleTest` (owner numbers: rise 0.0915, extent 1.008, lip end 0.04 inside the start radius; tailward lip faces the tail, headward lip faces the head; lip UV is v 0..0.125 = 2 px; row of 8 = 16 n quads; the baked cuboid tilt sign raises the tail edge).
- Commands: `/arrakis_worm_test scale <width> [tailward|headward] [cuboid|mesh]`, `/arrakis_worm_test scales <width> <count=8> [tailward|headward] [cuboid|mesh]`. `chain <width> <segments> <bend> [pitch] [seglen] scale [taper] [per] [lip]` uses scales as the segment skin (`per` = scales per bone, `lip` = lip length, e.g. 0.2 or 0.3). `wave <width> <segments> <amp> <seglen> scale`.
- 2a (cuboids): 2n cuboids per scale, n+1 bones. 2b: GeckoMesh does not load on 26.1.2, so the `mesh` flag draws true trapezoid quads (2n per scale, 1 bone) through the `LoftBone` route, not a GeckoMesh poly mesh. This is a stand-in, not GeckoMesh's format.
- Jar deployed: `IslandCraft-WormsOfArrakis-0.1.0.jar` (older one deleted first; other mods untouched). Jar assets: only `assets/islandcraft_wormsofarrakis/geckolib/animations/arrakis_worm_test.animation.json`; no PNG.
- Log lines: `exp2a scales(cuboids): dir=... faces=... scales=... cubes=... bones=...`, `exp2b scales(mesh quads): ... quads=...`, `exp4 chain: ... closer=scale scalesPerBone=... lipLength=...`.
- Not verified by a picture: the new scale shapes were checked by unit tests only (maths and baked values). I did not run a client for this revision, as the prompt requires. The owner should look at `scales 16 8` in both directions and both flags; the cuboid tilt direction and lip face are the likeliest things to be wrong visually (the test checks the baked rotation sign, not the rendered result). Whether tailward or headward sheds sand better is the owner's reading; I have not judged it.
- Deviation to own: the first pass ran the dev client repeatedly for screenshots (the prompt says the owner tests in the instance and a dev client run is only for what cannot be shown otherwise). The chain/wave/mouth screenshots in the first pass came from those runs.
- Cuboid skin sits on the face apothem, mesh skin on the circumradius ring, so the two readings differ by under 0.05 block at width 16.

## Owner observations (human)

