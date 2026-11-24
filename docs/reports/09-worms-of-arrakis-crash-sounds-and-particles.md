# Report — Prompt 09 Worms of Arrakis: crash sounds and particles

Status: partly — the sheet, the anchors, the sounds and the particles are built, unit-tested (153 tests, 0 failures) and smoke-started on a dedicated server; nothing was seen or heard in a client (hard rule), so how it looks and sounds is unverified.   Commit / tag: tags `fx-1`, `fx-2`, `fx-3` (one commit, "Prompt 09: crash sounds and particles ...")

## What was built

**Part 1 — the sheet and its anchors (fx-1).** New package `fx` (pure code, no Minecraft classes):
- `fx/CrashFx.java`: the owner's sheet as data and nothing else: 17 sound rows (`CUES`, the Repeat column adds up to 82), 14 particle rows (`EMITTERS`), the enums `Anchor`, `Group` (tremor, breach, stream, roar, mouth, slide, fall), `PType`, `Place`, and the numbers of the scaling and the budget. No sound id or particle number is typed anywhere else.
- `fx/FxAnchors.java`: the seven anchors computed from the move (head tip height scanned every 5 ms, linear interpolation): approach (tip above -3 r0), breach (tip above 0), closeStart (the MouthAnim start), riseEnd, fallStart, reentry, end (playTime), plus the model z of the hole (tip at the breach) and of the reentry. Emerge and engulf use `fallStart = advanceTime + hold` (the start of the retreat or of the drop) and the first instant after it with the tip at or below 0. On a crest riseEnd and fallStart are not defined (NaN): rows that need them play nothing; reentry is searched after the highest point of the tip.
- `fx/FxOptions.java`: fx, fxsound, fxparticles, fxvol (0..2), fxcount (0..3); the default count scale `min(r0/8, 2.0) x fxcount`.
- `fx/FxSink.java`: the interface behind which the sheet is played (sound, particle, log) in the worm's model frame.
- `fx/FxPlayer.java`: the player of one worm: expands the schedule, takes the script time once per game tick, and tells the sink what happens. Seeded random (reproducible), budget thinning, cancel, runtime switches (sound, particles, solo group), statistics.
- New keys on crest, emerge, crash and engulf (generated into the `moves` text as before): `fx=on|off` (default on for crash, emerge, engulf; off for crest; `fx=on` on a crest is accepted and says in chat that only the rows anchored at approach, breach and reentry play), `fxsound`, `fxparticles`, `fxvol`, `fxcount` (a clamp or a bad value goes to the player in chat). The spec got p26..p30; a spec with the defaults keeps its older, shorter length. `crasharc` and `crashproj` have no fx keys and no effects.
- `/arrakis_worm_test fx` (anchors of the first moving worm, the options, one chat line per sound group and per particle row, the registry check; the log has every row with its time), `fx solo <group|off>`, `fx particles on|off`, `fx sound on|off`. `freeze` stops the effects because the effects run on the script clock.

**Part 2 — sounds (fx-2).** `test/client/FxClient.java` plays each cue with `ClientLevel.playLocalSound(x, y, z, SoundEvent, SoundSource.HOSTILE, volume, pitch, false)`. Every cue is placed at the hole (the model z of the tip at the breach; the three Re-entry cues at the tip at the reentry), so a sound does not wander. Volume = vol x fxvol, pitch = pitch x (1 +- jit random, seeded per cue and repeat); the engine applies the sound entry's own volume and pitch.

**Part 3 — particles (fx-3).** The same `FxClient` makes the particles through `Minecraft.particleEngine.createParticle(...)` and then sets each particle's velocity, lifetime, scale and tint (see answer 6 for why not `ClientLevel.addParticle`). The four placements (crossing, overhead, body, mouth) are in `FxPlayer`; the particle types are `block` (sand), `dust_plume`, `cloud`, `explosion`.

**Where the player runs.** On the client, in a `ClientTickEvent.Post` handler (20 Hz), once per worm, from the clock the renderer already uses: `MoveFactory.tau(spec, entity.tickCount)`. The server only knows the spec (as before). See answer 2.

## Confirmed   (tested, or read from the code or the jar; say which)

