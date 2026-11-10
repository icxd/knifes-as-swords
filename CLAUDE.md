# Project: CS2 knives in Minecraft (client mod)

## Goal
A purely client-side Fabric mod that visually replaces Minecraft's vanilla swords with
Counter-Strike 2 knives (models, skins/finishes, animations), using ONLY files from the
player's own local CS2 install. The mod ships code only.

The player picks, in game, per sword: knife model, skin (finish), pattern (seed) and wear.

## Hard rules (non-negotiable)
- **Never commit, bundle, download-host or distribute any Valve asset** (models,
  textures, animations, sounds, item definitions, extracted or converted). Not even
  "just textures", not in test fixtures, not in a server resource pack.
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

## Architecture
- **Client-side only Fabric mod** (Minecraft Java). No server component, no server
  changes. It must work in singleplayer and on any multiplayer server.
- **Purely visual.** Replaces how vanilla swords render (held in first/third person,
  in inventory/GUI, dropped). Never changes item behavior, damage, attack speed,
  hitboxes or anything sent to the server. Other players see normal swords.
- Swords to cover: wooden, stone, iron, golden, diamond, netherite (and any
  future vanilla sword, if easy).

## In-game configuration
- Per sword type, the player sets:
  - **Knife model** (karambit, butterfly, etc., from what's found in their CS2 install)
  - **Skin** (paint kit / finish, or "vanilla" no-finish)
  - **Pattern** (pattern seed, CS2 range 0–1000)
  - **Wear** (float 0.0–1.0, ideally with the named ranges shown: Factory New,
    Minimal Wear, Field-Tested, Well-Worn, Battle-Scarred)
  - Option to disable replacement for that sword (show vanilla)
- Config screen reachable in game (keybind and/or Mod Menu integration), with a live
  3D preview of the knife if feasible.
- Persist config locally (client config file). Changes apply without restart.
- Stretch goal: per-item overrides (e.g. keyed by a sword's custom name), since
  individual items have no stable client-side ID in multiplayer.

## Extraction pipeline
- Library: **ValveResourceFormat (VRF)**, the library behind Source 2 Viewer
  (C#/.NET). Reads VPK archives and compiled `_c` resources; exports models
  and animations to glTF 2.0. Also ships `Source2Viewer-CLI`.
- **Decided:** invoke Source2Viewer-CLI as a subprocess (not a Java port). The CLI is
  MIT licensed but ~50 MB per platform, so it is **not bundled**: on first use the mod
  asks in game, then downloads the pinned release from VRF's GitHub and checks its
  SHA-256 (`ExtractorRelease`). `extractorPath` in the config points at an own copy instead.
- Finding CS2: Steam root (Windows registry / default folders) → `libraryfolders.vdf`
  → the library with `steamapps/appmanifest_730.acf` → `common/<installdir>`. Assets
  are in `game/csgo/pak01_dir.vpk`. `cs2Dir` in the config overrides the search.
- Cache is keyed by CS2 build id (`buildid` in the app manifest); other builds are
  pruned on scan. Layout: `<game dir>/knives_as_swords/{tools,cache/<buildid>/models/<knife>}`.
- The VPK *index* is read in Java (`VpkDirectory`, cheap and needs no extractor);
  everything that touches resource contents goes through the CLI.

## Hard parts
- **Skins are not plain textures.** CS2 weapon finishes are composited at runtime from
  paint kits + pattern seed + wear (composite materials). VRF does **not** composite
  (`vcompmat` only dumps as text), so the mod has to do it itself. Plan: start with
  vanilla (no finish) knives, then implement compositing.
- **Rendering**: skeletal glTF animation isn't native to Minecraft. Decide between a
  custom glTF renderer in the mod vs. converting to an existing animation format.
  Minecraft 26.x renders through Mojang's own abstraction with OpenGL *and* Vulkan
  backends, so a custom renderer must go through that API, never raw GL calls.
- Animations to support first: draw/deploy, idle, inspect, attack (light/heavy).

## Environment
- I develop on Windows.
- **Minecraft 26.3**, Fabric Loader 0.20.1, Fabric API, Java 25, Gradle 9.8.2,
  Loom 1.19.4 (plugin id `net.fabricmc.fabric-loom`). 26.x is unobfuscated: Mojang's
  names, no Yarn, no mappings line. Many names differ from older tutorials
  (`minecraft.gui.setScreen`, Fabric's `ClientCommands`, ...): check the jar, don't guess.
- Mod id `knives_as_swords`, package `io.github.icxd.knives_as_swords`, MIT license.

## Suggested milestones
1. ✅ Locate CS2 + list knife models from the VPK (console output only).
2. 🟡 Export one knife (default finish) to glTF in the local cache. Code is in place and
   tested up to the real CLI, but never run on a real model: `/knives export <knife>`.
3. Render it in place of one vanilla sword, held in first person, static.
4. Extend to all sword types and all render contexts (third person, GUI, dropped).
5. Animations (deploy, idle, inspect, attack).
6. In-game config screen: knife model per sword, persisted locally.
7. Finish compositing (paint kit, pattern seed, wear) + skin/pattern/wear in config.

## Facts checked so far (re-check after game or VRF updates)
CS2 file layout, from SteamDatabase's public file listing (Nov 2026):
- 22 knives, one model each: `weapons/models/knife/knife_<id>/weapon_knife_<id>.vmdl_c`
  (`karambit`, `butterfly`, `m9`, `default_ct`, `default_t`, ...).
- Next to each model: `materials/weapon_knife_<id>.vmat_c` and
  `materials/composite_inputs/` (ao, masks, pos, surface textures) for finishes.
- Animations are **not** in the model. They are Animgraph 2 clips:
  `animation/anims/viewmodel/knife/knife_<id>/{draw,idle1,idle2,light_hit1,light_miss1,
  heavy_hit1,heavy_miss1,lookat01,...}_<id>.vnmclip_c`, skeleton in
  `animation/skeletons/weapons/knife_<id>.vnmskel_c`. VRF can export clips to glTF.
- Item/paint-kit definitions: `scripts/items/items_game.txt`. Finish materials:
  `weapons/paints/**/*.vcompmat_c` (~1000 under `legacy/`).

Source2Viewer-CLI 20.1 (go by its own `--help`; the website documents unreleased options):
- glTF export needs `--vpk_decompile` as well as `--gltf_export_format`.
- With an existing output folder it writes `<folder>/<path inside the VPK>.glb`, textures
  as separate PNG files beside it, collision as `<name>_physics.glb`.
- It exits with 0 even when a file fails; failures go to stderr and to an
  `exceptions.txt` in its working directory. Check for the output file instead.

## Working on this repo
- `./gradlew build` runs the unit tests (synthetic VPKs and a stand-in CLI script; no
  game data). `./gradlew runClientGameTest` runs the in-game smoke test in
  `src/gametest` against a made-up game folder; screenshots land in
  `build/run/clientGameTest/screenshots`.
- `/knives`, `/knives list`, `/knives rescan`, `/knives export <knife>` are client-side
  dev commands until the config screen exists. `rescan` re-reads the config file.
