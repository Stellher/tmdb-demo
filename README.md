# TMDB Demo · Android Compose

A real Android Studio multi-module Kotlin/Compose demo inspired by three provided screenshots. Pure native Compose, no WebView.

## Features

- Search TV series in Chinese via TMDB API v3, debounced text entry
- Strip season suffixes (e.g. 第二季/S02) from search keywords, carry the desired season into detail navigation, and expand TMDB series into season-specific cards where season data is available
- 3-column poster results implemented using **LazyColumn** rows, with year, type, country and progressively enriched latest episode / total episode count
- Full TV detail metadata + season navigation, including special episodes (season 0)
- Horizontal, landscape-still episode cards with title, air date, and runtime
- Expandable **ModalBottomSheet** showing episode stills and synopsis; tap any episode for the full description dialog
- MVVM: ViewModels hold loading, query, selections, and errors; data access stays in repository module
- Haze 1.7.1: background source + frosted glass top bars; avoid applying expensive blurs to every poster card
- UI-only demo, **no pirated playback or video scraping**

## Modules

| Module | Responsibility |
|---|---|
| :app | Application, composition root, navigation, token bootstrap |
| :core:model | Repository interface + platform-independent domain objects |
| :core:data | Retrofit, Gson, OkHttp auth, DTO mapping, in-memory detail cache |
| :core:ui | SaaS visual palette, shared media image, Haze glass |
| :feature:search | SearchViewModel and 3-column LazyColumn screen |
| :feature:detail | DetailViewModel, season switch, episode cards/sheet |

## Build

Android Studio supporting AGP 8.13.2, JDK 17, Android SDK 36, Gradle 8.13; minSdk 26 / targetSdk 35. Open this repository as a Gradle project.

**Do not commit the TMDB token to a public repository.** This repository is public. The token is injected at build time through Gradle properties/environment and becomes part of the APK. It can still be extracted from the APK, so use restricted credentials and consider a backend proxy for production.

Local development:

```bash
# Put the API Read Access Token in ~/.gradle/gradle.properties (NOT this repository):
# tmdbToken=YOUR_TMDB_API_READ_ACCESS_TOKEN
gradle :app:assembleRelease
```

Or:

```bash
TMDB_READ_TOKEN="YOUR_TMDB_API_READ_ACCESS_TOKEN" gradle :app:assembleRelease
```

GitHub Actions: configure **Settings → Secrets and variables → Actions → New repository secret** with name **TMDB_READ_TOKEN**, then run **Actions → Android release APK → Run workflow**. The workflow also runs on main pushes. Download the `tmdb-demo-release` artifact once the job succeeds.

The Actions workflow creates a **temporary signing key** for each demo APK. This is not a stable production release identity; configure your own persistent upload/signing key before shipping updates. The demo can be built without a token but then presents a temporary in-app token entry screen rather than silently failing.

For a standard Gradle wrapper, run `gradle wrapper --gradle-version 8.13` once locally and commit the generated wrapper scripts and verified binary JAR. CI installs Gradle 8.13 independently.

## API use

```text
GET /3/search/tv?query=...
GET /3/tv/{id}?language=zh-CN
GET /3/tv/{id}/season/{season_number}?language=zh-CN
```

TMDB only provides metadata, not playable streaming sources. The newest *aired* episode in series details may belong to a different season from the one currently selected, and episode poster/still coverage varies by TV series. A missing still displays the neutral image placeholder.

Data and images by TMDB. This product uses the TMDB API but is not endorsed or certified by TMDB.
