# EPG Multi-Source Architecture

## Overview

StreamVault supports multiple EPG (Electronic Program Guide) sources per provider. External XMLTV sources can be added, assigned to providers, and matched against channels using ID- or name-based matching. The system resolves the best EPG source per channel at sync time and persists the result for fast guide rendering.

## Data Flow

```
User adds EPG source (Settings)
       │
       ▼
┌─────────────────────────────────────────────────────────────────┐
│  EpgSourceRepository.addSource(name, url)                      │
│   → Insert into `epg_sources` table                            │
│   → Assign to provider via `provider_epg_sources`              │
└───────────┬─────────────────────────────────────────────────────┘
            │
    SyncManager (full sync or EPG-only)
            │
            ▼
┌─────────────────────────────────────────────────────────────────┐
│  1. Refresh provider-native EPG   (existing `programs` table)  │
│  2. Refresh external sources      (EpgSourceRepositoryImpl)    │
│      → Download XMLTV (.xml or .xml.gz)                        │
│      → Parse channels → `epg_channels` table                   │
│      → Parse programmes → `epg_programmes` table               │
│  3. Run resolution engine         (EpgResolutionEngine)        │
│      → Match each channel to best source                       │
│      → Persist results in `channel_epg_mappings` table         │
└───────────┬─────────────────────────────────────────────────────┘
            │
    Guide Grid render (EpgViewModel)
            │
            ▼
┌─────────────────────────────────────────────────────────────────┐
│  EpgRepository.getResolvedProgramsForChannels()                │
│   → Read `channel_epg_mappings` for provider                   │
│   → EXTERNAL mappings → query `epg_programmes` table           │
│   → PROVIDER mappings → query `programs` table (legacy)        │
│   → Merge into unified Map<lookupKey, List<Program>>           │
│   → Fallback: Xtream on-demand API for unmapped channels       │
└─────────────────────────────────────────────────────────────────┘
```

## Resolution Algorithm

For each provider channel, the `EpgResolutionEngine` evaluates matches in priority order:

| Priority | Match Type        | Confidence | Source     | Description |
|----------|-------------------|------------|------------|-------------|
| 1        | MANUAL            | 1.0        | External   | User-overridden mapping (future feature) |
| 2        | EXACT_ID          | 1.0        | External   | Channel's `epgChannelId` matches XMLTV `channel@id` |
| 3        | NORMALIZED_NAME   | 0.7        | External   | Normalized channel name matches XMLTV `<display-name>` |
| 4        | PROVIDER_NATIVE   | 0.5        | Provider   | Provider has EPG data for this channel |
| 5        | NONE              | 0.0        | —          | No EPG coverage |

When multiple external sources are assigned, they are evaluated in assignment priority order (lowest number = highest priority). First match wins.

## Database Schema (v25)

### `epg_sources`
Global registry of external XMLTV EPG URLs.

| Column | Type | Notes |
|--------|------|-------|
| id | INTEGER PK | Auto-increment |
| name | TEXT | User-facing label |
| url | TEXT UNIQUE | XMLTV URL (HTTPS) |
| enabled | INTEGER | 1/0 |
| priority | INTEGER | Global ordering |
| last_refresh_at | INTEGER | Epoch millis |
| last_success_at | INTEGER | Epoch millis, null if never |
| last_error | TEXT | Last error message |
| created_at | INTEGER | Epoch millis |
| updated_at | INTEGER | Epoch millis |

### `provider_epg_sources`
Many-to-many: which external sources are assigned to which providers.

| Column | Type | Notes |
|--------|------|-------|
| id | INTEGER PK | Auto-increment |
| provider_id | INTEGER FK | → providers.id (CASCADE) |
| epg_source_id | INTEGER FK | → epg_sources.id (CASCADE) |
| priority | INTEGER | Per-provider ordering |
| enabled | INTEGER | 1/0 |

### `epg_channels`
Channels from external XMLTV sources. Rebuilt on each source refresh.

| Column | Type | Notes |
|--------|------|-------|
| id | INTEGER PK | Auto-increment |
| epg_source_id | INTEGER FK | → epg_sources.id (CASCADE) |
| xmltv_channel_id | TEXT | From `<channel id="...">` |
| display_name | TEXT | From `<display-name>` |
| normalized_name | TEXT | Lowercased, accent/symbol-stripped |
| icon_url | TEXT | From `<icon src="...">` |

### `epg_programmes`
Programme data from external XMLTV sources. Rebuilt on each source refresh.

| Column | Type | Notes |
|--------|------|-------|
| id | INTEGER PK | Auto-increment |
| epg_source_id | INTEGER FK | → epg_sources.id (CASCADE) |
| xmltv_channel_id | TEXT | Maps to epg_channels |
| start_time, end_time | INTEGER | Epoch millis |
| title, description | TEXT | Programme info |
| category, lang, rating, image_url | TEXT | Optional metadata |

### `channel_epg_mappings`
Precomputed resolution results. One row per provider channel.

| Column | Type | Notes |
|--------|------|-------|
| id | INTEGER PK | Auto-increment |
| provider_channel_id | INTEGER FK | → channels.id |
| provider_id | INTEGER FK | → providers.id (CASCADE) |
| source_type | TEXT | EXTERNAL / PROVIDER / NONE |
| epg_source_id | INTEGER | Nullable, FK → epg_sources.id |
| xmltv_channel_id | TEXT | Nullable |
| match_type | TEXT | EXACT_ID / NORMALIZED_NAME / MANUAL / PROVIDER_NATIVE |
| confidence | REAL | 0.0–1.0 |
| is_manual_override | INTEGER | 1/0 |
| updated_at | INTEGER | Epoch millis |

