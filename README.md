# Stellar View Remastered

Stellar View Remastered is a maintained fork of [Stellar View](https://github.com/Povstalec/StellarView) by Povstalec, a Fabric mod that improves the night sky while keeping the Vanilla feeling. The original mod is also available on [CurseForge](https://www.curseforge.com/minecraft/mc-mods/stellarview) and [Modrinth](https://modrinth.com/mod/stellarview). This fork is released under the MIT licence, like the original.

## What Remastered changes

- Vanilla-like moon cycle (option, on by default): the Moon stays opposite the Sun and no longer shows in the daytime sky
- Steps aside for Iris shader packs: while a shader pack is active the shader draws the sky; Stellar View comes back when shaders are turned off, without a restart (option, on by default)
- Bug fixes
- Less work per frame

## Features

- Milky Way Galaxy
- Brighter Stars
- Independent Moon movement
- Light interference (Stars appear dimmer if you're standing near a light source)
- Location-based Sky rotation
- Supernovae
- Shooting Stars
- Meteor Showers
- Custom Skybox support
- Planets and apparent retrograde planetary motion
- Can be used as a dependency for other mods

The mod is purely client-side and does not need to be installed on a server. It does not change anything on the server; all changes are visual.

## Requirements

- Minecraft 1.21.1
- Fabric Loader 0.16.14 or newer
- Fabric API
- Mod Menu is recommended for the config screen
- Client-side only

## Building

This mod uses the [Fabric](https://fabricmc.net/) modding API. Build it with:

```
gradlew build
```

The jar is written to `build/libs`.

## Credits and licence

- Original mod: [Stellar View](https://github.com/Povstalec/StellarView) by Povstalec (Woldericz_Junior). Upstream contributors: Noobly Walker, tehgreatdoge, Erdragh, tristankechlo.
- Remastered: navrelis. Source and issue tracker: [navrelis/StellarViewRemastered](https://github.com/navrelis/StellarViewRemastered).
- Documentation of the original mod: [moddedmc.wiki](https://moddedmc.wiki/cs/project/stellarview/docs).
- Licensed under the MIT licence, see `LICENSE.txt`. Copyright (c) 2024 Povstalec, Copyright (c) 2026 navrelis.
