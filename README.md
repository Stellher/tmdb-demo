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
| :app | Application, composition root, navigation, built-in demo token |
| :core:model | Repository interface + platform-independent domain objects |
| :core:data | Retrofit, Gson, OkHttp auth, DTO mapping, in-memory detail cache |
| :core:ui | SaaS visual palette, shared media image, Haze glass |
| :feature:search | SearchViewModel and 3-column LazyColumn screen |
| :feature:detail | DetailViewModel, season switch, episode cards/sheet |

## Build

Android Studio supporting AGP 8.13.2, JDK 17, Android SDK 36, Gradle 8.13; minSdk 26 / targetSdk 35. Open this repository as a Gradle project.

## Demo authentication & APK

The TMDB **API Read Access Token is intentionally hardcoded** in `app/build.gradle.kts` for this disposable demo. The app starts directly on the search screen, with no token input and no GitHub Secret requirement.

```bash
./gradlew :app:assembleRelease
```

GitHub Actions automatically builds on pushes to `main`; you can also trigger [Android release APK](https://github.com/Stellher/tmdb-demo/actions/workflows/release.yml) manually. After a successful run, download artifact `tmdb-demo-release`. It is signed with a **temporary demo signing key** that changes on each run and is not suitable for production app updates.

Because this repository is public, the test credential is in both the APK and public Git history. **Revoke/regenerate the credential after testing**; deleting the source line later does not erase Git history.

The Gradle 8.13 wrapper files are included (official Gradle 8.13.0 scripts and binary). Use `./gradlew` or `gradlew.bat` in Android Studio's terminal.

## API use

```text
GET /3/search/tv?query=...
GET /3/tv/{id}?language=zh-CN
GET /3/tv/{id}/season/{season_number}?language=zh-CN
```

TMDB only provides metadata, not playable streaming sources. The newest *aired* episode in series details may belong to a different season from the one currently selected, and episode poster/still coverage varies by TV series. A missing still displays the neutral image placeholder.

Data and images by TMDB. This product uses the TMDB API but is not endorsed or certified by TMDB.
