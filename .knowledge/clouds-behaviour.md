# Cloud renderer: behaviour description (clean-room input)

Subject: the observable behaviour and data flow of an existing third-party cloud mod (MPL-2.0), as it
behaves on **Minecraft 1.21.1, Fabric**. This document is written in the describer's own words, for
engineers who will build a new MIT-licensed renderer without seeing the original. It contains no code,
no shader text, and none of the original's identifiers, config keys, file names or UI strings. Where
Minecraft classes are named, Yarn names are used, with the Mojang name in brackets where it helps.

Conventions used below:

- **Cloud space**: a horizontal coordinate system that equals world X/Z shifted by the accumulated wind
  drift. Cloud shapes are fixed in cloud space; wind moves cloud space relative to the world.
- **Cell**: one point of a regular horizontal grid in cloud space. Each cell may hold zero, one or two
  cloud boxes.
- **Box**: one axis-aligned cuboid ("puff") drawn with GPU instancing. A cloud is a cluster of
  overlapping boxes.
- **Window**: the square region of cells around the player that currently has geometry.
- **Group**: a square block of cells (the unit of caching, culling and draw batching).
- **Overlap count**: per screen pixel, the number of box surfaces that cover it (in front of terrain).
- "Option X" refers to the user-facing options defined in section 2 by a neutral description.

Uncertain points are marked **[unverified]**. Section 10 contains file:line references into the
original for the lead's verification only.

---

## 1. Visual result

### 1.1 Shape and softness
- The sky holds a layer of soft, puffy clouds built from many small cuboids (default 16 blocks wide,
  8 blocks tall) placed on a fine horizontal grid (one cell every 5.25 blocks by default). Boxes are
  much larger than the grid pitch, so neighbouring boxes overlap heavily. From a distance the result
  reads as rounded, cauliflower-like cumulus with a subtly blocky, "voxel" character that still fits the
  vanilla style; up close, individual cuboid edges are visible.
- Cloud thickness varies: denser parts of the noise field get boxes placed higher above the layer base
  (up to a configurable vertical range, default 64 blocks), so cores are tall and edges are thin and
  low. The base of the layer is flat (all boxes start at the layer's base height or above it), unless
  the "bottom fill" option adds a second box under dense cells, which makes the underside lumpy too.
- Outlines are broken up randomly: each cell has a random acceptance threshold, so edges dissolve
  into scattered single boxes instead of a hard contour ("fuzziness" option). Each box is also jittered
  randomly within its cell so the grid is not visible.
- With box height set to 0, every box becomes a flat horizontal square; clouds then look like a
  layered, flat sheet (closest to vanilla "fast" clouds, but still soft and translucent).
- Large clear-sky areas alternate with cloud fields: a very low-frequency coverage mask removes clouds
  over wide regions, and a region selector blends between several pattern styles (see 4.3), so
  different parts of the sky look different (big fields, small heaps, scattered dots).

### 1.2 Transparency
- Clouds are translucent. Opacity per pixel depends on how many box surfaces overlap at that pixel:
  a single box is faint (default base opacity 20%), every additional overlapping box adds opacity with
  diminishing returns, so thick cores look almost opaque and thin edges look wispy. There is no
  per-box sorting artefact because opacity depends only on the count, never on draw order.
- Colour at a pixel comes from the nearest box surface at that pixel (its height in the layer, a
  colour-variation noise value, and its distance fade).

### 1.3 Lighting and colour through the day
Lighting is not physically simulated. The colour of a cloud pixel is looked up from a small, authored
2D colour table indexed by **time of day** (one axis) and **angle between the view direction and the
sun** (other axis). That table encodes:
- **Day**: near-white, very slightly cooler/bluer when looking away from the sun.
- **Sunrise / sunset**: a strong warm (orange/peach, at sunrise also yellowish) glow on the side of
  the sky facing the sun, shading through mauve to a dusky violet-blue on the opposite side.
- **Night**: very dark blue-violet clouds, with a cold, brighter blue rim on the side facing away from
  the sun (where the moon is).
- The table lookup is non-linear in the sun angle so that the region close to the sun (and the
  antipode) occupies more of the table; this concentrates the colour change near the sun.
- Optional **halo** (default on): near the sun and the moon the angular lookup is warped into a
  slightly non-circular glow, imitating forward scattering (bright rim around the sun/moon when they
  are behind clouds).
- The sun direction is the vanilla sun direction, optionally tilted around the east-west axis by a
  "sun path tilt" option, so it can match shader packs that tilt the sun's path.
- The time-of-day axis is remapped piecewise so that four user-set times (sunrise start/end, sunset
  start/end, in ticks) land on the table's fixed sunrise/sunset rows. This lets a preset shift when
  the warm colours appear.
- Day/night brightness: a brightness multiplier blends smoothly from a "night brightness" value to a
  "day brightness" value across the sunrise window, and back across the sunset window.
- Per-pixel variation: luminance is modulated by (a) the box's relative height in the layer (lower
  parts darker, giving shaded undersides) and (b) a low-frequency noise value sampled per box, so
  neighbouring clouds differ slightly. A "colour variation" option scales this modulation (default
  0.8). The height-based darkening is disabled on the GPU fallback path (see section 8).
- Colour grading after the lookup: saturation (default 1), gamma (default 1; negative values mean
  the reciprocal), brightness, and an RGB tint (default white).

### 1.4 Weather and vanilla influences on colour
A global tint is computed every frame on the CPU and multiplied into the cloud colour. It reproduces
the vanilla cloud-colour logic and adds a few things:
- **Rain** desaturates the clouds toward grey and darkens them.
- **Thunder** desaturates and darkens much more strongly (dark grey storm clouds).
- **Night** darkens the tint (with a floor, so clouds never become pure black), and **moonlight**
  adds brightness at night in proportion to the moon phase (full moon brighter).
- **Fog colour**: the vanilla fog colour (which carries the horizon/sunset tint and biome fog) is
  blended into the tint's hue and brightness, so clouds harmonise with the sky/fog colour. The sky
  colour itself is not read.
- **Night vision** brightens clouds like vanilla brightens the world.
- Mod hooks: lunar events of Enhanced Celestials tint clouds (e.g. red for a blood moon) and change
  the moonlight contribution (section 7).
- Weather also changes the **amount** of clouds: clear weather uses a medium baseline "cloudiness";
  rain raises it moderately and thunder raises it a lot. Higher cloudiness lowers the density
  threshold (more and larger clouds) and shrinks the clear-sky regions. Season mods scale it further.
  Cloudiness changes are applied in discrete steps of 1%, each step rebuilding the cloud geometry, so
  during a weather transition clouds grow or shrink in visible small jumps rather than smoothly.

### 1.5 Edges near the player and at the horizon
- **Near the camera**: boxes whose centre is within roughly one to two box widths of the camera fade
  out with an ordered (4x4 Bayer) dither pattern, i.e. a screen-door fade. Flying through a cloud
  therefore shows a stippled dissolve instead of a hard cut.
- **Far away**:
  - Boxes shrink toward the edge of the cloud radius (to 25% size at the edge by default, quadratic in
    distance) and their height above the base is compressed by the same factor, so the far field
    becomes a thin, flat band toward the horizon.
  - A distance fade reduces opacity: starting and ending distances come from the vanilla terrain fog
    computed for the cloud radius (fade ends at the cloud radius), with two options that stretch the
    end distance and the length of the transition. On 1.21.1 the fade uses horizontal distance, plus an
    additional fade on vertical distance near the end of the range. The fade is applied to opacity
    (clouds become transparent), not by mixing toward the fog colour.
  - If fog is effectively disabled (no usable fog range), clouds fade only over the last few blocks
    before the cloud radius, giving a nearly hard edge.
  - Blindness/darkness and "thick fog" situations shrink the vanilla fog range, so clouds vanish
    accordingly.
  - Optional world curvature (off by default) bends far clouds downward to match shader packs with
    curved worlds.
