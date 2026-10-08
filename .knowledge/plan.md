# Plan

Status values: open / in progress / in review / done.

| # | Task | Definition of done | Depends on | Model | Status |
|---|------|--------------------|-----------|-------|--------|
| T0 | Baseline build of the untouched branch | `gradlew build` result known | - | lead | done |
| A1 | Read-only audit: bugs, per-frame cost, orbit maths, config system | two ranked lists with file:line, four answers | - | Opus | done |
| A2 | Read-only: how to drive the pack's client harness (`sky_shot`) | exact commands, side effects, restore steps | - | Sonnet | done |
| G1 | Game driver wrapper in the session scratchpad (reuses the pack harness unchanged, game lock, absolute ticks, shader toggle step) | dry-run prints a complete plan, nothing written outside the scratchpad | A2 | Sonnet | done |
| T1 | R1 reproduce in the pack with 0.5.3 | screenshots: moon in the day sky, day and time noted | A2, game lock | lead | done |
| T2 | R1 fix: `vanilla_moon_cycle` in `LunaRenderer` | from Earth the moon is at the antisolar point at every tick; option off = old behaviour; other view centers unchanged | A1 | Opus | done (in-game proof in T9) |
| T3 | R2 shader switch + both new config options (spec, screens, lang) | with a shader pack in use `renderViewCenterSky` returns false; toggling needs no restart; option `disable_with_shaders` default on; no crash without Iris | A1 | Sonnet | done (in-game proof in T9) |
| T4a | R3 render fixes (audit A2, A3, A6, A7, A8, A12, A14, A21, profiler pop, DayBlending codec, B3) | each finding fixed or rejected with reason; build green | A1 | Opus | done (in-game checks in T9) |
| T4b | R3 small fixes (audit A4, A11, A20, A23 parts, A24, A25, debug prints) | each finding fixed or rejected with reason; build green | A1 | Sonnet | done |
| T4c | R3 maths/brightness fixes + per-frame cost (audit A1, A5, A9, A10, A13, A15, B1, B2, B4, B5; judge A16-A19, A22) | fixed or rejected with reason; same picture where promised; build green | T2, T4a | Opus | done (in-game checks in T9) |
| T5 | R3 optimisation with numbers | before/after: allocation per frame and sky render time, same picture | T4, measuring method from A2 | Opus | done |
| T6 | Metadata: name, version 0.6.0, credits, links, jar name, licence file in jar, Mod Menu optional, README | jar name and `fabric.mod.json` correct, LICENSE inside the jar | - | Sonnet | done |
| T7 | R5 pixel-art logo 64x64 -> 512, mod icon | both PNGs in the repo, generator script, nearest-neighbour proven (512 = 64 x 8 blocks) | - | Opus | done |
| T8 | R4 `CURSEFORGE.md` | summary <= 150 characters, description, credits, MIT | T2-T5 known | Sonnet | done |
| T9 | R6 install in the pack, in-game check of R1 and R2 for every shader pack | screenshots day/night, shaders on/off without restart, pack launches | T2-T6, game lock | lead | done (Derivative, static sky, instancing off not run: owner stopped further tests) |
| T10 | Is the pack's shader-side double-sun workaround still needed? | answer with evidence in the report | T9 | lead | done (answer: not needed any more; left in place) |
| T11 | Final acceptance, `report.md`, push, line in `F:\Coding\.knowledge\log.md` | full build, no leftovers | all | lead | in progress |

Parallel: A1 + A2 now. T2 + T3 + T6 + T7 touch different files (T3 owns config/lang/screens, T2 only `LunaRenderer`/`Luna`, T6 metadata/build files, T7 logo files).

## Manual test checklist (grows)
- Moon by day/night over 8 game days, option on and off.
- Each shader pack: one sun, one moon, no Stellar View stars; shaders off: Stellar View sky back, no restart.
