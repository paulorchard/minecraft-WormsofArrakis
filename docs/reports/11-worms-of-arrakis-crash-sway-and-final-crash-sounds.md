# Report — Prompt 11 Worms of Arrakis: the body sway and the final crash sound sheet
Status: partly — both parts built, 204 unit tests pass (0 failures), one dedicated-server smoke start ran clean; nothing was seen or heard in a client (hard rule), so how the sway looks and how the new crash sounds sound are unverified.   Commit / tag: `sway-1` on b82eb5a (Part 1), `fx-6` on the Part 2 commit (see `git log`)

## What was built
**Part 1 — the body sway (sway-1).**
- `path/Sway.java` (new, pure): `Params` (the nine keys, defaults 0.04 / 0.04 / 14 / 21 / 0.6 / 0.45 / 3.0 / 0.25), `env`, `lr`, `fb`, `offsets(joints, r0, tPhase, tEnv, tBreach, params)`. The formulas are exactly those of the prompt (FB phase +1.3). LR is the model x axis; FB is the normal of the tangent inside the vertical plane, (fy, fz) -> (fz, -fy), +y when flat. The tangent comes from the neighbouring joints. No randomness, no ripple of the radius.
- `WormSkin.build(..., offsets)`: the offsets are added to the chain centres (and the tail chain), so the skin, the liner, the tail and the head (placed from `chain.center[0]` and the frame of ring 0) move rigidly with their ring; the frames do not change, so the mouth animation is unchanged. Offsets null = the old geometry byte for byte.
- `MoveFactory.withSway / swayParams / swayOffsets`; spec fields p38 sway, p39..p46 the eight numbers, appended only when not default. `tBreach` = `FxAnchors.of(...).breach()` (for a crest the head breach of prompt 10; NaN = full sway). The phase is the continuous script time; the envelope uses the loop time.
- `MoveOptions`: keys sway, swayamp, swayamp2, swaywave, swaywave2, swayhz, swayhz2, swayfade, swayidle for crest, emerge, crash, engulf (generated into the moves text); clamps told in chat; `sway=maybe` refused.
- `TestWormRenderer`: the skin is rebuilt every tick while the sway is on (the rings move even when the head stands still).
- Log: `[WormsOfArrakis] <move> sway: on|off ampLR= ampFB= (fractions of r0=...: at most N blocks, no ripple of the radius) ...`.

**Part 2 — the final crash sound sheet (fx-6).** `CrashFx.CUES` is now 17 rows, 79 plays: "Tremor: warden step" (entity.warden.step, same numbers as the gravel row), "Tremor: deep heartbeat" with 1 play, "Re-entry: impact" removed. All particle rows are unchanged. Emerge and engulf use the same sheet. The crest sheet (`CrestFx`) is not changed.

## Confirmed   (tested, or read from the code or the jar; say which)
- Tested: the formulas against hand values; env = 1 before the breach, idle + (1 - idle) e^-1 at breach + fade, tends to idle, monotone; the offset never exceeds (ampLR + ampFB) r0; the neighbour difference is below 0.25 on W16 (0.17); sway off = the old geometry byte for byte on all six moves (crest, emerge, crash/fall, engulf, projectile, ballistic crash); with sway on only positions move (same quads, drawn flags, joints, frames); ring k moves by exactly its offset; the head frame is unchanged; the tail sways with the same formula (last ring index = segment count); path, joints, timing and anchors of every move are the same with sway on or off; keys, clamps, refusals and the moves text; the sheet has 17 rows and 79 plays, the tremor event is entity.warden.step, the heartbeat has 1 play, no "Re-entry: impact"; the other Prompt 09 tests (monotone rows, cancelling, particle counts) pass. 204 tests, 0 failures.
- Smoke start: sway lines for crash, crash sway=off, crest with every key clamped (5 clamp lines in chat and log), `sway=maybe` refused, engulf swayamp2=0.1; `crash fx ... sound plays=79`; `fx registry check: 15/15 sound events and 4/4 particle types`.
- Read from the code: the `crash` command builds `Moves.fall` (`MoveOptions` "crash" -> `specFull("fall", ...)`).

