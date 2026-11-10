# Artlyrics

A client-side Fabric mod for **Minecraft 1.21.11** that turns the song you're listening to into live, synced
lyrics drawn in the world around you.

- **Skywriting**: each line is written across the sky where you were looking when it started, then slowly
  drifts upward as it fades. It's part of the world, so hills and trees can get in the way.
- **Hover**: lines hang just above your crosshair and trail a little behind as you turn. Nothing covers them.
- **Song card**: a small card in the corner you pick, with the title, artist, a progress bar, the current line
  colored as it's sung, and a preview of the next line between verses.
- **Animations**: *Bloom* (words open up bright and settle into color), *Typewriter* (letters typed in time
  with the singing), *Sweep* (color sweeps along a faint line) and *Spotlight* (one big word at a time).
- **Palettes** named after Minecraft materials: Amethyst, Cherry, Ember, Prismarine, Honey, Moss, Quartz and the
  slowly shifting Aurora. Add a shadow or a sign-style outline, an optional arc, size, sky height and smoother font.
- **Demo song**: a short built-in song so you can try every look without playing music.

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
| *unbound* | Turn lyrics on/off |
| *unbound* | Switch between Skywriting and Hover |

Change them under Options → Controls → Key Binds → Artlyrics.

## Settings

| Setting | What it does |
| --- | --- |
| Lyrics | Turns everything on or off |
| Song card | Shows the song card |
| Card corner | Top left, top right or bottom right |
| Mode | *Skywriting* or *Hover* |
| Animation | *Bloom*, *Typewriter*, *Sweep* or *Spotlight* |
| Text effect | Shadow, outline or none |
| Palette | Color palette |
| Size | Text size (50–250%) |
| Font | Minecraft font, or Minecraft's smoother Unifont-based font |
| Sky height | How high above the horizon skywriting appears |
| Sync offset | Shifts lyrics earlier (+) or later (−) if they're off for your setup |
| Preferred player | Which app to follow when several are playing |
| Arc | Bends lines into an arc |
| Demo song | Loops the built-in demo song; close the settings to watch it |
| Look up lyrics again | Clears the cache and looks the current song up again |

Settings are saved in `config/artlyrics.json`.

## Troubleshooting

- **"No lyrics found"**: LRCLIB doesn't have this song yet, or the player reports an unusual title.
  Artlyrics already retries with cleaned-up titles (dropping things like "Official Video" or "Remastered").
- **"No timed lyrics for this song"**: LRCLIB has the words but no timestamps, so they can't be synced.
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
  render/                  Skywriting / Hover renderer and the song card
  screen/                  settings screen
  config/                  settings and color styles
src/gametest/              client game test that takes screenshots
```

## Credits

Lyrics are provided by [LRCLIB](https://lrclib.net). The demo song's lyrics were written for this mod.
