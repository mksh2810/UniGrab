# Graph Report - unigrab  (2026-06-03)

## Corpus Check
- 9 files · ~12,377 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 341 nodes · 713 edges · 21 communities (16 shown, 5 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 5 edges (avg confidence: 0.93)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `91b134b6`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- [[_COMMUNITY_Community 0|Community 0]]
- [[_COMMUNITY_Community 1|Community 1]]
- [[_COMMUNITY_Community 2|Community 2]]
- [[_COMMUNITY_Community 3|Community 3]]
- [[_COMMUNITY_Community 4|Community 4]]
- [[_COMMUNITY_Community 5|Community 5]]
- [[_COMMUNITY_Community 6|Community 6]]
- [[_COMMUNITY_Community 7|Community 7]]
- [[_COMMUNITY_Community 8|Community 8]]
- [[_COMMUNITY_Community 9|Community 9]]
- [[_COMMUNITY_Community 10|Community 10]]
- [[_COMMUNITY_Community 11|Community 11]]
- [[_COMMUNITY_Community 12|Community 12]]
- [[_COMMUNITY_Community 13|Community 13]]
- [[_COMMUNITY_Community 14|Community 14]]
- [[_COMMUNITY_Community 15|Community 15]]
- [[_COMMUNITY_Community 16|Community 16]]
- [[_COMMUNITY_Community 17|Community 17]]
- [[_COMMUNITY_Community 18|Community 18]]
- [[_COMMUNITY_Community 19|Community 19]]
- [[_COMMUNITY_Community 20|Community 20]]

## God Nodes (most connected - your core abstractions)
1. `UnsupportedSourceError` - 21 edges
2. `Track` - 18 edges
3. `sources()` - 18 edges
4. `ResolvedSource` - 17 edges
5. `UniGrabError` - 15 edges
6. `MetadataResolutionError` - 15 edges
7. `normalize_artist_values()` - 15 edges
8. `DependencyMissingError` - 14 edges
9. `hostname()` - 14 edges
10. `interactive()` - 14 edges

## Surprising Connections (you probably didn't know these)
- `SpotifyResolver Class` --rationale_for--> `No DRM Bypass Principle`  [INFERRED]
  unigrab.py → README.md
- `AppleMusicResolver Class` --rationale_for--> `No DRM Bypass Principle`  [INFERRED]
  unigrab.py → README.md
- `README.md Overview` --references--> `requirements.txt Packages`  [EXTRACTED]
  README.md → requirements.txt
- `graphify workflow` --conceptually_related_to--> `graphify rule`  [INFERRED]
  .agents/workflows/graphify.md → .agents/rules/graphify.md

## Hyperedges (group relationships)
- **Media Resolvers** — unigrab_spotify_resolver, unigrab_apple_music_resolver, unigrab_podcast_platform_resolver, unigrab_ytdlp_metadata_resolver [INFERRED 0.85]

## Communities (21 total, 5 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.12
Nodes (17): List all supported sites and sources., List all supported sites and sources., List all supported sites and sources., List all supported sites and sources., List all supported sites and sources., List all supported sites and sources., List all supported sites and sources., List all supported sites and sources. (+9 more)

### Community 1 - "Community 1"
Cohesion: 0.09
Nodes (20): AppleMusicResolver, ArchiveOrgResolver, BlockedSourceResolver, hostname(), is_apple(), _is_explicit_playlist(), is_spotify(), _is_youtube_host() (+12 more)

### Community 2 - "Community 2"
Cohesion: 0.12
Nodes (29): _client_credentials_token(), _extract_identifier(), _fallback_resolved_source(), _fallback_title(), _feeds_from_public_search(), _fetch_metadata(), _filter_media_files(), _find_episode() (+21 more)

### Community 3 - "Community 3"
Cohesion: 0.09
Nodes (41): _artist_values_from_info(), _artist_values_from_track(), _audio_codec(), build_parser(), bundled_ffmpeg_path(), _burn_subtitles(), clean_filename(), doctor() (+33 more)

### Community 4 - "Community 4"
Cohesion: 0.16
Nodes (16): _artist_values(), _clean_oembed_title(), _episode(), _fallback_podcast(), normalize_artist_values(), parse_duration(), remove_emojis(), _resolve_without_api() (+8 more)

### Community 5 - "Community 5"
Cohesion: 0.08
Nodes (25): Exception, _audio_extension_from_url(), ConsoleDownloadReporter, _curl_progress_fraction(), DependencyMissingError, _download_direct_audio(), _download_direct_audio_with_curl(), _download_direct_audio_with_python() (+17 more)

### Community 6 - "Community 6"
Cohesion: 0.22
Nodes (3): Protocol, DownloadReporter, Resolver

### Community 7 - "Community 7"
Cohesion: 0.1
Nodes (20): files, README.md, requirements.txt, unigrab.py, generatedAt, gitCommitHash, mode, hash (+12 more)

### Community 8 - "Community 8"
Cohesion: 0.32
Nodes (8): AppleMusicResolver Class, main function, No DRM Bypass Principle, Resolver Registry Pattern, ResolverRegistry Class, SpotifyResolver Class, YtDlpDownloader Class, YtDlpMetadataResolver Class

### Community 12 - "Community 12"
Cohesion: 0.07
Nodes (28): Advanced Options, code:powershell (python -m pip install -r requirements.txt), code:powershell (python unigrab.py download v "https://www.youtube.com/watch?), code:powershell ($env:UNIGRAB_COOKIES="C:\path\to\cookies.txt"), code:powershell (python unigrab.py download v "URL" --geo-bypass), code:powershell (python unigrab.py download v "URL" --subs), code:powershell (python unigrab.py formats), code:powershell (python unigrab.py sources) (+20 more)

### Community 13 - "Community 13"
Cohesion: 0.1
Nodes (19): _embed_metadata(), http_text(), is_url(), M3UPlaylistResolver, MetadataResolutionError, _parse_m3u(), _playlist_title(), PodcastQueryResolver (+11 more)

### Community 14 - "Community 14"
Cohesion: 0.15
Nodes (12): edges, layers, nodes, project, analyzedAt, description, frameworks, gitCommitHash (+4 more)

### Community 15 - "Community 15"
Cohesion: 0.2
Nodes (9): complexity, files, filteredByIgnore, frameworks, importMap, unigrab.py, languages, projectDescription (+1 more)

### Community 18 - "Community 18"
Cohesion: 0.4
Nodes (4): analyzedFiles, gitCommitHash, lastAnalyzedAt, version

### Community 19 - "Community 19"
Cohesion: 0.4
Nodes (4): clickTrackingParams, replayChatItemAction, actions, videoOffsetTimeMsec

### Community 20 - "Community 20"
Cohesion: 0.29
Nodes (7): ensure_dependencies(), Check for required packages and auto-install any that are missing., Check for required packages and auto-install any that are missing., Check for required packages and auto-install any that are missing., Check for required packages and auto-install any that are missing., Check for required packages and auto-install any that are missing., Check for required packages and auto-install any that are missing.

## Knowledge Gaps
- **113 isolated node(s):** `Base error for expected application failures.`, `Raised when a required local executable or API credential is missing.`, `Raised when a source cannot be resolved by the configured resolvers.`, `Raised when metadata lookup fails for a supported source.`, `Resolver for M3U/M3U8 playlists (IPTV streams, HLS playlists, local media lists)` (+108 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **5 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `sources()` connect `Community 0` to `Community 3`?**
  _High betweenness centrality (0.064) - this node is a cross-community bridge._
- **Why does `ensure_dependencies()` connect `Community 20` to `Community 3`?**
  _High betweenness centrality (0.024) - this node is a cross-community bridge._
- **Why does `ConsoleDownloadReporter` connect `Community 5` to `Community 3`?**
  _High betweenness centrality (0.023) - this node is a cross-community bridge._
- **What connects `Base error for expected application failures.`, `Raised when a required local executable or API credential is missing.`, `Raised when a source cannot be resolved by the configured resolvers.` to the rest of the system?**
  _113 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.12 - nodes in this community are weakly interconnected._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.09 - nodes in this community are weakly interconnected._
- **Should `Community 2` be split into smaller, more focused modules?**
  _Cohesion score 0.12 - nodes in this community are weakly interconnected._