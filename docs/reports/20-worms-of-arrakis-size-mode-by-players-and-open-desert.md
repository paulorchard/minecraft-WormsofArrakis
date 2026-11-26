# Report — Prompt 20 Worms of Arrakis: size mode by players and open desert
Status: partly (all four parts are built; 407 unit tests pass and a dedicated-server smoke start ran both modes; the Mods menu config screen was never opened on a client, so what the owner sees there is inferred)   Commit / tag: sizemode-1 and sizemode-2 (one commit)

## What was built
**Part 1 — SizeMode (sizemode-1)**
- Config `size.SizeMode` = `depth` (default) or `players` (an enum with lower-case constants, so the toml and the config screen read `depth` / `players`). Copied into `HuntConfig.sizeMode` on every refresh, so a change reaches the next event that starts.
- The event stores the mode it started with (`HuntEvent.sizeMode()`, `sizeInfo()`); `hunt status` prints `size mode: <mode> (<the decision line>)` and the start log line says `(size mode <mode>)`. A running event keeps its mode, its size and its strike weights (the strike reads `e.sizeMode()`).
- Depth mode is untouched: `SizeTable.decide("depth", ...)` calls `forDepth`.

**Part 2 — the players rule (sizemode-1)**
- `SizeTable.forPlayers(players, open, cfg)`: table 1 player open medium / not open small; 2 to 5 large / medium; 6 or more huge / medium. Config `size.PlayersLargeAt` (2), `size.PlayersHugeAt` (6), `size.PlayersMode.Open.<single|group|crowd>` and `size.PlayersMode.Closed.<...>` for the six sizes (a bad word gives the default of that cell, with a start-up warning).
- `SizeTable.countPlayers(roster)` is the one place the count is made (vehicles will change it there). `SizeTable.decide(mode, depth, players, verdict, cfg)` returns the size (null = too shallow) and the log line.
- `hunt/OpenDesert` (engine-free): `check(terrain, x, z, size, cfg)` samples the disc of `size.OpenDesertRadius` (48) on a grid every `OpenDesertSampleSpacing` (4) blocks, 441 columns; a sample passes when the top block facing air (the same `Corridor.Terrain` the crest corridor reads: the heightmap, chunk-safe) is in `SandBlocks`; an unloaded chunk fails and is never loaded; open when at least `OpenDesertPercent` (98) percent pass. The size argument is not used yet (see answer 5).
- Evaluated once at the trigger, in `HuntManager.start`, after the roster is locked (`rosterOf`), before the event is built, so the stalk (width + 8 s) follows the size. Shallow sand (depth 1 to 4) still gives no event and the score penalty in both modes (the check is done first, as before).
- Log: `size: mode=players list=<n> open=<yes|no> sand=<x>% of <samples> samples depth=<d> -> <size> W<n> (<samples> columns read, <ms> ms)`; depth mode: `size: mode=depth depth=<d> -> <size> W<n>`; a command-forced size: `size: mode=<mode> forced by the command -> ...`.
- Strike weights per mode: depth mode keeps `strike.StrikeWeights.<size>.<type>` (the prompt 19 keys, values unchanged); players mode `size.PlayersMode.StrikeWeights.<size>.<type>`, defaults 70/5/5/20 for small, medium and large, 0/0/0/100 for huge. `HuntConfig.weightsFor(mode, size)`, `StrikeChoice.skipSet/decide/line` take the mode; availability, rescaling and the huge fallback (a medium crash) are as in prompt 19 in both modes. Bad rows (negative or sum 0) give the default row of that mode with a warning.

