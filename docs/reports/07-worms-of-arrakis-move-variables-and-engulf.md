# Report — Prompt 07 Worms of Arrakis: move variables and engulf
Status: done (both parts built, 105 unit tests pass, one dedicated-server smoke start, jar deployed; nothing seen on screen because no client was run)   Commit / tag: vars-1 and engulf-1 on one commit (see Deviations)

## What was built
- **Part 1 (vars-1).** One helper, `test/MoveOptions.java` (pure code), parses and validates the keyed options of crest, emerge, crash and engulf. Its table of keys (name, type, default, range, meaning) is the only list of variables: the parser, the usage text of every refusal and the new `/arrakis_worm_test moves` command are generated from it. Common keys: `speed`, `pause`, `loop`, `mouth`, `force`. Per move: crest `height radius radius_down`; emerge `n | height, angle, hold, retreat_speed`; crash `height lean hold gravity min_radius`; engulf `exposed lean hold gravity`.
- The moves got the machinery behind the keys: `Move.pause` / `setPause` (the loop period changes by exactly the difference), `retreatSpeed` on emerge (new overload, the old signature delegates with retreat = speed), `playTime()`, and named phases (`phases()`, `phaseAt(tau)`).
- `loop=off`: the client holds the move at its end (under the sand); the server (`TestWormEntity.tick`) removes the worm 0.5 s after the end of its single run and logs it.
- The client logs `[WormsOfArrakis] <move> phase <name> t=...` on every phase change.
- **Part 2 (engulf-1).** `Moves.engulf`: rise as emerge at angle 90 − lean (default 83), hold, then a straight free-fall drop of the head tip height (h = h0 − g t²/2, path position back by (h0 − h) / sin(angle)), then pause. No hook, no pose family. Command `engulf <width> <segments> [exposed=2] [lean=7] [hold=0.5] [speed=12] [gravity=12] [pause=1.5] [loop] [mouth] [force]`.
- `freeze hold` / `freeze <t>` / `freeze off` work for engulf (0 = hold pose, 1 = under the sand). Frame lines carry `exposed= tipHeight= phase=`.
- Older commands untouched: chain, curve, wave, ring, segment, scale, scales, mouth, stats, crasharc, crashproj, freeze, stop, speed, surface, base, stagger, offset, jitter.

## Confirmed   (tested, or read from the code or the jar; say which)
- Tested (unit tests, 105 in all: the 84 earlier ones unchanged, plus 21 in `Prompt07Test`): keys in any order give the same spec; the old positional forms give spec strings identical to what the old code built (`emerge 16 60 10 60`, `crest 16 60 0.5 12`, `crash 16 120`); `emerge height=` and `n=` agree (and the head tip at the hold is within the rounding of n); both given is refused; `loop=off` ends once and the worm stays under the sand; pause changes the period by exactly the difference (all four moves); speed 6 / 12 / 24 changes the rise inversely (all four moves); the moves text contains every key and default of every move.
- Tested (engulf, W 3 / 16 / 48, exposed 1 / 2 / 3): exposed count at the hold; tip height at the hold = (exposed + HEAD_LENGTH) x sin(83°) within 0.05 (it is exact to 1e-9 in practice); free fall at five times (1e-6); strictly falling head and never-increasing exposed count; nothing above the sand at the end and the tunnel straight behind the head; same ring positions up and down for 41 path positions; bend limit for every visible joint over the whole move (largest visible joint angle 0.0000 of the limit: the tunnel is straight); no visible ring dropped by the underground rule (W 3 and 16); phases in order with the right durations; minimum segment refusal with the number needed.
- Read from the sources jar (`minecraft-patched-26.1.2.129-sources.jar`): `Entity.tick()` (public void), `Entity.level()`, `Entity.tickCount`, `Entity.discard()`. This is the only Minecraft class touched; everything else is pure maths and command wiring.
- Dedicated-server smoke start: `moves` printed four lines; every command of the "you look" list parsed and logged; `exposed=4`, `n=3 height=1`, `foo=1`, `engulf 16 3`, `mouth=wide` were refused with a chat message; `speed=100` and `pause=-1` were clamped with a message; `crashproj 16 260`, `crasharc 16 150 25`, `crest 16 60 0.5 12`, `emerge 16 60 10 60` still run as before.

