# Decisions (session S2)

- Licence: Better Clouds is MPL-2.0 and cannot be relabelled MIT; owner chose a clean-room MIT renderer over porting MPL files. Reason: owner wants the whole mod MIT; clean room is the legal way.
- Clean-room separation: only D1 reads Better Clouds (source clone in the lead's scratchpad); implementers get the prose description only and are told not to open the source or the pack's better-clouds jar. The clone is deleted after D1 is accepted.
- Source for D1 is the 1.13.11 code (branch `1.21.11`, `versions/1.21.1-fabric`), the version in the pack, not the linked `1.21.0` branch (1.7.1, MC 1.21.0). No decompiling: the source is public.
- Settings in Stellar View's own config system, no YACL. Reason: owner wants it simple; no new runtime dependency.
- Standalone Better Clouds installed: our clouds step aside (no hard `breaks`). Reason: packs that add Stellar View keep launching.
- Version 1.1.0 (new feature, no breaking change).

## S1 decisions (still valid)
- Package `net.povstalec.stellarview` and mod id stay (add-ons, configs).
- Iris called via its public API by MethodHandle, no build dependency.
- Shader switch in `ViewCenters.renderViewCenterSky`.
- Under an Iris shader pack our clouds yield (renderClouds runs on to vanilla/the pack). Reason: none of the pack's shader packs use vanilla clouds (Complementary/Derivative clouds=off, BSL discards them); drawing into Iris targets caused double clouds and crashes in the original (spec W18, 7.4).
- Hook: own mixin, HEAD cancellable on LevelRenderer.renderClouds; pass through when the dimension has a Fabric DimensionRenderingRegistry cloud renderer (Aether) or no cloud height. Reason: order-independent against Porting Lib, Sporran, Fabric API (survey).
- Cloud amount from weather changes on the GPU (density threshold per frame), no rebuild per step. Reason: spec W14 (pops and CPU spikes).
- Colour from our own analytic sky/sun model, no lookup table. Reason: the original's table is an MPL asset.

## S2 continued (2026-10-27, second session)
- I2 accepted without another delegation: the partial files already met the spec, and the build was green.
- R1 glGet in GlStateSnapshot: no change unless T2 measures a cost. Reason: these are plain state queries the driver answers from its own client-side copy (no GPU readback); the owner's live session showed no stutter.
- R1 Polytone expression-form cloud height lost: accepted (Sodium behaves the same). "ARB" GL path label: cosmetic, left.
