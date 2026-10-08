# Log

- 2026-10-08 Lead: survey (branch, upstream fetch, core render path, pack mods/config, Iris API from the pack jar, upstream issues #101 #117 #119 #120 #125). Result in `requirements.md`, `decisions.md`. Questions with defaults sent to the owner.
- 2026-10-08 T0 baseline build started (lead).
- 2026-10-08 A1 delegated (Opus, read-only): audit of bugs, per-frame cost, orbit maths, config system. Running.
- 2026-10-08 A2 delegated (Sonnet, read-only): how to drive the pack's client harness for sky screenshots. Running.
- 2026-10-08 T0 done: baseline `gradlew build` exit 0 (jar `Stellar View-1.21.1-0.5.4-alpha-Fabric.jar`).
- 2026-10-08 T6 delegated (Sonnet): metadata, version 0.6.0, jar name, licence in jar, Mod Menu optional, README. Running.
- 2026-10-08 T7 delegated (Opus): pixel-art logo 64x64 -> 512, generator script, mod icon. Running.
- 2026-10-08 T6 returned (Sonnet): five files changed as specified; diff read by lead, OK; Mod Menu moved to recommends (only the modmenu entrypoint imports it). Build check pending (other agents editing). Status: in review.
- 2026-10-08 T3 delegated (Sonnet): Iris hand-over via MethodHandle, guard in ViewCenters, options disable_with_shaders and vanilla_moon_cycle, screens, lang en/de. Running.
- 2026-10-08 T3 returned (Sonnet): diff read by lead, matches spec; correction 1 (camelCase names in IrisCompatibility, lead's brief was misleading) done and checked. Compile check pending. Status: in review.
- 2026-10-08 A2 returned (Sonnet, read-only): harness lives in `F:\Coding\NytheriaDevelopment\.dev\perf\client` (moved out of the instance, so it cannot run as is); `sky_shot` complete but never run, accepts only ticks 0-23999, no Iris toggle op; settings guard restores options/iris.properties by sha256; Director drives time/look/screenshot/keys over TCP 25610; world is a copy of `PerfTest-20261003`. Harness does not know the game lock.
- 2026-10-08 G1 delegated (Sonnet): wrapper in the session scratchpad that reuses the harness without changing it (paths patched in memory), handles the game lock, takes absolute tick times and a shader toggle step; dry-run only.
- 2026-10-08 T7 returned (Opus): logo 64x64, 23 colours, generator with self-checks; lead viewed the 512 image (moon, ringed planet, Milky Way, hills, tree, shooting star), re-ran the generator: identical hashes, all checks ok. Accepted.
- 2026-10-08 Lead: `gradlew build` exit 0 with T3+T6+T7; jar `StellarViewRemastered-1.21.1-0.6.0-Fabric.jar` 593 KB (was 1270 KB), contains LICENSE_stellarview.txt, new icon, IrisCompatibility. T3, T6, T7 accepted and committed.
