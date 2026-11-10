# Artlyrics

A client-side Fabric mod for **Minecraft 1.21.11** that shows live, synced lyrics for whatever song you're
listening to, written word by word across the sky.

- **Sky mode**: words appear in 3D out in the sky, in the direction you were looking when the line started.
  Blocks in front of them hide them, like real scenery.
- **Front mode**: lines float in front of you wherever you look and are never hidden (better for PvP).
- **Top bar**: the current line at the top of the screen, with the word being sung highlighted and a
  song-progress line.
- **Animations**: word by word, karaoke (the whole line lights up as it's sung), or one big word at a time.
- **Styles**: Lavender, Dreamy, Sunset, Ice, Royal, Mint, Classic and Rainbow, with an optional smoother font,
  curved lines, shadow, size, sky height and a sync offset.
- **Preview**: a built-in demo song lets you try every look without playing music.

Lyrics come from [LRCLIB](https://lrclib.net), a free, open lyrics database. When LRCLIB has word-level timing
for a song, words appear exactly as they're sung; otherwise Artlyrics estimates word timing from the line
timestamps.

## Installing

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.11.
2. Put these in your `mods` folder:
   - `artlyrics-<version>.jar` (build it as described below, or take it from a release)
   - [Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.11
   - optional: [Mod Menu](https://modrinth.com/mod/modmenu), which adds a settings button to the mod list
3. Start the game and play a song in Spotify (or another player).

## How it finds the song

Artlyrics only reads what your computer already knows is playing. It doesn't need your Spotify login.

| OS | How | Notes |
| --- | --- | --- |
| Windows 10/11 | System media controls, through a small PowerShell helper | Works with the Spotify app, browsers (YouTube, Spotify Web, ...) and most players that show up in the Windows volume pop-up. The helper is written to `config/artlyrics/media-windows.ps1` so you can read it. It closes when Minecraft does. |
| macOS | Spotify and Apple Music, through AppleScript (JXA) | The first time, macOS asks whether Minecraft/Java may control Spotify or Music. Allow it. |
| Linux | MPRIS, through `playerctl` | Install `playerctl` (for example `sudo apt install playerctl`). |

When several players are active, Artlyrics follows the one that's playing, and prefers the one named under
**Preferred player** (default: `spotify`).

The only network requests the mod makes go to `lrclib.net`, and they contain the track's title, artist, album and length.

## Controls

| Key | Action |
| --- | --- |
| `K` | Open Artlyrics settings (or use Mod Menu) |
| *unbound* | Toggle lyrics on/off |
| *unbound* | Switch between sky and front mode |

Change them under Options → Controls → Key Binds → Artlyrics.

## Settings

| Setting | What it does |
| --- | --- |
| Lyrics | Turns everything on or off |
| Top bar | Shows the current line at the top of the screen |
| Display | *In the sky* or *In front of you* |
| Curved lines | Bends lines into a gentle arc |
| Animation | *Word by word*, *Karaoke* or *One word at a time* |
| Shadow | Drop shadow under the words |
| Style | Color look |
| Size | Text size (50–250%) |
| Font | Minecraft font, or Minecraft's smoother Unifont-based font |
| Sky height | How high above the horizon sky lyrics appear |
| Sync offset | Shifts lyrics earlier (+) or later (−) if they're off for your setup |
| Preferred player | Which app to follow when several are playing |
| Preview | Plays the built-in demo song; close the settings to watch it |
| Search lyrics again | Clears the cache and looks the current song up again |

Settings are saved in `config/artlyrics.json`.

## Troubleshooting

- **"No lyrics found"**: LRCLIB doesn't have this song yet, or the player reports an unusual title.
  Artlyrics already retries with cleaned-up titles (dropping things like "Official Video" or "Remastered").
- **"Only unsynced lyrics exist"**: LRCLIB has the words but no timestamps, so they can't be synced.
- **Lyrics are early or late**: adjust **Sync offset**. Some browsers report the playback position coarsely.
- **Nothing is detected on Linux**: check that `playerctl metadata` works in a terminal.

## Building

Requires JDK 21.

```sh
./gradlew build          # mod jar in build/libs/artlyrics-<version>+mc1.21.11.jar
./gradlew test           # unit tests for the lyrics parsers and matching
./gradlew test -Dartlyrics.liveTests=true   # also query the real LRCLIB API
./gradlew runClient      # launch Minecraft with the mod (add -Dartlyrics.demo=true to the JVM args to start the demo song)
./gradlew runClientGameTest   # launch the game, play the demo song and save screenshots of each mode
```

The game-test screenshots end up in `build/run/clientGameTest/screenshots/`. The game test needs a machine with a
real GPU; on headless software rendering (Mesa llvmpipe) Minecraft 1.21.11 only produces black frames.

## Project layout

```
src/main/java/com/artlyrics/
  ArtlyricsClient.java     entrypoint: events, keybinds
  nowplaying/              detecting the playing song (Windows / macOS / Linux / demo) and a smoothed clock
  lyrics/                  LRCLIB client, LRC and word-synced parsers, timing estimation, lookup cache
  render/                  3D sky / front renderer and the top bar HUD
  screen/                  settings screen
  config/                  settings and color styles
src/gametest/              client game test that takes screenshots
```

## Credits

Lyrics are provided by [LRCLIB](https://lrclib.net). The demo song's lyrics were written for this mod.
