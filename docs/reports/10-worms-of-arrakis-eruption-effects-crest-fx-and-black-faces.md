# Report — Prompt 10 Worms of Arrakis: eruption effects, crest fx and black faces

Status: partly — all three parts built, 181 unit tests pass (0 failures) and one dedicated-server smoke start ran clean; nothing was seen in a client (hard rule), so the look of the eruption and whether the light fix cures the black faces are unverified.   Commit / tag: tags `fx-4` and `fx-5` on commit 6fd0c61 (parts 1 and 2 were built together), tag `light-1` on commit d217acd (part 3 and this report)

## What was built

**Part 1 — the eruption (fx-4).**
- `fx/EruptionFx.java`: the owner's twelve rows as data (names, numbers exactly as in the prompt) and the constants `ERUPTION_SUSTAIN=5.0`, `ERUPTION_JETS=14`, `JET_REROLL=0.6`, jet angle sigma 0.12, strength 0.5..1.4, shade 0.85..1.15, fade 15 % in / 40 % out.
- `FxPlayer`: the particle code now works on "lanes" (a row at a site). Eruption rows are lanes anchored at the breach; bursts are spread evenly over their `over` seconds; sustain rows run to breach + 5 s. Jets are re-rolled every 0.6 s (`FxPlayer.jetsFor(seed, epoch)`, deterministic); a jets particle picks one of the 14 jets, takes its angle plus a gaussian of 0.12 rad and multiplies up and out by the jet's strength. Placements ring, disc, jets, curtain and the velocity rule are as written. The eruption uses its own random stream, so the older rows come out exactly as before.
- New key `eruption=on|off` (default on) on crest, emerge, crash, engulf, generated into `moves`; spec field p31. `fxcount` scales the eruption rows like the others.
- `FxSink.Spawn` carries endScale, alpha, shade, drag and a `shaped` flag (eruption rows only). New particle type `CHUNK`.
- `fx/PuffTracker.java` (pure): follows every puff, once per client tick sets size = start x (1 + (endScale-1) f) and opacity = row alpha x fade(f), drops it at death. `FxClient` ticks it, also when no worm is left.
- `FxClient` sink: colour = tint x shade (clamped to 1), the sheet's gravity on the puffs, drift factor with the row's own drag.
- Budget raised from 12000 to 14000; the first half of an eruption burst is never thinned (see answer 1).
- `fx` chat/log show the eruption rows (`eruption: ...` lines, `fx sheet:` lines); the `crash fx:` log line says `eruption=on|off`.

**Part 2 — the crest (fx-5).**
- Crest effects are on by default (`fx=off` still works); the partial-sheet note is gone.
- `FxAnchors` gets `apex` (all moves) and for the crest `riseEnd = apex + close_end` (end of the mouth closing) and `fallStart = apex`. The anchors line of crash, emerge and engulf is unchanged.
- `FxGeometry.Snapshot` gets `entryZ` and `exitZ`. Crash, emerge, engulf: `entryZ = crossZ` as before. Crest: tip above the sand -> the first crossing from the tip is the entry; tip below -> the first is the exit and a second one, if the body still passes it, is the entry.
- Crest rows: no hold rows and no "Hold: sliding" sounds; "Fall: sand pouring into the hole" runs at the exit from the reentry to the end; "Fall: rushing sand" starts at the reentry; the re-entry rows are at the exit; the Rise rows run until the apex; the base mask runs at the entry from the breach and at the exit from the reentry; everything else as the prompt's table. A crossing emits only while some part of the body still passes through it.

