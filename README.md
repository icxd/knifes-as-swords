# Knives as Swords

A client-side Fabric mod of visuals: the kind of eye candy cheat clients are known for, without
any of the cheating. Its biggest part replaces how vanilla swords look with knives from a very
popular first person shooter game by Valve. Around that there are effects, cosmetics and HUD
panels, each switched on and off in a menu. It is purely visual: item behaviour, damage and
everything sent to the server stay untouched, and other players see normal swords.

The mod ships **code only**. Models, animations and finishes are converted at runtime from the
game files already installed on your own machine (CS2 via Steam), into a local cache. If the game
is not installed, swords simply keep their vanilla look.

## What it does

- Every kind of sword is drawn as a knife of your choice, wherever swords are drawn: in your hand
  in first and third person, in inventories, on the ground, in item frames, in other players' and
  mobs' hands.
- In first person the knife uses its own animations: drawing it when you switch to it, idling,
  slashing when you attack (a stab for a fully charged hit) and, on a key, the inspect animation.
- Each sword can have a finish with a pattern seed and wear, painted from the game's own recipe
  for that finish. Pattern seeds place the pattern as they do in the original game.
- Knives are drawn with a shader of their own, so steel and anodized finishes reflect light the
  way metal does.
- Knives have their own sounds, from your game files like their looks: drawing, slashing,
  hitting, inspecting. Only you hear them.
- Knives have effects, in a look you pick per sword (Phantom, Storm, Inferno, Void, Venom, Solar,
  Prism, or none): a swung blade leaves a trail of light and afterimages, bolts crawl over the
  steel, a hit throws sparks, and the knife glows. The effects follow what the knife does, and
  are drawn by a small effects engine of the mod's own, without any textures.
- You can also put a look together yourself: how much of each effect, whether sparks fall or
  rise, and the colours, in a gradient editor with as many colours as you like (up to eight).
  The editor shows a knife slashing by itself, so you see what you are changing.
- Everything is picked in game, per kind of sword, with a preview you can turn around.

Besides the knives there are visuals of the mod's own, each with its settings and colours:

| | |
| --- | --- |
| Combat | Hit effects, kill effects (burst, pillar of light, bolts, rising wisps) |
| Player | A trail behind you, rings where you jump and land, a halo, hat or wisps to wear |
| World | Your own crosshair, a glowing block outline, tinted sky and fog, the colour of the hurt flash |
| HUD | Keystrokes, clicks per second, FPS, armour and effects, wherever you drag them |

The menus look like the effects they set up: dark glass with glowing rims, switches and sliders
that light up, embers drifting behind them. Each part of the menu has colours of its own, the
knife settings glow in the colours of the chosen sword's effects, and every panel on the HUD has
a gradient you can change.

| Key | Does |
| --- | --- |
| `Right Shift` | Open the menu for all visuals |
| `K` | Open the knife settings |
| `G` | Inspect the knife in your hand |

### Fair play

Every visual only restyles or decorates what the game already shows you. Nothing is drawn through
walls, nothing is outlined or tracked, nothing is predicted, nothing that hinders you is removed
(dark stays dark, fog stays as thick, only its colour changes), and nothing sent to the server is
touched.

Two visuals show something the game does not show by itself: the **target HUD** and **damage
numbers** say how much health the thing you are fighting has and loses. They are off unless you
switch them on, are marked `!` in the menu, and only ever concern what is right in front of you.
Some servers do not allow even that; check before you switch them on.

All can be rebound under *Controls → Knives as Swords*.

## Requirements

- Minecraft 26.3 with Fabric Loader and Fabric API
- Java 25
- CS2 installed through Steam

## How it works

- Steam's library folders are searched for the game; nothing is read from the network.
- To convert models the mod uses [Source2Viewer-CLI](https://github.com/ValveResourceFormat/ValveResourceFormat)
  (MIT). It is not bundled: the first time it is needed, the mod asks in game before downloading
  the pinned release from the project's GitHub page and verifies its SHA-256. You can instead
  point `extractorPath` in `config/knives_as_swords.json` at your own copy.
- Knife sounds are converted the same way. The game keeps them as MP3, which Minecraft cannot
  play by itself, so the mod carries a small MP3 decoder (JavaMP3, MIT; see
  [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md)).
- The extractor and everything it produces live in `<game dir>/knives_as_swords/` and never leave
  your machine. The cache belongs to one version of the game and is rebuilt after an update.
- The first time a knife or a finish is used it takes a few seconds to convert; until then the
  sword is shown as a sword.

## Settings

Everything in the settings screen is saved to `config/knives_as_swords.json` as you change it.
A few things are only in that file:

| Setting | Meaning |
| --- | --- |
| `cs2Dir` | The game's install folder, if Steam's library list does not lead to it |
| `extractorPath` | Your own copy of Source2Viewer-CLI, instead of the one the mod downloads |
| `enabled` | Master switch; `false` keeps every sword vanilla |
| `shine` | `false` draws knives like any other item, without reflections (also in the screen) |
| `effects` | `false` turns off all effects at once (the look of each sword's is picked in the screen) |
| `textureSize` | Longest side of knife textures in pixels (default 1024) |

## Known limits

- Reflections show an imagined room (bright above, dark below), not the Minecraft world around
  you, so a knife looks the same in a cave as on a mountain apart from how brightly it is lit.
- Pattern placement was checked against the original game with one well-known seed. Where wear
  scratches fall for a given seed may still differ.
- Knives are drawn without arms in first person, like any item in Minecraft.
- Other players' knives glow and leave trails when swung, but their hits throw no sparks. Knives
  in inventory slots have no effects.
- Names of knives and finishes are shown in English.

## Development

```
./gradlew build                # compile + unit tests (no game files involved)
./gradlew runClient            # dev client
./gradlew runClientGameTest    # in-game smoke test (needs no game files and no network)
```

With `KNIVES_VISUAL_EXTRACTOR` set to the path of a Source2Viewer-CLI executable,
`runClientGameTest` also runs a visual test against the real game and leaves screenshots in
`build/run/clientGameTest/screenshots`. The client-side `/knives` command shows what was found
(`/knives`, `/knives list`, `/knives rescan`, `/knives export <knife>`).

No game asset, extracted or converted, may ever be committed to this repository, and that
includes screenshots of them.

## License

MIT, see [LICENSE](LICENSE). Not affiliated with or endorsed by Valve or Mojang.
