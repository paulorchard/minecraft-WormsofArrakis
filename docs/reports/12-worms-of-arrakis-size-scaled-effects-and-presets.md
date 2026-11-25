# Report — Prompt 12 Worms of Arrakis: size-scaled effects and the size presets
Status: partly — both parts built, 219 unit tests pass (0 failures), one dedicated-server smoke start ran clean; nothing was seen or heard in a client (hard rule), so how a small worm reads (sizes, quiet) is unverified.   Commit / tag: `scale-1` (Part 1), `preset-1` (Part 2); see `git log`

## What was built
**Part 1 — effects scaled by the size of the worm (scale-1).**
- `fx/SizeFactor.java` (new, pure): `of(W)`, the four anchor points as constants (`W = {0, 16, 32, 48}`, `F = {0, 0.5, 0.75, 1.0}`; 0 below, flat 1.0 above 48; W6 gives 0.1875). Log at start: `[WormsOfArrakis] size factor: W8=0.25 W16=0.5 W32=0.75 W48=1.0`.
- `FxOptions.countScale(width)` is now `SizeFactor.of(width) x fxcount`. It replaces `min(r0 / 8, 2.0) x fxcount` (`CrashFx.COUNT_REFERENCE_R0` and `MAX_AUTO_SCALE` stay as unused constants). `FxPlayer` takes the width of the move (`m.width`, not r0).
- Sounds: `FxPlayer.expand(a, opt, seed, sizeFactor)`: volume = vol x factor x fxvol; pitch, repeat counts and times are not touched. The three-argument form (factor 1.0) is the W48 reference schedule the older tests use.
- Counts: the one place where the old scale was applied (`FxPlayer.step`: instant rows `floor(count x scale + random)`, rate rows `rate x scale x dt`), so the crash rows, the eruption rows (entry and the crest's exit eruption) and the crest rows all follow it. A burst row of 1 or more makes at least 1 particle when the factor is at least 0.1; below that the stochastic rounding keeps the mean exact.
- Sizes: after a particle is made, every type except BLOCK and CHUNK has its start size multiplied by the factor (`FxSink.Spawn.withSize`); `endScale` acts on the scaled start size as before. Sand grains (BLOCK 0.1 / 0.15) and chunks (CHUNK 0.25) keep their size.
- Log and chat: each move's `fx: on` line, the `fx player started` client line and the `fx` command header now say `size factor=0.50 (W16)` and the count scale; the number of sound plays is unchanged by the factor.

**Part 2 — the four size presets (preset-1).**
- `test/Presets.java` (new, pure): sizes small 8, medium 16, large 32, huge 48; moves crash, ballistic, crest, emerge, engulf; `minSegments` searches the smallest segment count the move builds without force (doubling then bisection over the move's own `Moves.Rejected` checks; a crest starts at ceil(5 W)); `specFor` builds the spec with every default (`MoveOptions.parse(move, W, segs, "")`; ballistic through `TestCommand.ballisticSpec`, the new shared builder of crasharc).
- `test/PresetCommand.java` (new): `/arrakis-worm <small|medium|large|huge> <crash|ballistic|crest|emerge|engulf>`, literals only, op level. It prints one chat line and one log line `preset small crash = crash W8 segments=49 (sizeFactor 0.25) = /arrakis_worm_test crash 8 49` and calls `TestCommand.launch`.
- `TestCommand`: the end of the move branch and the placement are now `launch(...)` and `place(...)` (no change in behaviour); the long form and the presets both end there. `moves` also prints the presets (`MoveOptions.movesText()`; `lines()` is unchanged).

## Confirmed   (tested, or read from the code or the jar; say which)
- Tested: sizeFactor at 0, 6, 8, 12, 16, 24, 32, 40, 48, 64 against hand values, monotone, never above 1, equal to the anchors; volume = vol x factor x fxvol and the range of the loudest cue at W16 is half that of W48; counts per row at W8, 16, 32, 48 equal the sheet count x factor (crash sheet and eruption rows); the crest scales linearly with fxcount and its entry fountain follows the factor; non-block sizes at W16 are half of W48 and the shrink to endScale is unchanged; block and chunk sizes are identical at every width; the number of sound plays is unchanged; the budget holds at W48 (peak under 14,040) and fxcount 3 is thinned.
- Tested: the 20 segment minima equal the prompt's table exactly; every preset builds without force; one segment fewer is rejected for crash, ballistic, emerge and engulf; crest presets are 5 W; the command tree accepts the 4 x 5 pairs and refuses everything else (a size or move that is not a literal, an extra argument, a number, `force`, `key=value`, the underscore name); tab completion lists the four sizes and then the five moves; the spec of a preset equals the spec of the long form; the describe line and the moves text.
- Smoke start: `size factor: W8=0.25 W16=0.5 W32=0.75 W48=1.0`; `command /arrakis-worm registered`; the five presets and the `moves` text; `arrakis-worm tiny crash` and `arrakis-worm small crash 5` refused by Brigadier ("Incorrect argument for command"); the long form of crash 16 120 prints `size factor=0.50 (W16)`.
- Read from the code: nothing else applies a width scale to sizes, speeds or spreads (see answer 1).

## Inferred    (not verified)
- Whether the W8 effect still reads as an event, and how the halved sizes and quieter sounds feel. Nothing was seen or heard.
- Hearing range: vanilla clamps the gain at 1, so a cue at 4.6 x 0.25 = 1.15 is as loud near the worm as before and carries a quarter as far (the 16 x volume rule of prompt 09; not heard).
- A console command runs the preset at the console's position; a player in game is what the placement is written for (same as the test command; the smoke start used the console).

## Answers to the prompt's questions
1. The old count scale was `FxOptions.countScale(r0)` = `min(r0 / 8, 2.0) x fxcount`, used once in `FxPlayer` (field `scale`, applied in `FxPlayer.step` to instant and rate rows), and printed by `FxPlayer.headerLine`, `TestCommand` and `FxClient`. The new factor is `SizeFactor.of(width)` in `FxOptions.countScale(width)`, applied in the same place, plus the volume in `FxPlayer.expand` and the size in `FxPlayer.step` (`Spawn.withSize`). No size, speed, spread or lifetime was scaled by the width anywhere: positions use the real r0 (ring radii), the speeds and spreads are the sheet's numbers, the client drift factor (`ParticleModel.driftFactor`) depends on the life only. So nothing stacks; I changed none of them.
2. Particles made (whole event, default fxcount 1, budget 14,000 alive), peak alive, and the range of the loudest cue (4.6, range 16 x vol x factor); "before" is the old scale with the same sheet, "after" the new factor:

| Event | W | before: particles / peak / thinned | after: particles / peak / thinned | range before → after (blocks) |
|---|---|---|---|---|
| crash | 8 | 17,764 / 7,449 / 0 | 8,901 / 3,763 / 0 | 74 → 18 |
| crash | 16 | 37,195 / 14,001 / 1,074 | 19,222 / 7,574 / 0 | 74 → 37 |
| crash | 32 | 53,325 / 14,018 / 33,452 | 32,719 / 11,296 / 0 | 74 → 55 |
| crash | 48 | 63,065 / 14,017 / 33,892 | 47,341 / 14,002 / 1,130 | 74 → 74 |
| crest | 8 | 26,789 / 10,306 / 0 | 13,429 / 5,219 / 0 | 74 → 18 |
| crest | 16 | 54,182 / 14,001 / 1,052 | 27,737 / 7,558 / 0 | 74 → 37 |
| crest | 32 | 139,260 / 14,061 / 37,234 | 66,518 / 11,349 / 0 | 74 → 55 |
| crest | 48 | 147,518 / 14,067 / 37,564 | 91,290 / 14,001 / 1,245 | 74 → 74 |

W48 makes about 25 % fewer crash particles than before and about 38 % fewer on the crest, because the old cap of 2.0 is gone and the budget thinning before was large; the numbers before are the thinned ones.
3. A row whose scaled count is below 1: a burst row of 1 or more makes at least 1 particle when the factor is 0.1 or more; below 0.1 the count is rounded stochastically (`floor(count x scale + random)`), so its mean is exact and it can be 0 on a given loop; at a factor of 0 nothing is made. Rate rows keep their running fraction (a row of 18.95 can show 18).
4. Types: scaled (non-blocks): dust_plume, cloud, explosion, poof, large_smoke. Kept (sand blocks): BLOCK (grains 0.10 and 0.15) and CHUNK (0.25). Row by row: the crash sheet and the eruption rows are listed with their type in `build/prompt12-numbers.txt` (written by the test); in short, the grain, fountain, spray, plume-block, curtain and fine-grain-haze rows are BLOCK (kept); the flying sand blocks and the blocks in the plumes are CHUNK (kept); the dust ring, shock ring, columns, haze, bank, cloud rows and the puffs are non-blocks (scaled). The crest uses the same sheets, so the same rows.
5. The minima computed are exactly the table: crash 49 / 98 / 195 / 292, ballistic 62 / 101 / 179 / 257, crest 40 / 80 / 160 / 240, emerge 12 x4, engulf 4 x4. No deviation. What a preset looks like (joint spacing is 1 block): emerge W8 is 12 rings = 12 blocks long, event time 10.59 s (9.59 s of play, 1 s pause) and engulf W8 is 4 rings = 4 blocks, 6.64 s (5.14 s of play); so a small emerge or engulf shows a 3.99-block-radius head and almost no body. Other events at W8: crash 49 blocks, 9.73 s; ballistic 62 blocks, 18.71 s; crest 40 blocks, 11.73 s.
6. Brigadier accepted the hyphenated literal `arrakis-worm` (the smoke start registered it and ran it; the unit tests build the same tree in a plain dispatcher). Permission: `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)`, the same as `/arrakis_worm_test`. Placement and base: the preset ends in the same `TestCommand.place` (in front of the player, across the view; the base level comes from `WorldBase.resolve` as set by `/arrakis_worm_test base ...`). The test command has no "base required" refusal and no one-worm-per-player rule (each run adds a worm; `clear` and `stop` remove them), so there is none to share.
7. Test numbers changed: `Prompt09Test.countScalingAndSwitches` (the default scale is now 0.5 at W16 and 1.0 at W60, instead of r0/8 and the cap 2.0); `Prompt09Test.particleCountsPerRowMatchTheSheetAtW16` (scale = size factor 0.5; the tolerance of a rate row is 1 particle instead of 0, because small rows lose a fraction in the running count); `Prompt10Test` (five tests that count thousands of particles at W16 now run with fxcount 2 = the old W16 scale: curtain heights, chunk sizes, the budget thinning by one ratio, entry/exit rows, which keeps their thresholds). New: 15 tests (9 in `Prompt12Test`, 6 in `Prompt12PresetTest`); 204 -> 219 tests.
8. Nothing blocks the next prompt that I know of. For the real entity: `SizeFactor` and `Presets` are pure, so the preset sizes can become settings; the minimum-length search is the thing to reuse (it runs about a hundred builds for a huge crash).

## Numbers
- Tests: 219, 0 failures. Size factor: W6 0.1875, W8 0.25, W16 0.5, W32 0.75, W48 and up 1.0.
- Table of answer 2; presets: answer 5 (20 minima).
- Jar deployed after each part: `IslandCraft-WormsOfArrakis-0.1.0.jar`.

## Surprises, problems, deviations from the prompt
- The "before" numbers are the old scale with the same sheet and the current budget; at W32 and W48 the old scale (about 2) was thinned a lot by the budget (33,000 of 53,000 on a crash), so the visible drop at W48 is smaller than the factor suggests.
- The crash at W8 starts a tremor of 0.25 x the sheet sizes: grains and chunks keep their size while the puffs are a quarter, so a small worm's sand looks relatively coarser.
- `lines()` of the moves text stays one line per move; the presets are added by `movesText()` (older tests count the lines).
- The ballistic preset is `crasharc` in the test command (the same move, `Moves.crash`); the chat line says "ballistic crash" and shows the `crasharc` line.

## How the owner can see it   (commands, log lines, the "you look and listen" list)
- Commands: `/arrakis-worm huge crash`, `large crash`, `medium crash`, `small crash` (and the same four for crest, emerge, engulf, ballistic); `/arrakis_worm_test crash 16 120` against `/arrakis-worm medium crash`; `fxcount=2 fxvol=2` still work on the test command; `/arrakis_worm_test moves` lists the presets and their segment counts; `/arrakis_worm_test fx` shows `size factor`.
- Log lines: `size factor: W8=0.25 W16=0.5 W32=0.75 W48=1.0`; `command /arrakis-worm registered`; `preset small crash = crash W8 segments=49 (sizeFactor 0.25) = /arrakis_worm_test crash 8 49`; `<move> fx: on ... size factor=0.25 (W8) count scale=0.25 ...`.
- Look and listen (creative world, stand 30 blocks away, set the base as for the test command): the same event four times; the particle clouds and sand shrink in number, the puffs shrink in size, the grains and chunks keep their size, the sounds carry less far (stand at 20, 40 and 70 blocks: the huge one is heard from further; ranges 74 / 55 / 37 / 18 blocks for the loudest cue); the same for crest, emerge, engulf, ballistic; whether a small worm (W8) still reads as an event or is too faint (the four anchor points are constants in `SizeFactor`).

## Suggested next steps (your view)
- If W8 is too faint, change the points in `SizeFactor.W` and `F` (the one place; the segment below W16 is the line through the origin, so a separate W8 point means adding it to the arrays).
- Consider a separate floor for volume (a quiet cue below 1 may be inaudible at W8).
- Let the preset command take the base from `here` when none is set.

## Owner observations (human)
