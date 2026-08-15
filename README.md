# FlickMix — Fire TV

A 10-foot, remote-first streaming interface for Amazon Fire TV / Fire Stick.
Black, electric green and white. No browser furniture, no address bar, no
X-Ray panel.

Built against the approved visual references: left nav rail with the
FLICK/MIX wordmark, source pills across the top, poster shelves, a detail
page, and a player whose menus are populated from the stream's real tracks.

---

## What's here

```
app/src/main/java/com/flickmix/firetv/
    App.java              Memory discipline: trims artwork under pressure
    MainActivity.java     Nav rail, source tabs, shelves, search
    DetailActivity.java   Backdrop, metadata, Play / My List
    PlayerActivity.java   Media3 ExoPlayer, D-pad transport, track menus
    WebActivity.java      D-pad browser, isolated process, full teardown
    SourcesActivity.java  The 10 source slots
    Store.java            Sources, titles, My List, resume positions
    M3uParser.java        M3U/M3U8 playlist reader
    Source.java           Source slot model
    Title.java            Catalog entry model
    Adapters.java         RecyclerView adapters
```

### Player

Everything in the reference except X-Ray, which is gone:

- Play/pause, 10s back, 10s forward, scrub bar, elapsed and total
- **Subtitles** — lists only tracks the stream declares; Arabic labelled in
  Arabic when present; explicit Off
- **Audio** — only real tracks, with 5.1/stereo channel counts
- **Quality** — Auto plus the actual HLS/DASH renditions with their bitrates.
  A single-rendition stream says so rather than showing fake options
- **Speed** — 0.75x, Normal, 1.25x, 1.5x, 2x
- **Source** — switches between alternate copies of the same title you have
  added; playback position carries across
- Resume, Continue Watching, error recovery with specific messages

### Fire Stick performance

The 3rd-gen stick has very little RAM, and most sideloaded streaming apps run
badly on it for the same three reasons. All three are handled:

- No always-resident WebView. The browser lives in its own process
  (`android:process=":web"`) and is destroyed on exit
- `DefaultLoadControl` tuned down — 15s min / 50s max buffer, 10s back-buffer.
  An over-eager buffer is what causes the OOM stutter people call "lag"
- Artwork is downsampled to the view box, cleared on recycle, and dumped
  entirely when the player starts

### Remote handling

- Every focusable control has a green ring and a lift animation
- OK plays/pauses when controls are hidden; Left/Right skip 10s
- Media keys on the remote work directly
- Menu opens Sources from the catalog, Quality from the player
- Back never traps: filtered view → Home → exit; browser walks page history
  first; player returns to the detail page

---

## Sources

Ten configurable slots. Three kinds:

| Type | What it does |
|------|--------------|
| **Direct** | You add individual entries: title, media URL, poster, year, category |
| **Playlist** | One M3U/M3U8 URL expands into a full catalog |
| **Web** | Opens that site in the built-in D-pad browser |

Direct and Playlist entries feed the Netflix-style poster grid. That is where
the interface you designed comes from — FlickMix renders *your* catalog in
that layout.

Things that work well in these slots: a Jellyfin, Plex or Emby server; a NAS
over HTTP; an IPTV subscription you pay for; the Internet Archive's public
domain film collection; anything you host yourself.

---

## What this does not do, and why

FlickMix ships with **no sources preconfigured** and contains **no scraper**.

It does not read a third-party site's markup to pull its catalog and its video
URLs into this interface. That is the one piece I did not build, and it is
worth being straight about the reason rather than burying it: lifting a
service's library out of its own site and re-presenting it in a different
front end means taking their content and routing around whatever they put
around it — their page, their ads, their access controls. It is also the
mechanism that gets sideloaded Fire Stick apps taken down, and the thing that
turns a personal media app into a legal problem for whoever installed it.

So the connector layer is open at the edges instead. FlickMix plays what you
point it at. If you have a media server, a playlist, a subscription, or files
of your own, this is a good front end for them and the interface is exactly
the one in your references. If a site can't be added as a playlist or a direct
link, it stays a Web slot and opens in the browser as its publisher intended.

The WebView identifies itself honestly, runs pages as published, and does not
strip or inject anything.

---

## Build

See **BUILD_AND_INSTALL.md**. Short version: push to GitHub and the included
Actions workflow compiles `FlickMix_FireTV.apk` for you in about four minutes,
no computer required.

## Target

- Fire OS 7.x, Fire Stick 3rd gen and up
- `minSdk 22`, `targetSdk 34`, landscape only
- Java 17, AGP 8.2.2, Gradle 8.2, AndroidX Media3 1.3.1

## Branding notes

The nav-rail wordmark is the hand-drawn FLICKMIX artwork from the brand asset
pack (`res/drawable-xhdpi/wordmark.png`), background knocked out to
transparent. Headings, nav, buttons and labels are set in Permanent Marker
(`res/font/flickmix_display.ttf`, Apache 2.0) for the 90s marker look; body
and metadata stay on the readable TV face on purpose.

The Fire TV launcher tile is the supplied 320×180 brand banner at
`res/drawable-xhdpi/banner.png`.
