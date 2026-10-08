# Report: Stellar View Remastered 1.0.0 (session S1, 2026-10-08)

Repository `navrelis/StellarViewRemastered`, branch `remastered` (base `stellarview-1.21.1-fabric` at e64d05f). Minecraft 1.21.1, Fabric. Mod id `stellarview`, display name "Stellar View Remastered".

## Result per requirement

| Req. | Result | Proof |
|---|---|---|
| R1 Moon in daytime | Fixed. Cause: upstream design, not a glitch: Luna has a realistic 8-day orbit and large bodies are drawn at up to full brightness by day. New option "Vanilla Moon Cycle" (`client.overworld.vanilla_moon_cycle`, default on) keeps the Moon exactly opposite the Sun. Option off = old behaviour. | Reproduced in the pack with 0.5.3 (Moon in the day sky on days 2, 4, 6). With the new build: no Moon in 48 daytime shots, Moon at the zenith at midnight on days 0, 2, 4, 6. `evidence/r1-before-...png`, `evidence/r1-after-...png` |
| R2 Off while shaders are active | Done. While an Iris shader pack is in use, `ViewCenters.renderViewCenterSky` returns false, so Vanilla and the shader draw the sky; next frame after shaders are switched off Stellar View draws again. Option "Disable With Shaders" (`client.disable_with_shaders`, default on). Iris is called through its public API by MethodHandle; no build dependency; nothing happens without Iris. | In one game session each: log lines "paused" / "resumed" / "paused" after Iris toggles and matching screenshots. Checked: Complementary Unbound r5.9.3 with and without Euphoria Patches 1.10.5, BSL 10.1.1. `evidence/r2-...png` |
| R3 Other bugs, optimisation | About 30 bugs fixed (commits below; player-facing list in `CURSEFORGE.md`). Per-frame garbage of the mod: night 159 -> 39 KiB (-75 %), day 124 -> 27 KiB (-78 %). | `evidence/perf-compare-0.5.3-vs-0.6.0.txt`; build green; in-game runs without errors from the mod |
| R4 CurseForge text | `CURSEFORGE.md`: summary (110 characters), suggested settings, description, file upload block with changelog, checklist. Original author and MIT credited. | read by lead |
| R5 Pixel-art logo | `logo/logo_64.png` (native, 23 colours), `logo/logo_512.png` (x8 nearest neighbour), generator `logo/make_logo.py` with self-checks; also the in-game icon. | generator re-run: identical hashes |
| R6 Jar in the pack | `mods/StellarViewRemastered-1.21.1-1.0.0-Fabric.jar` installed; original jar moved out. | launch check with that jar |

## Owner additions (evening)
- Version is 1.0.0. Jar: `StellarViewRemastered-1.21.1-1.0.0-Fabric.jar` (600,552 bytes, sha256 `8252fc986c9a54bf3495e517952366e260dae828770c9cdfdff6511e87c8de81`).
- Further optimisation and the remaining test runs were stopped on the owner's request.

## What was changed and why (60 files, 9 code commits)

| Commit | Content |
|---|---|
| 2cc3aed | Name, credits, links to the fork, jar name, licence file now packed into the jar (build.gradle pointed at a missing file), Mod Menu recommended instead of required, README |
| 23201d3 | Logo and mod icon (replaces a 684 KB icon; jar 1.27 MB -> 0.60 MB) |
| 4ef7c46 | `compatibility/iris/IrisCompatibility`, guard in `ViewCenters`, the two new options (config, screens, lang en/de) |
| 5624b3f | `LunaRenderer.getPosition`: antisolar position from Earth's own position vector (Sun and Moon exactly 180 degrees apart in every frame) |
| 31a1f02 | Render path: null mesh crash (static sky in the End, upstream issue 125), non-instanced vertex format, GPU buffers released on reload, constellations drawn once, static sky rebuilt on change, blend state restored, profiler section popped, DayBlending codec, no sweep after time jumps, dust cloud budget |
| fe88c0f | Config sliders usable with the mouse, config file clamping/caching/UTF-8, debug prints removed, Enhanced Celestials fallback colour, Star NBT key, view center codec range, client-only mixins, Antlia 2 declination |
| 08398e3 | Meteor chance applied as percentage with proper seeding, one path per shooting star, type weights; `SpaceCoords` fractional light years; galactic conversion; star/nebula alpha restored; fade-out direction; config screen saves only on change; unparsable booleans; child drawn once per frame; Constellation codec; vertex shaders use the right km component |
| 280203d | Zero-alpha and off-screen layers skipped; light level/config/fade once per frame (time-based); reused scratch objects; sampler boxing cached |
| bddcddf | Version 1.0.0 |

## Key decisions
- Moon: vanilla-like cycle as an option that is on by default, realistic orbit kept (upstream issue 101 asks for exactly this).
- Shader switch in the central render entry point, so add-on view centers are covered; all dimensions.
- No JUnit (rule: no new libraries). Proof by build, numeric checks by the agents (bit-identical maths for the optimisation), and in-game screenshots.
- Meteor chance fix kept although meteors are now much rarer by default (10 % of days / of 1000-tick periods instead of always). Set `client.overworld.meteor_shower_chance` and `shooting_star_chance` to 100 for the old frequency.
- Left alone on purpose: `SpaceRegion` truncating division (intent unclear), options that are defined but never read (`stars_always_visible`, Aether/Twilight `config_priority` and chances, `alt_vertex_build_order`), upstream TODO comments.
- More in `decisions.md`.

