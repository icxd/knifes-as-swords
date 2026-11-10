# Project: a client-side visuals mod for Minecraft (knives, effects, HUD)

## Goal
A purely client-side Fabric mod of visuals: the kind of eye candy cheat clients are known
for, without any of the cheating. Every visual is a module the player switches on and off in
a client-style click menu.

Its first and biggest part visually replaces Minecraft's vanilla swords with
Counter-Strike 2 knives (models, skins/finishes, animations), using ONLY files from the
player's own local CS2 install. Everything else (effects, cosmetics, HUD widgets) is the
mod's own and uses no game files at all. The mod ships code only.

The player picks, in game, per sword: knife model, skin (finish), pattern (seed), wear, and
the look of the knife's effects.

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

### Fair play (just as non-negotiable)
The mod must be safe to use on any server. The test for every visual: **it may only restyle
or decorate what the game already shows the player.**
- **Never reveal what is hidden.** Nothing is drawn through walls. No outlines, tracers,
  X-ray or finders for players, mobs, chests or ores. Nothing about entities that are out of
  sight, out of range or invisible. A visual attached to an entity is depth-tested like the
  entity itself.
- **Never predict.** No trajectories, no paths, no timers for things the game does not time
  on screen.
- **Never remove an impairment.** Dark stays dark (no full brightness, no forced daylight),
  fog may be tinted but not thinned, blindness, nausea and the hurt shake stay.
- **Never change what the player can do or what is sent to the server.** No reach, timing,
  movement or input automation. Mixins only read, as before.
- **Information the vanilla screen does not show** (the health of what you are fighting,
  damage numbers) is allowed only as its own visual that is off by default, says so in the
  menu (`Visual.showsInformation`), and only ever concerns an entity in plain sight: in line
  of sight and not invisible. Some servers do not allow even that; the menu says so.
- When in doubt, leave it out.

Context: this sits in a tolerated gray zone, not "legal". Steam's Subscriber Agreement
prohibits reverse engineering/extracting content without Valve's consent; Valve
tolerates it in practice (Source 2 Viewer is on the Valve Developer Community wiki).
Distribution is where real risk starts, hence the rules above.

## Status
All seven milestones are done and checked against a real install (CS2 build 26445667,
Nov 2026): locating the game, converting models, drawing knives everywhere a sword is
drawn, first-person animations, the settings screen, and finishes with pattern and wear.

On top of the milestones: knives are drawn with their own shader, so metal reflects
(`shine` in the config), pattern seeds place patterns as in the game, and knives have effects
(trails, afterimages, bolts, sparks, glow) from the mod's own effects engine.

Since then the mod is growing into a general visuals mod (see Goal and Fair play). There is a
framework for visuals with a client-style click menu (Right Shift), and a first set of them:
- Combat: hit effects, kill effects (burst, pillar, bolts, souls), damage numbers (information),
  and the knife's own sounds from the game files.
- Player: trail, jump rings, halo (halo, hat, wisps).
- World: crosshair, block outline, sky and fog tint, hurt colour.
- HUD: keystrokes, CPS, FPS, armour, effects, target HUD (information).

All of the mod's menus and HUD panels share one look of their own, made to go with the effects:
dark glass, light in the colours of whatever is being set up (see "The look of the menus").

Not done, in rough order of value:
- **Real surroundings in the reflections.** The shader mirrors an imagined room (bright
  above, dark below), not the Minecraft world, and has no normal maps or pearlescence.
- **Seed parity is checked with one seed only** (Case Hardened 387 on the karambit, against
  a picture from the game). Where wear scratches and dirt sit for a seed is still a guess.
- **Effects for what other players do.** Their knives glow, crackle and leave trails when
  swung, because that can be seen; their hits throw no sparks, because the client is not told
  about them in time. Glow is faked with soft additive shapes; there is no bloom pass.
- **Sounds that fit each knife and each animation.** The knife sounds played are the ones all
  knives share, started by the same events as the effects (draw, swing, hit, inspect). The game
  itself starts sounds from inside its animations and has own sounds for some knives
  (`sounds/weapons/knife_<name>/`); neither is used. Other players' knives are silent.
- More visuals: enchantment glint colour (there is no uniform for it; the texture has to be
  swapped), view model tweaks, cosmetics on other players.
