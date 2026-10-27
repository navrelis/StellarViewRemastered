# Requirements: Stellar View Remastered (session S2, clouds)

S1 (2026-10-08) shipped 1.0.0: moon cycle fix, shader hand-off, ~30 bug fixes, logo, CurseForge text. Details in `report.md` history (git).

## Owner's words (2026-10-27)
"Integrate Better Clouds into Stellar View ... find bugs and fix all of them, optimise further, fully compatible with all mods in Nytheria ... upload to CurseForge."
Follow-up: "Fabric, 1.21.1. Our licence stays MIT. ... remove every telemetry, remove the issue report and whatever is useless and makes the game slower ... make it work with our mod pack, optimise ... upload to CurseForge as our mod, project id 1733831. Clean up unused branches and worktrees, push, merge and commit everything, do everything on your own."

## Licence decision (owner chose 2026-10-27)
Better Clouds is MPL-2.0 (Qendolin and contributors); it cannot be relabelled MIT. Owner chose: **own MIT cloud renderer, clean-room**. One agent reads Better Clouds 1.13.11 and writes a behaviour description (no code, no shader code, no identifiers or comments copied). Separate agents that never see Better Clouds code (source or jar) implement from that description. Whole mod stays MIT.

## Requirements
- C1 New cloud renderer inside Stellar View (mod id `stellarview`), Fabric, Minecraft 1.21.1, Mojang mappings: soft, voluminous clouds in vanilla style, with the features of the behaviour description that are worth their cost.
- C2 Clean-room: no Better Clouds code, shader code or resources in the repo; implementers get only the description.
- C3 No telemetry, no issue report / log upload, no RenderDoc, no game tests, no Forge/NeoForge or other MC versions, nothing that only costs performance.
- C4 Works in the Nytheria pack (Sodium, Iris + its shader packs, Sodium Extra, any other mod touching clouds/sky/fog/weather). Standalone Better Clouds present: our clouds step aside.
- C5 Bugs avoided/fixed, cost measured: frame cost compared with Better Clouds 1.13.11 and vanilla in the pack.
- C6 Settings in Stellar View's own config system and screens (no YACL, no new runtime dependency); Mod Menu stays optional.
- C7 Version 1.1.0, jar `StellarViewRemastered-1.21.1-1.1.0-Fabric.jar`; README, `CURSEFORGE.md`, changelog updated (credit Better Clouds as inspiration only).
- C8 Pack: back up and remove `better-clouds-1.13.11+1.21.1-fabric.jar`, install the new jar.
- C9 Upload to CurseForge project 1733831 with the owner's uploader (`/curseforge-upload`), release, 1.21.1, Fabric, Client.
- C10 Git: commit per task, push to `origin` (navrelis/StellarViewRemastered); merge `remastered` into the default branch; delete unused branches and worktrees.

## Defaults the lead applies (owner said "do everything on your own")
- Stellar View's sky still hands over to Iris shader packs; clouds follow the description's shader-pack behaviour unless that breaks the Nytheria shader packs.
- New build dependencies only compile-only (APIs of Iris/Sodium etc.), none shipped.
- Testing: build + in-game in the pack (shaders off, one shader pack, Sodium on); stop once proven (owner prefers shipping).

## Constraints
- Windows 11, PowerShell, Java 21. MC 1.21.1, Fabric Loader 0.16.14 (project), Fabric API 0.116.0, Mojang mappings, Loom 1.10.
- Pack `C:\Users\nikol\curseforge\minecraft\Instances\NytheriaDevelopment` (402 mods; Iris 1.8.14-beta.1, Sodium 0.8.13, Sodium Extra 0.9.4). Pack repo `F:\Coding\NytheriaDevelopment`; backups go to its `mods_backup/`.
- Subagents implement; lead writes only `.knowledge/`, runs builds, tests, git. No secrets in the repo.