**Part 3 — toggling (sizemode-2)**
- `client/WormsOfArrakisClient`: a second `@Mod(value = MOD_ID, dist = Dist.CLIENT)` entry that registers `IConfigScreenFactory` with `ConfigurationScreen::new` (NeoForge's built-in screen). Client only: the dedicated server never constructs it (its log line does not appear in the server log). `SizeMode` and every other setting of the common config are editable there.
- Translation keys in `lang/en_us.json` (`islandcraft_wormsofarrakis.configuration.*`): title, the config section, `SizeMode` (+ tooltip and the two values), the open-desert and players keys, the section names. Other settings show their key names.
- `/arrakis_worm_test hunt sizemode [depth|players]` (operator): no argument shows the mode; one sets it, saves it (`ModConfigSpec.save`) and says so in chat; a bad word is refused in chat.
- Log `size: mode changed to <mode>` by either route (the command, or the Mods menu / file seen by the next config refresh).

**Part 4 — seeing what it would do (sizemode-2)**
- `hunt preview size [players]` (operator): both rules at the caller: depth and its size; the open-desert percentage, the verdict and the cost; when not open the first five failing samples with block id and offset; the players size for the roster a trigger would lock (or the given number) and the open / not open sizes for that count. `hunt preview choice` uses the weights of the current mode and says which. `hunt trigger <who> <size>` keeps forcing a size in both modes.
- For the smoke start only: `hunt fake trigger` (no size: the real rules on the fake player) and `hunt fake size [players]`.

New files: `OpenDesert`, `WormsOfArrakisClient`, `Prompt20Test`.

## Confirmed
- Read from the NeoForge 26.1.2.129 jar (`javap`): `IConfigScreenFactory.createScreen(ModContainer, Screen)`, `ConfigurationScreen(ModContainer, Screen)`, `ModContainer.registerExtensionPoint(Class, T)`; the code compiles against them and the jar contains the client class.
- Unit tests (407 in all, 12 new in `Prompt20Test`): the table for 1, 2, 5, 6, 12 players, open and not open, a count of 0, thresholds and sizes from config, a bad word; the grid (441 samples, inside the disc, the rim sampled, each column read once); all sand 100 %; exactly the threshold passes and just below fails (8 failing of 441 passes, 9 fails; an exact-threshold case); an unloaded sample fails; depth mode equals the prompt 13 sizes for depths 5 to 60; shallow safety in both modes; the log line; a fake world gives medium alone in the open and large with 3 in players mode, small and medium not open, the depth sizes in depth mode; an event keeps its mode after the setting changes; the stalk is 24 s for the medium; forced size in both modes; depth weights equal prompt 19, players weights, players huge needs no crest columns, the fallback, bad rows.
- Smoke start (dedicated server, fake players on a sand arena with stone floor): both modes' tables logged at start-up; `hunt sizemode` showed, set and saved the mode (the toml held `SizeMode = "players"`); a bad word was refused; `preview size` ran (depth 5 gives small; open: 1 player medium, 3 large, 6 huge; with a 3-block stone wall 96.6 % of 441 is sand: not open, 2 players medium); players-mode events: 1 player medium with a 24 s stalk, 2 players large with a 40 s stalk, 2 players not open medium; depth mode gave small at depth 5; an event started in players mode kept `players` after the switch to depth; all events were stopped with their rumble cells restored; no error in the log.

## Inferred
- What the Mods menu screen looks like and that the `SizeMode` button cycles depth and players: from the API and the key names, never opened. The enum value keys (`...SizeMode.depth`) may not be the ones the screen looks up.
- That a single common-config value is the server's only rule on a multiplayer server: the common config is read by each side from its own file, and the rule only runs on the server, so a client's value is never used.
- Fake players stand in the level's player list only for the smoke; a real player's roster and depth are read the same way, but the preview with a real roster was not run.

## Answers to the prompt's questions
1. **Config screen.** Registered in `WormsOfArrakisClient` (`@Mod(dist = Dist.CLIENT)`, `registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new)`). `SizeMode` stays in the COMMON config (`islandcraft_wormsofarrakis-common.toml`, key `size.SizeMode`): no type change, so the old toml value carries over as nothing moved (the key is new, default `depth`). COMMON is editable from the Mods menu at the main menu and in game, and the dedicated server reads only its own file. The owner should see Mods, Worms of Arrakis, Config, one entry "Worms of Arrakis settings", the sections (aggro, sand, strike, size ...); the section "Worm size" holds "Size mode" and the players-mode keys. Not seen on a client: I did not start the client (the prompt allowed one run only when it cannot be shown another way; I judged the registration confirmed by the API and left the screen to the owner).
2. **Cost of the open-desert check.** 441 columns (radius 48, spacing 4), one heightmap read and one block read each in loaded chunks. Measured in the smoke on the dedicated server: 1.6 ms the first time (cold), 0.10 to 0.66 ms after. It runs in the trigger tick, once per event; no spreading over ticks was needed.
3. **Migration of the prompt 19 keys.** None needed: depth mode keeps the keys where they are (`strike.StrikeWeights.<size>.<type>`), so an owner toml that has them keeps them exactly, as the depth mode's values. Only the players mode is new (`size.PlayersMode.StrikeWeights.<size>.<type>`). Deviation from the prompt's wording `StrikeWeights.<mode>.<size>`: I did not move the depth keys into a `depth` sub-table, because that would have lost them from an existing toml without a migration hook in the spec.
4. **Tests changed.** `Prompt13TextTest.theLanguageFileHasEveryKeyWithTheSameText`: it counted every key of `en_us.json`; the config screen keys (`islandcraft_wormsofarrakis.configuration.*`) are now counted apart (the hunt keys and the death message are still compared one to one) and a check that `SizeMode` has a screen text was added. Nothing else changed.
5. **For the later freeze-time check and vehicles.** Open desert: `OpenDesert.check(Corridor.Terrain terrain, double x, double z, SizeTable.Size size, HuntConfig cfg)` (the terrain is `StrikeRunner.terrainOf(level)`; `HuntAdapter.openDesert(UUID, Size)` is the world-side call; the size is accepted and not used yet, so a later prompt can scale the disc by it). The size: `SizeTable.decide(mode, depth, players, verdict, cfg)` / `SizeTable.forPlayers(players, open, cfg)`. The count: `SizeTable.countPlayers(roster)` (vehicles change it there). The event fields: `HuntEvent.sizeMode()`, `sizeInfo()`; `HuntManager.rosterOf(...)` builds the roster.

## Numbers
- Tests: 407 pass (395 before, 12 new).
- Open-desert check: 441 columns; 1.6 ms cold, 0.10 to 0.66 ms warm.
- Smoke sizes: players alone open medium (stalk 24 s); 2 players open large (40 s); 2 players not open (96.6 % sand) medium; depth 5 small (16 s).
- The jar `IslandCraft-WormsOfArrakis-0.1.0.jar` (704 KB) deployed once for both parts.

## Surprises, problems, deviations from the prompt
- One commit with both tags (the commands of Parts 3 and 4 live in the same files as Part 1 and 2).
- The smoke arena needed a stone floor: sand placed on void falls out of the world, so the first two smoke runs saw no sand at all (0 % sand, "void_air"); a diagnostic run showed the cause. The code was not at fault.
- `hunt fake trigger` (no size) and `hunt fake size` were added so a smoke start can run the real rules on a fake player.
- `hunt aggro` still shows the depth size in its line (the depth rule), also in players mode; `hunt preview size` is the way to see the players rule.
- `size.PlayersMode.StrikeWeights` sits under the `size` section, not under `strike`.
- Not done on purpose: no size re-check at the freeze, no rock-clipping fix, no vehicle count.

## How the owner can see it
- Mods menu: open Worms of Arrakis, Config, flip "Size mode", leave; `/arrakis_worm_test hunt sizemode` shows the same value; the log says `size: mode changed to <mode>`.
- Open dune, alone, players mode (`hunt sizemode players`): `hunt preview size` says medium; `hunt preview size 3` large; `hunt preview size 6` huge. Beside rock (below 98 % sand): small, medium, medium.
- A real event alone in the open desert: a medium (16) worm with a 24 s stalk, mostly a crest (`hunt preview choice medium` shows the shares of players mode).
- `hunt sizemode depth` and trigger on the same spot: the size follows the depth as before.
- Shallow sand (1 to 4 blocks): no event in either mode, the score loses the penalty.
- Log lines: `size: mode=players list=<n> open=<yes|no> sand=<x>% of <samples> samples depth=<d> -> <size> W<n>`, `hunt: event N started on <name> (size mode <mode>): ...`, `size: mode changed to <mode>`, `hunt config: SizeMode=... ` and `size.PlayersMode.StrikeWeights.<size> ...` at start-up; `hunt status` shows `size mode: <mode> (...)` for a running event.

## Suggested next steps (your view)
- Open the Mods menu screen once and say what it shows; if the "Size mode" entry reads badly, the translation keys are the place to adjust.
- Test the players rule next to dune edges and rock to see whether 98 % of a 48-block disc is the right strictness; the percent and radius are config.
- The freeze-time re-check (call `OpenDesert.check` again with the strike size) and the vehicle count (`countPlayers`) are the two small follow-ups prepared here.

## Owner observations (human)
