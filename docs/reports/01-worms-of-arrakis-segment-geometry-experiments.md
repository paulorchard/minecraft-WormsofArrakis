# Report — Prompt 01 Worms of Arrakis: worm segment geometry experiments with GeckoLib and GeckoMesh
Status: blocked (Part 0 done; GeckoMesh does not load on NeoForge 26.1.2, so I stopped as instructed before any experiment)   Commit / tag: see git log (no exp tags yet)

## What was built
- Part 0 only. Gradle project (ModDevGradle 2.0.148, Gradle 9.2.1 wrapper, Java 25 temurin, NeoForge 26.1.2.129 — same toolchain as WeatherOfArrakis), mod id `islandcraft_wormsofarrakis`, package `com.paulorchard.islandcraft.wormsofarrakis`, jar `IslandCraft-WormsOfArrakis`, empty `@Mod` class that logs one INFO line, `docs/reports/`.
- GeckoLib is a required dependency in neoforge.mods.toml (`[5.5.2,6)`), compiled against the jar in the instance (not bundled). GeckoMesh is wired in build.gradle only behind `-Pgeckomesh=1` (not in the toml) so the failure could be reproduced.
- No experiment code, no entity, no commands yet. Nothing deployed to the instance.

## Confirmed   (tested, or read from the code or the jar; say which)
- Instance mods folder (read): `geckolib-neoforge-26.1.2-5.5.2.jar` (GeckoLib 5, version 5.5.2, MIT per its neoforge.mods.toml) and `geckomesh-neoforge-26.1-1.2.0.jar` (GeckoMesh 1.2.0 by Bay4lly, ships `LICENSE_GeckoMesh` = plain MIT, no extra restriction in the licence text).
- Instance NeoForge (read from minecraftinstance.json): `26.1.2.127`.
- GeckoLib 5.5.2 loads on a dedicated server on NeoForge 26.1.2.129 together with our mod (tested: `Done (1.323s)`, our INFO line present, no client class touched).
- GeckoMesh 1.2.0 does NOT load (tested on dedicated server): `Missing or unsupported mandatory dependencies: Mod ID 'neoforge', Requested by 'geckomesh', Expected range '[26.2.0.1-beta,)', Actual version '26.1.2.129'` → `Failed to start FML`. Its toml also says minecraft `[26.1, 26.3)`, so only the NeoForge range is wrong for us. The instance's 26.1.2.127 is lower still, so it will fail the same way there.

## Inferred    (not verified)
- GeckoMesh's NeoForge floor is probably a build-time mistake or a deliberate 26.2 target; whether it would work at runtime on 26.1.2 if the range were relaxed is unknown. I did not edit the jar or override the check (that would be the silent workaround you ruled out).

## Answers to the prompt's questions
1. GeckoLib 5.5.2, MIT, loads on 26.1.2: yes. GeckoMesh 1.2.0, MIT licence text with no distribution restriction (the "premium" label is only on the CurseForge page; I found no restriction in the jar), loads on 26.1.2: NO (needs NeoForge >= 26.2.0.1-beta).
2–6. Not started (blocked).
7. Owner decision needed, see next steps.

## Numbers
- n for W=16: 50, R≈7.97; W=48: 150, R≈23.87; W=3: 10 (from the prompt's formula, not yet rendered).

## Surprises, problems, deviations from the prompt
- The project folder already existed as `C:\Apps\IslandCraft-MC\WormsofArrakis` (IntelliJ skeleton, no commits, Gradle 9.1 Kotlin build). I used that folder instead of creating `WormsOfArrakis\WormsOfArrakis`, and replaced the skeleton's Kotlin build files with the Weather toolchain.
- The instance's latest.log predates the Gecko jars, so it says nothing about them; I tested in a dev dedicated server instead.

## How the owner can see it
- Nothing to see in game yet. To reproduce: `gradlew -Pgeckomesh=1 runServer` shows the error above; without the flag it starts cleanly with `[WormsOfArrakis] loaded (Part 0 smoke)`.

## Suggested next steps (your view)
- Option A (recommended): continue without GeckoMesh. Experiment 1 needs GeckoLib only. Experiment 2 becomes the prompt's fallback 2b (frustum via `submitCustomGeometry`), and experiment 4 would use custom vertex generation (closer c) anyway, since a mesh cannot bend. This also removes a dependency on a paid/beta-targeted library.
- Option B: you supply a GeckoMesh build whose range covers 26.1.2 (or confirm the author's intent); then experiment 2 is done as written.
- Option C: I patch the toml range in a local copy of the jar for testing only. Works as a probe, but not shippable, so I only do it if you say so.

## Owner observations (human)
