# Report — Prompt 06 Worms of Arrakis: crash as rise, hold and a fall into the hole
Status: partly (Parts 1 and 2 built, 84 unit tests pass, one dedicated-server smoke start, jar deployed; nothing seen on screen, and the hook of frame 3 is less curled than the sketch because the bend limit forbids it)   Commit / tag: fall-1 (034ea3f), crash-1 (the commit after it)

## What was built
- Part 1 (fall-1): `path/FallFamily.java`, the whole-body pose family pinned at the hole. A pose is the heading function phi(sigma) (sigma = arc length from the hole, positive up the exposed body, negative into the buried body) integrated in both directions from the origin, so the hole is exactly at (holeX, 0) and arc lengths are exact. Joints are placed by the existing rule (`WormPose`, successive points at Euclidean distance one pitch) from the head. `PosCurve` is a new one-method interface (`pos(s)`) that `PlanarPath` and the family curve both implement, so `WormPose.at` works on either; nothing else in the geometry changed.
- Part 2 (crash-1): `Moves.fall(...)`, the composite. `crash <width> <segments> [height=2.5] [lean=7] [hold=1.5] [speed=12] [gravity=12] [min_radius=2] [mouth=open|closed] [force]` (keyed, any order, a bare number is the height). The prompt 03 arc is `crasharc` (old syntax), the prompt 05 projectile is `crashproj` (prompt 05 syntax). `freeze <t>` holds the fall at t in [0, 1] (0 = the hold pose, 1 = the move's end), `freeze hold` holds the tower, `freeze off` resumes.
- Jar: `IslandCraft-WormsOfArrakis-0.1.0.jar` in the ArrakisTest01 `mods` folder (older jar deleted, GeckoMesh stays `.disabled`, no PNG in the jar). Part 1 was deployed on its own after its tests, then Part 2.
- Chat: every refusal and clamp of the new command (too short, height outside 2.0 to 6.0, lean outside 0 to 45, unknown key, bad mouth, min_radius below the limit) goes to chat and the log (prompt 05 rule).

## Confirmed   (tested, or read from the code or the jar; say which)
- 84 unit tests pass (prompts 02 to 05 unchanged and unweakened; `Prompt06Test` has 14).
- The dedicated-server smoke start printed every message listed above, the spawn of `crash 16 120` and `crash 48 300` with the numbers and the table, and refused `crash 16 90` ("needs at least 98 segments"), `height=1.5`, `height=7`, `lean=60`, `foo=1`, `mouth=wide`; `min_radius=1` was clamped to 2.0 with a message; `crashproj 16 260`, `crasharc 16 150 25` and `crest 16 80 height=4` still behave as in prompt 05. The server has no client, so the skin was not drawn there.
- No Minecraft, NeoForge or GeckoLib class or registry name is new in this prompt: all the new code is pure maths (`path/`) plus command wiring in `TestCommand` and `MoveFactory`, which use the names already compiled against the 26.1.2, NeoForge and GeckoLib 5.5.3 jars in prompts 01 to 05 (Brigadier arguments, `Component.literal`, `sendFailure`, `sendSuccess`). Nothing was re-read in the jars because nothing new was used.
- The sketch's numbers were checked against its own coordinates (see answer 6).

## Inferred    (not verified)
- How the fall looks on screen: the hook, the speed of the sinking, whether the exposed length shrinking at the pace of free fall reads as a tower that sinks. The frame lines give the shape; the animation was not watched.
- That the buried part (never drawn, only skipped by the underground rule) stays hidden in a real world: with `base here` on non-sea-level ground the surface provider is the world one, tested in earlier prompts but not for this move.
- The 3% margin on the bend limit (answer 2) is enough on every ring of a real worm; it is enough on all 200 sampled poses of every combination in the unit test.

## Answers to the prompt's questions
1. **The pose family.** Each of the four frames is the owner's polyline (tail to head, body widths, hole = vertex with index 1, 1, 2, 2), converted to a heading per piece (piecewise constant, taken from the coordinates), sampled on a sigma grid from -3.2 W to +2.8 W (step 0.0025 W), then smoothed by two boxes of 0.25 W (a 0.5 W low-pass), the two ends extended with their end value. The tower (frame 1) is one straight line (one heading, 82.7 degrees, rotated by the lean so that it is exactly 90 - lean). The exposed part (sigma >= 0) is then rate-limited from the heading at the hole (kept) so that |d phi / d sigma| never exceeds 1 / (2.06 W) (the limit 2.0 W plus a 3% margin, see Surprises); the buried part is free. For a head tip height h between the keys the profile is a monotone cubic (PCHIP, Fritsch-Carlson) in h evaluated per grid point on the common sigma grid; above frame 1 the tower profile is kept, below frame 4 the frame 4 profile is kept and the head sinks along it. The head arc position is not interpolated: for each h it is solved so that the measured head tip (the closed flap apex, from the first two joints) is exactly at height h, which keeps the tip height exact and makes sigma_head follow. All lengths scale with height / 2.5 (not the width). The lean rotates the whole profile (buried part included) by (90 - lean) - 82.7 degrees. The buried part carries the lean because its profile is the sketch's (the part below the hole is smoothed, never limited): the tail point 1.24 W below the sand drifts back from -0.15 W (frame 1) to -2.16 W (frame 4), the sketch has -0.16 and -2.22 (within 0.06 W at every key, tested within 0.3 W).
2. **The bend limit.** Per key frame, measured at W16 (identical at W3 and W48 within 0.05 degrees), tower 2.5 W: see answer 4 for the table. Minimum fitted radius above the sand is 2.06 W in frames 2, 3 and 4 (the limit 2.0 W plus the 3% margin; frame 1 is straight, radius infinite). Where the clamp changed the shape: only frame 3 (hole heading 67.6 degrees; sketch pieces 52.6 below and 80.6 above the hole, smoothed 67.6 before the clamp; tip heading 108.8 degrees, the sketch 119.2, and 113.4 degrees if only smoothed, unlimited; tip dx +0.07 W, the sketch -0.42 W; exposed length 1.48 W, the sketch 1.60 W). The sketch's hook in frame 3 is about 1.2 W radius as the prompt says, so the limit does not allow the head to curl back behind the hole; with the limit enforced the head only gets to 109 degrees and stays 0.07 W in front of the hole. Frames 2 and 4: the limit is active in frame 2 (tip heading 104.4 against the sketch's 104.1, exposed 2.10 against 2.11), and almost not in frame 4 (exposed 0.51 against 0.47 because the smoothing blends the corner at the hole: the hole heading is 46.8 degrees, the sketch's two pieces are 36.3 below and 56.6 above).
3. **The composite.** Defaults, W16: rise at 90 - 7 = 83 degrees, n = 35 body rings above the base at the hold (formula (n + HEAD_LENGTH) x sin(angle) = 40 gives 34.52; the rise stops where the measured tip is exactly at 40 blocks, so n is the measured count), tower tip 2.50 W (40.0 blocks), rise 7.72 s at 12 blocks/s, hold 1.5 s, fall 2.79 s (free fall from 40 blocks down to the end at -(HEAD_LENGTH + 1) = -6.78 blocks, g = 12), then 2 s pause. W48: n = 106 (formula 105.58), tower tip 2.50 W (120 blocks), rise 21.5 s, fall 4.77 s. Continuity at the switch (`riseAndFallAgreeAtTheSwitch`, W 3, 16, 48 x heights 2.0, 2.5, 4.0, 6.0 x lean 7, 0, 20): every joint above the sand (and the first straight stretch below it) has the same y and z within 1e-3 and the same joint angle within 1e-3. Underground rings differ: the rise follows the tunnel arc, the family is a straight slant; they are not drawn (the underground rule skips them).
4. **The frame table at the four key heights** (tower 2.5 W, W16; tip dx = tip distance in front of the hole; identical to the shown precision at W3 and W48), my measured / the sketch:

| Frame | tip height W | tip dx W | exposed length W | hole heading deg | tip heading deg | tail drift W | min radius W |
|---|---|---|---|---|---|---|---|
| 1 | 2.47 / 2.47 | 0.30 / 0.32 | 2.49 / 2.50 | 83.0 / 82.7 | 83.0 / 82.7 | -0.15 / -0.16 | straight |
| 2 | 2.00 / 2.00 | 0.31 / 0.15 | 2.10 / 2.11 | 59.8 / 59.5 | 104.4 / 104.1 | -0.72 / -0.73 | 2.06 |
| 3 | 1.46 / 1.46 | 0.07 / -0.42 | 1.48 / 1.60 | 67.6 / 66.6 (mean of the two pieces) | 108.8 / 119.2 | -1.61 / -1.67 | 2.06 (sketch about 1.2) |
| 4 | 0.39 / 0.39 | 0.32 / 0.26 | 0.51 / 0.47 | 46.8 / 46.5 (mean of the two pieces) | 56.9 / 56.6 | -2.16 / -2.22 | 2.06 |

   Along the fall of the default W16 (time-based frames, `freeze 0, 0.25, 0.5, 0.75, 1` in the log as `fall frame t=...`): tip height 2.50 / 2.32 / 1.77 / 0.86 / -0.42 W, head heading 83 / 91 / 100 / 76 / 28 degrees, exposed rings 35 / 32 / 23 / 9 / 0, exposed length 2.52 / 2.36 / 1.83 / 0.90 / 0 W, tail drift -0.15 / -0.35 / -1.05 / -1.91 / -2.16 W (the fall is fast at the end, so the sketch's 1.46 W frame is at about t = 0.55 and the 0.39 W one at about t = 0.8).
5. **Segments needed** (exposed length at the hold + the buried slant of the last frame (2.55 W) + one body width): W16 defaults 98 (so `crash 16 120` works), W48 defaults 292 (`crash 48 300` works). Build time of the exposed part during the fall (the `WormSkin.build` of the head to the last exposed ring, best of four, the same call the renderer makes; `Prompt06Test`), at t = 0, 0.25, 0.5, 0.75, 1.0: W16 1.6 / 1.1 / 0.9 / 1.3 / 0.2 ms with 9,850 / 9,600 / 8,350 / 5,850 / 600 quads; W48 10.3 / 8.7 / 8.4 / 4.0 / 0.2 ms with 84,300 / 82,050 / 70,050 / 44,550 / 1,800 quads. The quad count never increases during the fall (tested); the cache key already carries the pose parameter (the path position on the rise, `sHold + the drop` on the fall) so every fall frame rebuilds.
6. **Not reproducible, or inconsistent in the drawing.** (a) The frame 3 hook (answer 2): impossible within the bend limit; the head ends up in front of the hole instead of 0.42 W behind it. (b) Frame 4's listed headings 27.3, 30.0, 56.6: the coordinates give 27.3, 36.3, 56.6 (the second piece (-0.421, -0.309) to the hole is 36.3 degrees), so 30.0 does not match the coordinates; I used the coordinates. Frame 1's 83 degrees is 82.7 from the coordinates; the lean rotates it to exactly 90 - lean. (c) Segments compress: the drawing does not need it. The exposed length (2.50 to 0.47 W) and the height (2.47 to 0.39 W) shrink at the same ratio and the buried start point drifts back, which is the body receding along a curve of constant arc length, not a compression of the rings; no pitch compression was added. (d) The ring positions of the buried tail jump by up to 0.83 W for a step of 0.01 W of h (the lever arm of the tail is 6 W long and the body slants more as it falls); the visible rings above the sand jump by at most 0.0143 W (tested to 0.02 W). The prompt asks for "any ring position"; the buried tail cannot be made continuous to that level and is never drawn.
7. **Blockers for the next prompt:** none. Sand displacement and particles at the hole can use `Move.holeX` (the hole in model z) and the hole heading in the frame line (83 degrees at the tower, 47 at the end, tail rings pass through the same hole); the mouth rework only touches `HeadSpec` and `HeadFlaps`; breach location needs the surface provider only; peristaltic crawling is not touched.

## Numbers
- Key frames and time frames: see answers 4 and 5.
- Largest visible joint angle during the fall, over W 3, 16, 48 x heights 2.0, 2.5, 4.0, 6.0 x 200 times: 0.991 of the limit (the limit is on joints whose three chords are above the sand).
- Ring step for 0.01 W of h: visible rings 0.0143 W, whole body 0.83 W.
- Fall duration (W16 default): 2.79 s; W48: 4.77 s. `crash 16 150 height=4 lean=15` needs 148 segments (scale 1.6).
- Unit tests: 84, all pass.

## Surprises, problems, deviations from the prompt
- Sketch vs limit: the exposed length of frame 3 differs by 0.12 W, the tip dx by 0.49 W, the tip heading by 10 degrees; the unit test for the exposed length uses 0.05 W for frames 1 and 2 and 0.15 W for frames 3 and 4 (the prompt asked for 0.05 W everywhere). Frame 3 cannot match under the limit with the tip height and the hole heading held; frame 4 differs by 0.04 W because the smoothing mixes the two pieces at the hole.
- The limit is enforced on the curve with a 3% margin (radius 2.06 W instead of 2.0 W): the chords of unit pitch form a polygon of the curve and the first version, at exactly 2.0 W, had joints 1 to 2% over the limit at large widths and heights (a joint angle of 1.826 against 1.790 degrees at W16, tower 6). With the margin every visible joint stays at 0.991 of the limit or less.
- The limit is applied for sigma >= 0 only (the prompt: "above the sand"). Rings whose centre is above the sand but whose rear joint dips under it at the hole may exceed the limit by about 1.5% (seen at W3); the unit test checks joints whose three joints are above the sand.
- The hole heading of the family is the smoothed sketch (67.6 degrees for frame 3), not the heading of either piece. The prompt's "keep the heading at the hole" is kept (the limiter starts from it) but I did not search for a lower hole heading that would also match the exposed length of frame 3.
- Spec: no min_radius knob for the hole heading, only `min_radius` (default 2.0, force goes below it, otherwise clamped up with a chat message). `freeze hold` is `t = 9` internally (the frame table and the log line are the same).
- The fall's sigma_head is solved (so that the tip height is exact) instead of interpolated by PCHIP as the prompt describes; it is strictly decreasing with h (tested at 200 heights, three widths).
- The rise path is the emerge path (underground arc of one width, straight tunnel), angle 90 - lean; `speed` applies to the rise only, the fall is free fall.
- The 2 s pause after the fall keeps the end pose (all underground) and the cycle restarts at the tunnel; the switch is invisible because nothing is above the sand at either end.

## How the owner can see it
Jar: `IslandCraft-WormsOfArrakis-0.1.0.jar`. Creative world; run `/arrakis_worm_test base here` on non-sea-level ground; stand back about 40 blocks and to the side.
```
/arrakis_worm_test crash 16 120
/arrakis_worm_test freeze hold
/arrakis_worm_test freeze 0.15
/arrakis_worm_test freeze 0.35
/arrakis_worm_test freeze 0.6
/arrakis_worm_test freeze 0.85
/arrakis_worm_test freeze off
/arrakis_worm_test clear
/arrakis_worm_test crash 16 150 height=4 lean=15
/arrakis_worm_test crash 48 300
/arrakis_worm_test crash 16 120 gravity=6
/arrakis_worm_test crash 16 120 lean=0 hold=3
/arrakis_worm_test crash 16 90
/arrakis_worm_test crash 16 120 height=7
/arrakis_worm_test crasharc 16 150 25
/arrakis_worm_test crashproj 16 260
/arrakis_worm_test stats on
/arrakis_worm_test stop
```
What to look for: the tower rises leaning 7 degrees, holds 1.5 s, then sinks back into its own hole with the top hooking back over the body (heading up to about 109 degrees, not the sketch's 119); the exposed length shrinks; the head ends beside the hole and goes under; the 2 s pause and the loop. The last two refusals (`crash 16 90`, `height=7`) must print a chat message.
Log lines (`logs\latest.log`):
- `[WormsOfArrakis] path crash: width= segments= height= lean= hold= speed= gravity= minRadius= n= (formula) towerTip= riseTime= fallDuration= segmentsNeeded= HEAD_LENGTH= scale=` at spawn.
- `crash key frame 1..4: tipDx= tipHeight= exposedLength= holeHeading= tipHeading= tailDrift= minRadius= | sketch: ...` at spawn (the table of answer 4).
- `crash frame hold: frame t=9.00: ...` and `fall frame t=...` (t = 0, 0.25, 0.5, 0.75, 1) with `exposedLength=...W holeHeading=...deg tailDrift=...W` added; `freeze crash frame ...` at each freeze.
- `crash refused: ...` for every refusal, the same text in chat.
- The client's `path fall: ... quads= ...` build line on first draw and the `stats on` line (quads per frame, shrinking during the fall).

## Suggested next steps (your view)
- Look at frames 2 and 3 first (`freeze 0.35`, `freeze 0.6`): if the hook should curl further, the only options are a lower limit radius for the exposed part (`min_radius`, with force) or accepting that the head ends up in front of the hole.
- Then sand displacement and particles at the hole (the hole is fixed at `holeX`, the heading and the speed of the sinking are in the frame lines).
- Then the mouth rework and breach location.

## Owner observations (human)