- **Under water / in lava / in powder snow**: no clouds at all are drawn while the camera is submerged
  (also no vanilla clouds).
- **Behind translucent blocks** (glass, water) with non-Fabulous graphics, clouds are hidden, because
  translucent terrain has already written depth before clouds are drawn (same as vanilla). With
  Fabulous graphics, clouds go to the clouds target and are composited correctly.

### 1.6 Movement
- **Wind drift**: cloud space moves relative to the world at a default speed of 0.03 blocks per tick
  (0.6 blocks/s, same order as vanilla), predominantly along one horizontal axis (toward negative X),
  with a slow lateral meander. The meander comes from a precomputed random-walk path (about 10,800
  control points spaced so one point is passed every 20 ticks at speed 1) interpolated with a smooth
  cubic spline; after the path's end it repeats with an offset so the drift continues forever.
- The drift position is a function of a chosen clock (default: world time of day). Consequences:
  clouds stand still when the daylight cycle is frozen, and jump when time is skipped (sleeping,
  /time set). Alternative clocks: total world age, or a client-side counter that restarts at 0 on join.
  Sub-tick interpolation scales with how fast the chosen clock advanced in the last tick, so
  time-acceleration mods stay smooth.
- **Billowing**: in addition to drift, the lower boxes of each cloud slowly grow and shrink, driven by
  two scrolling large-scale noise fields (very large, kilometre-scale features and a medium-scale
  modulation). Upper boxes are unaffected, so cloud bottoms appear to churn. Two options set the
  strength (default 1.0) and speed (default 0.8).

---

## 2. User-facing options

Notation: type, range (slider step), default. Cost: none / low / medium / high (CPU generation, GPU
fill, memory). "Rebuild" = triggers a full geometry regeneration. "Recompile" = triggers shader
recompilation. In the original, almost all options apply live while the config screen is open.

Options are stored in one JSON file in the config folder, field names in lower snake case.
A group of colour options lives inside a **look preset** (see 2.6), not at top level.

### 2.1 General
| Purpose (neutral name) | Type / range / default | Visible effect | Cost |
|---|---|---|---|
| Master enable | bool, default on | Off: vanilla (or Sodium) clouds are shown instead. | none |
| Cloud radius multiplier | float 1 to 4 (0.05), default 4 | Cloud radius in blocks = this × render distance (chunks) × 16. At 12 chunks and default: 768 blocks. | **High**: geometry and CPU cost grow with the square of the radius. Rebuild + buffer reallocation. |
| Look preset selector | index into preset list | Switches all colour options at once (2.6). | none |
| Pattern preset selector | index into pattern list | Switches the noise style (2.6). | Rebuild |

### 2.2 Shape generation
| Purpose | Type / range / default | Visible effect | Cost |
|---|---|---|---|
| Cell spacing (grid pitch in blocks) | float 2 to 64 (0.25), default 5.25 | Smaller = more, denser boxes. | **High** (inverse square). Rebuild + realloc. |
| Pattern scale | float 0.25 to 4 (0.01), default 1 | Larger = larger cloud features (lower noise frequency). | Rebuild |
| Edge fuzziness | float 0 to 1, default 1 | 1 = ragged dissolving edges; 0 = crisp outlines. | Rebuild |
| Random thinning | float 0 to 1, default 0 | Removes this fraction of cells at random (sparser look, cheaper). | Rebuild; reduces cost |
| Jitter | float 0 to 1, default 1 | Random offset of each box within its cell (fraction of the spacing). 0 shows the grid. | Rebuild |
| Vertical range (blocks) | float 0 to 265 (1), default 64 | Maximum height of boxes above the base: cloud thickness. | Rebuild |
| Height offset (blocks) | float -384 to 256 (8), default 0 | Moves the cloud layer up/down. See weakness W12 (applied twice). | Rebuild; also changes the vanilla cloud height globally |
| Time source for drift | enum: world time of day / world age / client counter; default world time of day | Which clock drives wind drift (1.6). | none |
| Bottom fill | float 0 to 1, default 0 | Fraction (by density) of cells that get a second box below the base; fills the flat undersides. | Up to 2× boxes, and **4× buffer memory** (W17). Rebuild + realloc. |
| Group size (cells per side) | int 16 to 128 (8), default 32 | Technical: granularity of caching, culling and draw batching. Hardly visible. | Smaller = more draw calls, finer culling. Rebuild. **Technical; candidate to hide.** |

### 2.3 Appearance (geometry and motion)
| Purpose | Type / range / default | Visible effect | Cost |
|---|---|---|---|
| Box width (blocks) | float 2 to 64 (1), default 16 | Size of each puff horizontally. | GPU fill grows with size². Recompile (W9). |
| Box height (blocks) | float 0 to 32 (1), default 8 | 0 switches to flat squares (and turns off face culling). | Recompile; realloc when crossing 0. |
| Far shrink | float 0 to 1 (0.05), default 0.25 | Box scale at the cloud radius edge (1 = no shrinking). | none |
| Drift speed | float 0 to 0.4 blocks/tick (0.005), default 0.03 (UI shows blocks/second) | Wind speed. | none |
| Billow strength | float 0 to 1 (0.05), default 1 | Amount of growing/shrinking of lower boxes. | none |
| Billow speed | float 0 to 1 (0.05), default 0.8 | Speed of the billowing. | none |
| Colour variation | float 0 to 1 (0.05), default 0.8 | Strength of per-box brightness variation and darker undersides. | none |
| Sun/moon halo | bool, default on | Glow around sun and moon (1.3). | Recompile; negligible GPU |
| Fade end multiplier | float 0.5 to 16 (0.1), default 1 | Multiplies the distance where the far fade reaches zero. Values above 1 push the fade beyond the cloud radius (clouds then end hard at the radius). | none |
| Fade length multiplier | float 0.1 to 8 (0.1), default 1 | Multiplies the length of the fade transition. | none |

### 2.4 Performance / technical
| Purpose | Type / default | Effect | Judgement |
|---|---|---|---|
| Use persistent mapped buffers | bool, on | Upload path for instance data (GL 4.4 / ARB_buffer_storage). | **Useless to users**; pick automatically. |
| Frustum culling | bool, on | Skip groups outside the view. | Keep internal; must be forced off with world curvature. |
| Generation cache | bool, on | Re-use per-group generation results across rebuilds. | **Useless to users**; always on (but see W4). |

### 2.5 Shader-pack and compatibility options
| Purpose | Type / default | Effect | Judgement |
|---|---|---|---|
| Render under shader packs | bool, on | Off: the mod deactivates itself whenever an Iris shader pack is active. | useful |
| Ignore pack's cloud setting | bool, on | The vanilla "clouds" option getter is forced to return the user's real setting, overriding Iris's per-pack override (packs that declare clouds off still get these clouds). | useful, but default causes double clouds with packs that have their own (7.4) |
| Draw into the pack's cloud target | bool, on | Under a shader pack, draw into the framebuffer Iris uses for its clouds program; off = draw into the vanilla target. | **Crash workaround only.** |
| Enabled dimension types | list of dimension-type ids; default overworld plus two modded overworld types (Big Globe's and a Middle-earth mod's) | Where the mod is active (section 3). Also editable with a client command that adds/removes the current dimension type. | useful |
| Season factors (Serene Seasons) | transition length in days (float ≥0, default 2) plus 12 sub-season multipliers 0 to 2 (defaults: spring 1.2/1.0/0.9, summer 0.8/0.7/0.8, autumn 0.9/1.0/1.2, winter 1.3/1.4/1.3, early/mid/late) | Multiplies cloudiness, blended across sub-season boundaries. | only with that mod |
| Season factors (Fabric Seasons) | transition days (default 4) plus 4 multipliers 0 to 2 (spring 1.0, summer 0.7, fall 1.0, winter 1.4) | Multiplies cloudiness, blended over the last days of a season. | only with that mod |

