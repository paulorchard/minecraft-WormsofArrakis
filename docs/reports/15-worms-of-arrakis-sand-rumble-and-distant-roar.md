# Report — Prompt 15 Worms of Arrakis: sand rumble and distant roar
Status: partly — all four parts are built and the engine-free rules (cell mapping, schedule, claims, budget, every restore path, roar maths) are covered by 310 passing unit tests, and a dedicated-server smoke start ran the rumble, the tremor rumbles and the roar on a fake player; but no client was involved, so how the sand looks and feels, the lighting, the client side of the packets and the sound itself are unseen.   Commit / tag: `rumble-1` (Part 1), `rumble-2` (Parts 2 and 3), `roar-1` (Part 4); `git log` has the hashes

## What was built

**Part 1 (`rumble-1`).** The block `islandcraft_wormsofarrakis:arrakis_rumble_sand` (display name `Arrakis_Rumble_Sand`), property `level` 1 to 15, a bottom-aligned slab; no item, no loot, no tab, no recipe; `RumbleCell.of(offset)` (the mapping); `RumbleModels` (generator of the 15 models and the single blockstate, run by `./gradlew generateRumbleModels`, output committed); `RumbleGuard` (leaked-block guard); `Prompt15CellTest`.

**Part 2 (`rumble-2`).** `Rumble.play(level, x, z, Params, viewers) -> Handle` (stop, running, seconds, plus notes and columns): `RumbleSchedule` (the pulse schedule, no Minecraft types), `RumbleCore` (events, claims, what each viewer is shown, the budget and every restore path, no Minecraft types), `RumbleClaims`, `RumbleBudget`, `RumbleSettings`, `RumbleManager` (game side: finds the columns, builds the packets, listens to the game's events), `RumbleCommands` (`/arrakis_worm_test rumble ...`). Config keys RumbleMaxSixteenths, RumblePulseSeconds, RumbleEdgeWeight, RumbleViewMargin, RumbleBlockBudgetPerTick, RumbleDropSteps, RumbleStepSeconds, RumbleSwingSeconds.

**Part 3 (`rumble-2`).** The stalk tremor plays the rumble (`HuntAdapter.rumbleFor`); the old whole-block ripple code and its config keys are removed.

**Part 4 (`roar-1`).** `Roar` (engine-free: distances, volume, range, loudness, origin, the sounds taken from the crash sheet), `HuntAdapter.playRoar` (origin, queue of sounds), config RoarDistance.small/medium/large/huge, RoarRangeMargin, RoarFarPitch; the start explosion and the start puff are gone; `hunt preview roar [W|small|medium|large|huge]`.

Test-only additions (operator only): `rumble fake [radius strength seconds]` (a rumble on the fake player of `hunt fake`, used by the smoke start).

## Confirmed

Read from the 26.1.2 sources and the NeoForge jar (class, method and field names used, all compiled against them):
- `ClientboundSectionBlocksUpdatePacket(SectionPos, ShortSet, LevelChunkSection)` reads the states from a section; its fields are private and its codec constructor is private; so a scratch `LevelChunkSection` (public constructor taking `Level.palettedContainerFactory()`) holds the states to send. `SectionPos.sectionRelativePos(BlockPos)` gives the short. Wire format: the section position, a varint count, per change a varlong of `stateId << 12 | position`; at most 4,096 cells per section.
- `SnowLayerBlock` overrides `getShape`, `getBlockSupportShape`, `getVisualShape` and `useShapeForLightOcclusion`; the block overrides `getShape` and `useShapeForLightOcclusion` (true).
- `ChunkAccess.getHeight(type, x, z)` is `heightmap.getFirstAvailable - 1`, the y of the top block (`Level.getHeight` is one more). I got this wrong first and the smoke start found it (see Surprises).
- Sound range: `SoundEvent.getRange(volume) = volume > 1 ? 16 x volume : 16`; `ServerLevel.playSound` sends to the players within it. Client: `SoundEngine` attenuation distance is `max(volume, 1) x 16`, `Channel.linearAttenuation` sets AL linear distance clamped (reference 0, rolloff 1, max = that distance), the volume is limited to 0..1 and the pitch to 0.5..2 (`calculateVolume`, `calculatePitch`).
- NeoForge events used exist with these accessors: `ChunkEvent.Load` / `Unload` (`getChunk`, `getLevel`), `ChunkWatchEvent.Sent` (`getPlayer`, `getPos`, `getLevel`), `LevelEvent.Unload`, `PlayerEvent.PlayerChangedDimensionEvent`, `PlayerLoggedOutEvent`, `ServerStoppingEvent`, `ServerTickEvent.Post`; `DeferredRegister.Blocks.registerBlock(name, factory, props supplier)`.
- The Dunes of Arrakis Dune Sand model is `cube_all` with texture `minecraft:block/sand` (its `dune_sand.json`), so the rumble models reference exactly that (a unit test reads Dunes' file when it is present on this machine).

Tested (unit tests, 310 in all, 0 failures):
- `RumbleCell.of` for every offset from -15 to +15 (kind, level, y), the range clamp, the 15 models and the blockstate regenerated and compared, texture by reference, side strip `[0, 16-level, 16, 16]`, no image copied (`Prompt15CellTest`).
- The schedule (`Prompt15RumbleTest`): same arguments same result and another seed another result; pulses 0.39 to 0.57 s, never past the next period of the column; weight 1 at the centre, 0.25 at the edge; strength scaling; offsets within the limit for max 1, 4, 8, 15; at least 1/16 when strength x weight is above 0.1; no global beat (the share of moving columns stays between a third and two thirds over a whole period); the share of pulsing columns equals S squared; a good share at rest, some up and some down at the same moment.
- The claim rule; the budget (one ratio, none starved to zero, under the budget everything); the core with a fake game: a run ends flat, `stop`, a viewer leaving (restored for them only), a chunk unloading and a chunk sent again, the hard timeout, world unload and server stop paths (`stopAll`), two rumbles on one column, nothing lowered or raised into a viewer standing in the middle, nothing raised into a placed block, over the budget the small event is not starved; after every path `core.empty()` is true and the fake client holds no fake cell.
- The roar (`Prompt15RoarTest`): distance table, the range covers the distance with the margin, the origin lies at the distance on the bearing and the same seed gives the same origin, the sounds are the crash sheet's roar rows (4 roars every 0.32 s, the growl 0.1 s after the first), no explosion string in `HuntAdapter` any more.

Seen in the dedicated-server smoke start (flat sand world, a NeoForge fake player, no client):
- `rumble fake 12 1 4`: 441 columns, peak 245 updates in a tick, 201 columns moving at the most, ended `done` with 187 cells restored, afterwards `0 cells shown now`; two rumbles with `stop`: `2 event(s) stopped, every fake cell restored`, 0 cells shown; the real `ClientboundSectionBlocksUpdatePacket` was built and sent from the scratch section without errors (the fake player has a connection object; the bytes in the log are counted from the codec's format, not read from the wire).
- A small event: the roar line `hunt: roar origin=-38.3,-58.0,-31.0 bearing=309 distance=50 volume=3.59 range=57 size=small W8`, no explosion; three tremor rumbles (S 0.54, 0.74, 0.97; radius 3, max 5/16; share 0.29, 0.55, 0.93; 4, 7 and 13 columns moving), each ended with its cells restored.

## Inferred

- **Everything seen on a client.** How the layers look (top texture, side strips, seams), the light (below), the feel of walking inside the patch, the rubber-banding risk, the sound.
- **The light.** The block is non-opaque with `useShapeForLightOcclusion` as snow layers are, so the client light engine should treat a raise like a snow layer and leave no dark squares; not looked at.
- **The black-faces fix of prompt 10** (see answer 4): read as safe from the code of the listener, not tried with the rumble running.
- **The guard for a leaked real block** (`RumbleGuard`, and the block's own tick that turns it to air) is written from the API and compiled; no test creates a chunk with the block in it.
- **The loudness rule.** Linear attenuation `1 - d / (16 x volume)` follows from the OpenAL settings read in `Channel`, not from listening.
- **Raising under the feet** was not tried (the safer rule first): never raised into a viewer's body.
- Odds of the viewer's pose rules (the 0.25 and 1.0 block heights of "standing on the column") are my choice.

## Answers to the prompt's questions

**1. The block.** Registry name `islandcraft_wormsofarrakis:arrakis_rumble_sand`, display `Arrakis_Rumble_Sand` (`block.islandcraft_wormsofarrakis.arrakis_rumble_sand` in the lang file). Shape: `Block.box(0, 0, 0, 16, level, 16)`, one shape per level, used for collision and outline. Models: `RumbleModels` writes 15 JSON models (`arrakis_rumble_sand_1` to `_15`: one cuboid, parent `minecraft:block/block`, all faces `#all`, `down` and sides culled) and one blockstate file with the 15 `level=n` variants, by `./gradlew generateRumbleModels`; the files are committed and a test regenerates them in memory and compares. Texture: `minecraft:block/sand` by reference, which is what Dune Sand itself uses (read from its model file); no image is copied, and with Dunes absent the same sand shows. Side strips: the sides use `uv [0, 16-level, 16, 16]`, the bottom `level/16` of the texture, not a stretched full side; the top uses the full texture. Light: `useShapeForLightOcclusion` true and `noOcclusion`, as snow layers (not seen on a client). Not part of the game: no `BlockItem` registered, `noLootTable`, pushReaction BLOCK, sand sounds (copied from sand: footsteps use the sand step group), no random ticks, no block entity. A leaked real one: `RumbleGuard` listens to `ChunkEvent.Load` (the spawn chunks at boot fire it too), asks each section whether its palette may hold the block (`LevelChunkSection.maybeHas`, a palette check, not a scan of 4,096 blocks) and only then scans that section, sets every such block to air and logs a WARN with the chunk; the block also schedules a tick on being placed and turns itself to air.

**2. The packet, the chunk resend, the cost.** The packet is the multi-block `ClientboundSectionBlocksUpdatePacket`, one per chunk section per viewer per tick, built from a scratch section (see Confirmed); there is no size problem (at most 4,096 cells and about 20 KB for one section; the protocol cap is far higher), no single-block fallback was needed. Chunk resend: I chose to **resend the cells**: on `ChunkWatchEvent.Sent` the core sends that viewer the cells it believes they have in that chunk (the chunk packet has just replaced them with the real blocks); the other choice (drop them until the next pulse) would leave a column at the wrong height for up to a second. Cost per tick, from the engine-free core with a counting host (a 4 s rumble at strength 1, default numbers, budget 600; the packet objects themselves are not in the time, and bytes are as the vanilla codec writes them; `build/minecraft-junit/build/prompt15-rumble-numbers.txt` is written by the test):

| radius | viewers | peak updates / tick | peak packets / tick | peak bytes / tick | core ms / tick (mean / max) |
|---|---|---|---|---|---|
| 6 | 1 | 67 | 4 | 250 | 0.044 / 0.102 |
| 6 | 5 | 67 | 20 | 1,250 | 0.082 / 0.166 |
| 6 | 20 | 67 | 80 | 5,000 | 0.152 / 0.252 |
| 12 | 1 | 244 | 4 | 786 | 0.109 / 0.254 |
| 12 | 5 | 244 | 20 | 3,930 | 0.217 / 0.382 |
| 12 | 20 | 244 | 80 | 15,720 | 0.624 / 1.774 |
| 40 | 1 | 600 | 32 | 2,404 | 0.987 / 1.490 |
| 40 | 5 | 600 | 160 | 12,020 | 1.545 / 2.958 |
| 40 | 20 | 600 | 640 | 48,080 | 3.644 / 5.846 |

"Updates" are cells (one cell to all viewers counts once). With no budget at all radius 40 wants 5,025 columns and 2,271 cell updates per tick on average (peak 2,517), about four times the default budget of 600, so at radius 40 the default budget thins the rumble (the log says so once); the Hytale figure of 172 at radius 40 is far below that, and I do not know what it counted. The hunt's tremors use radius 3 to 6 (67 at most), so they are never near the budget. At 20 viewers a radius-40 rumble is about 48 KB per tick (about 1 MB/s). **Not measured:** the server time of building the real packet objects and the real network cost; the figures above are the core only.

**3. A player inside the rumble.** Two rules, judged every tick from each viewer's position: a raise is skipped when the cell (the 1 x 1 column up to level/16) would overlap a viewer's bounding box (0.6 x 1.8 blocks), and a column is not lowered while a viewer stands on it (feet from 0.25 below to 1.0 above the surface top and the box overlapping the column); the viewer's own column and the one to four columns their box touches stay real. A listed player who stands still in the middle therefore sees the sand shudder around them in a ring and not under their feet. Raising under the feet was not tried: a raised layer is at most 8/16 = 0.5 block (below the 0.6 step vanilla allows) and the server would not know it, but the safer rule needs no such argument. Rubber-banding risk that remains: a player who walks onto a column that is already lowered for them (the rule only looks at who is on the column at the moment it is lowered) is lowered by up to 0.5 block on their client while the server's block is full height; the server may correct that step. Pulses last at most 0.57 s, so it is one step at most. Not seen on a client.

**4. The black-faces fix.** `LightClient` (prompt 10) only reads: it samples `level.getBrightness(BLOCK/SKY)` at the worm's probe and the reference points and uses `isSolidRender() || isCollisionShapeFullBlock()` for "solid"; it writes nothing, so it cannot fight the fake blocks. A rumble layer is neither opaque nor a full collision block, so it would be read as air, and the engine lights it like a snow layer. The cases that remain unverified, client only: (a) a strike worm's light probe inside a raised layer next to the worm (the verdict would read a thin layer as air, which is right), (b) the client light engine's update after hundreds of block updates in a second (vanilla handles rain of block updates, but I have not looked at dark squares round the patch), (c) the fake blocks sent at the moment a chunk's light is recomputed. All three are for the owner to look at in the gallery and in a strike.

**5. How the tremor maps S and the size factor.** `radius = max(3, TremorRadius x f)` (6 at W48, 4.5 at W32, 3 at W16 and W8: 3 and 1.5 raised to the minimum); the most a column moves `= round(RumbleMaxSixteenths x (0.5 + 0.5 f))` = 5, 6, 7, 8 sixteenths for W8, 16, 32, 48; `seconds = 1.0`; strength S (0.3 to 1) is a constant curve, so a pulse's depth is `S x weight x max x U(0.7, 1.3)`; the share of columns that pulse is `S squared` (0.09 at S 0.3, 0.55 at 0.74, 1 at 1), done by the schedule's `share` function (a column draws a number from the hash and pulses only when it is under S squared). So at low S few columns move and by little; at 1 all move by the full amount; the radius does not depend on S (the old ripple's did). Viewers: the player and those within TremorViewDistance (32).

**6. The roar.** Volume = `distance x 1.15 / 16`, never below 1, for both layers (the crash sheet's 4 and 3.5 are replaced): W8 50 blocks volume 3.59 range 57.5; W16 70 blocks volume 5.03 range 80.5; W32 90 blocks volume 6.47 range 103.5; W48 110 blocks volume 7.91 range 126.5. Origin: the trigger player's position plus the distance on a bearing drawn from the event's seed (0 = north, 90 = east); the bearing is tried first and, if the column there is not sand in a loaded chunk, the seven others 45 degrees apart; the first that is sand is used (y just above its top); if none, the first bearing at the surface of a loaded chunk, or the player's y if it is not loaded (no chunk is loaded or forced). Everyone gets the same sound from that point (the game attenuates and pans). What a player hears, from `loudness = 1 - d / range` (before the Hostile Creatures slider): the trigger player (at the distance) 0.13 for every size (that is what the margin 1.15 gives: faint, audible); small 50 blocks from the origin 0.13, 100 and 200 nothing; medium 0.38, nothing, nothing; large 0.52, 0.03 (the tail), nothing; huge 0.61, 0.21, nothing. A player beyond the range is sent no packet at all. **Pitch:** `RoarFarPitch` is 0.9, so the sheet's 0.5 becomes 0.45 and the game limits the pitch to 0.5 (`SoundEngine.calculatePitch`), so it makes no audible difference; the log says "some below the game's minimum 0.5" and the pitch sent is raised to 0.5. I kept the config value and 0.9 as the prompt said, so you can hear for yourself that it changes nothing; raising the sheet's pitch would be a change to the crash sheet, which the prompt forbids.

**7. Tests changed, old code removed.** `Prompt13Test`: the ripple test is replaced by the test of the new mapping (radius, maximum, share), two assertions in `strengthBuildUpAndSizeScaling` follow the new radius rule (radius = TremorRadius x f, at least 3, and no longer scaled by S), and the printed numbers say sixteenths. `Prompt13TextTest`: the lang file now has two more keys than the hunt texts (the death message and the rumble block name), and the config-key list names the new keys (RumbleBlockBudgetPerTick replaces TremorBlockBudgetPerTick). The preview text and its lang entry say "sand moves up to n/16 per column" instead of layers. The old whole-block tremor code was removed: `Tremor.Ripple`, `Tremor.FakeBlocks`, `HuntAdapter.Ripple` with its step, cancel and counters, and the config keys TremorRippleRadius, TremorRippleLayers, TremorRippleTicks and TremorBlockBudgetPerTick (only the tremor used them; the strike code does not). An old config file that still has those keys is harmless (NeoForge ignores and rewrites it).

**8. What blocks the camera prompt and the later mobs, totems and the placed-block disturbance.** Nothing blocks them. `Rumble.play` is the call to make; the camera plugs in where it did (`TremorFx.camera`, `FreezeHook`, the phase hooks). Things the next prompts should know: the viewers must be in the same level as the call; radius is limited to 64 and seconds to 600 (the call's `notes()` say when it clamped); the budget is shared by the whole server; two rumbles on a column share it by the claim rule (the newer draws); the schedule takes a `share` function and a strength curve of your own; a feature that wants a fixed shape for a while can use the gallery path (`playFixed`, test-only for now, not in the public API).

## Numbers

- Schedule defaults (all config): pulse period 1.0 s (at least 0.6; the code takes the larger of the config and the length of the longest pulse, 0.57 s), 2 descent steps of 0.06 s (about half the depth, then all), swings +0.6 d, -0.4 d, +0.25 d, -0.12 d, then 0, each 0.09 s +-25 %, a pulse 0.39 to 0.57 s; depth `strength x weight x max x U(0.7, 1.3)`, at least 1/16 when strength x weight is above 0.1; weight 1 at the centre, 0.25 at the edge; RumbleMaxSixteenths 8; RumbleViewMargin 32; budget 600 cells per tick; hard timeout seconds + 3; each column has its own period phase (see Surprises).
- Tremor: radius 3 / 3 / 4.5 / 6, max 5 / 6 / 7 / 8 sixteenths for W8 / 16 / 32 / 48; share S squared; 1.0 s.
- Roar: distances 50 / 70 / 90 / 110, volumes 3.59 / 5.03 / 6.47 / 7.91, ranges 57.5 / 80.5 / 103.5 / 126.5 blocks, loudness at the trigger player 0.13; 5 sounds over 0.96 s.
- Cost: see the table in answer 2. Smoke: `rumble fake 12 1 4` 441 columns, peak 245 updates, 15,868 updates and 56,640 bytes over the 4 s.
- Tests: 310 in all (280 before; 3 in `Prompt15CellTest`, 21 in `Prompt15RumbleTest`, 6 in `Prompt15RoarTest`, the replaced ripple test).

## Surprises, problems, deviations from the prompt

- **The schedule as written beats like a heartbeat.** If every column's periods start at time zero, a pulse (0.46 to 0.57 s) that starts in the first 43 % of the period leaves nobody moving in the last tenth of every second and nearly everyone moving at 0.45 s: a global rhythm, not "a thousand columns on a thousand rhythms". My own tests found it (the viewer-in-the-middle test saw an empty patch exactly on a period boundary). Fix: every column has its own phase (a hash of seed and column), its periods are counted from it, and a pulse that would start before the event does is not played. Still a pure function of (seed, column, time); the pulse never overlaps the column's next one. A test now checks the share of moving columns stays between a third and two thirds over a whole period.
- **`ChunkAccess.getHeight` is the top block, not the first free block above it** (`Level.getHeight` is one more). My first column finder was one block too low and found no columns; the smoke start showed "0 columns" and I fixed it (the roar origin had the same slip, y -59 instead of -58). No unit test could have caught it (it is game code); the smoke start did.
- **The far-pitch drop is inaudible** (answer 6).
- **Both roar layers use the computed volume**, so the low layer is no longer a little quieter than the roars as on the sheet (3.5 vs 4); the sheet's own rows are untouched.
- **The gallery is a row of 31 columns, not 16** (offsets -15 to +15 are 31 values); columns that are not sand with air above are skipped and the chat says how many were drawn.
- **At radius 40 the default budget thins the rumble** (answer 2); the Hytale figure of 172 updates does not match what this schedule wants at that radius.
- **Raise cells are not sent into a placed block**, checked each time a column changes (not each tick); a block placed in the cell during a pulse can be overdrawn for under a second. (A placed block on a column at the start excludes the column.)
- **Skip rules are per event, not per viewer:** if any viewer of the event stands on a column, it is not lowered for all viewers of that event.
- The test-only `rumble fake` command exists for the smoke start; remove or keep.
- No client was started and no screenshot taken, as asked.

## How the owner can see it

Deployed jar: `IslandCraft-WormsOfArrakis-0.1.0.jar` in `ArrakisTest01\mods` (the older one removed; GeckoMesh not there).

Log lines: `rumble: block islandcraft_wormsofarrakis:arrakis_rumble_sand registered`; `rumble: event N started at ... radius ... max .../16 ... columns ... viewer(s)`; `rumble: event N ended (done|stopped|timeout|server stopping|world unloaded): ... cells restored ... peak ... updates in a tick ... bytes`; `rumble: over the budget (...)`; `rumble: chunk ... sent again to ...`; `rumble: ... logged off ...`; `hunt: tremor rumble S=... radius ... max ... share of columns ...`; `hunt: roar origin=x,y,z bearing=deg distance=n volume=v range=r size=name Wn` and `hunt: roar 5 sounds over ... s, pitch ...`.

Commands (operator): `/arrakis_worm_test rumble [radius=12] [strength=1] [seconds=4] [self|nearby]`, `rumble ramp [radius] [seconds=6]`, `rumble stop`, `rumble status`, `rumble gallery`, `hunt preview tremor [strength] [W]`, `hunt preview roar [W|small|medium|large|huge]` (default huge).

You look and listen (open Dune sand, the test instance):
1. `rumble gallery`: a row of 31 columns, offsets -15 (left) to +15 (right) sixteenths, held 20 s: the sand texture on top and sides, the side strips match the heights, no dark squares, no seams, nothing left afterwards.
2. `rumble 12 1 4`: the sand shudders on a thousand rhythms in small steps; no block left raised or lowered afterwards; stand in the middle: you are not pushed; walk over the patch and say if you feel a correction.
3. `rumble ramp 12 6`, then `rumble 12 0.3` and `rumble 12 1`: gentle to violent; the two fixed strengths differ in height and in how many columns move.
4. A second player near (`rumble 12 1 4 nearby`) sees it too; a player far away (over radius + 32) does not.
5. Walk away and back (chunk unload and resend) during `rumble 40 1 20`; the log lines say what was resent.
6. A real event: tremors every 3 to 6 s under each listed player; small for a small worm (radius 3, up to 5/16), bigger for a huge one (radius 6, up to 8/16), few columns at the start of the stalk and all of them at the end; the sound and particles as before.
7. At the start of an event no explosion: the roar from far away from a fixed direction; stand 150 blocks away and it is faint or silent while the tremors still come; `hunt preview roar small|medium|large|huge` for each size (does the far roar feel far, and loud enough at the distance? RoarRangeMargin is the knob).
8. Light: look for dark squares round a rumbling patch, at dusk and at night too, and around a strike worm.

## Suggested next steps (your view)

- Look at the gallery first: it settles the models, the texture strips and the light in one look.
- If the far roar is too faint at the trigger player (0.13 at the default margin), raise RoarRangeMargin (2.0 gives 0.5); the range grows with it.
- If a corrected step shows up when walking on the patch, lower the rule's tolerance or skip lowering within one block of a viewer's column too.
- Decide whether the tremor should also pulse harder toward the end beyond S (the height is already S times the depth).
- The strike's long hold (flagged in report 14) is unchanged.
- Remove or keep the test-only `hunt fake` and `rumble fake` commands.

## Owner observations (human)

