# Report — Prompt 19 Worms of Arrakis: strike weights by size
Status: done (395 unit tests pass, one smoke start ran a huge event with a failed engulf through the medium-crash fallback to the end; jar deployed; the real feel in game is not yet seen)   Commit / tag: weights-1

## What was built
- **Weights per size.** `HuntConfig.strikeWeightsBySize[size][crest,crash,emerge,engulf]`, defaults small 10/70/10/10, medium 70/5/5/20, large 70/5/5/20, huge 0/0/0/100. Config `strike.StrikeWeights.<size>.<type>`. `weightsFor(size)` returns the configured row, or the default row when the row has a negative number or sums to 0; `weightProblems()` gives one line per bad row/fallback key, logged as a WARN `[WormsOfArrakis] config: ...` at start-up.
- **Old key retired.** The four single keys `StrikeWeights.crest/crash/emerge/engulf` stay defined in the spec (so a toml that has them is still valid and can be detected); `retiredKeysInUse()` warns once if they differ from the old defaults 30/25/25/20, saying the per-size tables replace them. Their numbers are ignored.
- **Zero weight = no check.** `StrikeChoice.skipSet(cfg, size, forced)`: crest and engulf are not evaluated when their weight for the size is 0 (crash and emerge cost no column reads). `evaluate(..., skip)` reads no crest column and/or no engulf disc for them; `Availability.skipped` carries it, and `why()` says "not checked: its weight is 0 for this size".
- **Decision with a strike size.** `StrikeChoice.decide(a, cfg, eventSize, rng, forced)` returns `Decision(choice, strikeSize, fallback, why)`. Candidates are the available types with weight above 0, picked by the rescaled weights. None → fallback: `FallbackSize.<size>` / `FallbackType.<size>` (huge medium crash; the others their own size and crash). Forced types ignore the weight (and are still refused when unavailable unless forced, as before).
- **Strike size separate from event size.** `HuntEvent.strikeSize()` (event size until the choice), `StrikeRunner.Run.strikeSize`, `GroupRunner.Group.strikeSize`. `HuntEvent.size/width/factor` stay the event's.
- **Rumble resize.** `HuntAdapter.resizeAttackRumbles(event, strikeSize)`: at the choice each member's attack run gets a plan and effect plan of the strike size's factor, and is recentred (the prompt 17/18 `recentre` mechanism: new rumble started, old one retired after the 0.25 s overlap), so no fake block is left.
- **`hunt status`** prints `size=huge strike=medium crash (fallback)`.
- **`/arrakis_worm_test hunt preview choice [size] [trials=1000]`** (operator): availability at the caller, why the others failed, columns read (and how many a full check would read), and the shares over seeded trials, fallback included.
- Log: `strike: no weighted type available for huge (engulf: <why>); fallback medium crash`, and `hunt: attack rumble of <name> resized at the choice ...`.
- Tests: `Prompt19Test` (10 tests), `Prompt14StrikeTest` adjusted.

## Confirmed
- Unit tests: default tables; shares of every size with everything available within 1 point over 40,000 seeded trials; medium and small without crest (about 67/17/17 and 78/11/11); medium without crest and engulf 50/50; huge always engulf when engulf is available; huge without engulf gives the medium crash; a zero weight is never chosen and its check is not run (the counting terrain shows fewer reads); bad config falls back to defaults with problem lines; the old key is reported; source assertions that the strike size drives the plan, devour table, group circle, tickets, camera peak, strike info and the rumble resize.
- Smoke start (dedicated server, three fake players, stone on the r0 = 24 ring): start-up logs the four per-size tables with the fallback keys; the huge event logged `strike: no weighted type available for huge (engulf: disc: not sand at x=24 z=0 ...); fallback medium crash`, started a W16 crash, the group circle was 19.11 blocks (medium crash), the far player (40 blocks) was released at the choice, both held members' rumbles went from radius 10.00 to 5.00 and 12/16 to 9/16, tickets 35.11 blocks, `hunt status` printed `size=huge strike=medium crash (fallback)`, the camera framing (peak 40.1) and the `discs:` line used crash W16, and the event ended DONE with the rumble cells restored and the chunk tickets released (28).
- Class, method and field names were checked against the code before use.

## Inferred
- Fake players are not in the level's entity list, so the worm never touches them: the smoke victim died by the backup, and no touch or disc crossing was seen. How the medium worm looks in the huge stalk's place is not seen.
- The visual change of the rumble at the choice (radius 10 to 5, a new rumble over the old) is judged only from the log and the restored-cell count.