## Inferred    (not verified)
- Nothing was drawn: no client was run. The smoke worms were in unloaded chunks (no player on the dedicated server: `addFreshEntity ... chunkLoaded=false`), so `freeze`, `speed`, `clear` found 0 worms there and the server-side `loop=off` removal was not exercised on the server. The removal logic is unit tested through `MoveFactory.finished`, not through a ticking entity.
- That the client holds the worm at its end for the 0.5 s before the server removal, and that the phase-change lines appear once per change (they depend on the renderer, not run here).
- `Entity.tick()` on the server uses the server's `tickCount`; the client's lags a few ticks, which the 0.5 s margin is meant to cover.

## Answers to the prompt's questions
**1. Table of variables** (exactly what `moves` prints; bare numbers keep their old meaning by position):
```
crest: height=0.4 (above 0 to 3, force lifts the 3) radius=6 (limit 2 and up, force lifts the limit) radius_down==radius (limit 2 and up, force lifts the limit) speed=12 (0.5..64) pause=0 (0..600) loop=on (on|off) mouth=closed (open|closed) force (lifts the limits that say force); bare numbers in order: height, speed
emerge: n=10 (1..1000) height=- (above 0) angle=90 (5..90) hold=2 (0..120) speed=12 (0.5..64) retreat_speed==speed (0.5..64) pause=1 (0..600) loop=on (on|off) mouth=open (open|closed) force (lifts the limits that say force); bare numbers in order: n, angle, speed, hold
crash: height=2.5 (2..6, force lifts) lean=7 (0..45) hold=1.5 (0..120) speed=12 (0.5..64) gravity=12 (0.5..80) min_radius=2 (limit 2 and up, force lifts the limit) pause=2 (0..600) loop=on (on|off) mouth=open (open|closed) force (lifts the limits that say force); bare numbers in order: height
engulf: exposed=2 (1..3, force up to 6) lean=7 (0..45) hold=0.5 (0..120) speed=12 (0.5..64) gravity=12 (0.5..80) pause=1.5 (0..600) loop=on (on|off) mouth=open (open|closed) force (lifts the limits that say force); bare numbers in order: exposed
```
(`radius_down==radius` means "default: the same as radius"; `retreat_speed==speed` likewise.) The crest usage string said `height=0.4` and that really is the code's default (`Moves.DEFAULT_CREST_HEIGHT`, unchanged); what was missing was the range, now "above 0 to 3, force lifts the 3". Old positional forms: emerge is no longer a typed-argument command but takes the same free text, with bare numbers read in the old order (n, angle, speed, hold) and the words `open`, `closed`, `force`; `emerge 16 60 10 60 12 2 open` gives exactly the old spec string. Crest keeps `crest_fraction=` as an alias of `height` and the second bare number as speed. Old specs are unchanged strings when pause, loop and retreat are at their defaults; otherwise the spec grows by p17 (freeze), p18 pause, p19 loop, p20 retreat.

**2. Parsing and validation.** One helper (`MoveOptions.parse`), used by all four commands; a new variable is one line in its table. Refused (chat message): unknown key or word, a key given twice, a value that is not a number, `mouth` not open/closed, `loop` not on/off, `n` and `height` together, `n` or `exposed` not a whole number, `height` too low for one ring, `radius` / `radius_down` of 0 or less, emerge angle outside 5..90 when height is given. Clamped into the range with a chat note: `speed` and `retreat_speed` 0.5..64, `pause` 0..600, `hold` 0..120, `gravity` 0.5..80. The limits that say force stay in `Moves` and come back as "<move> refused: ..." (crest height above 3, crash height 2..6, lean 0..45, `min_radius` below 2 is clamped with a note, engulf exposed 1..3 / force up to 6 (7 is refused even with force), too few segments with the number needed).

