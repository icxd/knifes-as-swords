# Knives as Swords

A client-side Fabric mod that replaces how vanilla swords look with knives from a very popular
first person shooter game by Valve. It is purely visual: item behaviour, damage and everything
sent to the server stay untouched, and other players see normal swords.

The mod ships **code only**. Models are converted at runtime from the game files already
installed on your own machine (CS2 via Steam), into a local cache. If the game is not installed,
swords simply keep their vanilla look.

> Work in progress: the mod currently finds the game, lists its knife models and can convert one
> to glTF in the local cache. Rendering comes next.

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
- The extractor and everything it produces live in `<game dir>/knives_as_swords/` and never leave
  your machine.

## Development

```
./gradlew build                # compile + unit tests
./gradlew runClient            # dev client
./gradlew runClientGameTest    # in-game smoke test (needs no game files and no network)
```

Until the config screen exists, the client-side `/knives` command shows what was found
(`/knives`, `/knives list`, `/knives rescan`, `/knives export <knife>`).

No game asset, extracted or converted, may ever be committed to this repository.

## License

MIT, see [LICENSE](LICENSE). Not affiliated with or endorsed by Valve or Mojang.
