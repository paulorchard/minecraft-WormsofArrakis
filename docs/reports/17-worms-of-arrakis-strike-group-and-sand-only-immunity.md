# Report — Prompt 17 Worms of Arrakis: strike group and sand-only immunity

Status: partly (all code, 372 unit tests and the jar are done; the group, the release at the choice and the chunk tickets ran in one server smoke start, but fake players cannot be touched by the worm, so touch, swallow-by-touch, kill and every client-side effect are untested in a real game)   Commit / tag: see the last commit on master; tags fix-1, fix-2, fix-3, fix-4, immune-1, group-1, group-2 all sit on that one commit (see Deviations)

## What was built

**Part 0 (fixes from the owner's prompt 16 test)**
- 0.1 `RumbleFollow` (engine-free) and the adapter's per-member `AttackRun`: the attack rumble is re-centred on the player every `AttackRumbleFollowSeconds` (0.25) while they slow, with a 0.25 s overlap so the old rumble is stopped only after the new one runs; once held it is pinned to the held block and never moves again.
- 0.2 Delayed death: `SwallowRules` (engine-free state table HELD / SWALLOWED / KILLED / RELEASED and the end paths) and `GroupRunner.swallow`. A touch makes the player invisible (re-asserted every tick), held, invulnerable and unable to interact (`SwallowGuard` cancels damage, break, place, clicks, entity interaction, attack, toss, pickup and mob targeting; chat and commands work). The kill comes at the end of the strike, or at `SwallowMaxSeconds` (default run time + 3 s). Stop, fizzle, server stop and nokill release alive; log-off and dimension change kill at once; nothing is saved across login. The victim's backup (DevourSeconds + ThroatBackupSeconds) now means "swallowed at that time". Mobs are still killed on touch.
- 0.3 The camera is now a strike camera: it starts when the player is held, for the victim and every held member, orbiting that player's own held spot with the turn spread over the whole move (`DeathCamTurnDegrees`, 0 = locked). Released players get their view back; killed players keep the camera behind the death screen. Log prefix `camera:` and the `DeathCam*` key names are kept and **now mean the strike camera**. `camera preview` and `nokill` still work.
- 0.4 Diagnostics: server StopTracking log, stall log, discard reason and age (`TestWormEntity.onRemoval`, server and client). Protection: `ChunkTickets` keeps the worm's and footprint chunks loaded and entity-ticking during a strike (`StrikeChunkTicketMax` 400, nearest first, only already loaded chunks, released on every end path).
- 0.5 Underground parts do not count: `WormHazard.firstContact` clips the box to above the move's sand level (`Box.above`). `VictimParts` (default `throat`) and `OthersParts` (default `body,head,throat`) pick the swallowing parts; `HazardParts` stays the global switch; mobs are killed by any visible part.

**Part 1 (immune-1)** `EntitySand` + `HazardRules.decide(..., playersImmuneOffSand, mobsImmuneOffSand)`; keys `HazardPlayersImmuneOffSand` and `HazardMobsImmuneOffSand` (both default true). Applies to the victim too. Log `hazard: <name> is not on sand, spared` (once per worm and entity).

**Part 2 (group-1)** `StrikeArea`: 16 radii derived from the eruption rows, margin `StrikeAreaMargin`, toml override `StrikeAreaRadius.<move>.<size>`, start-up log lines, and `/arrakis_worm_test hunt preview area [size] [move|max]` (`AreaPreview`).

**Part 3 (group-1)** `StrikeGroup.select` (engine-free) and `GroupRunner.start`: the group is built once at the freeze start.

**Parts 4 and 5 (group-2)** every member gets the same freeze, rumble and camera, keyed by UUID; members outside the chosen circle are released at the choice (`GroupReleaseOutside`); `hunt status` lists the group; `trigger ... nokill` catches whoever is in the circle and kills nobody; config keys are logged at start-up. Added a test-only `hunt fake named <name> <x> <y> <z>` command so several fake players can stand in for a group.

New files: `StrikeArea`, `StrikeGroup`, `SwallowRules`, `RumbleFollow`, `EntitySand`, `GroupRunner`, `SwallowGuard`, `ChunkTickets`, `AreaPreview`, `Prompt17Test`.

## Confirmed
Read from the 26.1.2 and NeoForge 26.1.2 sources/jars:
- `ServerChunkCache.addTicketWithRadius` / `removeTicketWithRadius`; ticket level = 33 − radius (radius 2 = level 31, entity ticking); `TicketType.DRAGON` has no timeout, loading + simulation flags, not persisted.
- `ServerLevel.isPositionEntityTicking`, `Entity.onRemoval(RemovalReason)`.
- NeoForge events: `LivingIncomingDamageEvent`, `BreakBlockEvent` (in `event.level.block`; there is no `BlockEvent.BreakEvent` in this version), `BlockEvent.EntityPlaceEvent`, `PlayerInteractEvent.*`, `AttackEntityEvent`, `ItemTossEvent`, `ItemEntityPickupEvent.Pre`, `LivingChangeTargetEvent`, `PlayerEvent.StopTracking`, `PlayerChangedDimensionEvent`.
- Seen in the smoke start (dedicated server): start-up strike-area lines; the `group:` line with the skipped reason; release at the choice; the strike cameras of two players; chunk tickets taken (8 chunks, level 31) and released; the event ends DONE with nothing left over; the toml override `StrikeAreaRadius.crash.small = 6.0` took effect (circle radius 6.00).

## Inferred (not verified in a client)
- Why the worm vanished in the owner's prompt 16 test: probably the dead player no longer kept the chunks ticked and tracked. Not proven; the new logs and tickets are there to prove or fix it.
- Invisible armour rendering, camera smoothness and yaw wrap on a real client, and log-off death (killed in the logout handler).
- Fake players are not in the level's entity list, so the worm never touched them (see Surprises); everything that follows a touch (swallow by touch, hidden, kill) ran only in unit tests.

## Answers to the prompt's questions

1. **Sand immunity in prompt 16?** No, prompt 16 had none. "On sand" is the SandBlocks rule with SandTolerance (`EntitySand.onSand`): on the ground, the block under the feet must be sand (a placed block on the sand means not on sand, so a player on a placed block is spared); in the air, the first non-air block within 1.5 blocks below must be sand (sand 1.4 blocks down counts, rock below or 2 blocks of air does not). Players and mobs use the same function (`HuntAdapter.onSandNow`).
2. **The 16 radii:** largest RING/DISC `ring` in `EruptionFx.ROWS` is 2.4 (the "fine grain haze" disc) × r0 (+ `StrikeAreaMargin`, default 0). The four moves share the same r0 per size, so the radius is the same for every move of a size (a drift test checks this against the rows):

   | size | r0 | crest | crash | emerge | engulf | maximum |
   |---|---|---|---|---|---|---|
   | small | 3.99 | 9.57 | 9.57 | 9.57 | 9.57 | 9.57 |
   | medium | 7.96 | 19.11 | 19.11 | 19.11 | 19.11 | 19.11 |
   | large | 15.92 | 38.20 | 38.20 | 38.20 | 38.20 | 38.20 |
   | huge | 23.87 | 57.30 | 57.30 | 57.30 | 57.30 | 57.30 |

3. **Selection:** at the freeze start, the victim (always, even alone or off sand) plus every other player within the maximum radius, same dimension, alive, Survival or Adventure, on Dune Sand, and not held by another event. No cap (a 101-candidate test selects all). Never extended: a player who walks in later is not frozen. In the smoke start (victim WormFake, Near 4 blocks away, Mid 7 blocks, Far 40 blocks, Rock on a stone pillar 3 blocks away): `group: event 1 size=small radius=9.57 members=[WormFake, Near, Mid] skipped=[Rock: not on Dune Sand]`; Far was beyond the radius and is not mentioned.
4. **Per-member freezes and rumbles:** the freeze is a map keyed by player UUID and the attribute modifiers (`freeze_speed`, `freeze_jump`) live on each player's own attribute instance, so the same id on two players does not clash; each member has an `AttackRun` with its own `Rumble.Handle` and its own seed; rumbles may overlap and share the prompt 15 claim rule (a column is restored only when nobody claims it) and one per-tick cell budget. Measured in a unit test (rumble core only, radius 6, 4 blocks apart, budget 600 cells per tick, fake sender): 1 member 0.04 ms per tick, 4 members 0.20 ms, 16 members 1.9 ms, 100 members 68.6 ms (peak 560 cell updates in a tick, 16.6 MB of packets in 6 s). The budget thins every rumble by one ratio, so the cell updates stay under 600; it does **not** thin the per-tick scheduling cost (that is what grows at 100) nor the bytes sent. The cost of the freeze and camera per member was not measured separately.
5. **Release at the choice:** when the strike is chosen the circle of that move is known; members outside it are released at once (freeze, rumble and invisibility off, `GroupReleaseOutside` default true), the victim never. In the smoke start Mid (7.00 blocks away, circle 6.00) was released at the choice and its status showed "released". An outside member simply regains movement while the worm erupts; they get no camera. They are not grouped again.
6. **Several cameras:** one invisible camera entity per held member (two in the smoke: entities 33/34, then 36/37), each orbiting that player's own spot, each cleaned up on its own path (restored for Near at the strike end, for WormFake when a new camera started, when the server stopped). The orbit radius is clamped to the player's loaded area (24 became 16 with view distance 2). Each player sees only their own camera; nobody sees the others'. The entity count is one per held member; with 100 members that is 100 extra entities for the strike. The packets went to fake players, so a real client view was not seen.
7. **Tests changed:** none of the existing 348 tests had to be changed. New: `Prompt17Test` with 24 tests (radii drift, selection, release rule, swallow table, lifecycle, rumble follow, sand reading, immunity switches, clipping and parts, strike cameras, chunk tickets, cost, world-placed touch). Total 372, 0 failures.
8. **Blocking later work:** placed-block disturbance (a block placed on sand makes a player count as off sand, and the rumble and eruption do not know about placed blocks); mobs and totems (the swallowed damage type bypasses totems and absorption, but mobs are only killed on touch and not frozen); rideable worms (the hazard has no collision and no rider logic); camera shake (the strike camera is a fixed orbit, no shake). None blocks anything now.
9. **Why the worm vanished:** unknown for certain. Likely the victim's death and camera left no player keeping the chunks ticked. Now there are StopTracking, stall, discard-reason and age logs, and a chunk ticket for the worm and footprint chunks (8 chunks at level 31 for a small worm; cost: those chunks are simulated for the strike, about 8 to 400 chunks, nothing is generated).
10. **Swallowed state:** damage is cancelled for every source (`LivingIncomingDamageEvent`) except the final kill; break, place, item use, attack, entity interaction, drop, pickup and mob targeting are cancelled; chat and commands work. What gets through: nothing that changes their body; the server still ticks them, a player can open the inventory screen and the game menu, and disconnect (which kills them at once).
11. **Strike camera timing:** starts one tick after the held start (the STRIKE phase), turns over the whole move; a released player gets `ClientboundSetCameraPacket(player)`; a killed player keeps the camera behind the death screen until Respawn, the timeout or another end path.
12. **Touch times** (a standing player at the entry crossing, seconds after the move started; unit test; devour table in brackets):

   | move | size | body | head | throat (table) |
   |---|---|---|---|---|
   | crest | small | 2.46 | never | 2.78 (2.92) |
   | crest | medium | 3.80 | never | 4.44 (4.58) |
   | crest | large | 6.46 | never | 7.78 (7.91) |
   | crest | huge | 9.14 | never | 11.12 (11.26) |
   | crash | small | 2.44 | 7.40 | 2.78 (4.14) |
   | crash | medium | 4.10 | 11.50 | 4.74 (7.45) |
   | crash | large | 7.46 | 19.20 | 8.66 (14.08) |
   | crash | huge | 10.84 | 26.66 | 12.60 (20.70) |
   | emerge | small | 2.54 | 6.86 | 2.86 (3.79) |
   | emerge | medium | 4.32 | 9.22 | 4.90 (5.84) |
   | emerge | large | 7.88 | 13.94 | 9.00 (9.93) |
   | emerge | huge | 11.46 | 18.66 | 13.08 (14.02) |
   | engulf | small | 2.58 | 4.56 | 2.90 (3.17) |
   | engulf | medium | 4.10 | 6.70 | 4.74 (5.01) |
   | engulf | large | 7.46 | 11.08 | 8.66 (8.94) |
   | engulf | huge | 10.84 | 15.36 | 12.60 (12.86) |

   The throat touches before the table time in every move; for crest, emerge and engulf only 0.1 to 1 s earlier, for crash 1.4 to 8 s earlier (the crash table time is when the mouth is wide open, the throat funnel reaches a standing player much earlier). The body touches first, so with the default `VictimParts=throat` a victim is not swallowed by the body. The "head never" for crest means the head never reaches the entry crossing. These are first contacts in the engine-free hazard, not measured in game.

## Numbers
- Tests: 372, 0 failures (24 new).
- Rumble core cost per tick: 0.04 / 0.20 / 1.9 / 68.6 ms for 1 / 4 / 16 / 100 members (see answer 4).
- Move build at start: 16 moves in 553 ms (smoke start).
- Smoke start: 3 members selected, 1 skipped, 1 released at the choice, 8 chunk tickets taken and released, camera started for 2 players.

## Surprises problems deviations
- **Fake players cannot be touched by the worm.** They are not in the level's entity list, so `level.getEntities` never returns them: in the smoke start nobody was touched, the victim was swallowed by the backup at 5.65 s (the 1.5 s backup after the table time 4.14 s), the kill was refused ("could not be killed ... released") and the others were released at the strike end. So the touch path, swallowing of members, the kill of members, hiding and sand-only immunity were not run in the server; they are covered by unit tests only. A real client test is needed.
- The second smoke run was meant to be a `nokill` run but ran as a normal one (the fake trigger branch does not seem to take the `nokill` word); nokill is covered by the unit tests and the real trigger command only.
- Fake players' camera packets go to a fake connection, so the camera was seen only in the log.
- Tags: the work was done as one working tree, so all seven tags (fix-1 to fix-4, immune-1, group-1, group-2) point at one combined commit, not at separate ones.
- A clean-up of the old `DeathCamManager.startForDeath` and the kill-listener hook was done; the `camera preview` command still uses the same orbit code.
- Not done: separate measurement of the freeze and camera cost per member; a real-client test.

## How the owner can see it
Commands (`/arrakis_worm_test ...`):
- `hunt preview area [small|medium|large|huge] [crest|crash|emerge|engulf|max]` draws the ring for 10 s.
- `hunt trigger self <size> strike <type> [nokill]` catches everyone in the circle.
- `hunt status` lists the group (name, on sand, in circle, frozen / swallowed / released / dead).
- `hunt fake named <name> <x> <y> <z>` (test only) adds a named fake player.

Log lines to look for: `strike area: ...` (start-up), `group: event N size=... members=[...] skipped=[...]`, `group: <name> is outside the circle ... released`, `group: event N chose <move> circle ...`, `swallowed: <name> by <part> at t=...`, `hazard: <name> is not on sand, spared`, `camera: start for ...`, `tick: event N keeps ... chunk(s)`, `tracking: worm N no longer tracked by ...`, `tick: worm N stalled ...`, `loop=off: ... finished its single run`, `group: event N ended DONE ... killed, ... released alive`.

You look and listen:
- `hunt preview area`: a ring of particles round you at the right size.
- Trigger with a friend near you on sand, another far on sand, and a third on rock: the near friend freezes and dies as you do, the far one is released at the choice, the one on rock is never frozen and never dies.
- The dying friend's own camera orbits their own spot.
- A nokill run releases everyone with their normal view and body.
- The rumbles of two players overlap without leaving holes.
- A cow or zombie on sand dies; on rock it does not.
- Walking into the area after the freeze starts: you are not frozen.
- Part 0 checks: (a) the rumble sits on the block you are frozen on; (b) the view goes out to the orbiting camera at the end of the 4 s while alive; (c) you are hidden when the mouth closes; (d) you die when the worm is gone and then the death screen comes; (e) the log shows `loop=off: ... finished its single run` near the move's run time and the event ends DONE, and the client never logs "fx cancelled" early; (f) the nokill run releases you with your normal view and body.

## Suggested next steps
- Owner test in the CurseForge instance with at least two real players; look at the `tracking:` and `stall` lines if the worm vanishes again.
- If the real throat touch feels too early for crash, set `VictimParts` or raise the throat reach, or use the time offset.
- Measure the freeze and camera cost per member with real players.
- Make the fake players real entities in the level (add them to the level) so the touch path can be smoke-tested.

## Owner observations (human)

