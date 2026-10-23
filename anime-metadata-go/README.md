# Go anime metadata API (no database)

A Go + Gin service which uses **AniBridge mapping ranges (primary)**, **Fribb index (secondary)**, **AniList GraphQL**, and **TMDB episode/still metadata**.

**No MySQL, Postgres, SQLite, Redis, local mapping edits, or manual corrections.** Upstream mapping files are downloaded directly into RAM at startup and refreshed daily. An upstream failure keeps the previous RAM snapshot. Restart requires refetching; no persistent server-side database. Does not provide video streams.

## Start

Go 1.23+:

```bash
cd anime-metadata-go
go mod tidy
TMDB_READ_TOKEN='<TMDB API Read Access Token>' go run .
```

The existing disposable TMDB demo token is reused by this repository's GitHub Actions for tests. For production, supply a separately provisioned key through an environment variable, never a hardcoded source value.

Endpoints:

```http
GET /health
GET /api/anime/search?q=Re%3AZero
GET /api/anime/21355                  # one AniList title / cour
GET /api/anime/108632                 # ReZero second-season part
GET /api/anime/tmdb/65942            # series with each AniList title/cour separately
GET /api/anime/tmdb/65931            # 文豪野犬 and related TV titles
```

Each `episodes[]` record has its **AniList-local** `number`, its **TMDB-native** `tmdbSeason`/`tmdbEpisodeNumber`, and the original still image URL. Count verification is reported honestly, with warnings, not manufactured.

### Mapping priority and limitations

- Local *in-memory* snapshot of **all** AniBridge episode range pairs, including discontinuous and multiple TMDB-season mappings; respects non-1 start offsets.
- If AniBridge has no record, read Fribb's mapped TMDB season and episode offset, capped at AniList's declared count.
- If neither maps the title, accept only an exact name and animation genre match in TMDB and report low confidence. Ambiguous mappings are not fabricated.
- For TMDB series listing, collect related AniList IDs from the mapping snapshot. Entries are sorted by AniList start date and retain independent course/OVA/movie designations where available.
- Episode images use TMDB's own published stills; absent images remain empty.
- The API cannot guarantee perfect mapping quality without curated corrections. This project intentionally **does not maintain any correction table**. Bad or missing upstream mappings remain visible as warnings/unresolved entries.
- This Go implementation is independent, derived from public API/mapping specifications. Review AniBridge/Fribb/TMDB/AniList respective usage and licensing terms.

CI: [Go metadata workflow](https://github.com/Stellher/tmdb-demo/actions/workflows/go-anime-metadata.yml).
