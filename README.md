# WorldTV Guide (Android)

A worldwide TV guide app for Android, built with Kotlin and Jetpack Compose.

## Features

The interface is styled after classic mobile portal apps. It has a home screen of glossy tiles, a gradient title bar with a live clock, and a soft-key bar along the bottom (**Options / Home** on the left, **Refresh** in the centre, **Back / Exit** on the right).

- **Home tiles**
  - **On Now**: everything airing right now, with a live count badge.
  - **Tonight**: programmes from 6 pm onwards.
  - **Full Guide**: yesterday through the next 6 days, grouped by channel or sorted by time, with genre filters.
  - **Following**: upcoming airings of the shows you follow.
  - **Search**: search all loaded listings.
  - **Streaming**: schedules for Netflix, Prime Video, Disney+, Pluto TV, Samsung TV Plus and others.
  - **Country**: choose any of about 200 countries.
  - **Sources**: turn guide sources on or off, or add your own XMLTV links.
  - **Themes**: choose one of five Snaptu-style skins (Classic, Light, Ocean, Lime, Berry). The change applies instantly and is remembered.
- The **Options** soft key opens a menu with Themes, Country, Guide sources, Refresh and About.
- **Programme pages** show artwork, synopsis, genres, a live progress bar, **Remind me** (adds the programme to your calendar) and ☆ follow.
- Air times are converted to your phone's time zone.
- Downloaded listings are cached, so the app still shows the last data it downloaded when you're offline.
- Needs Android 8.0 or later.

## Data sources (free, no API keys)

The guide merges every enabled source into one list and removes duplicates. You can switch sources on or off, and add your own, from the **Sources** tile or the ⚙ button in any guide view.

| What | Source | Coverage |
|---|---|---|
| Show listings with artwork and synopses | [TVmaze API](https://www.tvmaze.com/api) (CC BY-SA) | US, UK, CA, AU, most of Europe, JP, plus streaming services |
| Channel-by-channel XMLTV guides | [epgshare01](https://epgshare01.online/epgshare01/) | About 70 countries, rebuilt daily. Includes India, the Gulf, Latin America and Southeast Asia, plus Plex, Peacock and DistroTV |
| Per-country XMLTV guides | [epg.pw](https://epg.pw/xmltv.html) | 20 countries (AU, BR, CA, CN, DE, FR, GB, HK, ID, IN, JP, MY, NZ, PH, RU, SG, TW, US, VN, ZA), updated daily |
| Per-country XMLTV guides | [GlobeTV EPG](https://github.com/globetvapp/epg) | Country folders on GitHub, updated daily |
| Free streaming TV guides | [i.mjh.nz](https://github.com/matthuisman/i.mjh.nz) | Pluto TV, Samsung TV Plus, Plex and Roku, shown under 🌐 Streaming |
| Your own guides | Any XMLTV URL (`.xml` / `.xml.gz`) | Show it for one country or for all of them |
| Country list and flags | [iptv-org/api](https://github.com/iptv-org/api) (public domain) | About 200 countries |

XMLTV files are streamed and parsed on the device, and only the selected day is kept in memory. Downloaded guide files are cached for 6 hours, and the cache is capped at about 300 MB. Some country files are several MB, so the first load of a country on mobile data takes a moment.

Each chip under the filters shows one guide file: **✓** means loaded (with a programme count), **…** means loading, and **✕** means it failed. Tap a chip to manage sources.

## Download the APK

Each push to `main` builds the app on GitHub Actions. The APK is published on the repo's **[Releases](../../releases)** page. To install it, download `WorldTVGuide.apk` on your phone and open it.

## Build & run

1. Install **Android Studio** (Ladybug 2024.2 or newer).
2. Open this folder with **File → Open**. Android Studio downloads Gradle and the dependencies automatically.
3. Press **Run ▶** to launch the app on an emulator or a phone with USB debugging enabled.

From the command line (requires the Android SDK and `ANDROID_HOME` set):

```bash
./gradlew assembleDebug      # → app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # minified, signed with the debug key
```

Before you publish to Google Play, set up your own signing key in `app/build.gradle.kts`.

## Project structure

```
app/src/main/java/com/worldtv/guide/
├── MainActivity.kt            navigation + soft-key bar
├── WorldTvApp.kt              app-wide repository & prefs
├── data/
│   ├── Models.kt              TVmaze + country models
│   ├── Programme.kt           common programme model (all sources)
│   ├── Xmltv.kt               streaming XMLTV parser
│   ├── Sources.kt             source list + per-country feed discovery
│   ├── Http.kt                OkHttp with file cache / offline fallback
│   ├── Repository.kt          merged multi-source guide loading
│   └── Prefs.kt               followed shows, source settings
├── ui/home/                   tile launcher home screen
├── ui/guide/                  On Now / Tonight / Full Guide / Search
├── ui/country/                country menu
├── ui/favorites/              followed shows
├── ui/sources/                guide sources & custom XMLTV
├── ui/detail/                 programme detail pages
├── ui/options/                Options menu + theme picker
├── ui/theme/Theme.kt          skins (palettes) + Material theme
├── ui/components/Snap.kt      title bar, soft keys, tiles, list rows
└── util/Util.kt               time & HTML helpers
```

## Ideas to extend

- Push notifications for followed shows (WorkManager + TVmaze `/shows/{id}/nextepisode`).
- A grid-style EPG timeline (channels × hours).
- Full XMLTV EPG for more countries, using guides generated with [iptv-org/epg](https://github.com/iptv-org/epg).
- Android TV launcher support (add a `LEANBACK_LAUNCHER` intent filter and a TV layout).