## Answers to the prompt's questions
1. **Reads of the event's size after the choice, now the strike size** (`StrikeRunner.onStrike`): `plan(c.type(), e.size)` → strike size; the devour table `devourSeconds[type][e.size]` → `[ss]`; the log width; the chunk ticket radius `strikeAreaRadius(type, e.size) + e.width` → strike size; `GroupRunner.onChoice` circle radius `strikeAreaRadius(type, e.size)` (this drives who is released); `GroupRunner.startCamera` `camPeak(t, e.size)`; the `discs:` log line width; `HuntAdapter.strikeInfo` (the size the hazard/discs know: `r.event.size` → strike size); the attack rumble plan/effects (`e.factor` of the event → the strike factor via `resizeAttackRumbles`); `hunt status`. Unchanged on purpose: the freeze-start group radius (`maxStrikeArea(e.size)`), the freeze rumble, stalk, roar, tremors, score handling, aggro and the event start log (all the event's size). The availability check also stays on the event's size (the corridor/disc is sized for the worm that is wanted first). The hazard and discs follow automatically because the worm is spawned from the strike plan.
2. **Old key in an existing toml:** it stays defined (spec accepts it), is ignored, and one WARN is logged at start-up if its numbers differ from 30/25/25/20. A toml that still has the untouched defaults raises no warning (it cannot be told from "not set").
3. **Column reads saved:** for huge the four crest corridors are not read. On flat sand in the test terrain that is 17 reads (the engulf disc only) instead of 1073 (a saving of 1056 columns per huge check, about 98 %). Any size with crest 0 skips the crest corridors and any with engulf 0 skips the 17 disc samples; crash and emerge never cost reads.
4. **Huge fallback, held group and rumble:** the group of the freeze (huge radius 57.30) is cut at the choice to the medium crash circle (19.11); members outside are released and their rumbles stop, the held members keep theirs, resized to the medium radius and height with a new rumble started at the held block and the old one retired after the overlap; cells restored. Nothing is left over.
5. **Tests changed:** `Prompt14StrikeTest` only: the legacy `pick/odds` calls take an explicit weight array now (kept as `LEGACY = {30,25,25,20}` to test the rescaling maths), and the log line test expects the small table `crash=70,emerge=10`. Everything else passed unchanged.

## Numbers
- Tests: 395 pass (385 before, 10 new).
- Huge check reads: 17 instead of 1073 on flat sand.
- Smoke: group circle 57.30 → 19.11 blocks; rumble radius 10.00 → 5.00, max 12/16 → 9/16; tickets 28 chunks within 35.11 blocks; strike run 11.84 s, backup swallow at 8.95 s (table 7.45 s).

## Surprises, problems, deviations from the prompt
- The first `strike: size=huge ... chosen=crash` line still prints the event's size and the empty weighted list; the next line is the fallback line. Left so the existing line format is unchanged.
- The first smoke attempt ended before the strike (the huge stalk is 56 s); rerun with longer waits.
- The `hunt preview choice` check reads the real columns at the caller, so it reports the saved reads.
- Config validation: the spec accepts negative numbers (min -100000) so that the refusal can be logged instead of the toml being rewritten; the engine-free code uses the default row.
- The toml keeps the retired four keys beside the new per-size sub-tables.

## How the owner can see it
- `/arrakis_worm_test hunt preview choice medium` on open flat dune: roughly crest 70, crash 5, emerge 5, engulf 20. Beside rock or on hills, crest drops out and the shares rescale (engulf 20, crash 5, emerge 5 of 30: about 67/17/17).
- `hunt preview choice huge` on open sand: 100 % engulf (columns read 17 instead of 1073); beside rock: `fallback medium crash 100 %`.
- Log lines: `[WormsOfArrakis] hunt config: StrikeWeights.<size> ...` at start-up; `strike: no weighted type available for huge (engulf: ...); fallback medium crash`; `hunt: attack rumble of <name> resized at the choice`; `group: event N chose crash circle radius ...`; `discs: ... (crash W16)`; `config:` WARN lines for bad rows or the old key.
- `hunt status`: `size=huge strike=medium crash (fallback)`.
- You look and listen: real events: small mostly crash; medium and large mostly crest where there is room; a huge event beside rock gives the huge stalk and roar, then a medium crash with the camera and the rumble fitted to the medium worm; on open sand a huge event strikes with the huge engulf as before.

## Suggested next steps (your view)
- Watch a few real huge events beside rock to judge whether the rumble step from the huge to the medium radius is noticeable.
- If the huge fallback feels too abrupt, raise `FallbackSize.huge` to large or lower the engulf disc's strictness; both are config.
- A `hunt preview choice` sweep at a few spots would give real shares to tune the tables.

## Owner observations (human)