Read from the 26.1.2 patched sources jar (`minecraft-patched-26.1.2.129-sources.jar`):
- All 14 sound events exist as `SoundEvents` constants and all four particle types exist in `ParticleTypes` (`BLOCK`, `DUST_PLUME`, `CLOUD`, `EXPLOSION`). Checked three ways: by the text of `SoundEvents.java` in a unit test, by a registry lookup at the command (`[WormsOfArrakis] fx registry check: 14/14 sound events and 4/4 particle types of the sheet found`, in the dedicated-server smoke), and by the client sink at play time (a missing event is logged once and skipped).
- Sound rules (answer 3): `SoundEngine` line 378 `attenuationDistance = max(volume, 1) x sound.getAttenuationDistance()`; `SoundEventRegistrationSerializer` reads `attenuation_distance` with the default 16; `Channel.linearAttenuation` sets the linear-clamped distance model with reference distance 0 and rolloff 1; `calculateVolume` clamps the volume to 0..1 before the category slider; `calculatePitch` clamps the pitch to 0.5..2; `AbstractSoundInstance.getVolume/getPitch` multiply the instance's value by the sound entry's value; `SoundEvent.getRange(volume) = volume > 1 ? 16 x volume : 16` (the server's broadcast range); `ClientLevel.playLocalSound(double x3, SoundEvent, SoundSource, float, float, boolean distanceDelay)`.
- Particle engine: one `ParticleGroup` (render type `SINGLE_QUADS`) holds all quad particles of the client in an `EvictingQueue` of 16384 (`ParticleGroup.MAX_PARTICLES`). `ParticleEngine.createParticle` is public and returns the `Particle`; `Particle.setParticleSpeed`, `setLifetime`, `scale(float)` and `SingleQuadParticle.setColor` are public; `Particle.gravity` is protected. `ClientLevel.addParticle` drops a particle farther than 32 blocks from the camera (unless the type overrides the limiter: explosion and poof do) and honours the Particles option (Minimal, Decreased).
- Quad sizes (answer 6): the render state draws a quad as +-quadSize, so the full width is 2 x quadSize.
- Registry names: `Registry.getValue(Identifier)` with `Identifier.withDefaultNamespace`.
- Tested (Prompt09Test, 28 tests; the whole suite is 153): anchors in order for crash at W6, 16, 48; the W16 values within 0.1 s of the previs list; closeStart equals the MouthAnim start; emerge and engulf have fallStart and reentry (the tip is above the base level 0.02 s before the reentry and below 0.02 s after); crest has only breach and reentry; the cue expansion; seeded determinism; 82 plays; monotone times per row; a cancel at seven instants drops exactly the later plays; fxvol=0, fxsound=off, fx=off play nothing; fxcount=0, fxparticles=off make nothing; per row the particle count within 5 percent of rate x duration x scale (or the burst count); the crossing rows are on the ring of radius ring x r0 and make nothing before the breach; out < 0 points inward and out > 0 outward for more than 95 percent of the particles; the overhead grains are in the disc, none after the breach, and more as the head comes up; the mouth row is within ring x r0 x 0.5 of the tip and normal to the head axis; the body row never under the sand; the budget thins every row and keeps every row and logs `fx: capped` once; a frozen clock plays nothing and a jump does not replay; a new loop starts the sheet again; the text of the `fx` command formats for the four moves.
- Dedicated-server smoke start (one, about 100 s): all the new keys and their refusals and clamps reach the command source; `fx solo`, `fx particles`, `fx sound` and their refusals; the `fx:` line at every spawn with the anchors, the scale and the 82 plays; the registry check; the old commands unchanged.

## Inferred    (not verified)

- How it looks and sounds: no client was started. The first run of `FxClient` (particles created and shaped through the engine, sounds through `playLocalSound`) is the owner's.
- That the previs's size column means the full width of a quad in blocks. I took it so because a block grain of 0.15 matches vanilla's 0.10 to 0.20 and the explosion's 4.5 matches vanilla's 4.0. If the previs meant a half width, every puff is twice as big as intended.
- That the previs slowed every particle by 0.98 per tick (the default friction). The drift factor of cloud and dust_plume (answer 6) is built on that.
- That `Particle.scale(f)` on a dust_plume or cloud particle (which have no physics) is harmless; for the block grains it also changes the collision box slightly (f is at most 1 there).
- That ClientLevel's `playLocalSound` with `distanceDelay=false` plays at once (read from the code, not heard).
- The effective pitch of a cue also depends on the sound entry's own pitch in `sounds.json` (not read here).
- That the instance's other mods (Sodium, Iris, Entity Culling, AmbientSounds, Distant Horizons are in the mods folder) do not change how these particles draw or sound.

## Answers to the prompt's questions

**1. The anchors for crash, emerge and engulf at W6, 16, 48 (seconds), and where the sheet lives.**
The sheet is `fx/CrashFx.java`; the anchors are computed by `fx/FxAnchors.java` (never typed). W48 needs 300 segments (see the surprises). Crest W16 height=1.3 for comparison.

| move W | approach | breach | closeStart | riseEnd | fallStart | reentry | end |
|---|---|---|---|---|---|---|---|
| crash 6 | 1.36 | 2.06 | 2.42 | 3.32 | 4.82 | 6.40 | 6.64 |
| crash 16 | 2.17 | 4.10 | 6.56 | 7.46 | 8.96 | 11.54 | 11.84 |
| crash 48 | 4.82 | 10.63 | 19.80 | 20.70 | 22.20 | 26.68 | 27.13 |
| emerge 6 | 1.42 | 2.12 | 2.38 | 3.28 | 5.28 | 6.43 | 8.55 |
| emerge 16 | 2.33 | 4.26 | 4.94 | 5.84 | 7.84 | 9.42 | 13.68 |
| emerge 48 | 5.30 | 11.12 | 13.12 | 14.02 | 16.02 | 18.93 | 30.05 |
| engulf 6 | 1.54 | 2.26 | 1.85 | 2.75 | 3.25 | 4.24 | 4.59 |
| engulf 16 | 2.17 | 4.10 | 4.11 | 5.01 | 5.51 | 6.86 | 7.38 |
| engulf 48 | 4.82 | 10.63 | 11.97 | 12.87 | 13.37 | 15.48 | 16.33 |
| crest 16 (h 1.3) | 0.00 | 3.82 | 7.34 | n/a | n/a | 12.17 | 25.45 |

Crash W16 against the previs list: 2.17 / 4.10 / 6.56 / 7.46 / 8.96 / 11.54 / 11.84 against 2.20 / 4.10 / 6.56 / 7.46 / 8.96 / 11.57 / 11.84: within 0.03 s. For emerge and engulf, fallStart is the start of the retreat or of the drop; reentry the moment the tip goes below the base level on the way down. On a crest the tip is already within 3 r0 of the sand at t=0 (approach 0), riseEnd and fallStart are undefined. At engulf W6 and W16 closeStart is before or at the breach (the rise is short), so the mouth starts to close about when the head comes out.

**2. Where the effects run, what the sink looks like, and the server-side mapping.**
- Client. The renderer already runs the worm's clock on the client (`MoveFactory.tau(spec, tickCount)`), so the player of the sheet is on the client, in `FxClient` (a `ClientTickEvent.Post` handler, 20 Hz; one `FxPlayer` per worm; the player is dropped, and its pending cues and emission cancelled, when the worm is gone or its move changes).
- The sink: `fx/FxSink`: `sound(Cue cue, x, y, z, volume, pitch)` and `particle(Spawn)` (a record with the type, position, velocity in blocks per tick, lifetime in ticks, full width, the sheet's gravity factor, tint and row) and `log(String)`; positions and velocities are in the worm's model frame (x lateral, y above the base level, z the direction of the move). The pure `FxPlayer` says what happens; a sink applies the origin and the heading of the worm and calls the game.
- How the rows map to server calls: each sound cue play is `level.playSound(null, x, y, z, soundEvent, SoundSource.HOSTILE, volume, pitch)` (the server broadcasts it to players within `SoundEvent.getRange(volume)`, so volume above 1 reaches farther there too). A burst row is `ServerLevel.sendParticles(options, x, y, z, count, dx, dy, dz, speed)`; with `count > 0` the three deltas are a random spread of the position and `speed` is the velocity, with `count = 0` one particle per call with the velocity (dx, dy, dz) x speed (one packet each; too many for the rate rows). What a server-side version cannot do with those vanilla calls: set a particle's lifetime, size, tint or exact velocity per particle (they go through the vanilla provider); for that it needs the client to shape them (a small payload, or the client player driven by the server's anchors). The sheet, the anchors and the schedule need no change.

**3. The real volume, range and pitch rules for sound events in 26.1.2.**
- The engine's gain is `clamp(volume x entry volume, 0, 1) x the category slider x master`: a volume above 1 is not louder.
- The range: `attenuationDistance = max(volume x entry volume, 1) x attenuation_distance` of the sound entry (16 when the entry does not say), with a linear fall-off from full gain at 0 blocks to 0 at that distance (`linearAttenuation`: linear distance model, reference distance 0, rolloff 1). So volume above 1 extends the range linearly: range = 16 x volume blocks, exactly what the previs assumed (for entries with the default 16). The sheet's volumes give: 4.6 -> 74 blocks, 4.5 -> 72, 4 -> 64, 3.5 -> 56, 3.2 -> 51, 3 -> 48, 2.8 -> 45, 2.4 -> 38, 2.3 -> 37, 2 -> 32, 1.4 -> 22, 1.2 -> 19, 0.9 and below -> 16 (and quieter than full gain at every distance); `fxvol=2` doubles them.
- Pitch: `calculatePitch` clamps `pitch x entry pitch` to 0.5..2. So vanilla does clamp, as the previs did. The tremor gravel row is stored at 0.25 (+-12 percent jitter: 0.22 to 0.28) and is heard at 0.5 (before the entry's own pitch). Several other rows sit at or just above 0.5 with jitter, so the low side of their jitter is also clamped to 0.5: Rise sand streaming and bone creak (0.5 +-20 percent), Roar and the low layer (0.5 +-3 percent), Mouth thud (0.5), Re-entry collapse (0.5 +-15 percent), and the low tail of Breach sand spray (0.55 +-15 percent), Re-entry gravel and Fall rushing sand.

**4. The SoundSource.** `SoundSource.HOSTILE` ("Hostile Creatures" slider). The worm will be a hostile creature, and the events the sheet borrows (ravager roar, warden, ender dragon growl, iron golem) are hostile or neutral creature sounds; NEUTRAL would put the worm with the passive animals. The sand and bone block sounds are played as the worm's own, so one slider (plus Master) governs the whole effect.

**5. Names or classes that differed from the prompt.**
- `SoundEvents.GENERIC_EXPLODE` (`entity.generic.explode`) is a `Holder.Reference<SoundEvent>` (`registerForHolder`), not a plain `SoundEvent` like the others; the client sink looks every event up in the sound-event registry by its id, so this makes no difference.
- Explosion: I used `ParticleTypes.EXPLOSION` (the single puff, with its size parameter), not `EXPLOSION_EMITTER` (which seeds 6 puffs for 8 ticks within 4 blocks of its centre and could not be placed or sized by the sheet).
- Everything else exists under the names of the prompt: `ParticleTypes.BLOCK` (with `BlockParticleOption` of `Blocks.SAND`), `DUST_PLUME`, `CLOUD`; the sound ids of the table; the `poof` and `large_smoke` types exist and are in the type switch (`CrashFx.PType`), not used by the sheet.

**6. Size honesty: per particle type.** Real quad size read from the 26.1.2 sources (full width, mean and range in blocks), whether it can be set, and what I did.

| type | real quad width | settable by the options | what I did | what the owner will see (inferred) |
|---|---|---|---|---|
| block (sand grain, `TerrainParticle`) | 0.10 to 0.20 (mean 0.15) | no | the sheet's 0.15 needs no scaling; the tremor grains (0.10) are scaled by 0.67 with `Particle.scale` | grains as vanilla block breaking |
| dust_plume (`DustPlumeParticle`) | 0.15 to 0.30 (mean 0.225); the provider's scale is fixed at 1.0 | no | `Particle.scale(sheet size / 0.225)`: 4.9 x for 1.1, 5.3 x for 1.2, 13.3 x for the 3.0 skirt | the previs's guess (1.1) was 5 times the vanilla size; the sheet's sizes are reached by scaling, so the puffs are as wide as the sheet says, made from the vanilla sprite |
| cloud (`PlayerCloudParticle`) | 0.375 to 0.75 (mean 0.56); fixed | no | `Particle.scale`: 1.6 x for 0.9, 1.8 x for 1.0, 6.4 x for 3.6 | the previs's guess (1.0) was 1.8 times the vanilla size |
| explosion (`HugeExplosionParticle`) | 4 x (1 - size/2), so 4.0 at size 0 | yes: the size parameter (xAux) | size parameter -0.25 for 4.5 blocks; lifetime set to 16 ticks (vanilla 6 to 9) | big puffs as the sheet says |

`Particle.scale(float)` is public on every quad particle and works on the particle the engine returns, so size is settable for every type of the sheet even where the options cannot set it; I did not need to change a type. Three more differences between vanilla particles and the previs, found in the sources:
- **Velocity of block grains:** `ClientLevel.addParticle` would ignore the sheet's velocity for block grains (the `Particle` constructor replaces it by a random direction of 0.06 to 0.18 blocks per tick). I take the particle the engine returns and call `setParticleSpeed`.
- **Friction:** a dust_plume slows by 0.96 x 0.92 per tick (it stops within about 8 ticks), a cloud by 0.96; the previs (inferred) by 0.98. With the sheet's velocities a vanilla dust plume would drift about one fifth of the previs's distance. `fx/ParticleModel.driftFactor` multiplies the velocity of cloud and dust_plume by the ratio of the two travel sums over the particle's life (cloud 1.2 to 1.3, dust_plume 5.4 to 7.4); block grains and the explosion keep their velocity.
- **Gravity:** `Particle.gravity` is protected, so the sheet's gravity column cannot be set. Block grains (1.0) are exactly vanilla's. The puffs' small negative gravity (-0.02 to -0.03, a slight upward drift) is not applied: a cloud has gravity 0, a dust_plume falls slightly (gravity 0.5 decaying by 0.88 per tick) and is not compensated beyond the drift factor. An access transformer for `Particle.gravity` would fix it if the owner sees the dust sink.

**7. The base-mask rows: changes, peak live particles, spawn rate, the vanilla cap.**
- No change to counts, ring or types: the sizes of the previs are reached by scaling (answer 6), so the rows are as the owner tuned them. Nothing was seen on screen, so "does it hide the edge" is the owner's to say; the first knobs are `fxcount` (up to 3), then the three rows' counts and ring in `CrashFx.EMITTERS`.
- Count scale: `r0 / 8`, capped at 2.0 (default), times `fxcount`: W6 0.36, W16 0.995 (printed 1.00), W48 2.00 (it would be 2.98 uncapped). I added the cap because W48 at 3.0 is 5600 live particles with a physics step each; the cap is `CrashFx.MAX_AUTO_SCALE`.
- Estimated peak particles alive (from the sheet's own lifetimes) and spawn rate, whole crash:

| crash | scale | spawned | peak alive | busiest second | average per second (breach to end) | base-mask rows per second |
|---|---|---|---|---|---|---|
| W6 | 0.36 | 2125 | 687 | 596 | 464 | 352 |
| W16 | 1.00 | 9447 | 1859 | 1604 | 1220 | 975 |
| W48 | 2.00 | 39063 | 3727 | 3270 | 2367 | 1960 |
| W48, scale 3.0 uncapped | 3.00 | 58588 | 5595 | | | |

Emerge and engulf are the same within 5 percent in peak (emerge W16 1860, engulf W16 1926). Per row at crash W16: sand skirt 5395, dust skirt 1310, low cloud 847, sand fountain 498, rise pouring 408, re-entry burst 298, fall pouring 282, tremor grains 126, hold sliding 104, dust ring 69, re-entry dust 44, rise dust 37, mouth breath 25, explosion 4.
- The vanilla cap: 16384 quad particles per client in one group (`ParticleGroup.MAX_PARTICLES`, an `EvictingQueue`, oldest dropped first, shared with all the world's particles). The sheet keeps itself under `CrashFx.BUDGET = 12000` (an estimate plus the real count read from `ParticleEngine.countParticles()` each tick): when the wanted spawns of a tick would pass it, every row is thinned by the same ratio (a row never drops to zero) and `[WormsOfArrakis] fx: capped N -> M particles ...` is logged once per worm. In none of the runs above does it trip (peak 5595 at the highest). `fxcount=3` at W48 is 4.0 (about 7400 alive): under the budget.

**8. Cancellation.**
- `stop`, `clear`, the `loop=off` ending (the server removes the worm) and a changed move: the next client tick finds the worm gone, or its move key changed, and calls `FxPlayer.cancel()`: every cue that has not started is dropped and emission stops at once; a cue that already started plays out, particles in flight die naturally. Logged: `fx cancelled for worm N ...` with the totals.
- `freeze <t|hold>`: the clock is frozen, so nothing is scheduled or emitted while it is held; the jump to the frozen time does not play what was skipped (a jump of the clock backwards or by more than a second is a seek); `freeze off` goes on from there. Sounds already playing finish.
- A new loop: the sheet starts again from the top; cues of the old loop that had not started are dropped (the longest, the sand streaming, ends 2 s before the end at W16, so none is cut in practice).
- A worm met in the middle of its run (a join or a chunk coming in) does not replay the past.
- `fx sound off` / `fx particles off` skip what has not started; `on` plays the next ones.

**9. What blocks the next prompt.**
- Nothing blocks it in the code. Before the breach location and sand displacement: the hole is the model z of the head tip at the breach (computed from the scripted move); the real breach location, and the sand that is displaced, will need the worm's position in the world to feed the same anchors. The sink interface is where a server-side version plugs in (answer 2).
- Driving the sheet from real server-side worm events: the anchors come from the scripted clock; a real worm needs the same seven instants from its own state (the player takes the loop time and a geometry, both pure).
- The effects are not seen or heard yet; any tuning (counts, the 2.0 cap, the drift factors, the missing gravity) waits for the owner.
- Engulf and crest: engulf plays the whole sheet (its mouth closes about at the breach at W6 and W16, see answer 1); crest is off by default and with `fx=on` plays only the rows anchored at approach, breach and reentry and the base mask (63 sound plays at W16).
- One thing for the owner: `crash 48 200` (in the prompt's look list) is refused, "the worm is too short for this fall: it needs at least 292 segments"; the longer head of prompt 08 raised that. Use `crash 48 300`.

## Numbers

- Tests: 153 in total (125 before this prompt + 28 new, none of the older ones changed); 0 failures. The suite numbers (anchors, per-row counts, budgets, drift factors) were written to `build/prompt09-numbers.txt` by the test `numbersForTheReport`.
- The sheet: 17 sound rows, 82 plays at every width (11 tremor, 10 breach, 24 stream, 5 roar, 4 mouth, 6 slide, 22 fall); 14 particle rows (4 types).
- Jar: `IslandCraft-WormsOfArrakis-0.1.0.jar`, 217,127 bytes, no PNG, no `sounds.json`.
- Particles over one crash (spawned, estimate of the peak alive): W6 2125 / 687, W16 9447 / 1859, W48 39063 / 3727 (scale 2.0).
- Sound plays per second of the busiest second at W16: the breach (boom, smash, 7 sprays, earth, roar start) is the densest moment.
- Frame cost of the effects: not measured (no client).

## Surprises, problems, deviations from the prompt

- **One build, one jar, one commit for the three parts** (as in prompts 07 and 08); the tags `fx-1`, `fx-2`, `fx-3` sit on the same commit. The jar after Part 1 or Part 2 alone was never deployed.
- **The previs file cannot be read**; the tables of the prompt were the spec. The previs's size and friction numbers are guesses of vanilla (as the prompt says) and several were wrong (answer 6).
- **`ClientLevel.addParticle` is not used**: it drops particles beyond 32 blocks and the owner watches from 30 to 90, and it cannot shape a particle. The engine is called directly; the player's Particles option is honoured by the sink itself (Minimal: none except the types that override the limiter; Decreased: a third fewer).
- **`playLocalSound` with `distanceDelay=false`**: vanilla would delay a sound by distance / 40 seconds; the sheet's times are the times heard.
- **The crossing after the reentry.** When the tip goes below the sand, the whole worm is already under the sand (the head is 9 blocks long and the body follows it down), so the axis has no crossing. The crossing rows after that (the re-entry burst and dust, the end of the base mask) use the last crossing, the hole the worm went into.
- **The count scale is capped at 2.0** (`MAX_AUTO_SCALE`): my addition, the prompt asked for a number.
- **The runtime switches of `fx` (sound, particles, solo)** are static flags read by the client in the same game, like `TestFlags`; they do not reach a client of a dedicated server.
- **`MoveFactory.get` cache key**: it was the whole spec with some fields zeroed; it is now the path part of the spec (p0..p20 with the defaults filled in), so adding the effect keys does not duplicate a cached move. A test (`theFxKeysDoNotChangeThePathAndKeepTheOlderSpecsUnchanged`) shows the fx and mouth keys give the same cached move. No path or timing changed (the older tests pass unchanged).
- **The dedicated-server smoke start has no player, so a worm is not in a loaded chunk**: `fx` (with a worm), `freeze` and `stop` only showed their "no moving worm" messages there; the text of `fx` is unit-tested and its first run with a worm is the owner's.
- **Gravity** of the puffs cannot be set (answer 6).
- Not run on screen or in a client: everything about the look and the sound.

## How the owner can see it   (commands, log lines, the "you look and listen" list)

Creative world; `/arrakis_worm_test base here` on non-sea-level ground; stand about 30 blocks away; `/arrakis_worm_test clear` between tests. Make sure the in-game sliders Master and Hostile Creatures are up and Particles is on All.

- `/arrakis_worm_test crash 16 120`: the rumble builds from about 2 s before the breach (gravel grumbles and a deep heartbeat, grains hopping on the sand over the head), then the boom, the ground smash and the sand fountain, the worm rising out of a dust cloud (the dust skirt and the low cloud; the worm itself should stay visible), the roar and the bone creaks while it rises, the mouth clamping with bone cracks and a thud, the hold with sand sliding off the body, the fall pouring sand into the hole, the final collapse and gravel.
- Move away: at 30, 60 and 90 blocks which layers are still heard (the ranges in answer 3: the tremor gravel and the boom reach about 70 and 64 blocks, the sand slide only 16).
- `crash 6 60`, `crash 48 300` (not 200): the scale of the dust and the sand mask; the frame rate at W48.
- `emerge 16 60`, `engulf 16 60`: the same sheet on the other two moves. `crest 16 80 height=1.3 fx=on`: the partial sheet.
- `fx` (anchors, options, the sheet), `fx solo breach`, `fx solo roar`, `fx solo off`, `fx sound off`, `fx sound on`, `fx particles off`, `fx particles on`, `crash 16 120 fxcount=2 fxvol=0.5`.
- `freeze hold` and `freeze off`: the effects pause and resume.
- Refusals and clamps to try: `crash 16 120 fx=maybe`, `fx solo foo`, `fx particles maybe`, `crash 16 120 fxvol=5 fxcount=9` (two clamp notes), `crash 48 200` (refused: needs 292 segments).
- Log lines (`logs\latest.log`):
  - `[WormsOfArrakis] crash fx: on sound=on particles=on fxvol=1 fxcount=1 count scale=1.00 (r0=7.96, min(r0/8, 2.0)) sound plays=82 anchors: approach=2.17 breach=4.10 ...` (at the command; `crash fx: off` when off)
  - `[WormsOfArrakis] fx registry check: 14/14 sound events and 4/4 particle types of the sheet found`
  - `[WormsOfArrakis] fx player started for worm N: ...` (client, once per worm)
  - `[WormsOfArrakis] fx cancelled for worm N (removed or fx off): X sounds played, Y particles made, peak alive Z, thinned T in C capped ticks` (client)
  - `[WormsOfArrakis] fx: capped A -> B particles ...` (client, only when the budget is hit)
  - `[WormsOfArrakis] fx: sound event ... is not in the registry` or `fx error for worm` (client, a problem)
  - `[WormsOfArrakis] fx sheet: ...` (the `fx` command: every row with its time)
- What to report back: what is heard and at which distance; whether the rumble builds as intended; whether the base mask hides the crossing and the worm is still visible; whether any puff is too big, too small, falls into the sand, or flickers; the frame rate at W48 and at `fxcount=3`; any crash on the first spawn; whether Sodium or Iris change how the particles draw.

## Suggested next steps (your view)

- Look and listen before anything is built on it; the first things to check are the scale of the dust puffs (answer 6: they are the vanilla sprite scaled 5 to 13 times), the mask against the worm, and the first run of `FxClient`.
- If the dust sinks into the sand or does not drift enough, add an access transformer for `Particle.gravity` (a small change) and set the previs's gravity per particle; the drift factor can then be tuned or dropped.
- If the sand skirt (about 5400 grains per crash at W16, 700 per second) is heavy, lower its rate first: it is 70 percent of the particles.
- A server-driven version: the client already takes everything from the anchors and a geometry; a payload carrying the anchors (or the breach time and position) and the move would let the real worm drive the same player.
- Crest and the two older crashes get no effects by default; the sheet would need rows of their own (a crest has no hold, the old crashes no hole).

## Owner observations (human)