**3. Engulf numbers at the defaults** (lean 7, speed 12, gravity 12, hold 0.5; tip height in blocks; "tip above sand" is how long the tip is above the sand):

| W | exposed | tip height at hold | rise | drop (to HEAD_LENGTH + 1 under) | tip above sand | segments needed |
|---|---|---|---|---|---|---|
| 3 | 1 / 2 / 3 | 2.95 / 3.94 / 4.93 | 1.96 / 2.05 / 2.13 s | 0.99 / 1.07 / 1.15 s | 0.70 / 0.81 / 0.91 s | 3 / 4 / 5 |
| 16 | 1 / 2 / 3 | 6.73 / 7.72 / 8.71 | 4.93 / 5.01 / 5.10 s | 1.50 / 1.55 / 1.61 s | 1.06 / 1.13 / 1.21 s | 3 / 4 / 5 |
| 48 | 1 / 2 / 3 | 16.20 / 17.20 / 18.19 | 12.78 / 12.87 / 12.95 s | 2.33 / 2.36 / 2.40 s | 1.64 / 1.69 / 1.74 s | 3 / 4 / 5 |

The prompt expected "about a second" for W16 and 2 rings: the drop to the prompt's end rule (tip at −(HEAD_LENGTH + 1)) is 1.55 s; the part with the tip above the sand is 1.13 s.

**4. Phases per move** (default W16 values; `Move.phases()` / `Move.phaseAt(tau)` in `path/Moves.java` give name, time inside it and length; the client log line is `[WormsOfArrakis] <move> phase <name> t=...`):
- crest: rise (to the apex), fall, pause (0 by default). `crest 16 80 height=1.3 speed=6`: rise 17.21 s, fall 33.16 s.
- emerge: rise, hold, retreat, pause. `emerge 16 60`: rise 5.83 s, hold 2 s, retreat 5.83 s, pause 1 s (the retreat has its own speed).
- crash: rise, hold, fall, pause. `crash 16 120`: 7.72 s, 1.5 s, 2.79 s, 2 s.
- engulf: rise, hold, drop, pause. `engulf 16 60`: 5.01 s, 0.5 s, 1.55 s, 1.5 s.
- crashproj and crasharc: run, pause. A phase of length 0 is listed but never reported. The mouth rework can read `Move.phaseAt(MoveFactory.tau(spec, age))` in `TestWormRenderer.submitMove` (already called there for the log line) and attach the flap animation to the phase name.

**5. Build time of the exposed part during the engulf drop** (best of 4, 2 exposed, normalised drop time 0 / 0.25 / 0.5 / 0.75 / 1): W16 0.56 / 0.47 / 0.26 / 0.20 / 0.05 ms, quads 1850 / 1600 / 850 / 600 / 0; W48 1.34 / 1.15 / 0.28 / 0.27 / 0.10 ms, quads 7050 / 5550 / 1800 / 1800 / 0. Quads never increase during the drop. The skin cache key already carries the path position, so every drop frame rebuilds.