- The mod's name and id still say "knives".
- Per-item overrides (e.g. by a sword's custom name), Mod Menu entry, names in
  languages other than English, arms in first person.
- The prompt that asks before downloading the converter is the game's own `ConfirmScreen` and
  looks like it; tooltips are the game's too.

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
  animation clips), `ResourceDumper` (material settings as text, textures as PNG/EXR),
  `SoundExporter` (the knife sounds as MP3).
- `gltf`, `model`: reading what the extractor wrote. `KnifeMesh`, `KnifeAnimations`.
- `finish`: painting a finish onto a knife, pure Java. `FinishPainter` gathers settings and
  textures, `FinishCompositor` does the per-pixel work and returns two images: colour, and a
  surface map (red roughness, green metalness, blue ambient occlusion).
- `render`: `KnifeVisuals` (loaded/loading knives, keyed by `Appearance`), `KnifeReplacement`
  and `KnifeItemRenderer` (item layer swap), `FirstPersonKnife` (animated first person),
  `KnifePipelines` (the render pipeline and render types for the knife shader).
- `fx`: the effects engine. It is the mod's own from top to bottom: no textures, nothing from
  the game files. The logic is plain Java without Minecraft classes and unit-tested: `FxBatch`
  (a list of glowing quads: ribbon, beam, ring, dot), `BladeShape` (where a mesh's blade is),
  `BladeTrail`, `SparkSystem`, `ArcBolts` (no state: a function of time and a seed),
  `Gradient`, `FxStyle` (the looks), `CustomEffect` (a look of the player's own, as the config
  holds it). The Minecraft side: `FxPipelines`, `FxDraw`, `MotionFx` (one knife whose every
  move is known), `PreviewFx`, `ItemFx`, `WorldFx`, and `WeaponFx` as the way in.
- `sound`: the knife sounds. `KnifeSound` (the moments that have one, and which files are
  takes of each), `Pcm` (a decoded sound; MP3 decoding), `PcmStream` (what Minecraft's sound
  engine reads from), `KnifeAudio` (loading on the worker thread, playing).
- `visuals`: everything that is not a knife. `Visual` is one thing the player can switch on
  and off, with `Setting`s (toggle, slider, choice, colour, colours, action) declared as
  fields; `Visuals` is the list and hands the game's moments on (tick, attack, world, HUD);
  `AllVisuals` registers them and holds the few extra hooks. Sub-packages `combat`, `player`,
  `world`, `hud` (`HudWidget`: a panel the player drags to its place).
- `screen`: `KnivesScreen` and its turnable preview; `EffectScreen`, the editor for a look of
  one's own; `ClickMenuScreen`, the menu for all visuals; `GradientScreen` and
  `GradientEditor`, the colour controls they share.
- `ui`: the look all of the above share. `UiPipelines` and `Glow` (shapes: glass, light, round
  corners), `Theme` (colours), `Ease` (numbers that glide), `Embers` and `Backdrop` (what lies
  behind a menu), and widgets that behave like the game's and are drawn with `Glow`:
  `GlowButton`, `GlowCycle`, `GlowSwitch`, `GlowSlider`, `GlowEditBox`.
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

### Effects
- Each sword has a look (`FxStyle`: a `Gradient` of colours, running from fresh to fading, and
  how much of each effect). In the config a sword's `effect` is a ready-made look's name,
  `none`, `custom`, or empty for the default of that sword (`WeaponFx.BY_SWORD`);
  `effects: false` turns everything off.
- `custom` means the sword's `customEffect` (`CustomEffect`): an amount for trail, afterimages,
  bolts, sparks, glow, white heat and raggedness, what sparks do (-1 fall to 1 rise), how fast
  the colours cycle, and the gradient as a list of `{at, color}`. `EffectScreen` edits it:
  sliders, a gradient bar with a marker per colour (click the bar to add, drag to move,
  right-click to remove), hue/saturation/brightness sliders and a `#RRGGBB` box for the picked
  colour. It works on a copy and saves a few ticks after the last change, never during a drag.
  Its preview is a knife that slashes by itself (`PreviewFx`), because most effects only show
  in motion.
- Everything is light added on top of the picture, through two pipelines of the mod's own
  (`FxPipelines`), both depth-tested without writing depth. "Energy" draws the quads of an
  `FxBatch`; the vertex slot for a normal carries the shape (ribbon, beam, ring, dot), how
  white-hot and how ragged instead, and `fx_energy.fsh` turns that into soft-edged light.
  "Ghost" draws the knife's own triangles as a shell that is bright at the outline
  (`fx_ghost.fsh`): the glow on the knife, and its afterimages.
- `MotionFx` is the effects of one knife whose every move is known. `FirstPersonKnife` has one
  and `PreviewFx` another. In first person it works in the animations' space, where the eye
  sits at (`SHIFT_RIGHT`, `SHIFT_DOWN`, 0). With effects on, `FirstPersonKnife` takes each frame's
  animation in steps of at most 1/120 s and reports the pose after each, so a slash is followed
  along its way. Tip speed drives everything: trail, afterimages, shed sparks, how much the
  blade crackles. Events add to it: drawing the knife, a swing, a hit landing (sparks and a
  ring of light from the tip, a moment after the swing starts), inspecting.
- Knives drawn as items (`ItemFx`) glow and crackle, with no state at all. The special-model
  argument is a `KnifeItemRenderer.Draw`, which says how and in whose hand the knife is drawn.
- Trails in the world (`WorldFx`): `LevelRendererMixin` marks the start of
  `LevelRenderer.submitFeatures`; a knife drawn between then and Fabric's `COLLECT_SUBMITS` is
  in the world, any other is in a menu and gets no effects (first-person contexts excepted).
  A held knife's blade is followed relative to its holder's position in that frame, so walking
  leaves no trail and swinging does. Trails and the sparks of a hit seen from outside are
  submitted in `COLLECT_SUBMITS`, in world axes relative to the camera.
- Inventory slots (`ItemDisplayContext.GUI`) never get effects: they are cached pictures.

### Visuals
- A visual is a subclass of `Visual` with its settings as fields (`toggle(...)`,
  `slider(...)`, `choice(...)`, `color(...)`, `colors(...)`, `action(...)`), overriding the
  moments it cares about: `tick`, `attack`, `world(WorldFrame)`, `hud(graphics, editing)`.
  They are only called while it is on. Add it in `AllVisuals.register()` and give it texts
  under `knives_as_swords.visual.<id>` (name), `.about`, `.<setting>` and, for choices,
  `.<setting>.<option>`. **Check it against the fair-play rules first.**
- Saving: `config.visuals` maps a visual's id to its settings as text plus `enabled`. A
  setting reads back what it wrote and ignores what it cannot understand. `Visuals.changed()`
  saves a few ticks after the last change.
- Light in the world goes through `WorldFx.paint`: every frame after the world has gathered
  what to draw, each visual gets a `WorldFrame` (the shared `FxBatch`, camera position, time)
  and adds shapes in world axes relative to the camera. It is depth-tested like everything
  else, so nothing shows through walls. `WorldSparks` and `WorldPulses` (flash, ring, ground
  ring, beam) are there to be reused; `WorldFrame.text` writes a short text facing the camera.
- `HudWidget`s draw at the origin and say how big they are; the base class places and scales
  them and lets the menu drag them. Their place is saved as a share of the room on screen (less
  a margin, so the plate behind them fits). The plate is `Glow.plate`: thin dark glass with a
  line of light in the widget's own `glow` colours, which `glowColor(along)` hands out.
- Hooks beyond the common moments (all in `AllVisuals`): the crosshair replaces Fabric's
  `VanillaHudElements.CROSSHAIR` element (and hands back to it where the game has rules of its
  own: third person, spectators); the block outline listens to `BEFORE_BLOCK_OUTLINE` and
  cancels the game's; sky and fog are recoloured in `LevelExtractionEvents.END_EXTRACTION`
  (same brightness, same fog distance, and not at all in fog that is there to hinder); the
  hurt colour repaints the upper half of the game's 16x16 overlay texture
  (`OverlayTextureAccessor`) and restores it when switched off; clicks are counted by
  `MouseHandlerMixin` (reads only).
- Visuals that show information the game does not (`showsInformation()`): `TargetHud`,
  `DamageNumbers`. Off at first, marked "!" in the menu, and only about what the crosshair is
  on or what the player just hit, while it is in line of sight and not invisible.
- The menu (`ClickMenuScreen`) is drawn by hand, not with widgets: a layout pass turns panels
  into rows, and the same rows are used for drawing and for the mouse. Left-click toggles,
  right-click unfolds settings, titles drag, the wheel moves a panel. It keeps the world in
  view (no blur) so changes show at once. The knives are an entry in it (`Knives`) whose
  switch is `config.enabled`. Folding glides: everything is laid out at full size and each row
  carries the range of heights it is shown in so far (drawing is clipped to it, and so is the
  mouse). `config.menu.layout` says which panel sizes the saved places were made for; places
  made for other sizes are laid out afresh.

### The look of the menus
- Everything is drawn through `Glow`, never with the game's sprites: `fill`/`rounded`/`disc`
  cover what is behind them, `light`/`dot`/`halo` add to it as the effects in the world do, and
  `pane`, `plate`, `button`, `slider`, `bar`, `text` are made of those. Coordinates are floats.
- Underneath are two pipelines (`UiPipelines`: translucent and additive) with one shader,
  `ui_glow.fsh`. A quad's texture coordinates say what it is: around 0 a plain rectangle, around
  11 a soft light (brightest where the coordinates are exactly 11, gone one unit away), around
  21 a hard-edged disc of radius one. A round corner is a quarter of such a disc, a halo is
  soft-light quads whose "middle" lies on the edge they spill from.
- A `pane`'s rim is a slightly larger rounded rectangle under its body, so it also tints a
  see-through body. For thin glass use `plate` (a line of light instead of a rim).
- Colours: `Theme.colors(category)` for the menu's panels; a screen about one sword takes the
  gradient of that sword's effects, the effect editor the gradient being edited. `Theme.now`
  lets a light wander slowly back and forth through its gradient.
- Motion: `Ease.frame()` once per frame drawn (each screen's `extractRenderState`), then
  `Ease.toward(key, target, rate)` for numbers kept by key (the click menu, forgotten when it
  closes) or `Ease.step(value, target, rate, Ease.seconds())` for a number a widget keeps itself.
- Screens draw `Backdrop` in `extractBackground` (after `extractBlurredBackground`), their
  widgets are the `Glow*` classes, and lists override the four `extract...` hooks.

### Sounds
- `SoundExporter` converts the folder `sounds/weapons/knife/` in one call (the trailing slash
  keeps `knife_bowie/` and the like out) into `cache/<buildid>/sounds/`; a `converted` file
  written last marks a finished conversion.
- `KnifeAudio.prepare` (from the `KnifeSounds` visual's tick) loads them once per scan on the
  worker thread: every file whose name is a moment's prefix plus a number is decoded and kept
  in memory. It never triggers the converter's download prompt.
- Playing goes through Minecraft's own sound engine, so its volume sliders apply (kind
  "players"): the mod's `sounds.json` declares one sound, `knives_as_swords:knife`, whose only
  entry is Fabric's placeholder (`fabric-sound-api-v1:empty`, `"stream": true`); a
  `SoundInstance` then overrides `getAudioStream` (Fabric adds that method to the interface)
  and returns a `PcmStream`. The stream hands out a direct `ByteBuffer` and returns `null`
  when it is over.
- The sounds are heard at the listener, not from a place in the world, and only with the
  knife's first-person animations. Minecraft's own attack sounds still play.

### In-game configuration
Right Shift opens the menu for all visuals, `K` opens `KnivesScreen`, `G` inspects (all
rebindable, category "Knives as Swords").
Per sword: knife, finish, pattern seed (0-1000), wear (limited to what the finish allows,
with its grade name), effect (with a "..." button into the editor for a look of one's own),
and "as knife"; for everything: animations and shine. Changes apply
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
  `cache/<buildid>/{data,textures,sounds}/<path inside the package>`.
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

### Sounds
- Knife sounds are `sounds/weapons/knife/*.vsnd_c` (223 shared by all knives, 349
  counting the folders of single knives). The extractor writes each as `.mp3` plus a small
  `.vsnd` text file. Names are `<what>_<take>.mp3`: `knife_draw_med_clean_01..07`,
  `knife_swish_light_*`, `knife_swish_heavy_*`, `knife_hit_light_flesh_*`,
  `heavy_hit_flesh_*`, `knife_inspect_turn_*`, and many more that are not used.
- They are MPEG-1 Layer III, 44.1 kHz stereo, variable bitrate, and begin with a frame of
  bookkeeping (Xing/LAME) that decodes to silence, followed by the encoder's padding.
  `Pcm.withoutLead` cuts that off, or every sound would start about 50 ms late.
- Minecraft plays Ogg Vorbis only. The decoder is JavaMP3 (`fr.delthas:javamp3` 1.0.1, MIT),
  shipped inside the mod's jar (`include` in `build.gradle`; its notice is in
  `THIRD_PARTY_LICENSES.md`, which goes into the jar too). **Its `decodeFullyInto` stops after
  the first frame**; read the `Sound` as a stream until it ends instead. On input that is not
  MP3 it throws an `IOException` at once.

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
- **A render type that blends needs a second set of pipelines.** With the video setting
  "improved transparency" on, see-through things in the world are drawn in three stages
  through an `OitPipelineSet` (`RenderSetup.setOitPipelines`); a blending `RenderType` without
  one makes `PreparedRenderType.drawFromBufferOit` throw. Build it with
  `OitPipelineSet.builder(suffix, RenderPipeline.builder(snippet).withShaderDefine("OIT_ADDITIVE"))`
  and register it with `RenderPipelines.register`. The fragment shader has to play along; copy
  the pattern of `rendertype_lightning.fsh` (`#include <minecraft:oit.glsl>`, `fragColor` only
  declared `#ifndef OIT_ALPHA_ONLY`, `executeAlphaOnlyPhase` / `sampleColorForAccumulation`).
  The first-person hand and menus always blend the classic way.
- A picture-in-picture (the settings preview) is drawn into a texture that is then pasted onto
  the screen with premultiplied alpha, and its pixels with alpha exactly 0 are thrown away.
  Light added into it therefore has to add alpha as well, and not squared as
  `BlendFunction.LIGHTNING` does it (faint light rounds to 0 and every glow gets a hard edge);
  hence `FxPipelines.ADD_LIGHT`.
- `submitCustomGeometry` with a blending render type lands in the translucent phase: drawn
  after everything solid, in the order submitted.
- `LevelRenderer.render` gathers entities first of all (`submitFeatures`), before any render
  pass. Fabric's `LevelRenderEvents.COLLECT_SUBMITS` fires at its end; its pose stack is the
  identity, meaning world axes with the camera at the origin (`cameraRenderState.pos`).
- Vertex attributes get their shader locations in the order of the vertex format
  (`POSITION_TEX_COLOR_NORMAL`: 0 position, 1 UV0, 2 colour, 3 normal). A normal is stored as
  three signed bytes exactly as given, not normalized. A pipeline of one's own may draw
  `PrimitiveTopology.TRIANGLES`; no doubled corners needed there.
- Minecraft's own shaders are in the game jar under `assets/minecraft/shaders/`, including the
  `include/*.glsl` files; read them there.
- The heads-up display is `net.minecraft.client.gui.Hud` (`minecraft.gui.hud`); `Gui` manages
  screens: `minecraft.gui.screen()`, `minecraft.gui.setScreen(...)`. Fabric's
  `HudElementRegistry` adds (`addLast`), removes and replaces (`replaceElement`) elements; a
  `HudElement` is `(GuiGraphicsExtractor, DeltaTracker) -> void`. Everything is hidden with F1.
- Drawing in two dimensions: `graphics.pose()` is a JOML `Matrix3x2fStack` (`pushMatrix`,
  `translate(x, y)`, `scale(x, y)`, `popMatrix`); there is no depth, later things are put on
  top with `graphics.nextStratum()`. `graphics.text` draws nothing if the colour's alpha is 0:
  always pass `0xFF......`. Items: `graphics.item(stack, x, y)`; sprites of the GUI atlas:
  `graphics.blitSprite(RenderPipelines.GUI_TEXTURED, id, x, y, w, h)`.
- A screen without the blurred background overrides `extractBackground` (there is no
  `renderBackground`). `mouseScrolled(x, y, scrollX, scrollY)`; `keyPressed(KeyEvent)`.
- The fog colour of a frame is `cameraRenderState.fogData.color` (a `Vector4f`), the sky
  colour `skyRenderState.skyColor` (a `Vector3fc`); both are set during extraction and can be
  changed in Fabric's `LevelExtractionEvents.END_EXTRACTION` without a mixin.
- Health of other entities is synced to the client (`LivingEntity.getHealth()`); damage comes
  as its own packet (`handleDamageEvent`), death as entity event 3. There is no entity event
  for "hurt" any more.
- Text in the world: `collector.submitText(poseStack, x, y, text, shadow, Font.DisplayMode,
  light, color, background, outline)`, after `poseStack.rotate(camera.orientation)` and a
  scale of 0.025 with y flipped. `cameraRenderState.projectionMatrix` lacks view bobbing, so
  projecting world points onto the HUD by hand would wobble against the world.
- The enchantment glint has no colour uniform (`GlintAlpha` is strength only); recolouring it
  means registering another texture under its identifier.
- A shape of one's own in a GUI: implement `GuiElementRenderState` (`buildVertices`,
  `pipeline`, `textureSetup` = `TextureSetup.noTexture()`, `scissorArea`, `bounds`) and hand it
  to `graphics.guiRenderState.addGuiElement`; take the pose as `new Matrix3x2f(graphics.pose())`
  and the scissor from `graphics.scissorStack.peek()`. **The bounds decide what lies on top of
  what** within a stratum, so they have to be right (and cut to the scissor). The pipeline is
  built from `GLOBALS_SNIPPET` plus `BindGroupLayouts.PROJECTION` and `DYNAMIC_TRANSFORMS`, with
  `POSITION_TEX_COLOR` quads and no depth state; vertices go in with
  `addVertexWith2DPose(pose, x, y).setUv(u, v).setColor(argb)`. `fwidth` works there.
- Skinning the game's widgets: a button is `AbstractButton.extractContents` (its
  `extractWidgetRenderState` is final); `Button` and `CycleButton` cannot be subclassed
  usefully, so the mod has its own on `AbstractButton`. A slider's
  `AbstractSliderButton.extractWidgetRenderState` can be overridden (call `handleCursor`); its
  handle's middle is at `x + 4 + value * (width - 8)`. A list has `extractListBackground`,
  `extractListSeparators`, `extractSelection`, `extractScrollbar`; **its `scrollBarX()` is
  `getRowRight() + 8`, which lies outside a narrow list**, so override that and `getRowLeft()`.
  An `EditBox` draws its frame only if `isBordered()` says so but places its text by the field
  behind it, so overriding `isBordered()` during the draw swaps the frame and nothing else.
