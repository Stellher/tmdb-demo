# TMDB Demo · Android Compose

A real Android Studio multi-module Kotlin/Compose demo inspired by three provided screenshots. Pure native Compose, no WebView.

## Features

- Search includes low-noise fallback for near-title typos (e.g. 兰花如故 → 兰香如故). A blue inline hint distinguishes a fallback from an exact result. Original exact results and their pagination remain unchanged.
- Exactly three search tabs, matching TMDB endpoint types: **全部** queries /search/tv and /search/movie in parallel, **TV** queries only /search/tv, and **电影** queries only /search/movie. Switching tabs reuses already-fetched results in the ViewModel without new requests. Only missing endpoints are fetched; each category retains its own pagination cursor. Each search query keeps up to eight local result caches. Animation genre 16 is never used for search category filtering.
- Strip season suffixes (e.g. 第二季/S02) from search keywords, carry the desired season into detail navigation, and expand TMDB series into season-specific cards where season data is available
- 3-column poster results implemented using **LazyColumn** rows, with year, type, country and progressively enriched latest episode / total episode count
- Dedicated movie detail screen with poster, synopsis, release date, rating, runtime and genres
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

GitHub Actions automatically builds on pushes to `main`; you can also trigger [Android release APK](https://github.com/Stellher/tmdb-demo/actions/workflows/release.yml) manually. After a successful run, download artifact `tmdb-demo-release`. All future builds use the same `app/signing/tmdb-demo-release.jks` test certificate, with increasing GitHub run-number-based `versionCode`. The Release build enables `isMinifyEnabled=true`, `isShrinkResources=true`, and `proguard-android-optimize.txt`. CI checks the fixed certificate SHA-256 after signing. The signing key is intentionally public **only** for this throwaway demo, not for production apps.

Because this repository is public, the test credential is in both the APK and public Git history. **Revoke/regenerate the credential after testing**; deleting the source line later does not erase Git history.

**Migration:** APKs before this change were signed using a fresh random key on every build. They cannot be overwritten by this new stable signing key. Uninstall the old Demo once, then future builds from this change can be installed over each other (same applicationId and higher versionCode).

Test signing certificate SHA-256:
`1D:B7:9B:85:3E:9C:EC:DA:97:C2:EE:2A:79:08:A5:36:6E:73:5C:BD:E4:32:CE:19:05:2C:07:B8:C3:4C:4D:F6`.

The Gradle 8.13 wrapper files are included (official Gradle 8.13.0 scripts and binary). Use `./gradlew` or `gradlew.bat` in Android Studio's terminal.

## API use

```text
# 全部: both requests, merged on the client
# TV: one /search/tv request
GET /3/search/tv?query=...&page=1
# 电影: one /search/movie request
GET /3/search/movie?query=...&page=1
GET /3/movie/{id}?language=zh-CN
GET /3/tv/{id}?language=zh-CN
GET /3/tv/{id}/season/{season_number}?language=zh-CN
```

TMDB only provides metadata, not playable streaming sources. The newest *aired* episode in series details may belong to a different season from the one currently selected, and episode poster/still coverage varies by TV series. A missing still displays the neutral image placeholder.

Data and images by TMDB. This product uses the TMDB API but is not endorsed or certified by TMDB.
