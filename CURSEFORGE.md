# CurseForge page draft: Stellar View Remastered

Note for the owner: this file is a draft for the CurseForge project page and the file uploads (the current one is 1.1.0). Nothing has been uploaded or published. Copy the parts you need by hand: the "Description" section goes into the project description, the changelog goes into the file upload form.

## Project name

Stellar View Remastered

## Summary

Fork of Stellar View: a starry sky with a Vanilla-style Moon, soft volumetric clouds and automatic Iris shader support.

<!-- 119 characters -->

Alternatives:

- Maintained fork of Stellar View: Milky Way sky, Vanilla-style Moon, its own soft clouds, Iris shader hand-over.

  <!-- 111 characters -->

- Stellar View, maintained: night sky, Moon that follows the Sun, voluminous clouds and a sky that steps aside for shaders.

  <!-- 121 characters -->

## Suggested project settings

- Categories: the owner chooses in the CurseForge form. Fitting choices are "Cosmetic" and "Miscellaneous" (or "Utility & QoL" if it is offered for Minecraft mods in the form). One main category is enough.
- Licence: MIT
- Source URL: https://github.com/navrelis/StellarViewRemastered
- Issues URL: https://github.com/navrelis/StellarViewRemastered/issues
  - Issues are currently disabled on the fork. Switch them on in the repository settings (Settings, General, Features, Issues). Until then, leave this field empty.
- Logo file: `logo/logo_512.png` (512x512, nearest-neighbour upscale of the pixel art). The native pixel art is `logo/logo_64.png`.

## Description

Copy everything below this line up to the "File upload" heading into the project description.

---