## Inferred    (not verified)
- How the sway looks and whether it scales well; how the warden step sounds as a tremor. Nothing was seen or heard.
- The previs could not be read, so "same as the previs" is not checked (the defaults are the owner's values).
- Culling bounds and the shadow: offsets are at most 1.4 blocks at W48, so I expect no change is needed; not seen in game.

## Answers to the prompt's questions
1. The offset is applied in `WormSkin.build` (the overload with `offsets`, private `shift` on the chain centres of the body and tail chains). The head follows because the mouth and head are placed from `chain.center[0]` and `chain.frame[0]`; the frame is not touched, so the mouth animation is unchanged. The liner and the tail come from the same chains and move too. The renderer calls `MoveFactory.swayOffsets` and rebuilds the skin every tick (`TestWormRenderer`). Culling bounds and shadow: no change made (see Inferred).
2. Maximum offset at the defaults (crest, over 60 s): W6 0.16 blocks (bound 0.23), W16 0.45 (bound 0.64), W48 1.35 (bound 1.91). Largest neighbour-to-neighbour difference: W6 0.062, W16 0.170, W48 0.511 blocks. The idle sway after the fade: 0.04 / 0.12 / 0.36 blocks.
3. The sway runs on every path move (crest with the tail, emerge, engulf, fall/crash, projectile, ballistic crash): the sway-off byte-for-byte test and the sway-on test cover all six; the tail test shows tail vertices moving by up to the bound.
4. The `crash` command runs `Moves.fall` (rise, hold and fall). `Moves.crash` (ballistic) has no command of its own here (only a spec kind in code and tests). The sound sheet is the same for both (the anchor mapping of prompt 09).
5. Cost: the offsets for 241 rings (two sines each) take about 9 microseconds per call, once per tick per worm (unit test, warm). The skin rebuild per tick is the larger cost and was not measured.
6. Test numbers changed: `Prompt09Test` (82 -> 79 plays in the schedule test; 2 x 82 -> 2 x 79 in `aNewLoopStartsTheSheetAgain`), `Prompt10Test` (82 -> 79, crash expansion) and `Prompt10CrestTest` (82 -> 79, the same assertion). Reason: the final sheet. 193 -> 204 tests (9 sway, 2 sound).
7. Nothing blocks the first real-entity prompt that I know of. A real entity will need the sway offsets computed on the client from the same time base; `Sway` is pure and does not depend on the test renderer.

## Numbers
- Tests: 204, 0 failures. Crash sheet: 17 rows, 79 plays (before: 17 rows, 82 plays); the crest is unchanged (133 plays at W16 h0.7).
- Sway: the table in answer 2; 9 microseconds per offsets call at 241 rings.
- Jar deployed: `IslandCraft-WormsOfArrakis-0.1.0.jar`.

## Surprises, problems, deviations from the prompt
- The final sheet in the prompt lists a row "Fall: rumble down" (entity.warden.dig, fallStart, off 0.3, vol 3, pitch 0.6, 1 play, jitter 0.04) that the crash sheet in the code did not have, while the text only says the impact is removed. The prompt's totals (17 rows, 79 plays) only match if that row is present, so I added it where the impact was (82 - 3 heartbeat plays - 1 impact + 1 = 79). Please confirm that is what you meant.
- The sway makes the skin rebuild every tick, also for a worm that stands still.
- The envelope uses the loop time, so every loop begins at full sway until its breach; the phase is the continuous script time.

## How the owner can see it   (commands, log lines, the "you look and listen" list)
- Commands: `crash 16 120`, `emerge 16 60`, `engulf 16 60`, `crest 16 80`, each with `sway=off` for comparison and with `swayamp=0.1 swayamp2=0.1` to exaggerate; `crash 6 60 force`, `crash 48 300`; `moves` lists the keys.
- Log lines: `<move> sway: on ...`, `<move> fx: on ... sound plays=79`, `fx registry check: 15/15 ...`, plus the clamp and refusal lines in chat.
- Look and listen (creative world, `/arrakis_worm_test base here`, stand 30 blocks away): the body weaves left-right and front-back during the breach and settles to a faint sway; does it scale at W6 and W48, any gaps between rings at W48; on the crest the tail sways with the body and the head with the mouth; the same on emerge and engulf; `sway=off` gives the old rigid body; the crash tremor sounds like heavy footsteps (not gravel), the heartbeat plays once, there is no explosion at the re-entry.

## Suggested next steps (your view)
- If W48 shows gaps, lower the amplitude for large worms (the neighbour difference is 0.51 blocks there) or scale the wavelengths with W.
- Skip the per-tick skin rebuild when the sway is off and the head is still.

## Owner observations (human)
