# WorldTV Guide (Android)

A worldwide TV guide app for Android, built with Kotlin and Jetpack Compose.

## Features

- **TV Guide**: daily TV listings for any country, from yesterday through the next 6 days.
  - You can pick any country, plus a **🌐 Streaming** schedule covering Netflix, Prime Video, Disney+ and others.
  - Listings can be grouped by channel (with sticky headers) or sorted by time.
  - **On now** shows a live badge and a progress bar. You can hide programmes that have ended, filter by genre, and search by show or channel.
  - Air times are converted to your phone's time zone automatically.
- **World Channels**: a directory of tens of thousands of TV channels from about 200 countries, with logos.
  - You can browse by country (each country shows its channel count) and filter by category (News, Sports, Kids, Movies…).
  - Search covers channel names, alternate names, networks and owners.
  - Each channel has its own page with its network, owner, launch date and website. A **Find in TV guide** button opens that channel's listings.
- **Programme details**: artwork, episode and show synopsis, genres and rating.
  - **Remind me** adds the programme to your calendar, which sends a reminder before it starts.
- **Favourites**: you can star channels and follow shows.
  - The Favourites tab lists upcoming airings of the shows you follow.
- Offline cache: the channel directory is cached for 24 hours and schedules for 30 minutes. If the network is down, the app shows the last data it downloaded.
- Supports dark and light themes, with Material You colours on Android 12 and later. Needs Android 8.0 or later.

## Data sources (free, no API keys)

| What | Source | Licence |
|---|---|---|
| TV schedules / programmes | [TVmaze API](https://www.tvmaze.com/api) | CC BY-SA |
| Worldwide channels, countries, categories, logos | [iptv-org/api](https://github.com/iptv-org/api) | Unlicense / public domain |

TVmaze's listings are strongest for the US, UK, Canada, Australia, most of Europe, Japan and a few others. Some countries have little or no listing data. For those countries the guide says so, and you can still browse their channels in the **Channels** tab.
Credit TVmaze in your app's store listing if you publish it. The app already credits both sources in the UI.

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
├── MainActivity.kt            navigation + bottom bar
├── WorldTvApp.kt              app-wide repository & prefs
├── data/
│   ├── Models.kt              TVmaze + iptv-org models
│   ├── Http.kt                OkHttp with file cache / offline fallback
│   ├── Repository.kt          schedules, channel directory
│   └── Prefs.kt               favourites & settings
├── ui/guide/                  TV guide screen + ViewModel
├── ui/channels/               channel browser + channel detail
├── ui/detail/                 programme detail
├── ui/favorites/              favourites
├── ui/components/             country picker, search, etc.
└── util/Util.kt               time & HTML helpers
```

## Ideas to extend

- Push notifications for followed shows (WorkManager + TVmaze `/shows/{id}/nextepisode`).
- A grid-style EPG timeline (channels × hours).
- Full XMLTV EPG for more countries, using guides generated with [iptv-org/epg](https://github.com/iptv-org/epg).
- Android TV launcher support (add a `LEANBACK_LAUNCHER` intent filter and a TV layout).