## Checks run
- `gradlew build`: green after every accepted task; final build 1.0.0 green. One failed build on the way (uncaught IOException), corrected.
- Game sessions in the pack through the pack's own client harness (unchanged, driven by a wrapper in the session scratch folder), each with settings restore verified by hash:
  1. 0.5.3, shaders off: perf baseline (JFR) + 80-shot moon series (R1 reproduced).
  2. 0.5.3, Complementary + Euphoria: Iris toggled (before pictures).
  3. Build 08398e3, shaders off: 80-shot moon series (R1 proven).
  4. Build 08398e3, Complementary + Euphoria: Iris toggled (R2 proven).
  5. Build 08398e3, Complementary with default settings (temporary copy without option file): one sun, one moon.
  6. Final code (280203d), shaders off: perf (JFR) + moon series + screen-edge shots + End round trip: 238/238 steps.
  7. Final code, Complementary + Euphoria and Complementary: Iris toggled, 16/16 steps each.
  8. Release jar 1.0.0, BSL: launch check with Iris toggled, 16/16 steps; log shows `stellarview 1.0.0-Fabric` and paused / resumed / paused. `evidence/release-1.0.0-bsl-toggle.png`
  Stopped on the owner's request before they ran: Derivative, static sky, instancing off (see the manual checklist).
- Same-picture check for the optimisation: 284 of 285 bright sky objects match one-to-one within 1.3 px between the build before and after (the camera in the pack is not perfectly still between runs, so no pixel-exact comparison); a Moon cut by the screen edge is drawn.

## Performance numbers
Pack, Overworld, shaders off, spectator at y=200 looking east 40 degrees up, 54 s analysed per window, Java Flight Recorder (allocation figures are sampling estimates).

| | 0.5.3 | Remastered | Change |
|---|---|---|---|
| Mod allocation per frame, night | 159 KiB | 39 KiB | -75 % |
| Mod allocation per frame, day | 124 KiB | 27 KiB | -78 % |
| Render-thread allocation per frame, night | 332 KiB | 202 KiB | -39 % |
| Render-thread allocation per frame, day | 292 KiB | 187 KiB | -36 % |
| fps in this scene, night / day | 1108 / 996 | 1363 / 2250 | indicative only: another game client ran during the baseline |
| Mod share of render-thread CPU samples, night / day | 3.9 % / 3.0 % | 2.6 % / 3.8 % | within sampling noise (under 30 samples per window) |

## The pack's shader-side double-sun workaround
No longer needed while "Disable With Shaders" is on: Stellar View draws nothing under a shader pack, shown with Complementary at its default settings (run 5, `evidence/t10-...png`). The four option files in `shaderpacks/` were not removed. They now only force the square sun style and sun angle 0 (Complementary) and sun path rotation 0 (BSL, Derivative). Decision for the owner / session S2.

## Changes in the pack (`C:\Users\nikol\curseforge\minecraft\Instances\NytheriaDevelopment`)
- `mods/Stellar.View-1.21.1-0.5.3-Fabric.jar` moved to `F:\Coding\NytheriaDevelopment\mods_backup\` (rollback copy).
- `mods/StellarViewRemastered-1.21.1-1.0.0-Fabric.jar` added.
- `shaderpacks/*.txt` (Iris option files of BSL, Complementary and Complementary + Euphoria): Iris rewrites the file of a pack whenever shaders are toggled (adds a date comment, Euphoria Patches adds two marker lines; same settings). All three were put back to their committed content after the last run; `git status` in the pack shows them unmodified.
- Temporary and gone again: a default-settings copy of the Complementary zip in `shaderpacks/`; the test world `saves/PerfTest-20261003` is the harness's own throwaway world and stays as before.
- `options.txt`, `config/iris.properties` and the other guarded files were restored by the harness after every run (hash verified). `config/stellarview-client.toml` was never changed.
- Not committed in the pack repository (only S2 commits there): S2 has to commit the jar swap. The CurseForge app may still list "Stellar View" for the instance; remove it there if it shows up.

## Manual test checklist (not done in the game)
- Shader pack Derivative Main d24.4.4: sky handed over and back.
- Option "Static Sky" on: Overworld -> End -> Overworld, no crash, the End shows its own stars.
- Option "Instancing" off, with textured stars on and off: stars and dust visible.
- Config screen: sliders with the mouse; both new options toggle and persist.
- A meteor shower night (with default settings day 4: `/time set 110000`) and a shooting star.
- F3+T several times: sky unchanged.
- Sun, Moon, planets crossing the screen edges while turning the camera: no popping.
- Light pollution next to a torch at night: stars dim quickly, dust clouds over about 2.5 s.
- Aether and Twilight Forest skies.

## Known limitations
- Enhanced Celestials and Lunar compatibility compiles but was not run (neither mod is in the pack).
- The vanilla moon cycle also applies in the Aether view center, which uses Earth.
- Nested synodic orbits and non-integer orbit counts (not used by the shipped data) still have upstream's behaviour.
- With an Iris pack and "Disable With Shaders" off, the off-screen skip is inactive.
- GitHub issues are disabled on the fork, but `fabric.mod.json` and `CURSEFORGE.md` point to the fork's issue page.

## For the owner
- CurseForge: nothing was uploaded. Files and text are listed in `CURSEFORGE.md`; jar in `build/libs`, logo in `logo/logo_512.png`.
- GitHub: enable Issues on the fork (Settings -> General -> Features), or clear the issues field on CurseForge. Optionally make `remastered` the default branch.
- `NytheriaRelease` instance was not touched.
- Upstream: the fixes are worth offering to Povstalec as pull requests (MIT on both sides).
