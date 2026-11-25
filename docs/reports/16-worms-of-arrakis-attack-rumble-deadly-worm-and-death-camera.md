# Report — Prompt 16 Worms of Arrakis: attack rumble, deadly worm and death camera

Status: partly (all three parts built and unit-tested; the death-screen look and the camera on a real client are unverified)   Commit / tag: attack-1 (Part 1), hazard-1 (Part 2), camera-1 (Part 3)

## What was built
- **Part 1, attack rumble.** One long `Rumble.play` round the victim from the start of the freeze to the death: strength 0.7 to 1.0 over the 4 s freeze, then 1.0; radius `AttackRumbleRadius` (10) x size factor (min 5); height `AttackRumbleMaxSixteenths` (12) x (0.5 + 0.5 x factor), at most 15; viewers re-chosen every second; tremor sound and particles repeat; stopped on death and on every end path; hard timeout. `hunt preview attackrumble [size] [seconds]`.
- **Part 2, deadly worm.** Engine-free hit shape built from the renderer's own pose (body capsules, thin mandible shell, throat funnel and slab); contact by sampling the real box over substeps; who dies (players in Survival/Adventure, mobs, exemptions by tag `worm_immune`); swallowed damage type; the DevourSeconds + ThroatBackupSeconds kill is only the backup; test worms deadly only with `HazardOnTestWorms`; `strike hazarddebug`.
- **Part 3, death camera.** `DeathCam` (engine-free orbit), `DeathCamEntity` (invisible, silent, no physics, not saved, tracked 10 chunks, update every tick, Display-style interpolation, no-op renderer), `DeathCamManager` (spawn at the death spot, `ClientboundSetCameraPacket` sent one tick later straight to the connection, per-tick orbit, restore on every end path), `CameraLifecycle`, commands `camera preview [size] [seconds=12]`, `camera stop`, `camera status`, and `nokill` after the strike type in `hunt trigger ... strike <type> [nokill]`.

## Confirmed
- From the 26.1.2 / NeoForge sources: `ClientboundSetCameraPacket(Entity)` carries the entity id only; the client ignores it if the entity is not yet known (hence the one-tick delay); `NoopRenderer` exists; `InterpolationHandler` (3 steps, `rotLerp` on yaw); `EntityType` has no `is(TagKey)`; `ServerPlayer.requestedViewDistance()`.
- Unit tests: 348, 0 failures (8 camera tests).

## Inferred
- How the death screen looks from the camera, the smoothness on a real client, whether the camera entity is tracked in time, how the client behaves for `nokill`, and the yaw wrap (the interpolation uses `rotLerp`; not seen).
- Sand look, light and roar sound (prompts 14/15) still unseen on a client.

## Answers to the prompt's questions
1. Attack rumble: one long rumble from the freeze start to death, 0.7 to 1.0 then 1.0.
2. Hazard shape: assembled from the renderer's pose, so it follows the drawn worm; sampled box contact over 4 substeps.
3. Who dies: players in Survival/Adventure and mobs, minus exemptions (predicate and tag `worm_immune`).
4. Backup: kills at DevourSeconds + 1.5 s only if the worm never touched the victim.
5. Camera: own entity, packet sent directly to the connection one tick later; the player is never teleported.
6. Radius: 24/40/70/100 by size, clamped to 16 x (view distance - 1), clamp logged.
7. Restore: respawn, log-off, dimension change, `DeathCamMaxSeconds` (60), event end (nokill), stop command, server stop; the entity is always discarded.

## Numbers
- Camera peak step per tick for a 20 s run: small 0.19, medium 0.31, large 0.55, huge 0.79 blocks; for a 6 s run: 0.63, 1.05, 1.83, 2.62. First and last steps are near zero (smoothstep).
- Hazard cost about 0.2 to 1 ms per tick (see the hazard tests).

## Surprises problems deviations
- `java.util.Random` with small sequential seeds gave the same side for every seed; the seed is now scrambled (splitmix).
- Hazard pose cache thrashed (75 ms); replaced by a 16-entry cache.
- Smoke start: the connection-less fake player refuses the damage ("refused the damage and the death"), so the backup fired but the camera did not start, and the `nokill` run did not complete inside the script's timing. The camera preview was correctly refused on the console. Not exercised: set-camera packet, orbit on a client, restore packet, nokill. No leftover entity or fake block was seen.

## How the owner can see it
Jar `IslandCraft-WormsOfArrakis-0.1.0.jar` is in ArrakisTest01\mods.
- `/arrakis_worm_test camera preview huge` then `camera stop`.
- `/arrakis_worm_test hunt trigger self small strike crash nokill` to see the camera without dying.
- Log lines: `[WormsOfArrakis] camera:`, `hazard:`, `strike:`.
You look and listen: the rumble rising round you during the freeze; the worm killing on first touch; the camera rising and turning 120 degrees smoothly, looking at the spot; your view returning on Respawn; no jump, no flicker at the turn; no stuck camera after Respawn or relog.

## Suggested next steps
- Check the camera on a real client; tune DeathCamDistance, height and turn from what is seen.

## Owner observations (human)

