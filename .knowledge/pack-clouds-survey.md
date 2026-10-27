# Cloud renderer survey: vanilla 1.21.1 and the Nytheria pack

Scope: Minecraft 1.21.1, Mojang names with intermediary in brackets. Pack instance `C:\Users\nikol\curseforge\minecraft\Instances\NytheriaDevelopment` (402 top-level jars, 983 jars counting Jar-in-Jar). Read-only.
Clean-room: `better-clouds-1.13.11+1.21.1-fabric.jar` is present in `mods\`. It was never opened, unzipped or scanned (the scanner skips it by file name). Its config file `config\betterclouds-v1.json` (9205 bytes) exists and was NOT read either. `options.txt` lists `betterclouds` in `incompatibleResourcePacks` (a plain string in the pack's options, nothing to do with the jar contents).

Legend: **READ** = read from source or bytecode. **INFERRED** = deduced, not observed at runtime.

Sources used: Mojang-mapped Minecraft sources jar from the Loom cache (hash 2198 = officialMojangMappings) extracted to `scratchpad\survey\mcsrc`; `mappings.tiny` (intermediary -> named) from the same Loom cache; mod classes read with `javap` and my own read-only class-file parser (`scratchpad\survey\tools\mixscan.py`). No code from any jar was executed.

---------------------------------------------------------------------------------------------------

## 1. Vanilla 1.21.1 cloud rendering

### 1.1 Names (Mojang -> intermediary), all READ from `mappings.tiny`

| Mojang | Intermediary |
|---|---|
| `LevelRenderer` | `net/minecraft/class_761` |
| `LevelRenderer.renderClouds(PoseStack, Matrix4f, Matrix4f, float, double, double, double)V` | `method_3259`, desc `(Lnet/minecraft/class_4587;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FDDD)V` |
| `LevelRenderer.buildClouds(Tesselator,double,double,double,Vec3)MeshData` (private) | `method_3239` |
| `LevelRenderer.renderSky(Matrix4f,Matrix4f,float,Camera,boolean,Runnable)V` | `method_3257` |
| `LevelRenderer.renderLevel(DeltaTracker,boolean,Camera,GameRenderer,LightTexture,Matrix4f,Matrix4f)V` | `method_22710` |
| `LevelRenderer.renderSnowAndRain(LightTexture,float,double,double,double)V` / `tickRain(Camera)V` | `method_22714` / `method_22713` |
| `LevelRenderer.renderWorldBorder(Camera)` / `allChanged()` | `method_3243` / `method_3279` |
| `LevelRenderer.getCloudsTarget()` / `getWeatherTarget()` | `method_29364` / `method_29363` |
| fields `cloudBuffer`, `generateClouds`, `prevCloudX/Y/Z`, `prevCloudColor`, `prevCloudsType`, `ticks`, `cloudsTarget`, `weatherTarget`, `level`, `minecraft` | `field_4094`, `field_4107`, `field_4082/4097/4116`, `field_4072`, `field_4080`, `field_4073`, `field_25278`, `field_25277`, `field_4085`, `field_4088` |
| `LevelRenderer.CLOUDS_LOCATION` (protected static) | `field_4108` |
| `ClientLevel` / `getCloudColor(F)Vec3` / `getSkyColor(Vec3,F)Vec3` / `effects()` | `class_638` / `method_23785` / `method_23777` / `method_28103` |
| `DimensionSpecialEffects` / `getCloudHeight()F` / field `cloudLevel` / `isFoggyAt(II)Z` / `getSunriseColor(FF)[F` / `forType(DimensionType)` | `class_5294` / `method_28108` / `field_24611` / `method_28110` / `method_28109` / `method_28111` |
| `DimensionSpecialEffects$OverworldEffects`, `$SkyType` | `class_5294$class_5297`, `class_5294$class_5401` |
| `Options` / `getCloudsType()CloudStatus` / `cloudStatus()` / field `cloudStatus` / field `renderDistance` / `getEffectiveRenderDistance()I` | `class_315` / `method_1632` / `method_42528` / `field_1814` / `field_1870` / `method_38521` |
| `CloudStatus` OFF / FAST / FANCY | `class_4063` `field_18162` / `field_18163` / `field_18164` |
| `FogRenderer` / `setupColor` / `setupFog` / `levelFogColor` / `setupNoFog` | `class_758` / `method_3210` / `method_3211` / `method_3212` / `method_23792` |
| `FogRenderer$FogMode` FOG_SKY / FOG_TERRAIN | `class_758$class_4596` `field_20945` / `field_20946` |
| `GameRenderer` / `renderLevel(DeltaTracker)` / `getProjectionMatrix(D)` / `getDepthFar()` / `getRenderDistance()` / field `renderDistance` / `getRendertypeCloudsShader()` / `getMainCamera()` | `class_757` / `method_3188` / `method_22973` / `method_32796` / `method_3193` / `field_4025` / `method_56846` / `method_19418` |
| `RenderType.clouds()` / `cloudsDepthOnly()` | `class_1921` `method_56849` / `method_56850` |
| `RenderStateShard.CLOUDS_TARGET` / `WEATHER_TARGET` | `class_4668` `field_25283` / `field_25282` |
| `Minecraft.useShaderTransparency()` / `getMainRenderTarget()` / `useFancyGraphics()` | `class_310` `method_29611` / `method_1522` / `method_1517` |
| `Level.getRainLevel(F)` / `getThunderLevel(F)` | `class_1937` `method_8430` / `method_8478` |
| `PoseStack`, `VertexBuffer`, `RenderTarget`, `Camera`, `DeltaTracker`, `LightTexture`, `Tesselator`, `MeshData` | `class_4587`, `class_291`, `class_276`, `class_4184`, `class_9779`, `class_765`, `class_289`, `class_9801` |

### 1.2 `renderClouds` body (READ, `LevelRenderer.java:1587-1653`)

Signature: `public void renderClouds(PoseStack poseStack, Matrix4f viewRot, Matrix4f projection, float partialTick, double camX, double camY, double camZ)`. The parameter names above are mine; the decompiled names are `matrix4f`, `matrix4f2`, `f`, `d`, `e`, `g`.

1. `h = this.level.effects().getCloudHeight()`. If `Float.isNaN(h)` the method does nothing (Nether and End have NaN).
2. Constants: cell width 12 blocks, cell height 4 blocks, drift `l = (ticks + partialTick) * 0.03F` blocks.
3. `m = (camX + l) / 12`, `n = h - camY + 0.33` (cloud base relative to the camera), `o = camZ / 12 + 0.33`. `m` and `o` are reduced modulo 2048 cells. Fractions: `p = frac(m)`, `q = frac(n/4)*4`, `r = frac(o)`.
4. `vec3 = this.level.getCloudColor(partialTick)`.
5. Integer cell key `s = floor(m)`, `t = floor(n/4)`, `u = floor(o)`.
6. **Mesh cache**: `cloudBuffer` (a static `VertexBuffer`) is rebuilt when `generateClouds` is true. `generateClouds` becomes true when any of these change: `s`, `t`, `u`, `options.getCloudsType()` vs `prevCloudsType`, or `prevCloudColor.distanceToSqr(vec3) > 2.0E-4`. It is also set true in `allChanged()` (`LevelRenderer.java:688`, fires on resource reload, level change, render distance or graphics change). Rebuild = close old buffer, `new VertexBuffer(STATIC)`, `upload(buildClouds(Tesselator.getInstance(), m, n, o, vec3))`.
7. `FogRenderer.levelFogColor()` (sets the fog colour uniform from the static `fogRed/Green/Blue`).
8. `poseStack.pushPose(); mulPose(viewRot); scale(12, 1, 12); translate(-p, q, -r)`.
9. Draw loop: `for w = (prevCloudsType == FANCY ? 0 : 1); w < 2; w++`. Pass 0 = `RenderType.cloudsDepthOnly()` (depth write only, colour mask off), pass 1 = `RenderType.clouds()` (colour+depth write). Each pass: `renderType.setupRenderState(); cloudBuffer.drawWithShader(poseStack.last().pose(), projection, RenderSystem.getShader()); renderType.clearRenderState();`. So FANCY draws the same buffer twice (depth pre-pass), FAST once.
10. `poseStack.popPose()`.

`buildClouds` (`LevelRenderer.java:1655-1832`), vertex format `POSITION_TEX_COLOR_NORMAL`, mode QUADS:
- UV = `(x * 0.00390625) + floor(m)*0.00390625` etc., i.e. 1/256 per texel. One texel = one cell = 12x4x12 blocks after the 12x scale.
- Colours: top face = cloud colour, bottom = 0.7x, +-X sides = 0.9x, +-Z sides = 0.8x, alpha always 0.8.
- FANCY: 8x8 tiles, `aa,ab` in [-3, 4], each tile 8 texels, so x in [-24, +32) texels = [-288, +384) blocks around the camera cell. Top face emitted if `z <= 5`, bottom if `z > -5` (z = base height in cell units). Side faces are 8 strips per tile with UV sampled at the strip centre (`+0.5`).
- FAST: 2x2 quads of 32 texels (x,z in [-32,32)) = +-384 blocks, only the bottom plane, normal (0,-1,0).
- No occupancy test in the mesh; shape comes from the texture alpha in the fragment shader (`discard` when `a < 0.1`).

### 1.3 Shader, texture, render state (READ)

- Shader: core program `rendertype_clouds` (`assets/minecraft/shaders/core/rendertype_clouds.{json,vsh,fsh}`), obtained via `GameRenderer::getRendertypeCloudsShader` (`method_56846`). Uniforms: `ModelViewMat`, `ProjMat`, `ColorModulator`, `FogStart`, `FogEnd`, `FogColor`, `FogShape`, sampler `Sampler0`. Fragment: `color = texture(Sampler0, uv) * vertexColor * ColorModulator; if (color.a < 0.1) discard; fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor)`. `linear_fog` blends RGB only toward the fog colour and keeps the alpha.
- Texture: `textures/environment/clouds.png`, via `RenderStateShard.TextureStateShard(LevelRenderer.CLOUDS_LOCATION, blur=false, mipmap=false)`. The `TextureManager` loads it lazily as a `SimpleTexture` (resource pack overridable). The vanilla file is 256x256, 1-bit palette PNG (4757 bytes). The shader relies on GL_REPEAT wrapping (UVs exceed 1).
- `RenderType.CLOUDS` / `CLOUDS_DEPTH_ONLY` (`createClouds(bool)`): shader `RENDERTYPE_CLOUDS_SHADER`, `TRANSLUCENT_TRANSPARENCY` (blend SRC_ALPHA/ONE_MINUS_SRC_ALPHA, ONE/ONE_MINUS_SRC_ALPHA), `NO_CULL`, write mask `DEPTH_WRITE` (depth-only) or `COLOR_DEPTH_WRITE`, output `CLOUDS_TARGET`, default depth test LEQUAL.
- **Render target**: `CLOUDS_TARGET` binds `levelRenderer.getCloudsTarget()` only if `Minecraft.useShaderTransparency()` (graphics mode id >= 2 = FABULOUS and not panorama), otherwise nothing is bound (main framebuffer). On clear it rebinds the main target. In Fabulous, `renderLevel` calls `cloudsTarget.clear()` right before `renderClouds`, and `Options.cloudStatus` change callback also clears it. The `clouds` target is composited by `assets/minecraft/shaders/post/transparency.json` (targets: water, translucent, itemEntity, particles, clouds, weather, final; clouds are sampled with `CloudsSampler` / `CloudsDepthSampler`).
- **State left behind** (INFERRED from the shard setup/clear code): blend disabled, cull enabled, depth mask and colour mask true, depth func LEQUAL, main render target bound, vertex buffer unbound, `RenderSystem.getShader()` still `rendertype_clouds`, clouds.png still bound on unit 0, fog uniforms unchanged except the fog colour reset by `levelFogColor()`. No `RenderSystem.setShaderColor` change.

### 1.4 Colour, height, fog sources (READ)

- Cloud height: `level.effects().getCloudHeight()` = `DimensionSpecialEffects.cloudLevel`. `OverworldEffects` = 192.0F (`CLOUD_LEVEL`), `NetherEffects` and `EndEffects` = `Float.NaN`. `effects()` is `DimensionSpecialEffects.forType(dimensionType)` = `EFFECTS.get(dimensionType.effectsLocation())`; custom dimensions with `effects: minecraft:overworld` therefore get clouds at 192.
- Cloud colour: `ClientLevel.getCloudColor(partialTick)`: start (1,1,1); `h = clamp(cos(timeOfDay*2pi)*2 + 0.5, 0, 1)`; rain: mix toward grey `(0.3r+0.59g+0.11b)*0.6` by `rainLevel*0.95`; then `r,g *= h*0.9+0.1`, `b *= h*0.85+0.15`; thunder: mix toward grey*0.2 by `thunderLevel*0.95`. No biome tint.
- Fog: `renderLevel` calls `FogRenderer.setupFog(camera, FOG_TERRAIN, max(renderDistanceBlocks, 32), isFoggy, partial)` at line 917 (before terrain); `renderDistanceBlocks = GameRenderer.getRenderDistance() = options.getEffectiveRenderDistance()*16` (`GameRenderer.java: this.renderDistance = ...*16`). In the non-special case FOG_TERRAIN gives `start = d - clamp(d/10, 4, 64)`, `end = d`, shape CYLINDER. Nothing in `renderClouds` touches fog start/end/shape; the clouds are drawn with whatever the fog uniforms are at that time (including all later mod tweaks, see section 6) and only `levelFogColor()` is re-applied.
- `Options.getCloudsType()` = `getEffectiveRenderDistance() >= 4 ? cloudStatus.get() : OFF`. `CloudStatus`: OFF(0,"false"), FAST(1,"fast"), FANCY(2,"true"). options.txt key `renderClouds`; legacy booleans decode to FANCY/OFF. Default FANCY.

### 1.5 Where `renderClouds` is called in `renderLevel` (READ, `LevelRenderer.java:874-1161`)

Order: lightmap/culling -> `FogRenderer.setupColor` + `levelFogColor` + `RenderSystem.clear` (l.908-910) -> **sky** `renderSky(viewRot, projection, partial, camera, foggy, fogRunnable)` (l.913-915, FOG_SKY fog via the runnable) -> `setupFog(FOG_TERRAIN)` (l.917) -> `setupRender`, `compileSections` -> terrain `solid`, `cutoutMipped`, `cutout` (l.923-925) -> entities (itemEntity target in Fabulous), block entities -> destroy progress, hit outline, debug -> **translucent**: Fabulous: `translucentTarget` gets translucent + tripwire layers, `particlesTarget` gets particles; Fancy/Fast: translucent layer, `bufferSource.endBatch(lines)`, tripwire, particles -> **clouds** (l.1129-1136):

```java
if (this.minecraft.options.getCloudsType() != CloudStatus.OFF) {
    if (this.transparencyChain != null) this.cloudsTarget.clear(Minecraft.ON_OSX);
    profilerFiller.popPush("clouds");
    this.renderClouds(poseStack, matrix4f, matrix4f2, f, d, e, g);
}
```

-> **weather** (l.1138-1152): Fabulous: `WEATHER_TARGET` bound, `renderSnowAndRain`, `renderWorldBorder`, `transparencyChain.process(...)`, main target rebound; otherwise `depthMask(false)`, `renderSnowAndRain`, `renderWorldBorder`, `depthMask(true)` -> `renderDebug` -> `FogRenderer.setupNoFog()`.

Matrices: `GameRenderer.renderLevel` builds `projection = getProjectionMatrix(fov)` (perspective, near 0.05, far `getDepthFar() = renderDistanceBlocks*4`; hurt/bob/nausea are multiplied into this projection) and `viewRot = new Matrix4f().rotation(camera.rotation().conjugate())` (rotation only, no translation, no bobbing). It calls `levelRenderer.renderLevel(deltaTracker, ..., camera, this, lightTexture, viewRot, projection)`. Inside `LevelRenderer.renderLevel` the params are named `matrix4f` (= viewRot) and `matrix4f2` (= projection) and passed unchanged to `renderClouds`. The `PoseStack` passed is the fresh identity `new PoseStack()` made at l.955. `renderClouds` never uses `RenderSystem.getModelViewStack()` (the stack that `renderLevel` pushed with `viewRot`), it passes its own model-view straight to `drawWithShader`. `d,e,g` = camera world position.

Callers of `Options.getCloudsType()` that matter (all go through the getter, so a mixin on it, like Iris', affects all): `renderLevel` l.1129, `renderClouds` l.1609/1615, Sodium's `CloudRenderer`, Fabric API's `WorldRendererMixin.beforeClouds` (INVOKE target), Dynamic FPS / BetterF3 (config only).

---------------------------------------------------------------------------------------------------

## 2. Sodium 0.8.13+mc1.21.1 (`sodium-fabric-0.8.13+mc1.21.1.jar`)

**Yes, it replaces vanilla cloud rendering** (READ).

- Mixin class: `net.caffeinemc.mods.sodium.mixin.features.render.world.clouds.LevelRendererMixin` (listed in `sodium-common.mixins.json`), `@Mixin(value = LevelRenderer.class)`, **no priority specified (default 1000)**, config `defaultRequire = 1`.
- Injection: `@Inject(method = "renderClouds" [method_3259], at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/DimensionSpecialEffects;getCloudHeight()F"), cancellable = true)` -> handler `renderClouds(PoseStack, Matrix4f, Matrix4f, float, double, double, double, CallbackInfo)`. The target is the first call in vanilla `renderClouds`, so effectively "just after method entry". The handler immediately calls `ci.cancel()`, lazily creates a `CloudRenderer(Minecraft.getResourceManager())`, then `poseStack.pushPose(); poseStack.mulPose(viewRot);` and calls `CloudRenderer.render(camera = gameRenderer.getMainCamera(), level, projection, poseStack, (float) levelRenderer.ticks, partialTick)`, `popPose()`.
- Two more injects in the same class: `RETURN` of `onResourceManagerReload` (`method_14491`) -> `CloudRenderer.reload(ResourceManager)` and `RETURN` of `allChanged` (`method_3279`) -> reload, `RETURN` of `close` -> `destroy()`.
- `CloudRenderer` (`net.caffeinemc.mods.sodium.client.render.immediate.CloudRenderer`, READ from bytecode): reads `effects.getCloudHeight()` (NaN check), `Options.getCloudsType()`, `ClientLevel.getCloudColor`, camera position, ticks; its own shader program `clouds` (ships `assets/minecraft/shaders/core/clouds.{json,vsh,fsh}` in the Sodium jar, ids `minecraft:clouds`), its own copy of `textures/environment/clouds.png` read through the resource manager as `NativeImage` (`loadTextureData`), geometry built per visible face with cell culling by view orientation; radius `getCloudRenderDistance() = max(32, options.getEffectiveRenderDistance()*2 + 9)` cells (12 blocks each). It calls `FogRenderer.setupFog(camera, FOG_TERRAIN, ...)` itself before drawing and restores `RenderSystem` fog start/end/shape afterwards. It binds `levelRenderer.getCloudsTarget()` when `Minecraft.useShaderTransparency()` and restores the main target afterwards; sets `RenderSystem.setShaderColor(cloudR, cloudG, cloudB, 0.8F)`, blend SRC_ALPHA/ONE_MINUS_SRC_ALPHA + ONE/ONE_MINUS_SRC_ALPHA, depth test enabled with its own `depthFunc`, disables cull, draws, then disables blend, enables cull, restores colour and target.
- Other Sodium mixins that touch the same area (READ):
  - `features.render.world.sky.LevelRendererMixin`: `renderSky` HEAD cancellable (`preRenderSky`). `features.render.world.sky.FogRendererMixin` (redirect `BiomeColors/CubicSampler.gaussianSampleVec3` in `setupColor`), `features.render.world.sky.ClientLevelMixin` (same redirect in `getSkyColor`). **All three are force-disabled by Iris** at startup: `latest.log` line `Force-disabling mixin 'features.render.world.sky.*' as rule 'mixin.features.render.world.sky' (added by mods [iris])`; Iris does this through `fabric.mod.json` `custom."sodium:options": {"mixin.features.render.world.sky": false}`. The cloud rule is NOT disabled in that log.
  - `features.options.weather.LevelRendererMixin`: `@Redirect` in `renderSnowAndRain` of `Minecraft.useFancyGraphics()` (weather_quality option).
  - `features.render.compositing.RenderTargetMixin`: HEAD cancellable on `RenderTarget.blitToScreen` (not cloud related).
  - `core.render.world.LevelRendererMixin`: `@Overwrite` of `renderSectionLayer`, `setupRender` and others, `@Inject` at `renderLevel` FIELD `globalBlockEntities`, `@Redirect` in `allChanged`. Terrain only.
- **Sodium cloud-related options**: the video setting `sodium:quality.clouds` (tooltip key `sodium.options.clouds_quality.tooltip`) is bound to the vanilla option `Options.cloudStatus` (`options.renderClouds`; values Fast/Fancy). There is no cloud key in `sodium-options.json`. The only switch is the mixin rule `features.render.world.clouds` (disable with `mixin.features.render.world.clouds=false` in `config/sodium-mixins.properties`, or from a mod via `fabric.mod.json` `custom."sodium:options"` like Iris does for sky; the naming of the rule was confirmed by the log line above for the sky rule, the clouds rule name is READ from `MixinConfig`: `features.render.world.clouds`).
- **What a mod needs to do to draw its own clouds**: a `@Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)` that calls `ci.cancel()` is enough. HEAD is before the `getCloudHeight()` INVOKE, so Sodium's handler is never reached, no matter which priority. Priority only decides the order against other HEAD handlers (Fabric API 1000, Porting Lib 951, Sporran 1050, Sodium Extra 1000, see section 6), not against Sodium. Alternatives: disable Sodium's rule (affects every user, not recommended) or `@WrapMethod`. Also keep Sodium's duties in mind: it re-reads the clouds texture on reload, honours Sodium Extra's `cloud_distance` (below) and fog.
- (INFERRED) With a shader pack active, Sodium's `CloudRenderer` still uses its own `clouds` ShaderInstance; no Iris class references `CloudRenderer` (grep over the Iris jar) and Iris' only Sodium cloud symbol is the unused enum entry `ShaderKey.CLOUDS_SODIUM`. Verify in game if it matters.

---------------------------------------------------------------------------------------------------

## 3. Sodium Extra 0.9.4+mc1.21.1 (READ)

Config file `config/sodium-extra-options.json` (`extra_settings` and `render_settings.fog_settings`). Pack values: `cloud_height_override=false`, `cloud_height=192`, `cloud_distance=100`, `fog_settings.advanced=false`, `atmospheric.distance_chunks=0`, `start_percent=100`, `shape_mode="VANILLA"`, `cloud_fog_percent=100`, `detail_settings.sky/sun/moon/stars/rain_snow/sky_colors = true`, `panini_projection=false`.

Cloud options and how they are applied (config `sodium-extra.mixins.json`):

| Option | Mechanism |
|---|---|
| `cloud_height_override` + `cloud_height` (int blocks) | `cloud.MixinDimensionEffectsOverworld` is `@Mixin(DimensionSpecialEffects$OverworldEffects.class)` and **overrides `getCloudHeight()` (`method_28108`)** to return `cloudHeight` when the override is on, else `super`. Works for the overworld effects only. Anything that reads `effects().getCloudHeight()` sees it, including Sodium's `CloudRenderer`. |
| `cloud_distance` (percent) | `sodium.cloud.MixinCloudRenderer` is `@Mixin(CloudRenderer.class)` (Sodium's class), `@Redirect(method = "getCloudRenderDistance", at = INVOKE Options.getEffectiveRenderDistance())` returning `renderDistance * cloudDistance / 100`. Only affects Sodium's renderer, so it is lost when Sodium's renderer is cancelled. |
| `fog_settings.atmospheric.cloud_fog_percent` | `fog.MixinLevelRenderer` : `@Inject(method = "renderClouds", at = HEAD)` (default priority, not cancelling) -> `setupCloudFog` calls `FogOverrideState.whileSettingUpCloudFog(() -> FogRenderer.setupFog(camera, FOG_TERRAIN, max(gameRenderer.getRenderDistance(), 32), isFoggyAt(...) || bossFog, partial))`. While that flag is set, `fog.MixinFogRenderer` (`@Inject setupFog TAIL`, `sodiumExtra$applyFog`) calls `sodiumExtra$applyCloudFog` and sets fog end from `FogDistanceHelper.getCloudEnd(percent) = cloudRenderDistance * 16 * clamp(percent,0,100) / 100`. This leaves the fog uniforms set for the cloud draw. |
| fog shape / render distance shape | `fog.MixinShaderInstance` (`sodiumExtra$decodeFogStart/End`) hooks **every** `ShaderInstance` that uploads FogStart/FogEnd and decodes an encoded cylinder shape. Inactive while `shape_mode = VANILLA` (the pack's value). A custom cloud ShaderInstance gets the same treatment. |
| sky detail toggles | `sky.MixinLevelRenderer`, `sun_moon.MixinLevelRenderer`, `stars.MixinLevelRenderer`, `rain_snow` via `particle`/`renderWeather` HEAD cancellable on `renderSnowAndRain` (`method_22714`), `sky_colors.MixinBiome`. No cloud on/off option. |

---------------------------------------------------------------------------------------------------

## 4. Iris 1.8.14-beta.1+mc1.21.1 (READ)

**No shader pack in use** (pack `enableShaders=false` currently, `config/iris.properties`: `shaderPack=ComplementaryUnbound_r5.9.3 + EuphoriaPatches_1.10.5`, `enableShaders=false`, `maxShadowRenderDistance=24`, `colorSpace=SRGB`): clouds are vanilla/Sodium. Iris still hooks the call site: `MixinLevelRenderer` (`mixins.iris.json`, default priority) has `iris$beginClouds` / `iris$endClouds` as `@Inject` at the INVOKE of `renderClouds` inside `renderLevel`, setting `WorldRenderingPipeline.setPhase(CLOUDS)` and back to `NONE`. These are at the **call site in `renderLevel`**, outside `renderClouds`, so a HEAD cancel inside `renderClouds` does not skip them.

**Iris does not cancel or replace `renderClouds`.** It intervenes in three other places:

1. `mixin.sky.MixinOptions_CloudsOverride`: `@Mixin(Options.class, priority = 1010)`, `@Inject(method = "getCloudsType", at = HEAD, cancellable = true)` -> `iris$overrideCloudsType`. If `options.renderDistance >= 4` and a pipeline exists, it returns the shader pack's `CloudSetting` (`clouds=` key in `shaders.properties`): `off` -> `CloudStatus.OFF`, `fast` -> FAST, `fancy` -> FANCY, `default` -> leaves the user's option alone. With `clouds=off` the whole `if (getCloudsType() != OFF)` block in `renderLevel` is skipped, **`renderClouds` is never called**, so neither Sodium's nor any HEAD mixin on `renderClouds` runs. `ShaderProperties` parses the keys `clouds` and `dhClouds` (enum `CloudSetting`: DEFAULT, FAST, FANCY, OFF).
2. `mixin.MixinGameRenderer`: `@Inject(method = "getRendertypeCloudsShader" [method_56846], at = HEAD, cancellable = true)`. When shaders are active it returns the pack's program for `ShaderKey.CLOUDS` (program `gbuffers_clouds`, vertex format `DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL`, alpha test ONE_TENTH_ALPHA, fog per-vertex) or `ShaderKey.SHADOW_CLOUDS` while `ShadowRenderer.ACTIVE`. This only matters for code that draws with the vanilla `RenderType.clouds()` / `GameRenderer.getRendertypeCloudsShader()`. Sky and star draws are overridden in the same class (`iris$overridePositionShader` for `getPositionShader` -> `SKY_BASIC`).
3. `MixinLevelRenderer` (`mixins.iris.json`) around `renderSky` (`iris$beginSky`, `iris$endSky`, `iris$renderTerrainShadows` at INVOKE `renderSky`), `renderSnowAndRain` (`iris$beginWeather`, `iris$endWeather`, `WrapWithCondition iris$disableWeather`), `renderWorldBorder`. `MixinLevelRenderer_Sky.preRenderSky` is `renderSky` HEAD cancellable. `MixinDisableFabulousGraphics` turns Fabulous off on resource reload / `allChanged` (so `cloudsTarget` is unused while a pack runs). `MixinTweakFarPlane` (see section 6).

**Shadow pass**: Iris' shadow pass draws terrain/entities only; no Iris class calls `renderClouds` other than the INVOKE-target references in `MixinLevelRenderer` (INFERRED: clouds are not drawn into the shadow map; `SHADOW_CLOUDS` exists only for the shader-override path).

**Public API** (`net.irisshaders.iris.api.v0.IrisApi`, interface, READ): `static IrisApi getInstance()`, `int getMinorApiRevision()`, **`boolean isShaderPackInUse()`**, **`boolean isRenderingShadowPass()`**, `Object openMainIrisScreenObj(Object)`, `String getMainScreenLanguageKey()`, `IrisApiConfig getConfig()` (`areShadersEnabled()`, `setShadersEnabledAndApply(boolean)`), `createTextVertexSink(...)`, `float getSunPathRotation()`. Internal non-API equivalents: `Iris.getCurrentPack()` (Optional), `ShadowRenderer.ACTIVE`. Stellar View's existing MethodHandle code (`IrisCompatibility`) binds `getInstance()` once and calls `isShaderPackInUse()` virtual, which matches this interface exactly. `isRenderingShadowPass()` would be bound the same way (no argument, boolean result).

Other Iris hooks to be aware of: `MixinRenderType` redirects `RenderType.setupRenderState/clearRenderState`; `MixinVertexBuffer` (`bindHelper`/`unbindHelper`); `MixinShaderInstance` (name handling); `MixinRenderTarget` (`iris$onBindFramebuffer`); Iris'`MixinGameRenderer` for the world-pass hooks (HEAD of `GameRenderer.renderLevel`).

---------------------------------------------------------------------------------------------------

## 5. Shader packs in `shaderpacks\` (READ from `shaders.properties`, `dimension.properties`, option txt, `gbuffers_clouds`)

Current pack option files (`shaderpacks\*.txt`):
- `ComplementaryUnbound_r5.9.3 + EuphoriaPatches_1.10.5.txt`: `SUN_ANGLE=0`, `SUN_MOON_STYLE_DEFINE=1`, Euphoria version markers. No cloud option changed.
- `ComplementaryUnbound_r5.9.3.zip.txt`: `SUN_MOON_STYLE_DEFINE=1`, `SUN_ANGLE=0`.
- `BSL_v10.1.1.zip.txt`: `sunPathRotation=0.0`. `Derivative Main d24.4.4.zip.txt`: `sunPathRotation=0.0`.
All cloud options are therefore at the pack defaults.

| Pack | Vanilla clouds? | Evidence |
|---|---|---|
| Complementary Unbound r5.9.3 (zip) and with Euphoria Patches 1.10.5 (folder) | Default **off**. `shaders.properties`: `#if CLOUD_STYLE_DEFINE != 50` -> `clouds=off`, plus `dhClouds = off`, `alphaTest.gbuffers_clouds=GREATER 0.0001`. Default `CLOUD_STYLE_DEFINE = -1` (`[-1 0 1 3 50]`), mapped to the pack's own style (Unbound: `CLOUD_STYLE_DEFAULT 3`, volumetric), drawn in the composite programs. Only style **50 = "Vanilla"** leaves `clouds` unset and uses `shaders/program/gbuffers_clouds.glsl`; in all other styles that program just `discard`s. Cloud altitude `CLOUD_ALT1` default 192. The Euphoria folder has the same lines plus `sun=false`, `moon=false`. | `cu\shaders__shaders.properties`, `lib/common.glsl`, `program/gbuffers_clouds.glsl` |
| BSL v10.1.1 | No `clouds=` key (DEFAULT). Uses option `CLOUDS` (`lib/settings.glsl`: `#define CLOUDS 2 //[0 1 2 3]`; 0 off, 1 skybox, 2 volumetric, 3 vanilla). `gbuffers_clouds.glsl` does `#if CLOUDS != 3 discard;` so vanilla clouds are drawn **only at CLOUDS=3** (pack text: "requires vanilla cloud setting set to default or on"). Default 2 = pack-drawn volumetric clouds, vanilla cloud geometry discarded, but `renderClouds` IS still called (Iris override DEFAULT). `alphaTest.gbuffers_clouds=GREATER 0.005`. `FOG_VANILLA_CLOUD` option (fog on vanilla clouds). | `bsl\shaders__shaders.properties`, `gbuffers_clouds.glsl` |
| Derivative Main d24.4.4 | `clouds = off` unconditionally, no `gbuffers_clouds` program in the zip, own planar + volumetric clouds. `sun = false`. | `deriv\shaders__shaders.properties` |

Iris is currently set to `enableShaders=false`, so none of them is active right now. With Complementary default and Derivative `renderClouds` never runs. With BSL default it runs but the clouds are discarded by the pack program (if the vanilla `rendertype_clouds`/`gbuffers_clouds` path is used, see the Sodium caveat in section 2).

---------------------------------------------------------------------------------------------------

## 6. Mods in the pack that touch clouds, sky, fog, weather, world renderer

Method: for all 983 jars (nested JiJ included) I parsed `fabric.mod.json` -> mixin configs -> refmaps -> every mixin class (class-file parser, `@Mixin` targets, injector annotations, resolved through the refmap), and separately regex-matched the constant pool of every class file for the intermediary names of `LevelRenderer`, `FogRenderer`, `DimensionSpecialEffects(+inner)`, `CloudStatus` and for the members `renderClouds`, `buildClouds`, `renderSky`, `renderLevel`, `renderSnowAndRain`, `tickRain`, `setupFog`, `setupColor`, `levelFogColor`, `getCloudColor`, `getSkyColor`, `getCloudHeight`, `getCloudsType`, `cloudStatus`, `getDepthFar`, `getRenderDistance`, `getProjectionMatrix`, `getRainLevel`, `getThunderLevel`, `getCloudsTarget`, `useShaderTransparency`, etc. (list in `tools/scanmods.py`). Full output: `scan.json`, `detail.txt` in the survey folder.

### 6.1 Explicit presence checks (READ from the mod catalogue)

| Item | Present? |
|---|---|
| Distant Horizons | **no** |
| Voxy | **no** |
| Fabric Seasons / Serene Seasons / any seasons mod | **no** (only `snowrealmagic` 12.2.4 and `simple_snowy_fix` 2.2.1, snow layers) |
| Enhanced Celestials / Lunar | **no** (Stellar View's optional compat does not apply) |
| Weather mods | `coolrain` 1.4.0-1.21.1 (rain sounds, redirects in `tickRain` only), `snowrealmagic`, `simple_snowy_fix`; `simplyswords` mixes `Level.getRainLevel/getThunderLevel` RETURN cancellable (local storm effect) |
| Immersive Portals | **no** (only `netherportalfix`, `customportalapi`) |
| VulkanMod | **no** |
| Nostalgic Tweaks | **no** |
| Sodium Extra | **yes** 0.9.4+mc1.21.1 (section 3), plus `reeses-sodium-options` 2.2.4 (UI only) |
| "Cloud" anything | `better-clouds-1.13.11+1.21.1-fabric.jar` present (NOT inspected, see header). Otherwise only Dynamic FPS / BetterF3 referencing `Options.cloudStatus`/`CloudStatus`, Vanilla Backport (`LevelRendererMixin` changes cloud mesh constants), MoreCulling (`buildClouds` HEAD cancel) |
| YACL | yes, `yet_another_config_lib_v3` 3.8.2+1.21.1 (plus 3.6.6 nested in `fragmentum`) |
| Iris | yes 1.8.14-beta.1; Euphoria Patcher `euphoria_patcher` 1.10.5-r5.9.3-fabric (RenderType.draw hooks only) |
| Fabric API | 0.116.17+1.21.1 in the pack (dev env uses 0.116.0+1.21.1); a second copy 0.102.1 is nested in `coroutil` |
| Polytone | yes 1.21-5.3.1 |

### 6.2 Hits that matter (injection point / type / likely purpose). Priority "default" = 1000.

**`renderClouds` (method_3259) and the cloud mesh**

| Mod / version | Target + injection | Likely purpose |
|---|---|---|
| Sodium 0.8.13 | `LevelRenderer.renderClouds` INVOKE `DimensionSpecialEffects.getCloudHeight` cancellable, default | replaces clouds (section 2) |
| Sodium Extra 0.9.4 | `renderClouds` `@Inject` HEAD, not cancelling, default | cloud fog setup (section 3) |
| Sodium Extra 0.9.4 | `OverworldEffects` overrides `getCloudHeight`; `CloudRenderer.getCloudRenderDistance` redirect | cloud height / distance options |
| Fabric API rendering-v1 5.2.1 (0.116.17; nested 5.0.5 of 0.102.1 identical) | `WorldRendererMixin.renderCloud`: `@Inject renderClouds HEAD cancellable`, default 1000. Calls `DimensionRenderingRegistry.getCloudRenderer(level.dimension())` and cancels only if one is registered | official per-dimension cloud renderer API. **Aether registers one** (`AetherRenderEffects.registerCloudRenderer`) |
| Porting Lib base 3.1.0-beta.87/91 (inside Sporran and TwilightForest jars) | `LevelRendererMixin`, **priority 951**: `renderClouds` HEAD cancellable `renderCustomClouds`, also `renderSky`, `renderSnowAndRain`, `tickRain` HEAD cancellable | NeoForge-style `IClientDimensionEffects`-like hooks |
| Sporran 1.0.8 | `dev.sporran.injects.client.renderer.LevelRendererInject`, **priority 1050**: `renderClouds` HEAD cancellable (`sporran$checkRenderCloudEffects`) calls an injected `DimensionSpecialEffects.renderClouds(level, ticks, partial, poseStack, x, y, z, view, proj)Z` and cancels when it returns true; same for `renderSky`, `renderSnowAndRain`, `tickRain`. `DimensionSpecialEffectsInject` is priority 900 | Forge compat layer |
| Polytone 1.21-5.3.1 | `LevelRendererMixin` **priority 1300**: `@ModifyExpressionValue` on the INVOKE `getCloudHeight` inside `renderClouds` -> `polytone$modifyCloudHeight` (dimension modifier `cloud_level`, may be an expression). Also writes `DimensionSpecialEffects.cloudLevel` directly for plain floats | resource-pack controlled cloud height. Because Sodium cancels before that INVOKE returns, the expression form is already ignored under Sodium (INFERRED) |
| Vanilla Backport 1.2.2 | `LevelRendererMixin`: four `@ModifyExpressionValue ... at CONSTANT` in `buildClouds` (fancy/fast start/end) | cloud range backport (only effective in the vanilla mesh path) |
| MoreCulling 1.0.10 | `LevelRenderer_cloudsMixin`: `@Inject buildClouds HEAD cancellable` | cull cloud mesh building |
| Iris 1.8.14 | call-site hooks, `Options.getCloudsType` override (prio 1010), `getRendertypeCloudsShader` override (section 4) | shader packs |
| Aether 1.5.11 | `LevelRendererAccessor` (accessors/invokers for `cloudBuffer`, `prevCloudsType`, `generateClouds`, `buildClouds`, `ticks`); `AetherSkyRenderEffects` extends DSE with **cloud height 9.5**, own `renderClouds`/`getCloudColor`; registered through Fabric API and Sporran's DSE hook | Aether dimension clouds (own copy of the vanilla algorithm) |
| Twilight Forest 4.8.754 | `CloudEvents` (WorldRenderEvents), `TwilightForestRenderInfo extends DSE`, `LevelRendererMixin` HEAD cancellable on `renderSky`, `renderSnowAndRain`, `tickRain` | dimension sky/weather |
| Eternal Starlight 0.9.1 | `ESClientHandler` reads `getCloudHeight`, `getCloudsTarget`, `useShaderTransparency`; `ESSkyRenderer`; `LevelRendererMixin` INVOKE `renderWorldBorder`, `renderSnowAndRain`, `ParticleEngine.render` | dimension effects |
| Dynamic FPS 3.11.4 | `OptionHolder` reads/writes `Options.cloudStatus`; `GameRendererMixin` HEAD cancellable on `GameRenderer.renderLevel` | disables world render when unfocused; may flip the cloud option |
| BetterF3 11.0.3 | `GraphicsModule` reads `CloudStatus` / `cloudStatus` | F3 text |

**Sky / fog colour (clouds fade into these)**

| Mod | Hook | Purpose |
|---|---|---|
| Stellar View 1.0.0 | `LevelRendererMixin`: `renderSky` HEAD cancellable (default) | our sky |
| Iris | `renderSky` HEAD cancellable (`preRenderSky`), many INVOKE hooks, `FogRenderer.setupFog` HEAD (`iris$setupLegacyWaterFog`) and `setupColor` TAIL | shader sky/fog |
| Sodium (sky rule disabled by Iris) | see section 2 | |
| Sodium Extra | `FogRenderer.setupFog` TAIL; `renderSky` WrapOperation (`getStarBrightness`, shader redirect), `renderEndSky` HEAD cancellable, `getSunriseColor` wrap | sky detail toggles, fog |
| Fabric API rendering-v1 | `renderSky` `@Inject` INVOKE `Runnable.run()` cancellable ord 0 (SkyRenderer registry), `renderWeather` HEAD cancellable (`renderSnowAndRain`) | per-dimension sky and weather |
| Porting Lib base, client_events (prio 951); Sporran FogRendererInject | `FogRenderer.setupColor` (ModifyArgs clearColor / INVOKE clearColor), `setupFog` HEAD cancellable (`setupFog` in base) and TAIL (event) | Forge fog events |
| BCLib 30.5.6, Better End, Better Nether | BCLib `FogRendererMixin`: `setupColor` RETURN, **`setupFog` HEAD cancellable** (`bclib_fogDensity`) | biome fog density |
| Platform 1.4.2 | `FogRendererMixin`: `setupColor` ModifyArgs, `setupFog` HEAD cancellable + TAIL | fog events |
| Galosphere, Foundation x7 ([Let's Do] mods), Resourceful Lib, YUNG's Cave Biomes, Aether (`FogRendererMixin` TAIL + WrapOperation clearColor), Kiwi (WrapOperation inside `setupFog`), Eternal Starlight (`setupFog` RETURN, `setupColor` WrapOperation), Puzzles Lib (`setupColor`, `setupFog` TAIL) | `FogRenderer.setupFog` TAIL or similar | biome / mod fog effects |
| Polytone | `FogRenderer.setupColor` WrapOperation/ModifyExpressionValue (rain, thunder, powder snow), `setupFog` TAIL (`polytone$modifyFogShape`), `ClientLevel.getSkyColor` wraps, `Blindness/DarknessFogFunction` constant mods, `renderSky` blend hooks, star count/size | colormap-driven fog and sky |
| BadOptimizations 2.4.1 (prio 700) | `ClientLevel.getSkyColor` HEAD cancellable + RETURN (caching), `renderSky` WrapOperation/Redirect on `getSkyAngle` | sky colour caching |
| C2ME client uncap VD 0.4.0-alpha.0.30 | `@ModifyArg` on the INVOKE of `FogRenderer.setupFog` inside the synthetic lambda `LevelRenderer.method_37365` (the sky fog runnable): `clamp(viewDistance, 32, 512)` | sky fog distance for render distance > 32 |
| Biome / dimension data | Terralith 2.6.2, Lithostitched, Polytone, Eternal Starlight, Twilight Forest, Aether change `BiomeSpecialEffects` fog and sky colours and DSE data; read through `ClientLevel.getSkyColor` / `FogRenderer` | |

**Weather**: Sodium, Sodium Extra, Fabric API, Iris (`iris$disableWeather`), Sporran/Porting Lib, Twilight Forest, MoreCulling (`rainMixin`), Kibe (Redirect), Subtle Effects (`LevelRendererMixin` priority 999, rain/snow textures and particles), CoolRain, SimplySwords, Particular (`tickRain`), Eternal Starlight (INVOKE `renderSnowAndRain`), malilib (INVOKE `renderSnowAndRain` ord 1). Our renderer does not touch weather but clouds must be drawn before it (fine: weather is after clouds in vanilla order).

**Render distance / far plane / projection**

- Iris `MixinTweakFarPlane` (`GameRenderer`): `@Redirect` of `getDepthFar()` inside `getProjectionMatrix(D)` -> with a shader pack loaded far = `getDepthFar() + 1024.0F`; `iris$tweakViewDistanceBasedOnFog` scales by 0.95. Without a pack: vanilla `renderDistance*16*4` (12 chunks -> 768 blocks). Our cloud extent (up to +-384 blocks, or Sodium's `max(32, RD*2+9)` cells) must fit the far plane: 384 < 768 OK.
- C2ME client uncap VD (sky fog clamp, above). Pack render distance is 12.
- Sodium Extra `panini_projection` (`GameRenderer.renderLevel` INVOKE `renderItemInHand` -> `sodiumExtra$applyPaniniProjection`), off in the pack.
- Just Zoom 3.1.0, Dynamic FPS, Polytone (`GameRendererMixin` prio 500 around `renderLevel`), Glintsmith, Knight Lib, Let's Do Furniture, Vanilla Backport, Sporran: hooks around `GameRenderer.renderLevel` (HEAD/TAIL/INVOKE), no far-plane change.
- Distant Horizons / Voxy absent, so no LOD far plane.

**Render targets / shaders**: Sodium `RenderTarget.blitToScreen` HEAD cancellable; Sodium Extra `RenderTarget` (`scalePresentedTexture`, mac only); Iris `RenderTarget.bindWrite` hooks and `RenderType.setupRenderState/clearRenderState` redirects; ImmediatelyFast `RenderTarget` (`checkForDrawCallWhileBatching`), `RenderType` (`changeTranslucency`), `VertexBuffer` upload optimizations (`optimizeVertexDataUploading` / `optimizeIndexDataUploading`); Fabric API `ShaderInstance` id rewriting (`modifyId`, `allowNoneMinecraftId`); Polytone `ShaderInstance.bindExtraUniforms`; Sporran `ShaderInstance` rewrite; Euphoria Patcher `RenderType.draw(MeshData)` (`method_60895`) HEAD/TAIL (inactive for custom draws); Stellar View `ShaderInstance` id rewrite (`StellarViewShaderMixin`).

### 6.3 Mods whose hits are only incidental
Icon/model renderers in BetterEnd, Galosphere, Illager Invasion, etc. only reference `FogRenderer`/`RenderSystem` methods (false-positive free after word-boundary matching; those showed only `Level.getRainLevel` style calls). Not relevant to clouds.

---------------------------------------------------------------------------------------------------

## 7. Stellar View's current hooks (READ, `src/main/resources/stellarview.mixins.json`, `src/main/java/net/povstalec/stellarview/mixin/`)

`package net.povstalec.stellarview.mixin`, `JAVA_17`, `required: true`, `injectors.defaultRequire = 1`, all in `"client"`:

| Class | Target | Injection |
|---|---|---|
| `LevelRendererMixin` | `LevelRenderer` | `@Shadow level`, `@Shadow ticks`; **`@Inject(method = "renderSky", at = HEAD, cancellable = true)`**, default priority 1000, cancels when `ViewCenters.renderViewCenterSky(...)` returns true. Per-dimension `replace_*` configs. No `renderClouds` hook. |
| `StellarViewShadersMixin` | `GameRenderer` | `@Inject(method = "reloadShaders", at = INVOKE List.add shift AFTER, slice from NEW ShaderInstance ordinal 0, locals CAPTURE_FAILHARD)` -> `StellarViewShaders.ShaderInit.registerShaders(resourceProvider, programs)`. This is the place to register an extra cloud `ShaderInstance`. |
| `StellarViewShaderMixin` | `ShaderInstance` | `@WrapOperation` on `ResourceLocation.withDefaultNamespace` in `<init>` (allow=1) to rewrite ids of `CelestialShaderInstance` via `FabricShaderProgram.rewriteAsId` unless Kilt. |
| `ResourcepackReloadMixin` | `Minecraft` | `@Inject <init>` at INVOKE `resizeDisplay` BEFORE -> `resourceManager.registerReloadListener(ResourcepackReloadListener.ReloadListener)`. |
| `VertexFormatElementMixin` | `VertexFormatElement` | `@Accessor("ELEMENTS")` static. |
| `BufferBuilderMixin` | `BufferBuilder` | `@Invoker("beginElement")`. |

Other facts: `fabric.mod.json` depends on `fabricloader >=0.16.14`, `minecraft ~1.21.1`, `java >=21`, `fabric-api *`; suggests `enhancedcelestials`, `lunar`. `StellarViewSpecialEffects extends DimensionSpecialEffects` (API classes with `cloudLevel` = 192 / NaN / NaN for overworld/nether/end), not registered by a mixin here. `IrisCompatibility` (MethodHandle on `IrisApi.getInstance().isShaderPackInUse()`), `shouldYieldSky()` is called from `ViewCenters.renderViewCenterSky` (config `GeneralConfig.disable_with_shaders`). `TexturedObjectRenderer` also checks `isShaderPackInUse()` for shader choice. No mixin touches `renderClouds`, `Options`, fog or `ClientLevel`.

---------------------------------------------------------------------------------------------------

## 8. Pack configuration that affects clouds (READ)

`options.txt`: `renderClouds:"true"` (= FANCY), `renderDistance:12` (192 blocks), `simulationDistance:10`, `graphicsMode:1` (FANCY, not Fabulous, so the `clouds` render target is not used; ids: 0 fast, 1 fancy, 2 fabulous), `entityDistanceScaling:0.75`, `biomeBlendRadius:2`, `mipmapLevels:4`, `fov:0.75`, `maxFps:140`, `enableVsync:false`. Active resource packs list contains no pack with `textures/environment/clouds.png` or `rendertype_clouds` shaders (all zips in `resourcepacks\` checked), so the vanilla texture is used. Pack options mention `betterclouds` only in `incompatibleResourcePacks`.

`config/sodium-options.json`: no cloud setting (`weather_quality=DEFAULT`, `use_fog_occlusion=true`, `use_entity_culling=true`, ...). `config/sodium-mixins.properties`: empty (so the clouds rule is on). `config/sodium-extra-options.json`: see section 3 (cloud override off, 192, distance 100, fog advanced off, cloud_fog_percent 100). `config/sodium-extra.properties`: empty.
`config/iris.properties`: `enableShaders=false`, `shaderPack=ComplementaryUnbound_r5.9.3 + EuphoriaPatches_1.10.5`, `maxShadowRenderDistance=24`, `colorSpace=SRGB`, `allowUnknownShaders=false`. `config/iris-excluded.json` placeholder.
`config/stellarview-client.toml`: `static_sky=false`, `instancing=true`, etc. (sky only).
Other cloud-adjacent config files exist: `config/betterclouds-v1.json` (not read), `config/coolrain.json`, `config/snowrealmagic-*.yaml`, `config/simple_snowy_fix_common.json`, `config/moreculling.toml`, `config/entityculling.json`, `config/c2me.toml`, `config/dynamic_fps.json`, `config/aether/`, `config/twilightforest-client.toml`, `config/badoptimizations.txt`, `config/porting_lib_config*.toml`, `config/yacl.json5`.
Log evidence (`logs/latest.log`, run 27.10.2026): "Loading 642 mods"; "Iris API found, Stellar View will hand the sky over to active shader packs"; Sodium sky rule force-disabled by Iris; no cloud-related errors outside Better Clouds (not searched).

---------------------------------------------------------------------------------------------------

## Consequences for our renderer

1. **Hook**: a new mixin class, separate from the existing `LevelRendererMixin`, `@Mixin(LevelRenderer.class)` with `@Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)`, handler signature `(PoseStack, Matrix4f viewRot, Matrix4f projection, float partial, double camX, double camY, double camZ, CallbackInfo)`. Cancelling at HEAD is enough to stop Sodium (its injection is at the later `getCloudHeight` INVOKE) and the vanilla body. Add `@Shadow ticks` (`field_4073`) and `level` for drift, as the sky mixin already does.
2. **Priority**: priority does not matter against Sodium. It only orders us against the other HEAD handlers: Fabric API registry (1000), Porting Lib (951), Sporran (1050), Sodium Extra's `setupCloudFog` (1000, not cancelling). I could not verify offline whether the later-applied (higher number) HEAD handler runs before or after earlier ones, so do NOT rely on it: make the handler order-independent. Before drawing check `DimensionRenderingRegistry.getCloudRenderer(level.dimension()) == null` (Aether, and anyone else, registers there) and that `level.effects().getCloudHeight()` is not NaN; if either fails return without cancelling. Add a one-line debug log in a dev run to learn the real order, then decide on a priority (default 1000 is acceptable with the checks).
3. **Gate**: only draw when `Minecraft.getInstance().options.getCloudsType() != OFF` (it already is, `renderClouds` is only called then; it also covers Iris' `clouds=` override and `renderDistance < 4`). If `IrisCompatibility.isShaderPackInUse()`: either do nothing (pack with `clouds=off` never reaches us anyway) or render through `RenderType.clouds()`/`GameRenderer.getRendertypeCloudsShader()` with `POSITION_TEX_COLOR_NORMAL` so Iris swaps in `gbuffers_clouds` (BSL CLOUDS=3, Complementary style 50). A custom `ShaderInstance` would NOT be remapped by Iris (INFERRED). Decide per product goal; the safest default is "yield to the pack when a pack is active", the same as the sky does.
4. **Read at render time, do not recompute**: height `level.effects().getCloudHeight()` (this already includes Sodium Extra's override and Stellar View/other DSE values; Polytone's expression form is ignored by Sodium today), colour `level.getCloudColor(partial)`, drift `ticks + partial`, texture `textures/environment/clouds.png` (resource pack overridable), fog from `RenderSystem.getShaderFogStart/End/Shape/Color()` at draw time (captures Sodium Extra, Porting Lib/Sporran events, Polytone, Foundation, BCLib, Platform and biome fog mods that all tweak `FogRenderer.setupFog` TAIL), render distance `gameRenderer.getRenderDistance()`, and `Minecraft.useShaderTransparency()` + `levelRenderer.getCloudsTarget()` for Fabulous. Optional compat: Sodium Extra `cloud_distance` percent and `cloud_fog_percent` (read its options reflectively), SodiumExtra's `FogOverrideState`.
5. **Matrices**: the two matrices passed in are view rotation (no translation, no bob) and projection (with bob/hurt). Camera position is passed separately; world cloud coordinates must be made camera-relative the way vanilla does (translate by `-frac`), model-view = `viewRot * scale(12,1,12) * translate(...)` if you keep the vanilla layout. Do not use the `RenderSystem` model-view stack.
6. **State to restore** after drawing (matches vanilla/Sodium): blend off, cull on, depth mask true, depth func LEQUAL, colour mask true, main render target bound (after Fabulous `cloudsTarget`), `RenderSystem.setShaderColor(1,1,1,1)`, fog uniforms as found. Do not leave `VertexBuffer` bound. Also reset `levelFogColor()` expectations: vanilla calls it first.
7. **Reload and lifecycle**: rebuild on `allChanged` and resource reload (Sodium reloads in `onResourceManagerReload` RETURN and `allChanged` RETURN; vanilla sets `generateClouds` in `allChanged`), close GPU buffers on `LevelRenderer.close`. `ImmediatelyFast` patches `VertexBuffer` uploads and `RenderTarget` draw-while-batching checks, so draw through normal `BufferBuilder` + `VertexBuffer` paths and not inside a batching scope.
8. **Shader**: register an extra `ShaderInstance` the same way as the celestial shaders (`StellarViewShadersMixin` on `reloadShaders`); mind Fabric API's `ShaderProgramMixin` id rewriting (already handled by `StellarViewShaderMixin`), Sodium Extra's `ShaderInstance` fog decode (inactive with `shape_mode = VANILLA`), Polytone's extra uniforms and Sporran's id rewrite.
9. **What not to collide with**: existing `renderSky` HEAD cancel in `LevelRendererMixin`; mods that mixin `buildClouds` (MoreCulling HEAD cancel, Vanilla Backport CONSTANT) become inert when we cancel `renderClouds`, which is fine. The Aether and any other dimension with a registered Fabric `CloudRenderer` must stay untouched. The Nether and End (`NaN`) must stay empty.
10. **Mods to test against**: Sodium 0.8.13 (+ Sodium Extra with `cloud_height_override`/`cloud_distance` changed), Iris with no pack, Complementary Unbound default (`clouds=off`: our code must simply not run) and style 50, BSL at CLOUDS 2 (default) and 3, Derivative Main, Fabulous graphics without Iris (cloudsTarget), render distance 2 (clouds off) and 32, Aether (registry renderer, height 9.5), Twilight Forest, Polytone `cloud_level`, Sporran/Porting Lib presence, Dynamic FPS, Vanilla Backport + MoreCulling, C2ME uncap VD, ImmediatelyFast. **Remove Better Clouds from the test instance** (it is in `mods\` with a config) and test it separately only once our design is fixed (clean-room, owner decision).

## Open questions (not answerable without running the game)

- Real run-time order of several HEAD handlers on `renderClouds` as a function of mixin priority (see item 2).
- Whether a shader pack with vanilla clouds enabled (BSL CLOUDS=3, Complementary style 50) is actually applied to Sodium's cloud shader (INFERRED: it is not).
- Whether Polytone's expression `cloud_level` is used anywhere in this pack (no dimension modifier JSON was searched for).

## Commands run (abridged, PowerShell 7 / Python 3.14 via `py -I`)

- `Get-ChildItem` on the Loom cache (`%USERPROFILE%\.gradle\caches\fabric-loom\1.21.1\...`, `minecraftMaven\net\minecraft\minecraft-merged\1.21.1-loom.mappings.1_21_1.layered+hash.2198-v2\...-sources.jar`).
- `[IO.Compression.ZipFile]::ExtractToDirectory(<sources jar>, survey\mcsrc)`; copied `mappings.tiny` from `...\loom.mappings.1_21_1.layered+hash.2198-v2\`.
- `Get-Content`/line slicing on `mcsrc\net\minecraft\client\renderer\LevelRenderer.java`, `GameRenderer.java`, `RenderType.java`, `RenderStateShard.java`, `FogRenderer.java`, `DimensionSpecialEffects.java`, `client\multiplayer\ClientLevel.java`, `client\Options.java`, `Minecraft.java`, `CloudStatus.java`.
- Extracted from `minecraft-client.jar` the `rendertype_clouds.*`, `post/transparency.json`, read the PNG header of `clouds.png`.
- `py -I tools\mapq.py mappings.tiny <class regex>` (named -> intermediary + descriptors).
- Extracted into `survey\ex\<name>\`: `sodium-fabric-0.8.13+mc1.21.1`, `sodium-extra-fabric-0.9.4+mc1.21.1`, `iris-fabric-1.8.14-beta.1+mc1.21.1`, `reeses-sodium-options-fabric-2.2.4+mc1.21.1`, `polytone-1.21-5.3.1-fabric`, `fabric-rendering-v1-0.116.17` (from the fabric-api jar), Sporran + Porting Lib base, C2ME uncapvd, Euphoria Patcher, plus small single-class probes (`ex\dseprobe`). `javap -p -v|-c -constants -cp . <class>` on the mixin and renderer classes.
- `py -I -X utf8 tools\scanmods.py mappings.tiny <mods dir> scan.json` (all jars, JiJ recursive, `better-clouds*` skipped by name), `tools\detail.py`, `tools\cat.py`, `tools\targets2.py`, `tools\grepjars.py` (read-only byte/regex searches), `tools\showmix.py` (annotation dump of one class file).
- Read: `options.txt`, `config\sodium-options.json`, `sodium-extra-options.json`, `sodium-extra.properties`, `sodium-mixins.properties`, `iris.properties`, `iris-excluded.json`, `stellarview-client.toml`, `logs\latest.log` (filtered, Better Clouds lines excluded), `shaderpacks\*.txt`, `shaders.properties` / `dimension.properties` / `gbuffers_clouds*` of the four shader packs, `lang\en_US.lang` of BSL.
- Repo (read-only): `build.gradle`, `gradle.properties`, `src\main\resources\stellarview.mixins.json`, `fabric.mod.json`, all six classes in `src\main\java\net\povstalec\stellarview\mixin\`, `compatibility\iris\IrisCompatibility.java`, `ResourcepackReloadListener.java` (excerpt).
- Not run: `gradlew genSources` (a Mojang-mapped sources jar already existed in the Loom cache). Nothing was written outside `scratchpad\survey\`.

Survey folder: `C:\Users\nikol\AppData\Local\Temp\claude\F--Coding-Stellar-View-Remastered\132f12d9-49f1-4846-876f-7d6cee785607\scratchpad\survey\` (`mcsrc\`, `mcassets\`, `ex\`, `shaders\`, `tools\`, `scan.json`, `detail.txt`, `mappings.tiny`).
