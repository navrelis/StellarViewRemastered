# Requirements: Stellar View Remastered (session S1)

Owner's words (2026-10-08): "fork this mod and fix the following issue: moon shows up even on day time and also when you enable shaders this mod should be deactivated and when shaders are turned off then this mod can be reactivated again. and if you find other bugs then fix them along the way as well and also if you can optimise it then do it. ... make a curseforge description and summary as well as a pixelart logo for it."

## Requirements
- R1 Moon is visible in daytime. Reproduce in the pack, find the cause, fix: moon follows the correct day/night visibility.
- R2 While an Iris shader pack is active, Stellar View's sky rendering is off completely and the shader draws the sky; active again when shaders are off. Automatic, no restart. Config option to disable this behaviour (default: behaviour on).
- R3 Find and fix other bugs. Optimise where measurable (render cost, allocations per frame), with before/after numbers.
- R4 `CURSEFORGE.md`: description and short summary, crediting the original author and the MIT licence.
- R5 True pixel-art logo: small native canvas, nearest-neighbour scale to 512 px PNG, native-size file kept.
- R6 Built jar installed in the pack, replacing `Stellar.View-1.21.1-0.5.3-Fabric.jar`.

## Owner answers
- Mod id stays `stellarview` (configs and add-ons keep working); display name "Stellar View Remastered".
- R2 as written is confirmed.
- Public repository is fine (MIT). No CurseForge upload without the owner's OK; prepare so the upload is one step.

## Open questions sent 2026-10-08 (lead works with the defaults until the owner says otherwise)
1. Moon: default = new option "vanilla moon cycle", on by default (moon opposite the sun, never in the day sky); off = upstream's realistic orbit.
2. Version `0.6.0`, jar `StellarViewRemastered-1.21.1-0.6.0-Fabric.jar`, package/API unchanged.
3. Logo 64x64 native, x8 to 512, also used as the in-game mod icon.
4. Shader switch applies to all dimensions and add-on view centers.
5. No JUnit (new library); proof by build and in-game screenshots.

## Constraints
- Windows 11, PowerShell, Java 21. Minecraft 1.21.1, Fabric Loader 0.16.14 (project) / 0.19.5 (pack), Fabric API 0.116.0, Mojang mappings, Loom 1.10.
- Pack: `C:\Users\nikol\curseforge\minecraft\Instances\NytheriaDevelopment` (Iris 1.8.14-beta.1, Sodium 0.8.13; no Lunar, no Enhanced Celestials). Shader packs: Complementary Unbound r5.9.3 (+ Euphoria Patches 1.10.5), BSL 10.1.1, Derivative Main d24.4.4.
- Work only in this project folder; pack: only swap the own jar under the game lock (`F:\Coding\.knowledge\game.lock`), old jar to the pack's `mods_backup/`, leave the pack launchable, list every pack change in the report. Only S2 commits in the pack repository.
- No new libraries. No Graphify. Subagents implement; the lead writes only `.knowledge/` and runs builds, tests, git.
- Base branch `stellarview-1.21.1-fabric` (e64d05f), working branch `remastered`. Remotes: `origin` = navrelis/StellarViewRemastered, `upstream` = Povstalec/StellarView. Never force-push.
- The pack's shader-side "double sun" workaround (Iris option files in `shaderpacks/`): check after R2 whether still needed, report, do not remove.
- When finished: one line in `F:\Coding\.knowledge\log.md`.
