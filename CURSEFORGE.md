# CurseForge page draft: Stellar View Remastered

Note for the owner: this file is a draft for the CurseForge project page and the first file upload. Nothing has been uploaded or published. Copy the parts you need by hand: the "Description" section goes into the project description, the changelog goes into the file upload form.

## Project name

Stellar View Remastered

## Summary

Fork of Stellar View: a sky of individual stars, a Vanilla-style Moon cycle and automatic Iris shader support.

<!-- 110 characters -->

Alternatives:

- Maintained fork of Stellar View with a Vanilla-style Moon cycle, Iris shader hand-over and bug fixes.

  <!-- 101 characters -->

- Stellar View, maintained: Milky Way night sky, Moon that follows the Sun, and a sky that steps aside for shaders.

  <!-- 113 characters -->

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

## What is different in Remastered

Compared with Stellar View 0.5.3, version 1.0.0 changes the following.

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
- Optional: Mod Menu (config screen), Iris with Sodium (shader packs), Enhanced Celestials, Lunar

Remastered uses the same mod id (`stellarview`) and the same config file as Stellar View. Existing configs, resource packs and add-ons keep working. For the same reason, do not install it together with the original Stellar View. Use one or the other.

## For resource pack and mod authors

The documentation for resource pack authors is maintained for the original mod: [Stellar View documentation](https://moddedmc.wiki/cs/project/stellarview/docs). Resource packs that set a meteor `probability` are now read as a percentage (0-100), as the config options always documented.

## Credits and licence

- Original mod: [Stellar View](https://github.com/Povstalec/StellarView) by Povstalec (in-game name Woldericz_Junior). Remastered builds on this work.
- Upstream contributors: Noobly Walker, tehgreatdoge, Erdragh, tristankechlo.
- Remastered: navrelis. Source code: [navrelis/StellarViewRemastered](https://github.com/navrelis/StellarViewRemastered).
- Licence: MIT. The licence text and the copyright notices of both authors are included in the jar and in the repository.

If you like this mod, please look at the original project too and support its author: [CurseForge](https://www.curseforge.com/minecraft/mc-mods/stellarview), [Modrinth](https://modrinth.com/mod/stellarview), [GitHub](https://github.com/Povstalec/StellarView).

---

## File upload: 1.0.0

- File: `StellarViewRemastered-1.21.1-1.0.0-Fabric.jar` (built by `gradlew build` into `build/libs` of the repository folder `F:\Coding\Stellar View Remastered`)
- Display name: Stellar View Remastered 1.0.0 (Fabric 1.21.1)
- Release type: Release (suggested)
- Game version: 1.21.1
- Mod loader: Fabric
- Java: 21
- Environment: client
- Relations:
  - Fabric API: required
  - Mod Menu: optional
  - Iris Shaders: optional

### Changelog

Copy the text between the lines into the changelog field.

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
- Upload `StellarViewRemastered-1.21.1-1.0.0-Fabric.jar` with the settings and changelog above.
- Enable Issues in the GitHub repository settings, or clear the issues URL field.
- Optional: tell the original author (Povstalec) about the fork. The MIT licence allows the fork as long as the licence and copyright notice stay with it. The jar and the repository both contain them.