### 2.6 Look presets and pattern presets
**Look preset** = a named bundle of these options (all "shader-related"); built-in presets are
read-only, the user edits a copy:
| Purpose | Range / default |
|---|---|
| Base opacity per box | 0 to 1, default 0.2 |
| Overall opacity multiplier | 0 to 1, default 1 |
| Opacity saturation exponent (how fast many overlaps reach full opacity) | 0.25 to 4, default 1.5 |
| Gamma (negative = reciprocal) | -5 to 5, default 1 |
| Day brightness / night brightness | 0.1 to 4 each, default 1 / 1 |
| Saturation | 0 to 2, default 1 |
| Tint colour | RGB, default white |
| Sunrise start / end (ticks of day) | -6000 to 6000, defaults -785 / 1163 |
| Sunset start / end | 6000 to 18000, defaults 10837 / 12785 |
| Offscreen resolution factor | 0.25 to 1, default 1. Only meant to match shader packs that render at a reduced internal resolution (TAAU); any other value breaks the image (W6). **Dangerous; hide or remove.** |
| Sun path tilt (degrees) | -60 to 60, default 0 |
| World curvature radius | discrete list: off, ±256 … ±16384, and small values down to 16; default off. Turns off frustum culling. **Niche.** |

Built-in look presets: a neutral default and nine tuned for specific shader packs (Soft Voxels Lite,
BSL v8, Chocapic13 v6 and v9, Complementary Reimagined v2, Complementary v4, Kappa v5, Rethinking
Voxels v0, Solas v1). They mostly adjust gamma (many packs' cloud colour target is an HDR buffer, so
a strong inverse gamma is used), sun tilt (to match the pack's sun path rotation), day/night
brightness, saturation, sometimes opacity and the resolution factor. Presets can also be supplied by
resource packs (a JSON map from key to preset, merged across all packs). Purpose: one-click
adaptation to a shader pack's colour pipeline. Of the user's packs, BSL and the Complementary family
have presets; Derivative and Complementary Unbound do not.

**Pattern preset** = a list of "octave sets"; each set defines the frequencies of one fractal simplex
noise. With more than one set, a very low-frequency region selector blends between sets, so the sky
shows different cloud styles in different areas. Built-ins: default (four sets blended regionally:
"varied patches"), and four single-set styles: fluffy (classic), grand (vast fields, heavier), medium
heaps, small dots. User-editable patterns exist in the config file; the GUI only offers selection
[unverified whether an editor is reachable in this version].

### 2.7 Hidden / non-GUI settings and debugging
- Flags to suppress chat warnings (GPU incompatible, "vanilla clouds are off" hint), a flag for the
  automatic issue report, internal bookkeeping (config migration version, telemetry version).
- Debug commands: profiling (CPU/GPU timings in chat), pausing the generator or the animation,
  forcing a rebuild, changing cache size, drawing culling boxes, toggling GPU fallbacks at runtime,
  RenderDoc integration. **Debug only.**
- A translation entry exists for a "write depth buffer" option that no longer exists (stale).

---

## 3. Per-dimension behaviour and fallback rules

### 3.1 Activation per frame (1.21.1)
The mod hooks the start of vanilla's cloud rendering call (see 6.1). Per frame, in this order:
1. If the GPU failed the startup capability check, or the renderer object does not exist: do nothing,
   the next renderer in the chain runs (vanilla, or Sodium's cloud renderer if Sodium is installed).
2. If no world is loaded: same, pass through.
3. If the current world's **dimension type** id is not in the enabled list: pass through. Default list:
   overworld, Big Globe's overworld type, a Middle-earth mod's type. Because the key is the dimension
   *type*, any custom dimension that reuses the overworld type is also covered.
4. If the mod is disabled, or a shader pack is active and "render under shader packs" is off: pass
   through.
5. If a critical GPU resource failed to load (shader compile/link error, offscreen framebuffer missing):
   **fallback**, pass through to vanilla/Sodium.
6. If the camera is submerged in a fluid or powder snow, or a special Ars Nouveau sky renderer is
   drawing: **suppress** both this mod and vanilla (no clouds).
7. Otherwise render this mod's clouds and cancel vanilla.

### 3.2 Vanilla cloud option
- **OFF**: vanilla never calls its cloud routine, so this mod renders nothing. Once per session it
  posts a chat hint that clouds are disabled in video settings (not shown while a shader pack is
  active, can be turned off).
- **FAST vs FANCY**: no visible difference. The mode only feeds a "shader parameters changed" check
  that triggers a recompile. Flat vs. volumetric is controlled by the box-height option instead.
- The vanilla option getter is wrapped (6.2) so that, while the mod is enabled and the override
  option is on, the user's stored value is returned even when Iris or another mod would override it.

### 3.3 Dimensions without clouds
- Nether and End have no cloud height (vanilla returns "not a number"). They are not in the default
  list, so vanilla runs and draws nothing. If a user adds them, the mod does not check for the missing
  height and renders with an invalid height (nothing sensible appears) [W13].
- Fixed-time dimensions: brightness uses the dimension-aware time, the colour-table row uses the raw
  day time (minor inconsistency).

### 3.4 Cloud height
- Base height = vanilla dimension-effects cloud height (overworld 192 on 1.21.1), read every frame.
- The height-offset option is added to that vanilla value through a global hook (affecting every
  reader of the overworld cloud height, including vanilla/Sodium clouds and Iris's cloud-height
  uniform), and **again** to every box [W12]. With Sodium Extra installed, a separate hook is used that
  stacks on top of Sodium Extra's own cloud-height override, so Sodium Extra's height setting moves
  these clouds too.

### 3.5 Render distance
- Cloud radius follows render distance × multiplier (default 4×, i.e. up to 2048 blocks at 32
  chunks). On 1.21.1 vanilla's far clip plane is 4× the render distance in blocks, so the cloud field
  ends right at the far plane; depth clamping is enabled while drawing so far boxes are not clipped.
- Changing render distance reallocates the instance buffer and rebuilds everything; clouds disappear
  until the first rebuild finishes (a brief blink).

---

## 4. How the cloud shape is produced

### 4.1 Inputs
- **No vanilla cloud texture** is used. Shape comes entirely from procedural noise evaluated on the
  CPU, plus hashing for randomness.
- A **world seed**: the client-side hashed biome seed (the value vanilla sends to clients for biome
  zoom, `BiomeAccess` seed), so all players on a server would see the same layout. In practice the seed
  is only picked up when GPU resources are reloaded while a world is loaded (see W5); otherwise 0.
- A **cloudiness** scalar in [0, 1] from weather and season mods (1.4, 7.7).
- The **pattern preset** (octave sets) and the generation options.
- A small tiling RGB noise texture (128×128, sampled on the GPU) for billowing and colour variation.

### 4.2 Spatial layout
- A square grid of cells in cloud space with pitch = spacing option. The window is centred on the
  camera's cloud-space position, snapped to a coarse "anchor" (anchor step = group-size option
  interpreted in blocks, 32 by default), and extends one cloud radius in each direction.
- The window is split into groups of N×N cells (N = group-size option). Groups are generated in row
  order; their boxes are written contiguously into one instance array, and per group the instance
  start, count and an axis-aligned bounding box are recorded.
- Per cell (using the cell's integer cloud-space position as noise input):
  1. Optional random thinning by a per-cell hash.
  2. Density value: fractal simplex noise at a frequency set by the pattern scale. With several octave
     sets, a region noise (order of a couple of thousand blocks) picks two neighbouring sets and blends
     them with a steep curve near the region border.
  3. Cloudiness remap: the density is shifted and rescaled so that higher cloudiness lets more cells
     pass, then multiplied by a large-scale coverage mask (another simplex noise at around a
     thousand-block scale) whose threshold also moves with cloudiness, producing clear-sky regions
     that shrink as cloudiness rises.
  4. Fuzzy edge test: a per-cell random number is compared with the density plus a margin that the
     fuzziness option controls; failing cells are dropped.
  5. If the remaining density is positive: place a box. Its height above the base grows non-linearly
     with density, scaled by the vertical-range option, plus the height offset. Its horizontal
     position is the cell position plus a hashed jitter (scaled by the jitter option and the spacing).
  6. If bottom fill is enabled and the density is high enough relative to that option, a second box is
     placed below the base (a fraction of the height, mirrored), with its own jitter.
- Only the box **centre position** (3 floats) is stored per instance. Box size, near/far behaviour,
  billowing and colour data are all computed on the GPU per frame.

### 4.3 Patterns and randomness
- Simplex/fractal noise is Minecraft's own (seeded from the world seed via vanilla random classes).
- Per-cell randomness (thinning, fuzzy threshold, jitter) uses an integer hash of seed, a channel tag
  and cell coordinates, converted to a float in [0,1). Jitter values are in [0,1), i.e. one-sided.

### 4.4 Following the player and wind
- Every frame: compute the drift offset (1.6), derive the camera's cloud-space position, and snap it
  to the anchor grid. A rebuild is requested when:
  - the anchor moved by more than 4 anchor steps (Manhattan distance) since the last requested build,
  - any option changed (the whole config, including purely visual options, is compared field by
    field every frame),
  - cloudiness changed by at least one percentage point,
  - the instance buffer was reallocated or nothing has been built yet.
- Between rebuilds the existing instance set is simply translated as a whole by a per-frame origin
  offset (anchor position minus camera plus drift), so drift is smooth and needs no upload.
- Consequence: the window lags the player by up to 4 anchor steps (about 128 blocks by default), so the
  far edge on the side the player moves toward comes up to that much closer before the next rebuild.
  Fog normally hides this.

### 4.5 Reach
- Radius = render distance × multiplier (3.5). Grid side length ≈ 2 × radius / spacing cells (about
  290 at 12 chunks, about 780 at 32 chunks with defaults). The instance buffer is sized for the full
  grid (doubled per side when bottom fill is on).

### 4.6 Update cadence and threading
- Rebuilds run on a background thread (the JVM's shared common pool, one task at a time); the render
  thread only starts tasks, picks up finished ones, and swaps/uploads the result.
- Task lifecycle: requested → running → completed → swapped (visible). A newer request replaces an
  older not-yet-started one. Running tasks check a cancel flag between groups.
- Upload: either (a) two persistently mapped, coherent GPU buffers (write into one, swap pointers on
  completion), or (b) a CPU-side array uploaded with a single sub-data call on swap.
- Caching: per group, the generated point list is cached in a two-generation LRU (entries used in the
  last build survive into the next); keyed by the group's cloud-space index. The cache is cleared
  whenever options or cloudiness change. (Correctness issues: W4.)
- Typical cadence: when standing still in clear weather, nothing is rebuilt; when flying, one rebuild
  per ~128 blocks; during a rain/thunder transition, one rebuild (with cache cleared) per 1%
  cloudiness step, i.e. roughly 18 rebuilds for rain and 30 for thunder over a few seconds.

### 4.7 CPU vs GPU split
- CPU: noise sampling, cell decisions, box centre positions, per-group bounds, frustum test per group,
  global tint, fog range, sun direction, brightness factors.
- GPU: box expansion from a unit box (instanced), size and far-shrink, billowing, near dither fade,
  far fade, terrain occlusion test, overlap counting, colour lookup and grading, compositing.

---

## 5. Rendering pipeline

### 5.1 Stages, in order (per frame, on the render thread)
1. **Prepare** (inside the vanilla cloud call, before anything is drawn):
   - early outs from 3.1;
   - read cloud height;
   - recompile shaders if any compile-time parameter changed (box size, halo, fallback flags, DH
     availability, curvature, plus irrelevant ones like the vanilla cloud mode and view distance);
   - reallocate the instance buffer if its size or mode changed;
   - compute cloudiness, update drift and request rebuilds (4.4); start a background build if none is
     running; swap in a completed build;
   - build matrices: full view-projection with the camera translation folded into a per-frame origin
     offset (camera-relative rendering, avoids float precision issues), and a rotation-only
     view-projection for the full-screen pass.
2. **Coverage pass** into a private offscreen framebuffer (same size as the main framebuffer, times
   the resolution factor):
   - attachments: an RGB8 colour texture for per-pixel cloud data, and a combined 24-bit depth /
     8-bit stencil texture. Clear colour to zero, depth to far, stencil to zero.
   - draw all visible instances: unit box (14-vertex triangle strip covering all 6 faces) or unit
     flat square (4-vertex strip) per instance; back-face culling for boxes, no culling for squares;
     depth test "less or equal" with depth writes; depth clamping on.
   - vertex stage per box: far shrink, billowing scale, near fade factor, far fade, relative height and
     a noise value for colour; optional world curvature; projection.
   - fragment stage: discard if behind the scene depth (reads the main framebuffer's depth texture at
     the same pixel; plus Distant Horizons' depth when present); discard by the 4x4 dither threshold
     for the near fade; write (far fade, relative height, colour noise) to colour.
   - **stencil counts overlaps**: the stencil operation increments on both depth pass and depth fail,
     so every box fragment that survived the discards adds one, regardless of depth order, while colour
     and depth keep the nearest surface. That gives "nearest surface data + overlap count" without
     sorting (an order-independent transparency scheme).
   - draw batching: with frustum culling, groups are tested against the camera frustum (expanded by the
     box size) and consecutive visible groups are merged into one instanced draw using a base-instance
     offset; without culling, everything is one draw.
3. **Bind the destination**: the vanilla clouds target (Fabulous) or main framebuffer; with an active
   shader pack, the framebuffer that Iris assigned to its clouds program (7.4).
4. **Shading / resolve pass**: a camera-centred cube is drawn with the rotation-only matrix, so it
   covers the whole screen and each fragment knows its view direction. Per pixel:
   - skip if the data texel is empty;
   - opacity from the overlap count (stencil read as an unsigned-integer texture);
   - colour from the colour table (sun angle, time), modulated by relative height and noise, graded,
     tinted; opacity multiplied by the far fade and the overall opacity factor;
   - writes the nearest cloud depth (copied from the offscreen depth) into the destination depth buffer;
   - standard alpha blending (source alpha / one minus source alpha for colour; additive-over for alpha);
     only colour attachment 0 is written.
5. **Optional depth copy (fallback path only)**: a full-screen pass meant to copy cloud depth when the
   depth texture cannot be sampled separately from stencil; it is effectively a no-op (W3).
6. **Restore state** (6.4).

### 5.2 GL features
- GLSL 3.30 core shaders. Instanced vertex attributes (attribute divisor). Draw-arrays-instanced with
  base instance where available. Offscreen framebuffer with multiple render targets on the fallback
  path. Depth-stencil texture sampled in stencil mode (stencil texturing) and a texture view of the same
  storage sampled in depth mode. Immutable texture storage where available. Persistent mapped buffers
  where available. Depth clamp. Per-attachment blend functions on the fallback path.
- **Not used**: compute shaders, SSBOs, image load/store, geometry/tessellation shaders, indirect
  draws, multisampling.

### 5.3 Depth, blending, sorting, fog, sky colour
- Depth: occlusion against terrain is done manually in the coverage pass by sampling the main depth
  texture (not by depth-testing against it, because the offscreen target has its own depth). Cloud
  depth is then written into the destination, so later geometry (weather, world border) is occluded by
  clouds. Depth is written for every covered pixel even where the cloud is almost transparent (W8).
- Blending: none in the coverage pass (normal path); alpha blending in the shading pass.
- Sorting: none needed (count-based opacity).
- Fog: via opacity fade only (5.1); fog colour only influences the global tint.
- Sky colour: not used.

### 5.4 Position in the vanilla 1.21.1 frame
- Clouds are drawn where vanilla draws clouds: after opaque, cutout and translucent terrain, entities
  and particles, before weather (rain/snow) and the world border [order after particles: unverified for
  every graphics mode]. With Fabulous graphics vanilla first clears and binds the clouds target; the mod
  draws into it (color + depth) and the transparency post-effect composites it. Without Fabulous it
  draws straight into the main framebuffer.
- The sky is untouched; clouds composite over whatever is in the target.

---

## 6. Integration with vanilla 1.21.1

### 6.1 Replaced / suppressed
- `WorldRenderer.renderClouds(MatrixStack, Matrix4f, Matrix4f, float tickDelta, double camX, double
  camY, double camZ)` [Mojang `LevelRenderer.renderClouds(PoseStack, Matrix4f, Matrix4f, float, double,
  double, double)`]: injected at its start with a lower-than-default mixin priority (900), so this
  handler runs before Sodium's own cloud hook [assumes Sodium also uses a cancellable head injection
  there; unverified for the pack's Sodium]; when it renders (or suppresses) it cancels the call, so
  neither vanilla nor Sodium draw clouds. The passed view and projection matrices are used.
- `GameOptions.getCloudRenderModeValue()` [Mojang `Options.getCloudsType()`]: wrapped so that, when the
  mod is enabled and the override option is on, the raw stored option value is returned (defeats
  Iris's per-pack override). This is global: every caller sees it.
- `DimensionEffects.getCloudsHeight()` [Mojang `DimensionSpecialEffects.getCloudHeight()`], overworld
  effects only: result increased by the height offset option (global, see 3.4).
- Vanilla fog, sky and weather rendering are not modified.

### 6.2 Vanilla data read
- Camera position and frustum (`WorldRenderer.capturedFrustum` when the frustum is frozen, else the
  current frustum), camera submersion type (`Camera.getSubmersionType()`).
- World time of day (`World.getTimeOfDay()` [Mojang `getDayTime()`]), world age (`getTime()`
  [Mojang `getGameTime()`]), lunar/dimension-aware time (`getLunarTime()`), sky angle in radians
  (`getSkyAngleRadians(tickDelta)` [Mojang `getSunAngle`]), sky angle fraction (`getSkyAngle`
  [Mojang `getTimeOfDay(float)`]), moon size (`getMoonSize()` [Mojang `getMoonBrightness()`]).
- Rain and thunder levels with partial ticks (`getRainGradient` / `getThunderGradient` [Mojang
  `getRainLevel` / `getThunderLevel`]).
- Fog: it calls `BackgroundRenderer.applyFog(camera, FOG_TERRAIN, cloudRadius, thickFog, tickDelta)`
  [Mojang `FogRenderer.setupFog`] with the cloud radius as view distance, reads the resulting fog
  start/end/colour from `RenderSystem`, then restores the previous fog state. "Thick fog" is taken from
  the dimension effects and the boss bar. If the fog colour alpha is zero it asks vanilla to apply the
  fog colour first.
- Night vision strength (`GameRenderer.getNightVisionStrength`).
- Main framebuffer and its depth attachment; the Fabulous clouds framebuffer
  (`WorldRenderer.getCloudsFramebuffer()`); the `RenderPhase` clouds target's setup/teardown.
- Hashed seed from `BiomeAccess`.
- Not read: vanilla `ClientWorld.getCloudsColor` (its logic is re-implemented instead, so mods that
  hook that method do not affect these clouds), sky colour.

### 6.3 Lifecycle
- GL capability check right after `RenderSystem.initRenderer`.
- Renderer object created in the `WorldRenderer` constructor; all GPU resources (shaders, textures,
  primitive meshes, offscreen framebuffer, generator) (re)created at the end of the world renderer's
  resource reload (`WorldRenderer.reload(ResourceManager)`); destroyed in `WorldRenderer.close()`.
- World reference updated in `WorldRenderer.setWorld`.
- Presets loaded by a client resource reload listener (two JSON files, merged across resource packs).

### 6.4 GL state handling (1.21.1)
- Before drawing: unbinds the current vanilla shader program through vanilla's API so vanilla's
  "currently bound program" cache is invalidated. (The explicit save/restore helpers are no-ops on this
  version.)
- After drawing it forces: blending off, depth test on, depth writes on, depth function less-or-equal,
  active texture unit 0, colour mask all on, face culling on, stencil test off with default func/op,
  depth clamp off (except on one early-return path, W1), vertex array object restored to the one
  vanilla's buffer renderer last bound.
- Not restored: viewport (W6), clear colour/depth/stencil values, stencil write mask, textures bound on
  units 1–6, the GL program (left as the mod's program, vanilla re-binds because its cache was
  invalidated), draw framebuffer under Iris.

---

## 7. Compatibility behaviour

Relevance to 1.21.1 Fabric is noted per item. The original was compiled against Sodium 0.6.13,
Sodium Extra 0.6.0, Iris 1.8.0, YACL 3.8.0, Fabric API 0.115.3. The target pack runs newer Sodium and
Sodium Extra versions; hook targets that depend on those mods' internals may no longer match
[unverified].

### 7.1 Sodium (relevant)
- No options-page integration on 1.21.1: code for a "use these clouds" toggle in Sodium's quality page
  exists but is never registered for this version.
- Sodium's own cloud renderer is bypassed by hook order (6.1). When this mod passes through (disabled,
  wrong dimension, fallback), Sodium's clouds are what the user sees.

### 7.2 Sodium Extra (relevant)
- Cloud height: honoured (3.4).
- Fog: while the mod queries vanilla fog for its own fade, Sodium Extra's fog override handler is
  cancelled, so Sodium Extra's fog distance/"fog off" settings do not change the cloud fade.
- No integration with any Sodium Extra "clouds on/off" setting; if such a setting works by forcing the
  vanilla option to OFF, clouds stop; if by cancelling the vanilla call earlier, also stop
  [unverified for Sodium Extra 0.9.x].

### 7.3 Iris without a shader pack (relevant)
- Behaves exactly like vanilla rendering; Iris is only queried for "is a pack active".

### 7.4 Iris with a shader pack (relevant)
- Destination: the framebuffer Iris created for its **clouds** program (ExtendedShader or fallback
  shader for the clouds key), choosing the "before translucent" or "after translucent" variant according
  to Iris's current phase. Only draw buffer 0 receives colour. The pack's own clouds program is
  **not** run: the mod's own shaders draw finished-looking colour straight into the pack's G-buffer
  attachment 0. Packs that treat that attachment as linear/HDR colour need the colour-grading presets
  (inverse gamma etc.). Deferred packs that expect albedo/normal layouts there may light or tone-map the
  clouds wrongly. If Iris's clouds program is of an unexpected type, an exception is thrown and the
  mod disables itself.
- Iris's per-pack cloud setting (shaders.properties) is overridden by default (6.1). Packs with their own
  sky clouds (Complementary family, Rethinking Voxels) therefore show **two cloud layers** unless the
  pack's own clouds are turned off in the pack settings or the override option is turned off (which then
  also hides these clouds if the pack declares clouds off).
- Frustum culling is disabled when Iris's pipeline asks for it.
- Shadow pass: no handling; clouds cast no shadows and are not drawn into shadow maps.
- The original's own compatibility notes (development notes, not code) list BSL and Complementary as
  forward-rendered packs whose target 0 is HDR colour ("mostly works" with gamma correction), and older
  Complementary Reimagined / Rethinking Voxels as "won't work" because their own clouds cannot be
  disabled. Complementary Unbound and Derivative are not mentioned anywhere. For the target pack,
  expect: BSL works with its preset; Complementary Unbound probably double clouds and HDR mismatch unless
  its own clouds are disabled; Derivative unknown.
- Resolution factor: intended for packs with TAAU; the factor must equal the pack's internal render
  scale (W6).

### 7.5 Distant Horizons (relevant on 1.21.1 Fabric if installed)
- Uses DH's public API (versions 2–6 detected by API major version). When DH is loaded, initialised,
  rendering and has rendered at least one opaque LOD pass:
  - the coverage shader is compiled with a second occlusion test against DH's depth texture using DH's
    projection (or Iris's DH projection when a pack drives DH), so clouds are hidden behind LOD terrain;
  - DH's own cloud rendering is switched off by writing DH's config option every frame (persistent
    side effect, W16).
- A chat hint explains that DH may turn vanilla clouds off through its "override vanilla settings".

### 7.6 Enhanced Celestials (relevant, Fabric 1.21.1 builds exist)
- During a lunar event (blood moon, harvest moon, ...), the event's sky-light colour (blended between
  previous and current event) tints the clouds, normalised so the default event is neutral; the event's
  moon size scales the moonlight contribution. Detected by reflection-free API calls for EC 2.x
  (classes for 1.x also supported).

### 7.7 Season mods (both have 1.21.1 Fabric builds)
- Serene Seasons and Fabric Seasons: cloudiness multiplied by a per-season factor (2.5), blended
  across boundaries over a configurable number of days. Fabric Seasons below 2.4.0 is ignored.

### 7.8 Other special cases
- **Ars Nouveau** (NeoForge-only on 1.21.1, so irrelevant here): suppresses clouds while its sky
  texture renderer draws.
- **Head in the Clouds** (a mod that alters rain levels): reads the raw rain/thunder fields instead of
  the getters (buggy, W11). Relevance unknown.
- **Big Globe**, **Middle-earth**: only the default dimension list entries.
- **VulkanMod**, **Immersive Portals**, Iris older than 1.6: declared incompatible ("breaks").
- **Lunar Client**: a nag message only on newer MC versions, not 1.21.1.
- **Mod Menu**: config screen entry. **YACL** (config library, required): GUI plus several GUI mixins.

---

## 8. Hardware requirements, capability checks and fallbacks

### 8.1 Startup check (after the GL context exists)
Hard requirements (otherwise the mod stays inactive, vanilla/Sodium clouds are used, and a chat
message is shown 5 s after joining a world):
- OpenGL 3.2;
- instanced arrays (GL 3.3, or ARB_instanced_arrays / the divisor entry point);
- either stencil texturing (GL 4.3 or ARB_stencil_texturing) **or** per-draw-buffer blending (GL 4.0,
  ARB_draw_buffers_blend, or the indexed blend entry points).
- Running without an OpenGL context (e.g. Vulkan renderer mods) is "incompatible".

### 8.2 Feature fallbacks (each logged; "partially incompatible" chat message)
| Missing feature | Fallback behaviour |
|---|---|
| Base instance (GL 4.2 / ARB_base_instance) | Before each merged draw, re-point the instance attribute to the run's first instance. |
| Stencil texturing | Overlap count accumulated additively into an extra R8 colour attachment (one unit per fragment) with per-attachment blending; the data attachment is overwritten by the last fragment rather than the nearest (unsorted), and the depth test is set to "always", so the stored data and depth come from an arbitrary layer; height-based shading is disabled to hide this. Separate 24-bit depth texture. |
| Immutable texture storage | Mutable texture allocation. |
| Texture views (needed to sample depth separately from stencil) | "Depth write fallback": the shading pass does not write depth; a separate depth-copy pass is attempted (it does nothing, W3). |
| Persistent mapped buffers (GL 4.4 / ARB_buffer_storage) | CPU array + sub-data upload. If mapping fails at runtime, the option is switched off and saved. |

- Framebuffer completeness is verified at creation; on failure the creation is retried with stencil
  fallback, then stencil+depth fallback; if all fail an exception disables the mod.
- A specific CPU/GPU combination (AMD Ryzen 3 3200G / 3200U / 3250U APUs with Radeon graphics) gets
  a "users reported issues" chat warning; nothing else changes.
- Shader compile failure: chat message ("GPU incompatible"), telemetry report, shaders deleted, render
  falls back to vanilla/Sodium (3.1 step 5).
- Any exception during render or reload: the mod disables itself (config flag) and offers an issue report
  screen; with the report feature off, the exception propagates and crashes the game (W22).
- macOS (GL 4.1) ends up on the stencil, base-instance, storage and depth-write fallbacks.

### 8.3 Approximate memory
- Offscreen target: about 7 bytes per screen pixel (RGB8 + D24S8), e.g. ~26 MB at 2560×1440.
- Instance buffers: 2 × (cells per side)² × 12 bytes (×4 with bottom fill): ~1 MB each at 12 chunks,
  ~7 MB each at 32 chunks with defaults (~29 MB each with bottom fill).

---

## 9. Performance techniques and costs

- **Asynchronous CPU generation** with whole-window rebuilds only on significant movement (>4 anchor
  steps), option change or 1% cloudiness change. Between rebuilds, zero CPU work per box and zero
  uploads; drift is a uniform.
- **Generation cache** per group (two-generation LRU) so a rebuild after movement mostly re-uses
  previous groups; cleared on any option or cloudiness change. The original reports hit-rates in its
  profiling chat output.
- **Minimal instance data**: 12 bytes per box; all shape variation computed in the vertex stage.
- **Double buffering** of instance data (two persistently mapped buffers or one GPU buffer plus CPU
  staging).
- **Frustum culling per group** on the CPU and **merging of consecutive visible groups** into a single
  instanced draw (base instance), so draw count is roughly the number of visible group rows (a few to a
  few dozen per frame).
- **Order-independent transparency via stencil counting**: avoids sorting tens of thousands of boxes.
- **Optional reduced-resolution coverage target** (resolution factor). The author's development
  notes show roughly two-thirds of the full-resolution GPU time at half resolution and under a third at
  quarter resolution (older pipeline variant; unusable without a TAAU shader pack, W6).
- **Uniform caching**: frequently set but rarely changing uniforms only upload on change.
- **Compile-time constants**: box size, halo, fallbacks baked into shader source (fast shaders, but
  slider drags cause recompiles, W9).
- Author's development measurements (GPU and settings unknown, so only indicative): full-resolution
  coverage + shading in the range of about 0.7–2.5 ms GPU per frame depending on the variant; fragment
  work dominates (a no-op fragment shader made it about 8× cheaper); the 4x4 dither transparency
  roughly doubled one variant's cost. CPU per-frame cost is small but non-zero: a reflective
  field-by-field comparison of the whole config every frame, several small allocations (vectors,
  render-target helper, fog record), and reflection-based method lookup with exceptions every frame
  (W10).
- Main costs that scale: number of boxes (radius² / spacing², ×2 with bottom fill), box screen size
  (box width²) and overdraw, rebuild frequency during weather changes.
- Not culled: boxes hidden behind terrain still go through rasterisation (rejected per fragment by the
  manual depth test, no early depth test because of discards); groups are not occlusion-culled; there is
  no distance-based level of detail besides the far shrink.

---

## 10. Weaknesses (with file:line references for the lead)

Paths are relative to `src/main/java/com/qendolin/betterclouds/` (shaders under
`src/main/resources/assets/betterclouds/shaders/core/`). Line numbers refer to commit c6ec8c3. Where the
code has per-version conditional sections, the 1.21.1 branch is meant.

**W1 Depth clamp leaks when nothing is built yet.**
Symptom: right after loading a world, after a buffer reallocation or a render-distance change,
GL depth clamping stays enabled for the rest of the frame (weather, hand, GUI) and following frames
until the first build is visible; may cause near-plane artefacts in other renderers.
Why: the coverage routine enables depth clamp, then returns early when no completed build exists,
skipping the disable. `clouds/Renderer.java:310, 386-389, 402`.
Do instead: scope every state change with guaranteed restoration (try/finally style) and check
renderability before touching state.

**W2 Generation/upload race: a new build overwrites the buffer that is about to be shown.**
Symptom: occasional garbage, missing or doubled clouds for a frame, or a background exception, most
likely during weather transitions or fast flight (when builds start back-to-back).
Why: in the same frame the render thread first starts a new background build (which clears and writes
the single write buffer) and then swaps in the previous completed build whose data lives in that same
write buffer. In persistent mode the swap hands the half-written buffer to the GPU; in staging mode it
flips/uploads the array while the worker writes into it. `clouds/Renderer.java:177-187`,
`clouds/ChunkedGenerator.java:235-258, 283-294, 425, 457-461`, `clouds/Buffer.java:142-172`.
Do instead: give every build its own staging memory (or a ring of three), and only reuse a buffer after
its content has been swapped in and a GPU fence shows the GPU is done with it.

**W3 Depth-copy fallback pass does nothing.**
Symptom: on GPUs without texture views, clouds never write depth, so rain/world border draw over
clouds; and if someone "fixes" it by enabling depth testing, the copy would overwrite the scene depth
with far-plane depth everywhere there is no cloud (no discard).
Why: depth test is disabled for the shading pass on that path and never re-enabled for the depth
copy, and depth writes require the depth test. `clouds/Renderer.java:456-461, 522-530`,
`betterclouds_depth.fsh` (whole file).
Do instead: write depth in the shading pass from a depth-only texture copy (blit depth to a separate
depth texture), or skip depth writing deliberately and document it.

**W4 Generation cache can return wrong content [suspected, verify by running].**
Symptom: strips of missing clouds or strips with doubled density (identical boxes drawn twice, so
higher opacity) near the boundary between re-used and freshly generated groups, mostly toward the far
edge; worse when moving toward negative X/Z.
Why: (a) the cache key is the group index of the group's first cell, but group boundaries are
aligned to the current window origin, which in cell units is not a multiple of the group size, so the
same key can denote different cell ranges in different builds; (b) the first group row/column of a
window is clipped to the window, and that clipped content is cached and later re-used where the group
lies fully inside. `clouds/ChunkedGenerator.java:416-423, 429-453, 490-493`.
Do instead: align groups to an absolute grid in cloud space (group = floor(cell / N)), always generate
complete groups (clip at draw time with bounds or let fog hide the excess), and key the cache by the
absolute group index plus a hash of the generation parameters.

**W5 World seed is effectively 0 most of the time.**
Symptom: the cloud layout is the same in every world, except after pressing F3+T (resource reload)
while in a world, after which that world's seed sticks even in other worlds.
Why: the generator (and its seed) is only created during the world renderer's resource reload; setting
a new world only stores the reference. `clouds/Renderer.java:74-81, 89`,
`mixin/required/WorldRendererMixin.java:105-121`.
Do instead: recreate (or reseed) the generator whenever the world or dimension changes.

**W6 Viewport left at the offscreen size; resolution factor < 1 breaks the image.**
Symptom: with a resolution factor below 1 and no matching TAAU shader pack, the occlusion test reads
the wrong depth pixels, the clouds are drawn into a reduced corner of the screen, and following vanilla
passes may run with the wrong viewport.
Why: the viewport is set for the offscreen pass and never restored; the destination bind on 1.21.1
does not set the viewport; all texture reads use window pixel coordinates without scaling.
`clouds/Renderer.java:110-116, 249, 256-257`, `clouds/VanillaRenderTarget.java:447-470`,
`betterclouds_coverage.fsh:33`, `betterclouds_shading.fsh:48-58`.
Do instead: always save/restore the viewport; if a reduced-resolution pass is offered, upsample
explicitly (with normalised texture coordinates and a depth-aware filter).

**W7 Background task cancellation can deadlock the render thread.**
Symptom: permanent freeze (render thread waiting forever), e.g. on resource reload or when changing
render distance/spacing while a build is queued or not yet started on a busy shared thread pool (very
plausible in a 400-mod pack where the common pool is shared).
Why: cancelling makes the caller wait on the task's monitor until the task notifies; a task that is
queued but never started, a task that starts after cancellation (returns immediately without
notifying), or one that completes between its last cancel check and setting its completed flag never
notifies. The wait happens while holding the generator lock. `clouds/ChunkedGenerator.java:156-165,
337-351, 407-410, 469-481`.
Do instead: never block the render thread on worker cancellation; use a cancellation flag plus a
generation counter and simply discard stale results; use a dedicated single-thread executor (daemon,
low priority) instead of the common pool.

**W8 Depth written for almost transparent pixels.**
Symptom: rain and the world border disappear behind faint cloud fringes, dithered near-camera fade
pixels and fully faded far clouds.
Why: the shading pass writes the nearest cloud depth wherever any cloud fragment exists, independent
of the final opacity. `betterclouds_shading.fsh:48-53, 116-118`.
Do instead: write depth only above an opacity threshold (or not at all, and render weather into a
separate pass), and never for fog-faded pixels.

**W9 Visual options baked into shader source; every slider step recompiles; a debug program leaks.**
Symptom: stutter while dragging box size; GPU program objects accumulate over a session.
Why: box size, halo and several flags are compile-time defines; any change triggers recompiling all
programs, and the debug program is never deleted on reload. Irrelevant parameters (vanilla cloud mode,
view distance) are also part of the recompile key. `clouds/Renderer.java:128-136, 168-172`,
`clouds/Resources.java:350-394`, `clouds/shaders/ShaderParameters.java`.
Do instead: pass sizes and toggles as uniforms; compile once per capability profile; delete every
program you create.

**W10 Reflection and exceptions every frame on 1.21.1.**
Symptom: wasted CPU per frame and exception churn.
Why: the vanilla clouds target's setup/teardown methods are looked up reflectively every frame, trying
several names in turn (dev name, Yarn name, intermediary name, ...), so each frame throws and catches
lookup exceptions before finding the intermediary name. `clouds/VanillaRenderTarget.java:432-433,
467-469, 484-502`.
Do instead: use an accessor/mixin invoker or call the target's public methods directly through mapped
names; resolve once at startup.

**W11 Rain/thunder getters swapped in the Head-in-the-Clouds path.**
Symptom (only with that mod): plain rain counts as full storm cloudiness.
Why: the helper named for rain returns thunder×rain and the one named for thunder returns rain.
`mixin/required/WorldMixin.java:192-212`, `clouds/CloudinessProvider.java:23-35`.
Do instead: read the two levels correctly, or just use the vanilla getters.

**W12 Height offset applied twice and leaked globally.**
Symptom: the cloud layer moves by twice the configured offset in the overworld (once elsewhere);
vanilla/Sodium clouds and any mod or shader reading the vanilla cloud height also move.
Why: the offset is added to the overworld dimension-effects cloud height by a hook and again to each
box. `mixin/runtime/DimensionEffectsMixin.java:79-88`,
`mixin/runtime/DimensionEffectsOverworldMixin.java:111-119`, `clouds/ChunkedGenerator.java:517`,
`clouds/Renderer.java:163-164` [verify intent].
Do instead: keep the offset internal to the cloud renderer; do not modify vanilla's cloud height.

**W13 No guard for dimensions without cloud height.**
Symptom: enabling the mod in the Nether/End/custom no-cloud dimensions produces no clouds or broken
rendering (invalid height in matrices).
Why: the height is used without checking for "not a number". `clouds/Renderer.java:163-164`.
Do instead: treat a missing height as "no clouds here" (or use a configurable per-dimension height).

**W14 Any option change rebuilds everything and wipes the cache; cloudiness rebuilds in 1% steps.**
Symptom: CPU spikes and visible jumps in cloud amount during weather transitions; changing tint or
opacity regenerates geometry.
Why: the whole config (including look presets and colour options) is compared every frame; a change
in cloudiness by one percentage point counts as a change; both clear the cache.
`clouds/ChunkedGenerator.java:199-211`.
Do instead: separate generation parameters from appearance; make weather cloudiness a GPU-side
threshold (store per-box density and fade boxes in/out smoothly), so weather changes need no rebuild.

**W15 Cache capacity fixed at first creation.**
Symptom: after raising the render distance or cloud radius, the cache is too small and thrashes until
the next resource reload. `clouds/ChunkedGenerator.java:47-54`.
Do instead: size the cache from the current window every time the window size changes.

**W16 Distant Horizons config written every frame.**
Symptom: DH's own cloud option is permanently turned off in the user's DH config, even after removing
this mod. `compat/DistantHorizonsSharedCompatImpl.java` (before-render event handler),
`compat/DistantHorizons3CompatImpl.java` (disable method).
Do instead: suppress DH clouds through an API that does not persist, or ask the user once.

**W17 Bottom fill quadruples buffer memory.**
Why: the per-side cell count is doubled instead of doubling the total. `clouds/ChunkedGenerator.java:114-119`,
`clouds/Buffer.java:63`. Do instead: size buffers as cells × boxes-per-cell, or grow on demand.

**W18 Vanilla cloud option getter overridden globally; double clouds with shader packs.**
Symptom: packs that declare clouds off (because they render their own) still get these clouds on top;
any other mod reading the option sees the raw value. `mixin/required/CloudSettingMixin.java:103-116`.
Do instead: decide "should our clouds draw" inside the cloud renderer only; default to respecting the
pack's choice and offer an explicit "draw anyway" toggle.

**W19 Attribute pointer cache not invalidated on buffer swap (base-instance fallback only).**
Symptom: on old GPUs with persistent buffers, the first run after a swap may draw from the wrong
offset. `clouds/Buffer.java:114-121, 155-166`. Do instead: reset the cache whenever the bound buffer
changes.

**W20 Fog colour state "restore" may be ineffective [unverified].**
Why: on 1.21.1 the vanilla fog colour getter returns its live internal array; the copy taken for
restoring is that same array, so after the fog colour is (re)applied the "restore" writes back the new
values. `clouds/fog/FogProvider1201.java:85-108`. Do instead: copy values, not the array reference;
or better compute the fog range without calling vanilla's side-effecting fog setup.

**W21 Clouds blink on any buffer reallocation.**
Symptom: changing render distance, spacing or bottom fill makes all clouds vanish until the next build
completes. Why: reallocation drops the visible build. `clouds/ChunkedGenerator.java:144-165`,
`clouds/Renderer.java:386-389`. Do instead: keep drawing the previous build until the new one is ready.

**W22 All-or-nothing error handling.**
Symptom: one transient error (e.g. an unexpected Iris program type) turns the mod off (in memory; it
becomes permanent if the config is saved later) and opens a report screen; if the report feature is
off, or a report was shown shortly before, the exception is re-thrown instead and crashes the game.
`mixin/required/WorldRendererMixin.java:264-278`, `telemetry/IssueReportManager.java:18-35`.
Do instead: fall back to vanilla for the frame, log once, retry later, never crash, never persist
"disabled".

**W23 Sodium options toggle is dead code on 1.21.1.** `mixin/runtime/SodiumGameOptionPagesMixin.java`
exists but is never added to the mixin list for this version (`mixin/RuntimeMixinPlugin.java:22-87`).
If a Sodium page entry is wanted, register it via Sodium's supported config API.

**W24 Built-in look presets are read-only and hold the opacity controls.**
Symptom: to change cloud opacity or brightness the user must first duplicate a preset; the common page
shows greyed-out controls. `config/ShaderPresetGUI.java:186-202`. Do instead: keep common options
editable at top level; make presets apply values rather than lock them.

**W25 Drift tied to world time of day by default.**
Symptom: clouds freeze with the daylight cycle disabled and teleport when sleeping or using /time set.
`clouds/Renderer.java:118-126`. Do instead: default to world age (game time) or a smoothed clock.

**W26 Minor.**
- Static hit/miss counters shared by all builds (debug only), completion callback dereferences a possibly
  null running task when logging an error (`clouds/ChunkedGenerator.java:22-23, 259-263`).
- Jitter is one-sided ([0,1) of the spacing), shifting the field by half a cell (harmless).
- A translation key for the bottom-fill option is misnamed (its tooltip is missing in the UI).

---

## 11. Things the new mod will not have (name only)

- **Telemetry**: sends anonymous system/GPU info on first launch per version, plus shader compile
  errors, to the author's cloud endpoint.
- **Issue report / log upload**: on an exception, opens a screen offering to upload the game log to
  mclo.gs and send a report.
- **RenderDoc**: download/install/load the RenderDoc capture library at pre-launch and trigger frame
  captures via commands.
- **Game tests**: automated client test harness with input/window/level-list mixins for CI.
- **Forge/NeoForge**: alternative loader entry points, event hooks and config-screen registration.
- **Other MC versions**: conditional code for 1.20.x, 1.21.3–1.21.11 (render-pass API, new fog API,
  environment attributes, cloud distance option).
- **Config library GUI mixins**: patches to YACL's tab bar, option list and widgets (custom scrollable
  category bar, dropdowns, group separators, dynamic defaults).
- Also excluded: profiling/debug commands, frustum-culling debug drawing, GPU-fallback toggling
  commands, the "Lunar Client" nag, config migration and data-directory migration.

---

## Appendix A. Open questions / not verified

1. Exact vanilla 1.21.1 draw order around clouds (after particles, before weather) was taken from
   memory of the vanilla `WorldRenderer.render` method, not from the original's code.
2. W4 (cache misalignment) and W2 (swap race) are derived from reading the code; not observed at runtime.
3. W12 (double height offset) might be intentional; it is double in effect either way.
4. Whether the user's Sodium / Sodium Extra versions still match the hook targets the original uses
   (Sodium's cloud hook order, Sodium Extra's fog handler names).
5. How Complementary Unbound and Derivative treat colour written into their clouds target (no notes exist).
6. Whether a pattern-preset editor is reachable in the GUI of this version.
7. The colour table's exact contents were inspected only coarsely; the description in 1.3 is a
   qualitative summary. The new mod must author its own table or an analytic model.
