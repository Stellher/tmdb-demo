# Actual Go API request verification (GitHub Actions)

Verified using the independent Go + Gin implementation, **not** AIOMetadata. The runner downloaded the original AniBridge and Fribb mappings into memory, called AniList and TMDB over HTTPS, then invoked the Go service on localhost.

- [First successful Go live requests](https://github.com/Stellher/tmdb-demo/actions/runs/39882910030)
- [Successful neighbor-corrected/special-episode live requests](https://github.com/Stellher/tmdb-demo/actions/runs/39883266369)

Mapping snapshot at test time: **5,981 AniList IDs** with AniBridge episode mappings; **20,886 Fribb index entries**. This is mapping coverage, not a claim that every indexed title has complete and correct episode stills.

## ReZero — TMDB 65942

| AniList title ID | Season / cour | EP count | TMDB S1 range | Episode still URLs |
|---|---|---:|---:|---:|
| 21355 | S1 | 25 | 1-25 | 25 |
| 108632 | S2 Part 1 | 13 | 26-38 | 13 |
| 119661 | S2 Part 2 | 12 | 39-50 | 12 |
| 163134 | S3 | 16 | 51-66 | 16 |
| 189046 | S4 | 19 | 67-85 | 19 |

All five cour counts match the AniList episode counts. Each record retains `tmdbSeason`, `tmdbEpisodeNumber` and `image`. Related ReZero short series are returned as extra entries. AniList 100049 (ReZero OVAs) was **intentionally left unresolved** after safety checks: its Fribb fallback would otherwise incorrectly map OVAs onto regular S1.

## Bungo Stray Dogs — TMDB 65931

| AniList title ID | Season | EP count | TMDB S1 range | Episode still URLs |
|---|---|---:|---:|---:|
| 21311 | S1 | 12 | 1-12 | 12 |
| 21679 | S2 | 12 | 13-24 | 12 |
| 103223 | S3 | 12 | 25-36 | 12 |
| 141249 | S4 | 13 | 37-49 | 13 |
| 163263 | S5 | 11 | 50-60 | 11 |

Fribb initially mapped S2 from TMDB episode 12; the Go service automatically moved it to episode 13 based on both verified AniBridge neighbor boundaries (end=12, next start=25). **There is no per-title override table.**

AniList 21791 (OVA "Walking Alone") resolved by an exact 2017-08-04 special air date to TMDB Season 0 Episode 1, with a distinct episode still.

## Limitations and safety

- `verifiedCount=true` confirms quantity equality with AniList, **not proof that each scene/image semantically matches the episode**.
- The original mapping data is curated and may include mistakes. Without local corrections, unresolved conflicts are returned as errors or in `unresolvedAniListIds` instead of being silently manufactured.
- Still URLs were present for all tested main-cour episodes, but CI did not issue HEAD/GET requests to every image host to independently verify availability.
- TVDB isn't required: this test uses AniBridge, Fribb, AniList, and TMDB. No API deployment or database needed for the GitHub Actions demonstration.