Stellar View Remastered is a maintained fork of [Stellar View](https://github.com/Povstalec/StellarView) by Povstalec. It replaces the Vanilla night sky with a Milky Way made of individual stars and dust clouds, and keeps the Vanilla feeling. The original mod is also on [CurseForge](https://www.curseforge.com/minecraft/mc-mods/stellarview) and [Modrinth](https://modrinth.com/mod/stellarview). This fork is not the original and is not made by its author. It is released under the same MIT licence.

The mod is client-side only. It does not need to be installed on a server and changes nothing on the server.

## Clouds

Version 1.1.0 adds Stellar View's own cloud renderer. It replaces the flat Vanilla clouds with soft, translucent cumulus in Vanilla style, built from many overlapping puffs.

- **Colours follow the time of day.** White by day, warm orange toward the sun at sunrise and sunset with violet on the far side, dark blue at night (brighter at full moon), and a silver lining around the sun.
- **Weather.** Rain and thunder make the clouds grow, merge and darken smoothly, without popping. Lightning flashes light them.
- **Distance and fog.** Far clouds shrink and melt into the fog. Near the camera they fade softly when you fly through them.
- **Wind.** Slow drift.
- **Vanilla cloud setting.** Off = no clouds, Fast = flat cloud sheets, Fancy = full volume. Works with Fabulous graphics.
- **Light on resources.** Clouds are generated on a background thread. At default settings that is about 13k puffs, a couple of draw calls and around 0.2 MB of cloud data.
- **Privacy.** No telemetry and no network access.

### Cloud settings

Open the Stellar View config screen and go to Clouds, or edit the section "Clouds Config" in `config/stellarview-client.toml`. All settings apply live, without a restart.

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

### Cloud compatibility

- With an Iris shader pack active, the pack's own clouds are used.
- If the standalone Better Clouds mod is installed, Stellar View leaves the clouds to it.
- Dimensions with their own sky or cloud renderer (Aether, Twilight Forest, Nether, End) are left unchanged.
- Works with Sodium (the Stellar View clouds replace Sodium's clouds) and honours the cloud height set in Sodium Extra.

Cloud look inspired by Better Clouds by Qendolin; Stellar View's renderer is an independent implementation.

## What is different in Remastered

Compared with Stellar View 0.5.3, version 1.0.0 changed the following. These changes are all still in 1.1.0.

- **Vanilla Moon Cycle.** The Moon now stays opposite the Sun. It rises at sunset, sets at sunrise and no longer shows up in the daytime sky. In the original, the Moon followed a realistic 8-day orbit and was in the daytime sky on most days. At new moon it appeared there as a dark square next to the Sun. The option is on by default. Turn it off to get the realistic orbit back.
- **Shader hand-over.** While an Iris shader pack is active, Stellar View does not draw the sky. The shader pack draws the sun, moon and stars as its author intended. When you switch shaders off, Stellar View's sky is back on the next frame. No restart and no resource reload are needed. The option is on by default.
- **Meteors are much rarer by default.** The meteor shower and shooting star chance options had no effect in 0.5.3. They work now and are read as a percentage (default 10): about one day in ten has a meteor shower, and about one in ten 1000-tick periods has a shooting star. In 0.5.3 a shower happened every day and a shooting star every 1000 ticks. Set the two Overworld options to 100 to get the old frequency.
- **Bug fixes.** A crash when loading a world, objects in wrong places, rendering errors and config problems are fixed. The full list is in the changelog of the file.
- **Less work per frame.** Layers that are fully transparent (most of the sky by day) or entirely off screen are no longer built and drawn. Light level and brightness are worked out once per frame instead of once per layer, and temporary objects are reused. The picture is unchanged. The mod's own memory allocation per frame on the render thread went from about 159 KiB to about 39 KiB at night and from about 124 KiB to about 27 KiB by day, measured in a large modpack with a sky-only view.
- **Smaller download.** The jar is about 0.6 MB instead of about 1.3 MB, because the large screenshot icon was replaced by a pixel-art icon.
- **Mod Menu is optional.** It is recommended for the in-game config screen, but the mod runs without it.

## Features

- A Milky Way galaxy made of individual stars and dust clouds
- Brighter stars
- Planets of the Solar System with apparent retrograde motion, and their moons
- Moon phases
- Other galaxies and nebulae
- Supernovae
- Shooting stars and meteor showers
- Light pollution: stars appear dimmer when you stand near a light source
- Sky rotation that depends on where you are in the world
- Custom skybox support
- Soft, voluminous clouds from its own renderer (see Clouds)
- Its own skies for the End, the Nether (through the config), the Aether and Twilight Forest when those mods are installed
- All sky content is data-driven and can be changed with resource packs
- Can be used as a library by other mods

## Shader packs

If you use an Iris shader pack, the shader pack usually draws its own sun, moon and stars. Two skies drawn on top of each other look wrong.

By default, Remastered avoids this. While a shader pack is active, Stellar View draws nothing and the shader pack handles the sky. When you turn shaders off with the Iris toggle key, Stellar View draws its sky again from the next frame. You do not need to restart the game or reload resources.

Iris is optional. Without Iris, nothing changes.

This was checked on Iris 1.8.14 with Complementary Unbound r5.9.3 (with and without Euphoria Patches 1.10.5) and BSL 10.1.1.

If you want the old behaviour, turn off "Disable With Shaders". Stellar View then draws its sky underneath the shader, as the original does. The existing "Static Sky" option is meant for that case.

## Configuration

The config screen is available through Mod Menu. Mod Menu is optional but recommended. Without it, edit the config file `config/stellarview-client.toml` by hand. Values outside the allowed range in this file are clamped instead of causing a crash later.

The two new options:

- **Vanilla Moon Cycle** (Overworld config), key `client.overworld.vanilla_moon_cycle`, default on. The Moon stays opposite the Sun. Off: realistic lunar orbit, the Moon can also be seen during the day.
- **Disable With Shaders** (General config), key `client.disable_with_shaders`, default on. Stellar View hands the sky to an active Iris shader pack. Off: Stellar View draws under the shader.

## Requirements and compatibility

- Minecraft 1.21.1
- Fabric Loader 0.16.14 or newer
- Fabric API
- Java 21
- Optional: Mod Menu (config screen), Iris with Sodium (shader packs), Sodium Extra (cloud height), Enhanced Celestials, Lunar

Remastered uses the same mod id (`stellarview`) and the same config file as Stellar View. Existing configs, resource packs and add-ons keep working. For the same reason, do not install it together with the original Stellar View. Use one or the other.

## For resource pack and mod authors

The documentation for resource pack authors is maintained for the original mod: [Stellar View documentation](https://moddedmc.wiki/cs/project/stellarview/docs). Resource packs that set a meteor `probability` are now read as a percentage (0-100), as the config options always documented.

## Credits and licence

- Original mod: [Stellar View](https://github.com/Povstalec/StellarView) by Povstalec (in-game name Woldericz_Junior). Remastered builds on this work.
- Upstream contributors: Noobly Walker, tehgreatdoge, Erdragh, tristankechlo.
- Remastered: navrelis. Source code: [navrelis/StellarViewRemastered](https://github.com/navrelis/StellarViewRemastered).
- Clouds: look inspired by Better Clouds by Qendolin; Stellar View's renderer is an independent implementation.
- Licence: MIT. The licence text and the copyright notices of both authors are included in the jar and in the repository.

If you like this mod, please look at the original project too and support its author: [CurseForge](https://www.curseforge.com/minecraft/mc-mods/stellarview), [Modrinth](https://modrinth.com/mod/stellarview), [GitHub](https://github.com/Povstalec/StellarView).

---

## File upload: 1.1.0

- File: `StellarViewRemastered-1.21.1-1.1.0-Fabric.jar` (built by `gradlew build` into `build/libs` of the repository folder `F:\Coding\Stellar View Remastered`)
- Display name: Stellar View Remastered 1.1.0 (Fabric 1.21.1)
- Version: 1.1.0
- Release type: Release
- Game version: 1.21.1
- Mod loader: Fabric
- Java: 21
- Environment: Client
- Relations:
  - Fabric API: required
  - Mod Menu: optional

### Changelog

Copy the text between the lines into the changelog field.

---

Stellar View Remastered 1.1.0 adds Stellar View's own cloud renderer: soft, translucent, voluminous clouds in Vanilla style instead of the flat Vanilla clouds.

**Added**

- Clouds built from many overlapping puffs.
- Colours follow the time of day: white by day, warm orange toward the sun at sunrise and sunset with violet on the far side, dark blue at night (brighter at full moon), and a silver lining around the sun.
- Rain and thunder make the clouds grow, merge and darken smoothly, without popping. Lightning flashes light them.
- Far clouds shrink and melt into the fog. Near the camera they fade softly when you fly through them.
- Slow wind drift.
- The Vanilla cloud setting is respected: Off = no clouds, Fast = flat cloud sheets, Fancy = full volume. Works with Fabulous graphics.
- Clouds are generated on a background thread. At default settings that is about 13k puffs, a couple of draw calls and around 0.2 MB of cloud data.
- No telemetry and no network access.
- Settings in the Stellar View config screen (Clouds) and in `config/stellarview-client.toml` (section "Clouds Config"), all applied live: Enabled, Cloud distance (100-350 %, default 200 %), Coverage (0-100 %, default 50 %), Cloud size (50-200 %, default 100 %), Thickness (0-96 blocks, default 40), Height offset (-128 to 256 blocks, default 0), Opacity (10-200 %, default 100 %), Drift speed (0-1000 % of Vanilla, default 100 %), Brightness (50-150 %, default 100 %), Sunset glow (0-150 %, default 100 %).

**Compatibility**

- With an Iris shader pack active, the pack's own clouds are used.
- If the standalone Better Clouds mod is installed, Stellar View leaves the clouds to it.
- Dimensions with their own sky or cloud renderer (Aether, Twilight Forest, Nether, End) are left unchanged.
- Works with Sodium (replaces Sodium's clouds) and honours Sodium Extra's cloud height.

Cloud look inspired by Better Clouds by Qendolin; Stellar View's renderer is an independent implementation.

The 1.0.0 changes (Vanilla Moon Cycle, shader hand-over, bug fixes) are all still included.

---

## Earlier upload: 1.0.0 (already released, kept as history)

- File: `StellarViewRemastered-1.21.1-1.0.0-Fabric.jar`
- Display name: Stellar View Remastered 1.0.0 (Fabric 1.21.1)
- Relations were: Fabric API required, Mod Menu optional, Iris Shaders optional

### Changelog 1.0.0

---

Stellar View Remastered 1.0.0, based on Stellar View 0.5.3. Same mod id and config file as Stellar View: do not install both.

Changed behaviour: meteors are much rarer than in 0.5.3 by default, because the meteor shower and shooting star chance options now work (default 10). Set the two Overworld options to 100 to get the old frequency.

**Added**

- Option "Vanilla Moon Cycle" (Overworld config, `client.overworld.vanilla_moon_cycle`, on by default). The Moon stays opposite the Sun, rises at sunset, sets at sunrise and is not in the daytime sky. Turn it off for the realistic 8-day orbit.
- Option "Disable With Shaders" (General config, `client.disable_with_shaders`, on by default). While an Iris shader pack is active, Stellar View does not draw the sky and the shader pack draws sun, moon and stars. When shaders are switched off, Stellar View's sky is back on the next frame. No restart and no resource reload. Iris is optional.
- New pixel-art icon.

**Fixed**

- Crash ("mesh is null") when loading a world with Static Sky in a far-away dimension such as the End.
- Objects positioned by right ascension and declination were snapped to a grid of whole light years, and the conversion to galactic coordinates used a wrong term. They are now in their correct places (shifts of up to about one degree in the Overworld sky; Alpha Centauri is 4.3 light years away instead of 2.8).
- Fade-out with distance was inverted: objects popped out at the start of the fade range and faded in towards its end.
- Shooting stars changed direction in mid-flight. Each one now keeps one path.
- The rarest meteor type could never appear.
- Stars of constellations inside a star field were drawn twice and looked too bright.
- A moon or planet crossing in front of its parent body could be drawn twice in one frame.
- The Sun and nebulae stayed dimmed after visiting the End until a resource reload.
- The sky swept across the whole jump for one tick after `/time set`, sleeping or a dimension change.
- The non-instanced render path (option "Instancing" off, or older GPUs) showed no stars.
- Static Sky kept the Overworld star sphere after a dimension change.
- The light pollution fade ran at a speed that depended on the frame rate and on how many objects were drawn. It is now time-based.
- Config sliders could not be set with the mouse and jumped to the minimum.
- Meteor shower and shooting star chance options had no effect: a shower happened every day and a shooting star every 1000 ticks, whatever the setting. The chance is now applied as a percentage (default 10: about one day in ten has a meteor shower, and about one in ten 1000-tick periods has a shooting star). Set the two Overworld options to 100 to get the old frequency.
- The config screen rewrote the config file and rebuilt the star buffers on every click, even when nothing changed.
- A config value that is not `true` or `false` was read as `false`. It now falls back to the default with a warning.
- Out-of-range numbers in the config file are clamped instead of causing a crash later.
- GPU buffers leaked on every resource reload (F3+T).
- Blend state leaked into later rendering.
- Mixins are now client-only. The jar no longer tries to patch client classes on a dedicated server.
- The licence text is now inside the jar.

**Changed**

- Less work per frame: layers that are fully transparent or entirely off screen are no longer built and drawn, light level and brightness are worked out once per frame, and temporary objects are reused. The mod's own memory allocation per frame on the render thread went from about 159 KiB to about 39 KiB at night and from about 124 KiB to about 27 KiB by day (large modpack, sky-only view, Java Flight Recorder estimates). The picture is unchanged.
- Smaller download: about 0.6 MB instead of about 1.3 MB.
- Mod Menu is now optional. It is recommended for the in-game config screen. The config file `config/stellarview-client.toml` can also be edited by hand.

---

## Checklist before publishing

- Create the CurseForge project (name, summary, categories, licence MIT, source URL).
- Paste the description.
- Upload the logo `logo/logo_512.png`.
- Upload `StellarViewRemastered-1.21.1-1.1.0-Fabric.jar` with the settings and changelog of the 1.1.0 block above.
- Enable Issues in the GitHub repository settings, or clear the issues URL field.
- Optional: tell the original author (Povstalec) about the fork. The MIT licence allows the fork as long as the licence and copyright notice stay with it. The jar and the repository both contain them.