**Part 3 — the black faces (light-1).**
- `light/LightMath` (packing = the game's `LightCoordsUtil.pack`, max-combine, smoother, mode choice), `light/LightProbe` (sample points, fake-grid reading), `light/LightWatch` (classifier, log lines, 200-sample ring, rate limit), `light/QuadCheck` (degenerate quads). Client: `test/client/LightClient` (samples every 4 ticks, logs, serves the renderer), hooks in `TestWormRenderer` (three `lightCoords` sites, mesh checks).
- New key `light=probe|vanilla|full` (default probe) on every move; spec field p32.
- Commands: `light`, `light dump`, `light log on|off|all`, `light force full|auto|<sky> <block>`.

## Confirmed   (tested, or read from the code or the jar; say which)

Read from the 26.1.2 patched sources (`minecraft-patched-26.1.2.129-sources.jar`):
- `SingleQuadParticle.setAlpha(float)` is **protected**, and so are the fields `alpha`, `quadSize`, and `Particle.gravity`. `setColor(r,g,b)`, `scale(float)`, `setLifetime`, `setParticleSpeed` and `isAlive()` are public. `PlayerCloudParticle.getQuadSize` multiplies by min(1, age/lifetime x 32) (a cloud grows in during the first 1/32 of its life). `DustPlumeParticle.tick` multiplies gravity by 0.88 and friction by 0.92 each tick.
- `ClientLevel.addEntity(Entity)` is public; `Display.BlockDisplay` keeps its block state and transformation in private setters and synched data.
- `EntityRenderer.getPackedLightCoords(entity, partial)` = `LightCoordsUtil.pack(blockLevel, skyLevel)` at `BlockPos.containing(entity.getLightProbePosition(partial))`; `Entity.getLightProbePosition` = `getEyePosition(partial)` = position + `getEyeHeight()`; `EntityRenderer` line 284 stores it in `state.lightCoords`; block level is 15 when the entity is on fire, else `level.getBrightness(LightLayer.BLOCK, pos)`, sky is `getBrightness(SKY, pos)`. `EntityDimensions` default eye height = height x 0.85, and the worm is `sized(1, 1)`. `LightCoordsUtil.pack = block << 4 | sky << 20`. In 26.1.2 `Level.getSkyDarken()` is an int and there is no `getDayTime()` (the log uses `getDefaultClockTime()`).
- Tested (Prompt10Test 15 tests, Prompt10LightTest 13 tests; suite 181, 0 failures; run twice, the second time after the build): the twelve eruption rows equal the table; counts per eruption row at W16 within 5 % of rate x duration x scale (budget lifted for that test); jets re-rolled every 0.6 s and every plume particle is within 5.5 sigma of a current jet; curtain heights 0.5..14.5; chunk rows make 0.25 blocks, opaque; shrink monotone and 0.333 x start at death within 2 %; fade curve (0, 0.5, 1, 1, 1, 0.5, 0 at f = 0, 0.075, 0.15, 0.4, 0.6, 0.8, 1); the budget thins every row by one ratio, keeps every row, spares the first half of a burst; eruption=off makes no eruption rows and leaves the old rows identical; crest anchors in order for W6/16/48 and heights 0.7/1.3/2.0; two crossings on a long crest body, one before the reentry; entry rows never at the exit and the reverse; no hold rows; 76 sounds; crash/emerge/engulf anchors unchanged; the classifier (probe-in-solid, probe-dark-air, reference-also-dark, not dark), the 5 s rate limit with the suppressed counter, the 200-sample ring; sample points never inside the body (distance from the axis > r0 + 0.5) and never below base + 1 (4 moves x 3 widths x every 0.25 s, more than 3000 points); max-combine; smoothing at most one level per tick and converging; `light=vanilla` returns the input exactly; `light=full` is 15/15; a fake slope where vanilla reads 0 and the probe reads 15; flat world in the open: probe equals vanilla at every instant tested; the normals check; a built mouth (throat, shells, teeth; W6, 16, 48; teeth on and off; angles 0, 5, 20, 45) and the body of a crash at four instants have **no** degenerate quads (`build/prompt10-light-numbers.txt`).
- Dedicated-server smoke (one start): the new keys and their refusals (`eruption=maybe`, `light=dim`), `eruption=off`, `light=vanilla/full`, crest `fx=off` (log `crest fx: off`) and default (`crest fx: on ... sound plays=76 anchors: ... apex=7.94`), the `light` commands and their clamps/refusals, the `fx:` line with `eruption=`.

## Inferred    (not verified)

- How the eruption looks. No client was started. The previs numbers are copied; the sprites are vanilla, scaled.
- That the previs's size column is the full width of a quad (as in prompt 09), and that the previs's friction was its `drag` column (used as given).
- Black faces: H1 is the most likely cause (see answer 5) but no black face was seen by me. That the light engine stores 0 inside an opaque block is the usual behaviour of the engine, not read from its code here.
- That setting `quadSize` directly (not `Particle.scale`) per tick is cheap; the measured cost is of the pure bookkeeping, not of the game.
- Block-display chunk cost (answer 3) is an estimate from the source, not a measurement.
- The access transformer is applied in the owner's instance (NeoForge reads `META-INF/accesstransformer.cfg` of a mod jar by itself). The jar contains the file; a first run is the proof. If it were not applied the client would crash with an `IllegalAccessError` on the first puff; the log line to look for is `fx error for worm`.

## Answers to the prompt's questions

**1. The eruption: peak alive and spawn per second at W6/16/48, budget, thinned rows at W48.** Crash (seed 7; "avg" = spawns per second from breach to end; sheet only / with eruption):

| crash | scale | total spawned | peak alive | avg per s | busiest second | thinned |
|---|---|---|---|---|---|---|
| W6 off | 0.36 | 2120 | 664 | 463 | | 0 |
| W6 on | 0.36 | 12536 | 5420 | 2737 | 3811 | 0 |
| W16 off | 1.00 | 9455 | 1818 | 1221 | | 0 |
| W16 on | 1.00 | 37195 | 14001 | 4803 | 10297 | 1074 (2.9 %, 19 capped ticks) |
| W48 off | 2.00 | 39052 | 3723 | 2366 | | 0 |
| W48 on | 2.00 | 63065 | 14017 | 3821 | 11506 | 33892 (93 capped ticks) |

Emerge and engulf are the same within a few percent (W16 on: peak 14002 / 14001, thinned 934 / 1353). Budget chosen: **14000** (the allowed maximum; vanilla cap 16384 leaves 2384 for the world). **Deviation:** the prompt expected the approved eruption to pass unthinned at W16. My estimate peaks at 14001 against the previs's 13,800 (1.5 % apart), so the budget trips for 19 ticks and thins 2.9 % of the particles at W16. The first half of every eruption burst is exempt. At W48 (scale capped at 2.0; the eruption needs no cap of its own) the made/wanted per row is: ground dust bank 45 %, rising dust columns 45 %, spiky sand plumes 45 %, sand blocks in the plumes 45 %, curtain 42 %, high haze 43 %, fine grain haze 45 %, sand fountain 89 %, central column 80 %, flying sand blocks 97 %, dust ring 100 %, shock ring 100 %. The older rows at W48 with the eruption on: sand skirt, dust skirt, low cloud 83 % of their count without it, rise pouring 72 %, the rest 99-100 %. `fx: capped` is logged once per worm.

**2. Particle calls per type.**
- Size: `Particle.scale(sheet size / real width)` once at creation for all types (grain 0.15 or 0.10, chunk 0.25/0.15 = 1.67x, dust_plume sizes 3.2..7 via 0.225, cloud 3.6 via 0.5625). Shrink: the puffs' `quadSize` field is written each tick (field made public by the access transformer; no bounding-box work, no accumulated ratio error). Alpha: the `alpha` field is written each tick (also made public: **`setAlpha` could not be made public**, `SpellParticle` overrides it as protected and the game recompile refuses that), alpha x fade(f). Colour: public `setColor(r,g,b)` = tint x shade, clamped to 1. Gravity: the `gravity` field (AT) set for the puffs; `DustPlume` multiplies it by 0.88 per tick so its -0.01 fades. Drift: the velocity is multiplied by `ParticleModel.driftFactor(type, life, drag)` with the row's own drag.
- Nothing the API refused. Block grains and chunks are opaque and keep vanilla's gravity 1.0 (equal to the previs's 1) and friction 0.98 (the chunk's 0.99 is compensated by the drift factor). What the owner sees instead of nothing: the cloud's own first-1/32 grow-in on top of my fade-in (a very small effect).
- Cost of the shrink/fade bookkeeping (pure part, JIT warm, from `numbersForTheReport`): 10 us per tick at 2000 puffs, 30 us at 6000, 59 us at 12000 (the game's two field writes are included, the rendering is not). The peak followed at W16 is about 6000 puffs, so about 30 us per tick. The client logs `fx puffs: N followed now, peak P, worst tick U us` every 5 s while a worm is on.

**3. The chunk type and the block-display experiment.** Built: `PType.CHUNK` = a sand block particle (`BlockParticleOption`, sand) scaled 0.25/0.15, long life, high speed, spread 0.2-0.3, vanilla gravity and block collision; a flat billboard, not a tumbling cube. Experiment (read from the source, **not measured, not built**): `ClientLevel.addEntity(Entity)` is public, so a mod can add a client-only `Display.BlockDisplay` that the server never knows; its block state, transformation and interpolation are private setters, so it needs reflection or another access transformer; ~560 entities at the peak would each be extracted and drawn through the entity pipeline every frame (my estimate: tens of microseconds each, so 10-30 ms a frame) plus 560 position updates per tick by the mod. I would not recommend it; no `chunks=` key was added.

**4. Crest.** Anchors at W16 (computed): h0.7 approach 0.00 breach 3.83 closeStart 5.40 apex 6.00 riseEnd 6.30 reentry 8.17 end 21.57; h1.3 approach 0.00 breach 3.82 closeStart 7.34 apex 7.94 riseEnd 8.24 reentry 12.17 end 25.45; h2.0 breach 3.82 closeStart 9.16 apex 9.76 riseEnd 10.06 reentry 15.69 end 29.07 (the approach is 0.00 because the head starts within 3 r0 of the sand). Sound plays: **76** (the partial sheet made 63). Particles at W16: h0.7 43,466 / peak 14,002; h1.3 44,240 / peak 14,002 (thinned 1022); h2.0 44,581 / 14,002; without the eruption 16,236 / 2957 (h0.7) and 16,448 / 1796 (h1.3). W6 h1.3: 10,870 / peak 5590; W48 h1.3: 119,809 / peak 14,069 (thinned 33,873). Crossings: the search walks the axis from the head tip and counts sign changes of the height; tip above the sand -> entry, tip below -> exit first and entry second. With the default 80 segments the body is shorter than the 96-block arc, so at W16 the body never passes both holes at once (a 300-segment body does); a crest lane emits only while its crossing exists. Rows the crest does not have: hold rows dropped; fall pouring moved to the exit; rushing sand starts at the reentry; rise rows end at the apex.

**5. Where `lightCoords` is computed, what it probes, H1-H5.** `EntityRenderer.getPackedLightCoords` (line 51), called from `extractRenderState` (line 284). It probes the single block at the entity's eye position (origin + 0.85 for this `sized(1,1)` entity), i.e. one point at the base level, not along the body. Supported by the reading: **H1** (the likely one): the entity origin is the base level, which the command takes from the player's Y or the sea level; on a slope or dune the ground at the worm's spawn point (up to 30 blocks from the player) can be higher than that, so the eye block is solid and has no light: the whole worm gets 0/0. It fits "in some instances" and "on a slope". **H3** is not excluded (a transient after a chunk load) but would clear itself; **H2** is excluded for a worm in the open (sky light is 15 at night; the shading by the day clock is applied later). **H4 is not supported**: built meshes of the mouth (shell, throat, teeth) at three widths and the body at four instants have no zero, tiny or NaN normals. **H5** (stale state) cannot be excluded from the source. Inferred, to be confirmed by the owner's next sighting.

**6. The `light:` log lines and commands; what to send back.**
- INFO on a change: `[WormsOfArrakis] light: worm <id> <label> mode=<probe|vanilla|full|forced:..> vanilla=b<n>/s<n> used=b<n>/s<n> probe=(x,y,z) <block id> solid=<bool> dim=<id> day=<0..23999> skyDarken=<n> pos=(x,y,z) base=<y> exposed=<rings> refs[hole=b/s entry=b/s top=b/s mid=b/s tip=b/s]`; with `dark verdict=<..>` inserted when it is dark.
- WARN (at most one per worm per 5 s): `[WormsOfArrakis] light: DARK verdict=probe-in-solid|probe-dark-air <same numbers> (N warnings suppressed since the last one)`. Verdict reference-also-dark (night) is logged as INFO only.
- `[WormsOfArrakis] light: FORCED to ...` once a second while `light force` is active; `light dump: ...` lines; `mesh check: <part> (...): N of M quads are degenerate ...` (silent when 0).
- Commands: `light` (current sample and verdict in chat), `light dump` (last 200 samples to the log), `light log on|off|all`, `light force full|auto|<sky> <block>` (clamped with a chat note), all refusals in chat. They read client data: single player only; on a dedicated server they say so.
- Please send: every `light:` and `light dump:` line around a sighting, the `crash light: mode=...` line from the spawn, and say whether `light force full` removes the black. If it does, H1-H3; if not, H4/H5 (then send any `mesh check:` line).

**7. The probe-based light.** Up to 8 points: hole (base+1, beside the body), entry (base+1, other side), top ring, middle ring, head tip and three evenly spaced rings; each ring point is `r0 + 1.5` from the ring centre on the sky side (moved further out if a bend is closer than r0 + 0.5), y at least base + 1; a point inside a solid block is lifted up to 8 blocks. Light = per-channel maximum over the points; with no exposed body only the hole point. Recomputed every 4 ticks, moved toward the target by at most 1 level per tick per channel (the first sample is taken at once). On a flat world in the open vanilla and probe are equal at every instant tested; they differ only when a block-light source lights some ring (the probe takes the maximum, by design) or the origin is in shade or solid.

**8. Test numbers changed.** Three Prompt09Test tests were updated, nothing else: `crestHasOnlyBreachAndReentry` (now `crestHasItsOwnAnchorsSincePrompt10`: riseEnd and fallStart are defined), `noCueForAMoveWithFxOffAndTheDefaultsOfTheMoves` (crest default on) and `theClampsOfTheKeysAreNotedAndBadValuesRefused` (no crest note; the moves text says `fx=on`). The legacy `FxOptions` constructor and `defaults()` mean "no eruption" so the earlier tests run the earlier sheet unchanged; its numbers equal the prompt 09 report (crash W16 9447 / 1859 etc.). Sound plays on a crest 63 -> 76.

**9. What blocks the next prompt.** Nothing in the code. Open: the first sighting of the effects and of the black faces; the real breach location and sand displacement; driving the sheet from real server-side worm events (the sink interface is unchanged); the first real-entity prompt. One decision for the owner: whether the budget should trip at W16 (3 % thinned); raise the vanilla-cap headroom use or lower the eruption rate of the plumes (12,000 grains) if it should not.

## Numbers

- Tests: 181 (153 before + 15 + 13), 0 failures.
- Jar: `IslandCraft-WormsOfArrakis-0.1.0.jar`, 275,956 bytes, contains `META-INF/accesstransformer.cfg`, no PNG, no `sounds.json`.
- Eruption: 12 rows; counts at scale 1 per crash: sand plumes 12,000, fountain 3,500, curtain 4,230, fine haze 3,500, bank 2,600, dust columns 1,267, flying blocks 260, plume blocks about 290, the rest under 600 each.
- Sheet totals (counts above); particles over one crest W16 h1.3: 44,240; sound plays 76 (crest), 82 (others).
- Puff bookkeeping: 10 / 30 / 59 us per tick at 2000 / 6000 / 12000 puffs.
- Mesh check: 72 meshes checked, 0 degenerate quads.

## Surprises, problems, deviations from the prompt

- **Parts 1 and 2 share one commit and one jar** (the crest changes the same player code); part 3 has its own commit. Tags: `fx-4`, `fx-5` on the first, `light-1` on the second. Two jars were deployed (after parts 1+2 and after part 3).
- **Access transformer needed.** `setAlpha`, `quadSize` and `Particle.gravity` are protected in 26.1.2 and there is no public alpha setter. I added `META-INF/accesstransformer.cfg` for the three fields (first attempt with `setAlpha` broke the recompile of the game sources because `SpellParticle` overrides it as protected). This also fixes prompt 09's "gravity cannot be set"; it is applied to the eruption rows only, the prompt 09 rows are unchanged. Building now needs `JAVA_HOME` set to temurin-25 (the default JDK of this machine cannot recompile the game sources after an AT change).
- **Budget trips at W16** (answer 1).
- **A crest body of 80 segments is shorter than the arc**: at W16 h1.3 the body leaves the entry before the head reaches the exit, so entry and exit effects are not at once for that length.
- **Crest hole/entry reference points** are placed beside the body, not on its axis, to satisfy "never inside the body".
- **H4 was a dead end** for the built meshes; the listener still reports it live.
- **No `chunks=display` key**, the experiment says no.
- Smoke limitation as before: a dedicated server with no player loads no worm chunk, so the client features (puffs, light samples) are not exercised there.

## How the owner can see it   (commands, log lines, the "you look and listen" list)

Creative world; `/arrakis_worm_test base here`; stand about 30 blocks away; also try on a slope or a dune.
- `crash 16 120`: the eruption (sand jets leaning out, tall ragged dust columns, flying small sand blocks, the dust bank hiding the crossing, the dust shrinking and fading as it ages) on top of the older effects. Compare `crash 16 120 eruption=off`, `fxcount=0.5`, `fxcount=2`.
- `emerge 16 60`, `engulf 16 60`: the same eruption.
- `crest 16 80 height=1.3`: sounds and particles now on, at the entry and again at the exit; `crest 16 80 height=1.3 fx=off` for none; `crest 48 ...` with enough segments for the body to span both holes.
- `crash 6 60`, `crash 48 300` (not 200): scale, frame rate, `fx: capped` in the log.
- Black faces: `light` while it happens; `light force full` (does the black go?); `light force auto`; `crash 16 120 light=vanilla` against `light=probe` on a slope; `light dump`; `light log all` for tuning.
- Log lines (`logs\latest.log`): `crash fx: on ... eruption=on ...`, `crest fx: on ... apex=...`, `crash light: mode=...`, `fx player started for worm`, `fx cancelled for worm`, `fx: capped ...`, `fx puffs: ...`, `fx error for worm`, `light: ...`, `light: DARK ...`, `light dump: ...`, `light: FORCED ...`, `mesh check: ...`, `fx sheet: eruption ...`.

## Suggested next steps (your view)

- Look first: the size of the dust puffs, the dust bank against the worm, the frame rate at W48 and with `fxcount=2`, and whether the access transformer loaded (no `fx error`).
- After a black sighting send the `light:` lines; if `light force full` clears it and the verdict is probe-in-solid, the probe default is the fix; if the black remains, look at `mesh check:` and the render state next.
- If the 3 % thinning at W16 is not wanted: lower the spiky plumes' rate (12,000 grains) or the budget estimate; the numbers are in `EruptionFx`.
- A longer default crest body (about 300 segments at W16) if the owner wants the entry and exit effects at once.

## Owner observations (human)
