# Graph Report - unigrab  (2026-06-03)

## Corpus Check
- 8 files · ~11,898 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 324 nodes · 690 edges · 18 communities (13 shown, 5 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 5 edges (avg confidence: 0.93)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `aaece9c6`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- [[_COMMUNITY_Community 0|Community 0]]
- [[_COMMUNITY_Community 1|Community 1]]
- [[_COMMUNITY_Community 2|Community 2]]
- [[_COMMUNITY_Community 3|Community 3]]
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

## God Nodes (most connected - your core abstractions)
1. `UnsupportedSourceError` - 20 edges
2. `Track` - 17 edges
3. `ResolvedSource` - 16 edges
4. `sources()` - 16 edges
5. `normalize_artist_values()` - 15 edges
6. `UniGrabError` - 14 edges
7. `MetadataResolutionError` - 14 edges
8. `hostname()` - 14 edges
9. `interactive()` - 14 edges
10. `_resolve_apple_podcast()` - 14 edges

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

## Communities (18 total, 5 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.07
Nodes (33): build_parser(), download(), DownloadOptions, ensure_dependencies(), formats(), interactive(), main(), media_type_from_mode() (+25 more)

### Community 1 - "Community 1"
Cohesion: 0.1
Nodes (20): AppleMusicResolver, ArchiveOrgResolver, BlockedSourceResolver, _fallback_resolved_source(), _fallback_title(), hostname(), is_apple(), _is_explicit_playlist() (+12 more)

### Community 2 - "Community 2"
Cohesion: 0.1
Nodes (41): _artist_values(), _clean_oembed_title(), _client_credentials_token(), _episode(), _extract_identifier(), _fallback_podcast(), _feeds_from_public_search(), _fetch_metadata() (+33 more)

### Community 3 - "Community 3"
Cohesion: 0.08
Nodes (47): Exception, _artist_values_from_info(), _artist_values_from_track(), _audio_codec(), _audio_extension_from_url(), bundled_ffmpeg_path(), _burn_subtitles(), clean_filename() (+39 more)

### Community 5 - "Community 5"
Cohesion: 0.18
Nodes (6): ConsoleDownloadReporter, format_duration(), metadata_bool(), metadata_value(), print_interactive_metadata(), progress_bar()

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
Cohesion: 0.11
Nodes (17): _embed_metadata(), http_text(), is_url(), M3UPlaylistResolver, MetadataResolutionError, _parse_m3u(), _playlist_title(), PodcastQueryResolver (+9 more)

### Community 14 - "Community 14"
Cohesion: 0.15
Nodes (12): edges, layers, nodes, project, analyzedAt, description, frameworks, gitCommitHash (+4 more)

### Community 15 - "Community 15"
Cohesion: 0.2
Nodes (9): complexity, files, filteredByIgnore, frameworks, importMap, unigrab.py, languages, projectDescription (+1 more)

### Community 18 - "Community 18"
Cohesion: 0.4
Nodes (4): analyzedFiles, gitCommitHash, lastAnalyzedAt, version

## Knowledge Gaps
- **102 isolated node(s):** `Base error for expected application failures.`, `Raised when a required local executable or API credential is missing.`, `Raised when a source cannot be resolved by the configured resolvers.`, `Raised when metadata lookup fails for a supported source.`, `Resolver for M3U/M3U8 playlists (IPTV streams, HLS playlists, local media lists)` (+97 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **5 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `sources()` connect `Community 0` to `Community 3`?**
  _High betweenness centrality (0.059) - this node is a cross-community bridge._
- **Why does `ensure_dependencies()` connect `Community 0` to `Community 3`?**
  _High betweenness centrality (0.026) - this node is a cross-community bridge._
- **Why does `ConsoleDownloadReporter` connect `Community 5` to `Community 0`, `Community 3`?**
  _High betweenness centrality (0.025) - this node is a cross-community bridge._
- **What connects `Base error for expected application failures.`, `Raised when a required local executable or API credential is missing.`, `Raised when a source cannot be resolved by the configured resolvers.` to the rest of the system?**
  _102 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.07 - nodes in this community are weakly interconnected._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.1 - nodes in this community are weakly interconnected._
- **Should `Community 2` be split into smaller, more focused modules?**
  _Cohesion score 0.1 - nodes in this community are weakly interconnected._