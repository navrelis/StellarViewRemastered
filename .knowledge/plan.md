# Plan (session S2: own cloud renderer, 1.1.0)

Status values: open / in progress / in review / done. Size: S = short (1), L = long (2).

| # | Task | Definition of done | Depends on | Model | Size | Status |
|---|------|--------------------|-----------|-------|------|--------|
| D1 | Behaviour description of Better Clouds 1.13.11 (1.21.1 Fabric), dirty room | prose spec: look, options with ranges/defaults, pipeline order, interaction with vanilla/Sodium/Iris/DH/fog/weather, perf ideas, weaknesses; no code, no shader code, no identifiers or comments copied | - | Opus | L | done (`clouds-behaviour.md`) |
| D2 | Pack + MC 1.21.1 survey (read-only, no Better Clouds jar) | every pack mod that touches clouds/sky/fog/weather/LevelRenderer listed with what it hooks; vanilla 1.21.1 cloud path in Mojang names; how Sodium 0.8.13, Iris 1.8.14 and the three shader packs handle clouds | - | Sonnet | L | done (`pack-clouds-survey.md`) |
| D3 | Game driver for this session (pack harness, game lock, screenshots, JFR, shader toggle) | dry run prints a full plan, writes nothing outside the scratchpad | - | Sonnet | S | done |
| I1 | Cloud renderer core: architecture + implementation in `client/clouds` | build green; clouds render in dev client; vanilla clouds replaced; Sodium + Iris paths per D2; option list handed to I2 | D1, D2 | Opus | L | done (01a2fa4) |
| I2 | Cloud options in Stellar View config, screen, lang en/de | options persist, screen usable, defaults per I1 | I1 | Sonnet | S | in progress |
| I3 | Version 1.1.0, metadata, README, `CURSEFORGE.md`, changelog | jar name right, credits, no BC code mentioned as included | I1 | Sonnet | S | done |
| R1 | Review + bug/perf audit of the new code | findings fixed or rejected with reason | I1, I2 | lead (+ owner of task) | - | open |
| T1 | In-game: shaders off / Complementary / BSL, rain, thunder, sunset, End/Nether, F3+T, cloud setting off | screenshots, no errors in log | I1, I2, D3 | lead | - | open |
| T2 | Perf: our clouds vs Better Clouds 1.13.11 vs vanilla in the pack | ms/frame and allocation numbers | T1 | lead | - | open |
| T3 | Pack: back up + remove better-clouds jar, install 1.1.0 | pack launches | T1 | lead | - | open |
| G1 | Git: commits, merge `remastered` into default branch, delete unused branches/worktrees, push | origin clean, one default branch | all code | lead | - | open |
| U1 | CurseForge upload, project 1733831 | file id + URL | G1, T3 | lead | - | open |

Parallel: D1 (Opus) + D2 + D3 (Sonnet) now. I2 + I3 after I1 (different files).

## Usage tally (work units)
Opus: 4 / Sonnet: 3 (+2 running) = ~45 % Opus. Planned: Opus D1 2 + I1 2 = 4; Sonnet D2 2 + D3 1 + I2 1 + I3 1 = 5.

## Manual test checklist (grows)