## Key Classes

| Class | Module | Purpose |
|-------|--------|---------|
| `EpgSource`, `ProviderEpgSourceAssignment`, etc. | domain | Domain models |
| `EpgSourceRepository` | domain | Interface for source management |
| `EpgSourceRepositoryImpl` | data | CRUD, download, ingestion |
| `EpgResolutionEngine` | data | Resolution algorithm + resolved queries |
| `EpgNameNormalizer` | data | Name normalization for fuzzy matching |
| `XmltvParser.parseStreamingWithChannels()` | data | Streaming channel+programme parser |
| `XmltvParser.maybeDecompressGzip()` | data | Transparent .gz support |
| `EpgViewModel.loadGuidePrograms()` | app | Resolved → legacy → Xtream fallback |

## Backward Compatibility

- The existing `programs` table and all existing queries remain unchanged
- Provider-native EPG continues to work exactly as before
- If no external sources are assigned, the guide falls back to legacy behavior
- Resolution is additive: external sources enhance, never remove, existing EPG coverage
- Migration 24→25 is non-destructive (creates new tables only)

## Matching Strategy

Channel-to-source matching uses a two-tier approach:

1. **Exact ID match** — the channel's `epgChannelId` (from `tvg-id` in M3U, or provider API) is compared directly against `<channel id="...">` in the XMLTV source. This is the strongest signal (confidence 1.0) and is always preferred.

2. **Normalized name match** — if no exact ID match exists, the channel's display name is normalized via `EpgNameNormalizer` (lowercase, trim, strip accents/punctuation/separators, remove common quality suffixes like HD/FHD/4K) and compared against the pre-normalized `normalized_name` column in `epg_channels`. Confidence is 0.7.

When multiple external sources are assigned to a provider, they are evaluated in their assignment priority order. The first source that produces a match wins. This prevents ambiguity and ensures deterministic results.

Fuzzy matching is deliberately conservative to avoid wrong mappings — there is no Levenshtein distance or partial substring matching.

## Refresh Flow

1. **SyncManager** triggers refresh during full provider sync or EPG-only sync
2. For each enabled external source assigned to the provider:
   - `EpgSourceRepositoryImpl.refreshSource()` downloads the XMLTV URL
   - `.gz` files are auto-detected via URL suffix and decompressed with `GZIPInputStream`
   - The response is size-limited (200 MB) and streamed through `XmltvParser.parseStreamingWithChannels()`
   - Existing data for that source is **deleted before ingestion** (atomic swap per source)
   - Channels and programmes are batch-inserted (500 items per batch)
   - Source `last_refresh_at`, `last_success_at`, or `last_error` are updated
3. After all source refreshes complete, `EpgResolutionEngine.resolveForProvider()` runs:
   - Recomputes all channel mappings for that provider
   - Preserves any existing manual overrides
   - Replaces all mappings atomically via `replaceForProvider()`
4. If a refresh **fails**, the error is recorded but previously-ingested data has already been cleared for that source (current design choice favoring consistency over stale data retention)

## Conflict Resolution

The system uses a **source-selection model**, not a programme-merge model:

- Each channel gets exactly **one effective source** (external or provider-native)
- The guide grid shows **one programme timeline per channel** — no visible collisions
- When two external sources both match the same channel, the **higher-priority source** (lower priority number) wins
- External sources always beat provider-native EPG for the same channel
- Resolution is precomputed at sync time and stored in `channel_epg_mappings`, not evaluated at render time

This design avoids the complexity and UX issues of per-programme conflict fusion (overlapping time blocks, duplicate entries, inconsistent data from different sources).

## Known Limitations

1. **Source refresh deletes before inserting** — if a refresh fails mid-parse, the source's data is lost. A staging-table approach could be added for higher reliability but adds complexity.
2. **No incremental XMLTV update** — every refresh re-downloads and re-parses the full XMLTV file. Conditional HTTP (`If-Modified-Since`) is not yet implemented.
3. **Manual override UI not implemented** — the data model supports it (`is_manual_override`, `MANUAL` match type) but there is no settings screen to set per-channel overrides yet.
4. **No per-channel EPG source indicator in the guide** — the guide grid shows resolved data but doesn't visually indicate which source provided it.
5. **Name normalization is conservative** — channels with significantly different naming across sources (e.g., "BBC 1" vs "BBC One") may not auto-match. Manual override or exact ID is needed.
6. **Resolution runs for all channels** — for providers with very large channel counts (10k+), the resolution pass touches every channel. This is fast in practice but could be optimized to only re-resolve when sources change.
7. **No stale programme auto-cleanup for external sources** — `DatabaseMaintenanceManager` handles provider-native programmes via `ProgramDao.deleteOld()`, but external `epg_programmes` stale data cleanup uses the same approach.

## Future Extension Points

1. **Manual override per channel** — add a channel details screen with "Override EPG Source" action that writes a `MANUAL` mapping to `channel_epg_mappings`
2. **Per-channel EPG diagnostics** — show match type, confidence, source name, and alternative candidates on a channel info screen
3. **Conditional refresh** — use `If-Modified-Since` / `ETag` headers to skip re-download when the source hasn't changed
4. **Staging-swap refresh** — ingest into temporary tables and swap atomically, preserving old data on failure
5. **Local XMLTV file import** — add a file picker for `.xml`/`.xml.gz` files on local storage
6. **Match preview** — show exact-ID/name/unresolved counts before committing source assignment
7. **Provider EPG toggle** — allow disabling provider-native EPG entirely when external sources provide better coverage
8. **Background refresh scheduling** — periodic refresh of external sources independent of full provider sync
