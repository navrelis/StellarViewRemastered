# Decisions

- Working branch `remastered` from `stellarview-1.21.1-fabric` (e64d05f), in the session worktree: the main checkout keeps the base branch untouched.
- Upstream has no newer commits on any 1.21.1 branch (fetched 2026-10-08): nothing to merge.
- R1 cause is upstream design, not a glitch: Luna has a realistic 8-day synodic orbit and `day_blending` draws large bodies at up to full brightness by day. Fix = option `vanilla_moon_cycle` (default on) that keeps the moon opposite the sun; realistic orbit stays available. Reason: this is what upstream issue #101 asks for and what vanilla players expect; hiding the moon by day only would leave nights without a moon.
- R2 uses Iris' public API (`IrisApi.isShaderPackInUse()`) through reflection/MethodHandle: no new build dependency, no crash without Iris.
- R2 guard sits in the central `ViewCenters.renderViewCenterSky`, not only in the mixin: add-ons that call the API are switched off too.
- No JUnit: rule "no new libraries". Proof by build and in-game screenshots.
- Package `net.povstalec.stellarview` and mod id stay: add-on and config compatibility.
- Game runs use a wrapper in the session scratchpad that imports the pack harness from `F:\Coding\NytheriaDevelopment\.dev` and patches paths in memory: the harness and the instance layout belong to session S2 and are not edited; no junctions in the instance.
- Lang keys for the new options only in en_us and de_de: the other three languages fall back to English; no machine translations of unknown quality.
- Fork has GitHub issues disabled; `fabric.mod.json` points to the fork's issue URL anyway. Enabling issues is a repository setting for the owner (in the report).
- Old jar backup goes to `F:\Coding\NytheriaDevelopment\mods_backup` (the pack's existing backup folder; the instance has none).
