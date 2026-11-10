# Project: CS2 knives in Minecraft (client mod)

## Goal
A purely client-side Fabric mod that visually replaces Minecraft's vanilla swords with
Counter-Strike 2 knives (models, skins/finishes, animations), using ONLY files from the
player's own local CS2 install. The mod ships code only.

The player picks, in game, per sword: knife model, skin (finish), pattern (seed) and wear.

## Hard rules (non-negotiable)
- **Never commit, bundle, download-host or distribute any Valve asset** (models,
  textures, animations, sounds, item definitions, extracted or converted). Not even
  "just textures", not in test fixtures, not in a server resource pack. That includes
  screenshots of converted content and anything copied out of the game's shaders.
- All extraction/conversion happens at runtime on the player's machine, into a local
  cache folder that is gitignored.
- If CS2 is not installed, the mod degrades gracefully (knives just don't load).
- The mod is free. No paid unlocks for knives (Mojang requires free mods, and selling
  access would be commercial use of Valve IP).
- No "CS:GO" / "Counter-Strike" branding in the mod name, server name or store pages.

Context: this sits in a tolerated gray zone, not "legal". Steam's Subscriber Agreement
prohibits reverse engineering/extracting content without Valve's consent; Valve
tolerates it in practice (Source 2 Viewer is on the Valve Developer Community wiki).
Distribution is where real risk starts, hence the rules above.

## Status
All seven milestones are done and checked against a real install (CS2 build 26445667,
Nov 2026): locating the game, converting models, drawing knives everywhere a sword is
drawn, first-person animations, the settings screen, and finishes with pattern and wear.

On top of the milestones: knives are drawn with their own shader, so metal reflects
(`shine` in the config), and pattern seeds place patterns as in the game.

Not done, in rough order of value:
- **Real surroundings in the reflections.** The shader mirrors an imagined room (bright
  above, dark below), not the Minecraft world, and has no normal maps or pearlescence.
- **Seed parity is checked with one seed only** (Case Hardened 387 on the karambit, against
  a picture from the game). Where wear scratches and dirt sit for a seed is still a guess.
- Per-item overrides (e.g. by a sword's custom name), Mod Menu entry, sounds, names in
  languages other than English, arms in first person.

## Architecture
- **Client-side only Fabric mod** (Minecraft Java). No server component, no server
  changes. It must work in singleplayer and on any multiplayer server.
- **Purely visual.** Never changes item behavior, damage, attack speed, hitboxes or
  anything sent to the server. Other players see normal swords. Mixins only read.
- A sword is anything in the `minecraft:swords` tag (or named `*_sword` before tags exist).

Packages under `io.github.icxd.knives_as_swords`:
- `cs2`: finding the game, the VPK index (`VpkDirectory`, also reads plain text files out of
  the package), KeyValues/KV3 readers, `KnifeModel`, `FinishCatalog` (which finish exists
  for which knife, and display names, from `items_game.txt` + `csgo_english.txt`).
- `extract`: everything that runs Source2Viewer-CLI. `KnifeExporter` (model, bone names,
  animation clips), `ResourceDumper` (material settings as text, textures as PNG/EXR).
- `gltf`, `model`: reading what the extractor wrote. `KnifeMesh`, `KnifeAnimations`.
- `finish`: painting a finish onto a knife, pure Java. `FinishPainter` gathers settings and
  textures, `FinishCompositor` does the per-pixel work and returns two images: colour, and a
  surface map (red roughness, green metalness, blue ambient occlusion).
- `render`: `KnifeVisuals` (loaded/loading knives, keyed by `Appearance`), `KnifeReplacement`
  and `KnifeItemRenderer` (item layer swap), `FirstPersonKnife` (animated first person),
  `KnifePipelines` (the render pipeline and render types for the knife shader).
- `screen`: `KnivesScreen` and its turnable preview.
- `mixin`: the hooks listed below.

`KnifeRuntime` owns the state and one worker thread for all file/process work; the client
thread only ever sees finished, immutable results.

### How knives get on screen
- `ItemModelResolver.appendItemLayers` is wrapped: vanilla resolves the sword as usual, then
  its layers are swapped for one special-model layer drawing the knife. The sword's own
  `ItemTransform` is kept, so the knife sits wherever the resource pack puts a sword
  (hands, GUI, ground, frames, other players). `KnifeVisual.cubeTransform` places the knife
  inside the item cube like a sword sprite: diagonal, grip where the sprite's grip is.
- First person, main hand: `FirstPersonHandsAndItemsRenderer.submitArmWithItem` is wrapped
  and `FirstPersonKnife` draws the knife with the game's own viewmodel pose instead.
  Events: draw on equip, idle loop, `light_miss*`/`light_hit*` on swing (`LivingEntity.swing`),
  `heavy_hit*` for a fully charged hit (`MultiPlayerGameMode.attack`), `lookat*` on the
  inspect key. With `firstPersonAnimations` off it falls back to the item-layer path.
- Geometry goes through `submitCustomGeometry`, triangles as quads with a doubled corner.
  The render type is the mod's own (`KnifePipelines`): the solid entity pipeline with the
  shaders in `assets/knives_as_swords/shaders/core/knife.*` and a second texture, the surface
  map. The shader lights per pixel: Minecraft's soft two-light shading for paint and plastic,
  plus the reflection of an imagined room and glints from the two lights, weighted by
  roughness and metalness. Non-metal mirrors little on purpose (`PLAIN_SHEEN`, and the rise at
  a slant goes with smoothness cubed): with more, dark wood reads as dark metal. "Above" in that room is between the two lights in the world and
  over the viewer's shoulder in menus (told apart by the projection being flat). With `shine`
  off, `RenderTypes.entitySolid` is used instead and the surface map is ignored. **Never flip winding because a pose has a negative
  determinant**: Minecraft's GUI and picture-in-picture poses are flipped on purpose and its
  projection flips back. Flip only for a mirror the mod adds itself (left-handed first person).
- Textures are `KnifeTexture`: mipmapped and linearly filtered, unlike `DynamicTexture`.

### In-game configuration
`K` opens `KnivesScreen`, `G` inspects (both rebindable, category "Knives as Swords").
Per sword: knife, finish, pattern seed (0-1000), wear (limited to what the finish allows,
with its grade name), and "as knife"; for everything: animations and shine. Changes apply
and save immediately (`config/knives_as_swords.json`), except that a slider applies when the
mouse lets go of it (keyboard steps: after a short rest), because every new look has to be
painted first. The preview shows what is applied, so dragging a slider starts nothing. `/knives`, `/knives list|rescan|export <knife>`
remain as troubleshooting aids.

## Extraction pipeline
- Tool: **Source2Viewer-CLI** from ValveResourceFormat (MIT), run as a subprocess. ~50 MB
  per platform, so it is **not bundled**: on first use the mod asks in game (settings screen
  or `/knives export`), then downloads the pinned release from VRF's GitHub and checks its
  SHA-256 (`ExtractorRelease`). `extractorPath` in the config points at an own copy instead.
- Finding CS2: Steam root (Windows registry / default folders) → `libraryfolders.vdf`
  → the library with `steamapps/appmanifest_730.acf` → `common/<installdir>`. Assets
  are in `game/csgo/pak01_dir.vpk`. `cs2Dir` in the config overrides the search.
- Cache is keyed by CS2 build id; other builds are pruned on scan. Layout under
  `<game dir>/knives_as_swords/`: `tools/`, `cache/<buildid>/{models,animations}/<knife>/`,
  `cache/<buildid>/{data,textures}/<path inside the package>`.
- The VPK *index* and plain text files are read in Java; everything compiled goes through
  the CLI.

## Facts checked (re-check after game or VRF updates)
### Game files
- 22 knives, one model each: `weapons/models/knife/knife_<id>/weapon_knife_<id>.vmdl_c`.
  In glTF space all of them have the blade along +Y, lie flat in the YZ plane (X is the
  thickness) and have the cutting edge towards +Z. Folding knives are open in the bind pose.
- The exported model has `JOINTS_0`/`WEIGHTS_0` but **no skin and no bones**: the skeleton
  lives in `animation/skeletons/weapons/knife_<id>.vnmskel_c`. Joint numbers index the
  *model's* bone list (`m_boneName` in the model's DATA block), whose order differs from the
  skeleton file's. `KnifeExporter.boneNames` reads that list.
- First-person clips: `animation/anims/viewmodel/knife/knife_<id>/<clip>_<id>.vnmclip_c`;
  `default_ct` has no folder and uses `_default_knife/<clip>_knife`. Clips: `draw`, `idle1`
  (`idle` for the defaults), `light_miss1/2`, `light_hit1/2`, `heavy_miss1`, `heavy_hit1`,
  backstabs, `lookat01` (some knives have more). 30 fps, about 1 s (inspects 4-8 s).
- A clip exports to a glb holding the arms' skeleton *and* the knife's. The knife's root
  bone sits on the arms' `wpn` bone (same spot as `attachHand_R`); bones below the root move
  relative to it. Both roots carry the Source→glTF axis turn, so in glTF the view space is:
  eye at the origin looking along +Z, +X left, +Y up, metres. A slash spends most of its
  second outside the view; that is how the animation is.
- `scripts/items/items_game.txt`: sections such as `items` and `paint_kits` occur many
  times; all count. Knife items carry `model_player`; the item `name` (`weapon_bayonet`,
  `weapon_knife_m9_bayonet`, ...) is what the inventory pictures
  `panorama/images/econ/default_generated/<item>_<kit>_light_png.vtex_c` are named after,
  and those pictures are the list of valid knife/finish pairs (556 across 20 knives).
- `resource/csgo_english.txt` has lines tens of thousands of characters long; no
  backtracking regex on it.

### Finishes
- A finish is `weapons/paints/**/<kit>.vcompmat_c` (path in `composite_material_path`, or
  `legacy/<name>`). It names a paint material (`.../paints/vmats/<kit>.vmat`, shader
  `csgo_customweapon.vfx`) and the ranges the seed picks from (pattern rotation/offset).
  The knife's material has a `composite_inputs` string attribute pointing at a second
  material with its AO, masks, position and surface textures plus `g_flUvScale1` /
  `g_flWeaponLength1`.
- Nine paint styles (`F_PAINT_STYLE` 0-8: solid, hydrographic, spray, anodized, anodized
  multicolored, anodized airbrushed, custom, patina, gunsmith; `items_game` numbers them one
  higher). Knife finishes use no other shader features that affect colour.
- The compositing logic was worked out from the shader's compiled variants. The CLI only
  decompiles variant 0 (solid); the others needed a scratch C# program against
  `ValveResourceFormat.dll` (`VfxProgramData.GetStaticCombo(id).ShaderFiles[0]
  .GetDecompiledFile()`, run with `DOTNET_ReadyToRun=0` when the DLLs are taken from the
  CLI's single-file bundle). The Java code is a reimplementation; nothing was copied.
- Pattern size: scale × (ignore weapon size ? 1 : `g_flUvScale1`, or `g_flWeaponLength1`/36
  for the projected styles). Checked against the game's own inventory pictures.
- Pattern seed: `UniformRandom` (the "minimal standard" generator with a shuffle table) seeded
  with the seed, then offset u, offset v and rotation are drawn **in that order, always all
  three**, each from the finish's span for it or its one fixed value. Drawing in the order the
  composite material lists them is wrong. Seeds 0 and 1 coincide.
- Surface: the shader's "roughness mode" gives roughness and metalness per style from the
  same masks as the colour; `FinishCompositor` follows it. The knife's own surface is its
  `g_tMetalness` texture (red roughness, green metalness). For finishes with
  `F_ROUGHNESS_TEXTURE` the code assumes `g_tPaintRoughness` simply replaces the single
  value, laid out like the pattern; that variant of the shader was not looked at.
- For the unpainted knife the extractor's glTF export already writes a surface texture next
  to the model (`*_orm_*.png`: red occlusion, green roughness, blue metalness).
- **Two textures are called `g_tAmbientOcclusion`.** The knife material's own is plain grey
  (all 22 checked) and is what a knife is shaded with, painted or not (`PaintJob.baseOcclusion`).
  The composite inputs' one drives dirt and wear only (red depth, green cavities, alpha edges);
  on the gut knife its red is a dull grey all over, and shading with it made the painted knife
  four times too dark.
- Colours match those pictures; the pictures additionally show reflections.
- Texture colour spaces: colour, AO and grunge are sRGB; masks, wear, surface and position
  linear; the pattern is linear for styles 1, 2, 4, 5 (it holds masks) and sRGB for 6-8.

### Source2Viewer-CLI 20.1 (go by its own `--help`; the website documents unreleased options)
- glTF export needs `--vpk_decompile` as well as `--gltf_export_format`. One call exports
  all 22 models in ~15 s, all 317 knife clips in ~8 s.
- With an existing output folder it writes `<folder>/<path inside the VPK>.glb`, textures
  as separate PNG files beside it, collision as `<name>_physics.glb`.
- It exits with 0 even when a file fails. Check for the output file instead.
- `--block DATA` prints a resource's settings as KV3 text; several paths (comma separated)
  give one `[n/m] <path>` section each. **Do not use `--vpk_decompile` on materials**: it
  also exports every texture they reference (gigabytes for the paint folder).
- Float textures come out as OpenEXR (ZIP, float RGBA); `ExrReader` handles that.

### Minecraft 26.3
- Unobfuscated: Mojang's names, no Yarn. `./gradlew genSources` and read the jar, do not
  guess. Much differs from older tutorials: `Identifier`, `GuiGraphicsExtractor` and
  `extractRenderState` (no `render`), `SubmitNodeCollector`, graphics classes under
  `com.mojang.renderpearl`, `FirstPersonHandsAndItemsRenderer`, key codes that are not GLFW's.
- Custom 3D in a GUI is a `PictureInPictureRenderer` registered with Fabric's
  `PictureInPictureRendererRegistry`; the state is added through `graphics.guiRenderState`.
- A custom shader needs no mixin: `RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)`
  with `withVertexShader`/`withFragmentShader(Identifier)` (files under
  `assets/<namespace>/shaders/`), extra samplers through
  `BindGroupLayout.builder().withUniform(name, COMBINED_IMAGE_SAMPLER)`, registered with
  `RenderPipelines.register` during mod init; then `RenderSetup.builder(pipeline)
  .withTexture(name, id)...` and `RenderType.create`. Shaders are GLSL 330 with explicit
  `layout(location)` on everything passed between stages and `#include <minecraft:...>` for
  the uniform blocks (there is a Vulkan backend too). Entity normals and light directions
  share one space; get the eye into it with `transpose(mat3(ModelViewMat))`.
- MixinExtras ships with the loader (`@WrapMethod` is used here).

## Environment
- I develop on Windows. CS2 is installed (Steam library on `D:`).
- **Minecraft 26.3**, Fabric Loader 0.20.1, Fabric API, Java 25 (JDK 26 builds it fine),
  Gradle 9.8.2, Loom 1.19.4 (plugin id `net.fabricmc.fabric-loom`).
- Mod id `knives_as_swords`, package `io.github.icxd.knives_as_swords`, MIT license.

## Working on this repo
- `./gradlew build` runs the unit tests: synthetic VPKs, glTF, EXR and flat colours only,
  no game data. The tests driving a stand-in CLI script are skipped on Windows.
- `./gradlew runClientGameTest` runs the in-game smoke test in `src/gametest` against a
  made-up game folder. With `KNIVES_VISUAL_EXTRACTOR=<path to Source2Viewer-CLI>` set it
  also runs `KnivesVisualGameTest` against the real game: every sword, animations, finishes,
  the settings screen. Screenshots land in `build/run/clientGameTest/screenshots`; they
  show game content and stay there. The run folder is wiped each time, so everything is
  converted afresh (about two minutes in all).
- The dev client (`./gradlew runClient`) keeps its extractor and cache in
  `run/knives_as_swords/` (ignored).
- Do not `cd` into `build/run/...` in a shell that outlives the command: Gradle cannot
  delete the folder while something sits in it.