- `Screen.extractBackground` is: panorama if there is no world, `extractBlurredBackground`,
  the menu background texture, then `minecraft.gui.hud.extractDeferredSubtitles()`. A screen
  with a background of its own keeps the first, second and last.
- Input is SDL3: key codes are scancodes (`InputConstants.KEY_*`, e.g. `KEY_RSHIFT` 229), and
  mouse buttons are numbered left 1, middle 2, right 3 (`InputConstants.MOUSE_BUTTON_*`). A widget of one's own extends `AbstractWidget`
  (`extractWidgetRenderState`, `onClick`, `onDrag`, `onRelease`); it only hears about the left
  button unless `isValidClickButton` says otherwise, and only the left button drags.

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
  the settings screen, effects. Screenshots land in `build/run/clientGameTest/screenshots`;
  they show game content and stay there. The run folder is wiped each time, so everything is
  converted afresh (about three minutes in all). `KNIVES_VISUAL_ONLY=effects` runs only the
  part about the knives' effects, `KNIVES_VISUAL_ONLY=visuals` only the part about the other
  visuals, their menu and the colour screen, `KNIVES_VISUAL_ONLY=sounds` only the check that the knife sounds load
  and are handed to the sound engine.
- The dev client (`./gradlew runClient`) keeps its extractor and cache in
  `run/knives_as_swords/` (ignored).
- Do not `cd` into `build/run/...` in a shell that outlives the command: Gradle cannot
  delete the folder while something sits in it.
