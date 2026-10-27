# Changelog

## 1.1.0

Adds Stellar View's own cloud renderer. It replaces the flat Vanilla clouds with soft, translucent cumulus in Vanilla style, built from many overlapping puffs.

**Added**

- Clouds with volume. The clouds are made of many overlapping puffs instead of flat sheets.
- Colours follow the time of day: white by day, warm orange toward the sun at sunrise and sunset with violet on the far side, dark blue at night (brighter at full moon), and a silver lining around the sun.
- Rain and thunder make the clouds grow, merge and darken smoothly, without popping. Lightning flashes light them.
- Far clouds shrink and melt into the fog. Near the camera they fade softly when you fly through them.
- Slow wind drift.
- The Vanilla cloud setting is respected: Off = no clouds, Fast = flat cloud sheets, Fancy = full volume. Works with Fabulous graphics.
- Clouds are generated on a background thread. At default settings that is about 13k puffs, a couple of draw calls and around 0.2 MB of cloud data.
- No telemetry and no network access.
- A "Clouds" page in the config screen and a "Clouds Config" section in `config/stellarview-client.toml`. All settings apply live:

| Setting | Range | Default |
|---|---|---|
| Enabled | on/off | on |
| Cloud distance | 100-350 % of render distance | 200 % |
| Coverage | 0-100 % | 50 % |
| Cloud size | 50-200 % | 100 % |
| Thickness | 0-96 blocks | 40 |
| Height offset | -128 to 256 blocks | 0 |
| Opacity | 10-200 % | 100 % |
| Drift speed | 0-1000 % of Vanilla | 100 % |
| Brightness | 50-150 % | 100 % |
| Sunset glow | 0-150 % | 100 % |

**Compatibility**

- With an Iris shader pack active, the pack's own clouds are used.
- If the standalone Better Clouds mod is installed, Stellar View leaves the clouds to it.
- Dimensions with their own sky or cloud renderer (Aether, Twilight Forest, Nether, End) are left unchanged.
- Works with Sodium (the Stellar View clouds replace Sodium's clouds) and honours the cloud height set in Sodium Extra.

**Credits**

Cloud look inspired by Better Clouds by Qendolin; Stellar View's renderer is an independent implementation.

## 1.0.0

First release of Stellar View Remastered, based on Stellar View 0.5.3. Same mod id and config file as Stellar View, so do not install both.

**Added**

- "Vanilla Moon Cycle" option (on by default): the Moon stays opposite the Sun and no longer shows in the daytime sky.
- "Disable With Shaders" option (on by default): while an Iris shader pack is active, the shader pack draws the sky. Stellar View is back on the next frame when shaders are turned off, without a restart.
- New pixel-art icon.

**Fixed**

- Crash when loading a world with Static Sky in a far-away dimension such as the End.
- Objects in the sky placed wrongly by coordinate rounding and a wrong conversion term.
- Fade-out with distance was inverted.
- Shooting stars changed direction in mid-flight, and the rarest meteor type could never appear.
- Meteor chance options had no effect. They now work as a percentage (default 10), so meteors are much rarer than in 0.5.3. Set the two Overworld options to 100 for the old frequency.
- Constellation stars drawn twice, and moons or planets drawn twice when crossing in front of their parent body.
- Sun and nebulae stayed dim after a visit to the End.
- Sky swept across a time jump (`/time set`, sleeping, dimension change).
- No stars with "Instancing" off, and the Overworld star sphere kept by Static Sky after a dimension change.
- Light pollution fade depended on frame rate. It is now time-based.
- Config sliders that could not be set with the mouse, config file rewrites on every click, and invalid or out-of-range config values.
- GPU buffers leaking on every resource reload, and blend state leaking into later rendering.
- Mixins are client-only now, and the licence text is inside the jar.

**Changed**

- Less work per frame: the mod's own memory allocation on the render thread went from about 159 KiB to about 39 KiB at night and from about 124 KiB to about 27 KiB by day (large modpack, sky-only view).
- Smaller download: about 0.6 MB instead of about 1.3 MB.
- Mod Menu is optional.