**6. Engulf maths that looks wrong or that I would change.**
- The straight drop does not hit the sand-crossing joint badly: the tunnel is straight, so every visible joint is 0.0000 of the limit throughout. The joint at the crossing is straight.
- The rise takes 5 s at W16 and 12.9 s at W48 because the head starts at the far end of the underground tunnel (10 blocks of lead, the 1-width arc, then the straight stretch, all at 12 blocks/s) and only the last second or two is above the sand. That is emerge's behaviour and I kept it ("as emerge"); a shorter lead for engulf would make it look more like a lunge. Suggest `speed=30` or a shorter approach.
- The drop is deliberately 0.4 s longer than what is visible, to the prompt's end rule; the loop therefore has about 0.4 s of nothing before the pause starts.
- The head falls straight down the line it rose on: it does not tip forward and has no gravity-drag, so it looks like an elevator. Fine for 1 to 3 rings.
- To keep the straight stretch long enough for the whole drop, engulf's tunnel is dug deeper than emerge's (`depth` is the larger of emerge's and the one for HEAD_LENGTH + 2 + 2 r0 below the crossing). At W48 that adds a few blocks of invisible rise.

**7. Blockers for the next prompt (mouth open/close animation, sand displacement and particles, breach location).** None. Notes: the phase API is ready but the flaps are still a static angle (`mouth=open|closed`); `finished()` / removal is a server-side check, so a breach-location prompt that moves worms around will need its own spawn path; the server smoke cannot tick worms without a player in range.

## Numbers
- Tests: 105 (84 earlier + 21 new), 0 failures.
- Engulf and build-time tables above. Largest visible joint angle during the engulf: 0.0000 of the limit.
- Smoke start log: loop of `engulf 16 60`: 8.57 s (rise 5.01, hold 0.50, drop 1.55, pause 1.50). `engulf 16 60 exposed=3 speed=30 gravity=20 hold=1`: rise 2.04 s, drop 1.24 s. `engulf 48 120`: rise 12.87 s, drop 2.36 s.
- `emerge 16 60 height=1 speed=20 retreat_speed=6`: n = 10 (formula 10.22), head tip at the hold 0.99 body widths, rise 3.50 s, retreat 11.68 s. Crest, crash and emerge at their defaults give the same periods as before (tested).
- Jar: `IslandCraft-WormsOfArrakis-0.1.0.jar` (150,544 bytes, no PNG inside; GeckoMesh stays `.disabled`).

## Surprises, problems, deviations from the prompt
- **One build for both parts.** The prompt asks for a jar and a tag after each part. Part 1 and Part 2 share the table, `Moves`, `MoveFactory` and the command code, so they were built, tested and deployed together and tagged `vars-1` and `engulf-1` on the same commit (as in prompt 04). The jar the owner has contains both.
- Drop duration 1.55 s against the "about a second" in the prompt, see answer 6.
- The dedicated server could not tick the smoke worms (unloaded chunks), so `freeze`, `speed` and the `loop=off` removal are covered by unit tests only.
- A lone word in the first bare slot is read as that slot's number (`engulf 16 60 wat` says "not a number: wat"); "unknown word" is for a second stray word.
- `emerge` segments argument now accepts 2..1000 (was 2..400), like the others.
- Crest `height=` default is 0.4 in the code, as the old usage string said; the owner's 1.3 is a reference, not the default (unchanged).

## How the owner can see it   (commands, log lines, the "you look" list)
Creative world; `/arrakis_worm_test base here` on non-sea-level ground; stand back about 30 blocks and to the side.
```
/arrakis_worm_test moves
/arrakis_worm_test engulf 16 60
/arrakis_worm_test engulf 16 60 exposed=1
/arrakis_worm_test engulf 16 60 exposed=3
/arrakis_worm_test engulf 16 60 speed=30 gravity=20 hold=1
/arrakis_worm_test engulf 48 120 exposed=2
/arrakis_worm_test engulf 16 60 loop=off
/arrakis_worm_test freeze hold
/arrakis_worm_test freeze 0.5
/arrakis_worm_test freeze off
/arrakis_worm_test crest 16 80 height=1.3 radius=6 speed=6
/arrakis_worm_test emerge 16 60 height=1 speed=20 retreat_speed=6
/arrakis_worm_test crash 16 120 speed=20 pause=4
/arrakis_worm_test engulf 16 60 exposed=4
/arrakis_worm_test emerge 16 60 n=3 height=1
```
The last two must each print a refusal in chat. Log lines (`logs\latest.log`): `path engulf: ...` (tip height, rise, drop, segmentsNeeded), `<move> phases: ...`, `engulf frame t=... exposed= tipHeight= phase=`, `engulf phase <name> t=...` on every phase change (client), `loop=off: ... worm removed` (server), `freeze engulf frame ...`, `refused: ...`.

## Suggested next steps (your view)
- Mouth open/close on the phase changes (rise opens, hold full, drop closes), reading `phaseAt`.
- A shorter approach for engulf (or a default speed of 20 to 30) so the visible lunge starts sooner.
- A way to spawn worms near the player on the dedicated server (a loaded chunk) so the freeze, speed and removal paths can be smoke-tested without a client.

## Owner observations (human)
