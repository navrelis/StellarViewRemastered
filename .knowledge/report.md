# Report: Stellar View Remastered 1.1.0, own cloud renderer (session S2, 2026-10-27)

Repository `navrelis/StellarViewRemastered`, branch `remastered`, fast-forwarded into the default branch `stellarview-1.21.1-fabric` (both at d9add37). Minecraft 1.21.1, Fabric, mod id `stellarview`. The S1 report (1.0.0) is in git history before this commit.

## Result per requirement

| Req. | Result | Proof |
|---|---|---|
| C1 Cloud renderer | Own renderer in `client/render/clouds` (11 classes, 4 shaders, mixin `CloudLevelRendererMixin`). Soft puffs (ray-traced chord and ellipsoid density per box, Beer-Lambert opacity), weather grow/darken via GPU threshold, time-of-day colours, fog fade, drift, Off/Fast/Fancy, Fabulous. | In-game runs s2smoke/s2soft: 21 shots each (noon, sunset, night, rain, thunder, day 365, above the layer, F3+T, End round trip) |
| C2 Clean room | Behaviour description by a dirty-room agent; implementers never saw Better Clouds source/jar; clone deleted. | `clouds-behaviour.md` scan, agent instructions |
| C3 No telemetry etc. | No network, telemetry, issue report, RenderDoc or game tests added. | code review |
| C4 Pack compatibility | Complementary on/off/on: only the pack's clouds while shaders are on, ours when off (log pause/resume lines). End/Nether left alone. Sodium path via HEAD cancel. Better Clouds present = we step aside. | run s2shader (12 shots), logs: no ERROR/WARN from Stellar View in 4 launches (incl. owner's own session) |
| C5 Bugs / cost | Weaknesses from the description avoided by design (see decisions). Measured: ~12.9k boxes, 52-54 draw groups, ~16 MB GPU (render targets incl.). Frame-time comparison with Better Clouds/vanilla **not measured** (T2 skipped, owner prefers shipping). | logs |
| C6 Settings | 10 keys in "Clouds Config" of `stellarview-client.toml`, clamped on read; Clouds screen with live preview, Reset, Back; en + de. | code review, build |
| C7 Version/docs | 1.1.0, jar `StellarViewRemastered-1.21.1-1.1.0-Fabric.jar`, CHANGELOG, README, CURSEFORGE.md; Better Clouds credited as inspiration only. | diff read |
| C8 Pack | `mods/StellarViewRemastered-1.21.1-1.1.0-Fabric.jar` (sha256 c948220e...); test jar removed; `better-clouds-1.13.11+1.21.1-fabric.jar` in `F:\Coding\NytheriaDevelopment\mods_backup\`. Original 1.0.0 jar kept in the S2 scratchpad (rebuildable from 41b7fef). | launches clean |
| C9 CurseForge | Uploaded to project 1733831: file id **9345188**, release, 1.21.1 + Fabric + Client, relations Fabric API (required), Mod Menu (optional), changelog = 1.1.0 section. | uploader output |
| C10 Git | Commits per task, pushed; default branch fast-forwarded; 8 upstream-copy branches deleted on origin (by the owner, classifier blocked the lead); leftover worktree removed. | `git branch -r` |

## Changed files (this session, after 7521dfd)
- `common/config/CloudsConfig.java` (new), `client/screens/config/CloudsConfigScreen.java` (new), `StellarViewConfig.java`, `ConfigScreen.java`, `StellarViewClient.java`, `lang/en_us.json`, `lang/de_de.json`: cloud settings and screen (4c1b5a2).
- `shaders/clouds/cloud_volume.vsh`, `cloud_volume.fsh`: soft puffs instead of hard-edged boxes; camera-inside boxes now drawn once (d9add37).
- Earlier in S2: renderer core (01a2fa4), version/docs (7521dfd).

## Key decisions
See `decisions.md`. Clean-room MIT renderer; yield under Iris shader packs, Better Clouds and dimensions with own cloud renderers; GPU weather threshold; own colour model; no YACL; glGet state snapshot kept (no measured cost).

## Checks run
- `gradlew build` green after every change.
- Game driver runs in the pack: s2smoke (old shaders), s2shader (Complementary toggle), s2soft (final jar): all clean, guarded settings restored and verified, game lock released.
- CurseForge: offline + remote dry run, plan reviewed, upload.

## Manual test checklist (not covered by the scripted runs)
- Fast and Off cloud settings; fly through a cloud (soft fade, no holes); rain to clear transition smoothness; full vs new moon; lightning flash; night vision.
- Fabulous graphics with water; submerged = no clouds; Aether/Twilight Forest unchanged; BSL shader pack.
- Window resize, render distance change, world/server switch, far /tp; no state leaks (hand, GUI, outlines, ImmediatelyFast HUD).
- Spark: renderClouds cost vs Better Clouds 1.13.11 (jar in mods_backup) and vanilla.
- Clouds config screen: sliders live, values persist after restart; Sodium Extra cloud height override.

## Model usage
Delegated work units: Opus 5 (D1 2, I1 2, I1 correction 1), Sonnet 5 (D2 2, D3 1, I2 1, I3 1) = about 50 % Opus. No precision overrides; the correction stayed with Opus as owner of the renderer.

## Known limitations and recommendations
- Perf numbers in CHANGELOG/CURSEFORGE/README are estimates (box count matches); measure with Spark and update if needed.
- Terrain inside a cloud: chord not clipped at terrain depth (slight over-opacity in front of mountains inside clouds).
- Polytone expression-form cloud height is ignored (same as Sodium).
- `CURSEFORGE.md` says 1.0.0 was released on CurseForge earlier; the uploader has no record of that for project 1733831, so 1.1.0 is likely the project's first file. Fix the wording if so.
- Local branch `main` (copy of upstream/main) still exists; `git branch -D main` is safe.
- No Graphify graph exists for this repo (nothing to update).
