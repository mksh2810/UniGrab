from __future__ import annotations

import argparse
import base64
import hashlib
import html
import importlib.util
import json
import os
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Protocol
from urllib.parse import parse_qs, parse_qsl, urlencode, urlparse, urlunparse
from urllib.request import Request, urlopen
import socket

# Force IPv4 globally to bypass broken regional IPv6 paths
_orig_getaddrinfo = socket.getaddrinfo
def _getaddrinfo_ipv4(host, port, family=0, type=0, proto=0, flags=0):
    if family == 0 or family == socket.AF_UNSPEC:
        family = socket.AF_INET
    return _orig_getaddrinfo(host, port, family, type, proto, flags)
socket.getaddrinfo = _getaddrinfo_ipv4


class UniGrabError(Exception):
    """Base error for expected application failures."""


class DependencyMissingError(UniGrabError):
    """Raised when a required local executable or API credential is missing."""


class UnsupportedSourceError(UniGrabError):
    """Raised when a source cannot be resolved by the configured resolvers."""


class MetadataResolutionError(UniGrabError):
    """Raised when metadata lookup fails for a supported source."""


DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"


@dataclass(slots=True)
class Track:
    title: str
    artist: str | None = None
    album: str | None = None
    duration_seconds: int | None = None
    isrc: str | None = None
    source_url: str | None = None
    platform: str | None = None
    track_number: int | None = None
    metadata: dict[str, Any] = field(default_factory=dict)

    @property
    def query(self) -> str:
        parts = [self.artist, self.title, self.album]
        return " ".join(part for part in parts if part).strip()

    def to_dict(self) -> dict[str, Any]:
        return {
            "title": self.title,
            "artist": self.artist,
            "album": self.album,
            "duration_seconds": self.duration_seconds,
            "isrc": self.isrc,
            "source_url": self.source_url,
            "platform": self.platform,
            "track_number": self.track_number,
            "metadata": self.metadata,
        }


@dataclass(slots=True)
class ResolvedSource:
    source: str
    kind: str
    platform: str
    tracks: list[Track]
    title: str | None = None

    def to_dict(self) -> dict[str, Any]:
        return {
            "source": self.source,
            "kind": self.kind,
            "platform": self.platform,
            "title": self.title,
            "tracks": [track.to_dict() for track in self.tracks],
        }

WINDOWS_RESERVED_NAMES = {
    "CON",
    "PRN",
    "AUX",
    "NUL",
    *(f"COM{i}" for i in range(1, 10)),
    *(f"LPT{i}" for i in range(1, 10)),
}


def is_url(value: str) -> bool:
    parsed = urlparse(value)
    return parsed.scheme in {"http", "https"} and bool(parsed.netloc)


def hostname(value: str) -> str:
    return (urlparse(value).hostname or "").lower().removeprefix("www.")


def clean_filename(value: str, fallback: str = "download") -> str:
    cleaned = re.sub(r'[<>:"/\\|?*\x00-\x1f]+', " ", value)
    cleaned = re.sub(r"\s+", " ", cleaned).strip(" .")
    if not cleaned:
        cleaned = fallback
    if cleaned.upper() in WINDOWS_RESERVED_NAMES:
        cleaned = f"{cleaned}_"
    return cleaned[:180]


def title_from_url_slug(value: str) -> str | None:
    parsed = urlparse(value)
    parts = [part for part in parsed.path.split("/") if part and not re.fullmatch(r"id\d+", part)]
    if not parts:
        return None
    slug = parts[-1]
    slug = re.sub(r"[-_]+", " ", slug).strip()
    return slug or None


def run_json_command(args: list[str]) -> dict:
    try:
        completed = subprocess.run(
            args,
            check=True,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
    except FileNotFoundError as exc:
        raise DependencyMissingError(f"Missing `{args[0]}` on PATH.") from exc
    except subprocess.CalledProcessError as exc:
        stderr = (exc.stderr or "").strip() or (exc.stdout or "").strip()
        raise MetadataResolutionError(stderr or f"`{args[0]}` exited with {exc.returncode}.") from exc

    try:
        return json.loads(completed.stdout or "{}")
    except json.JSONDecodeError as exc:
        raise MetadataResolutionError(f"`{args[0]}` did not return valid JSON.") from exc

def http_json(
    url: str,
    *,
    method: str = "GET",
    headers: dict[str, str] | None = None,
    body: bytes | None = None,
    timeout: int = 20,
) -> dict:
    req_headers = {"User-Agent": DEFAULT_USER_AGENT}
    if headers:
        req_headers.update(headers)
    request = Request(url, data=body, headers=req_headers, method=method)
    try:
        with urlopen(request, timeout=timeout) as response:
            charset = response.headers.get_content_charset() or "utf-8"
            return json.loads(response.read().decode(charset))
    except Exception as exc:
        raise MetadataResolutionError(f"HTTP metadata request failed for {url}: {exc}") from exc


def http_text(
    url: str,
    *,
    headers: dict[str, str] | None = None,
    timeout: int = 20,
) -> str:
    req_headers = {"User-Agent": DEFAULT_USER_AGENT}
    if headers:
        req_headers.update(headers)
    request = Request(url, headers=req_headers)
    try:
        with urlopen(request, timeout=timeout) as response:
            charset = response.headers.get_content_charset() or "utf-8"
            return response.read().decode(charset, errors="replace")
    except Exception as exc:
        raise MetadataResolutionError(f"HTTP metadata request failed for {url}: {exc}") from exc


def ensure_directory(path: Path) -> Path:
    path.mkdir(parents=True, exist_ok=True)
    return path

def yt_dlp_command(*, required: bool = True) -> list[str]:
    if importlib.util.find_spec("yt_dlp") is None:
        if required:
            raise DependencyMissingError(
                "Missing Python package `yt-dlp`. Run `python -m pip install -r requirements.txt` from the project folder."
            )
    return [sys.executable, "-m", "yt_dlp"]


def bundled_ffmpeg_path(*, required: bool = False) -> str | None:
    system_ffmpeg = shutil.which("ffmpeg")
    try:
        import imageio_ffmpeg
    except ImportError as exc:
        if system_ffmpeg:
            return system_ffmpeg
        if required:
            raise DependencyMissingError(
                "Missing FFmpeg. Run `python -m pip install -r requirements.txt` from the project folder or install FFmpeg on PATH."
            ) from exc
        return None

    try:
        return imageio_ffmpeg.get_ffmpeg_exe()
    except Exception as exc:
        if system_ffmpeg:
            return system_ffmpeg
        if required:
            raise DependencyMissingError(f"FFmpeg is unavailable: {exc}") from exc
        return None


def python_package_status(module_name: str) -> str:
    return "installed" if importlib.util.find_spec(module_name) else "missing"

class Resolver(Protocol):
    def can_resolve(self, source: str) -> bool:
        ...

    def resolve(self, source: str) -> ResolvedSource:
        ...


class ResolverRegistry:
    def __init__(self, resolvers: list[Resolver] | None = None, *, media_type: str = "music") -> None:
        default_resolvers = [
            BlockedSourceResolver(),
            SpotifyResolver(),
            AppleMusicResolver(),
            ArchiveOrgResolver(),
            M3UPlaylistResolver(),
            YtDlpMetadataResolver(),
            QueryResolver(),
        ]
        if media_type == "podcast":
            default_resolvers = [
                PodcastIndexUrlResolver(),
                PodcastPlatformResolver(),
                PodcastFeedResolver(),
                PodcastQueryResolver(),
                *default_resolvers,
            ]
        self.resolvers = resolvers or default_resolvers

    def resolve(self, source: str) -> ResolvedSource:
        for resolver in self.resolvers:
            if resolver.can_resolve(source):
                return resolver.resolve(source)
        raise UnsupportedSourceError(f"No resolver available for: {source}")


class BlockedSourceResolver:
    BLOCKED_HOSTS: set[str] = set()

    def can_resolve(self, source: str) -> bool:
        return hostname(source) in self.BLOCKED_HOSTS

    def resolve(self, source: str) -> ResolvedSource:
        raise UnsupportedSourceError("This source is not supported by this app.")


class QueryResolver:
    def can_resolve(self, source: str) -> bool:
        return not is_url(source)

    def resolve(self, source: str) -> ResolvedSource:
        return ResolvedSource(
            source=source,
            kind="track",
            platform="query",
            title=source,
            tracks=[Track(title=source, platform="query")],
        )


class M3UPlaylistResolver:
    """Resolver for M3U/M3U8 playlists (IPTV streams, HLS playlists, local media lists)."""

    def can_resolve(self, source: str) -> bool:
        if not source:
            return False
        lower = source.lower()
        if lower.endswith((".m3u", ".m3u8")):
            return True
        if is_url(source):
            path = urlparse(source).path.lower()
            return path.endswith((".m3u", ".m3u8"))
        return False

    def resolve(self, source: str) -> ResolvedSource:
        content = self._read_playlist(source)
        tracks = self._parse_m3u(content, source)
        if not tracks:
            raise MetadataResolutionError("M3U playlist is empty or contains no valid stream URLs.")
        title = self._playlist_title(source)
        return ResolvedSource(
            source=source,
            kind="playlist",
            platform="m3u",
            title=title,
            tracks=tracks,
        )

    @staticmethod
    def _read_playlist(source: str) -> str:
        if is_url(source):
            return http_text(source)
        path = Path(source)
        if path.is_file():
            return path.read_text(encoding="utf-8", errors="replace")
        raise MetadataResolutionError(f"M3U file not found: {source}")

    @staticmethod
    def _parse_m3u(content: str, source: str) -> list[Track]:
        lines = content.splitlines()
        tracks: list[Track] = []
        pending_title: str | None = None
        pending_duration: int | None = None
        pending_group: str | None = None

        for line in lines:
            line = line.strip()
            if not line or line.startswith("#EXTM3U"):
                continue
            if line.startswith("#EXTINF:"):
                info = line[8:]
                comma = info.find(",")
                if comma >= 0:
                    duration_str = info[:comma].strip().split()[0]
                    pending_title = info[comma + 1:].strip() or None
                    try:
                        dur = int(duration_str)
                        pending_duration = dur if dur > 0 else None
                    except ValueError:
                        pending_duration = None
                group_match = re.search(r'group-title="([^"]*)"', line)
                if group_match:
                    pending_group = group_match.group(1) or None
                continue
            if line.startswith("#"):
                continue
            url = line
            if not is_url(url) and not Path(url).is_absolute():
                if is_url(source):
                    parsed = urlparse(source)
                    base = parsed._replace(path=parsed.path.rsplit("/", 1)[0] + "/" + url)
                    url = urlunparse(base)
                else:
                    url = str(Path(source).parent / url)

            title = pending_title or Path(urlparse(url).path).stem or "Stream"
            tracks.append(Track(
                title=title,
                duration_seconds=pending_duration,
                source_url=url,
                platform="m3u",
                metadata={
                    "group": pending_group,
                    "playlist_source": source,
                },
            ))
            pending_title = None
            pending_duration = None
            pending_group = None

        return tracks

    @staticmethod
    def _playlist_title(source: str) -> str:
        if is_url(source):
            path = urlparse(source).path
            name = Path(path).stem
        else:
            name = Path(source).stem
        return name or "M3U Playlist"


class ArchiveOrgResolver:
    """Resolver for Internet Archive items (archive.org/details/...)."""

    VIDEO_EXTENSIONS = {".mp4", ".mkv", ".avi", ".webm", ".ogv", ".mov", ".mpeg", ".mpg"}
    AUDIO_EXTENSIONS = {".mp3", ".ogg", ".flac", ".wav", ".m4a", ".aac", ".opus"}

    def can_resolve(self, source: str) -> bool:
        return hostname(source) == "archive.org" and "/details/" in urlparse(source).path

    def resolve(self, source: str) -> ResolvedSource:
        identifier = self._extract_identifier(source)
        metadata = self._fetch_metadata(identifier)
        item_metadata = metadata.get("metadata", {})
        files = metadata.get("files", [])

        title = item_metadata.get("title") or identifier
        creator = item_metadata.get("creator")
        if isinstance(creator, list):
            creator = ", ".join(creator)
        description = item_metadata.get("description", "")
        if isinstance(description, list):
            description = " ".join(description)
        date = item_metadata.get("date") or item_metadata.get("publicdate")
        item_media_type = item_metadata.get("mediatype", "")
        license_url = item_metadata.get("licenseurl", "")

        media_files = self._filter_media_files(files, item_media_type)
        if not media_files:
            raise MetadataResolutionError(
                f"No downloadable media files found in archive.org item `{identifier}`. "
                "The item may contain only metadata, images, or unsupported formats."
            )

        tracks = []
        for idx, file_info in enumerate(media_files, start=1):
            file_name = file_info.get("name", "")
            file_title = file_info.get("title") or Path(file_name).stem or f"File {idx}"
            file_url = f"https://archive.org/download/{identifier}/{file_name}"
            duration = None
            if file_info.get("length"):
                try:
                    duration = round(float(file_info["length"]))
                except (ValueError, TypeError):
                    pass

            tracks.append(Track(
                title=file_title,
                artist=creator,
                album=title,
                duration_seconds=duration,
                source_url=file_url,
                platform="archive.org",
                track_number=idx,
                metadata={
                    "archive_identifier": identifier,
                    "file_name": file_name,
                    "file_format": file_info.get("format", ""),
                    "file_size": file_info.get("size"),
                    "description": description[:500] if description else None,
                    "date": date,
                    "license": license_url,
                    "artists": normalize_artist_values(creator),
                },
            ))

        kind = "playlist" if len(tracks) > 1 else "track"
        return ResolvedSource(
            source=source,
            kind=kind,
            platform="archive.org",
            title=title,
            tracks=tracks,
        )

    @staticmethod
    def _extract_identifier(source: str) -> str:
        parsed = urlparse(source)
        match = re.search(r"/details/([^/?#]+)", parsed.path)
        if not match:
            raise UnsupportedSourceError("Could not extract archive.org item identifier from URL.")
        return match.group(1)

    @staticmethod
    def _fetch_metadata(identifier: str) -> dict:
        return http_json(
            f"https://archive.org/metadata/{identifier}",
        )

    @classmethod
    def _filter_media_files(cls, files: list[dict], media_type: str) -> list[dict]:
        allowed = cls.VIDEO_EXTENSIONS | cls.AUDIO_EXTENSIONS
        if media_type in {"movies", "video"}:
            preferred = cls.VIDEO_EXTENSIONS
        elif media_type in {"audio", "music", "podcast"}:
            preferred = cls.AUDIO_EXTENSIONS
        else:
            preferred = allowed

        preferred_files = [
            f for f in files
            if Path(f.get("name", "")).suffix.lower() in preferred
            and f.get("source", "") == "original"
        ]
        if preferred_files:
            return preferred_files

        original_media = [
            f for f in files
            if Path(f.get("name", "")).suffix.lower() in allowed
            and f.get("source", "") == "original"
        ]
        if original_media:
            return original_media

        return [
            f for f in files
            if Path(f.get("name", "")).suffix.lower() in allowed
        ]


class PodcastPlatformResolver:
    PLATFORM_HOSTS = {
        "open.spotify.com": "Spotify",
        "podcasts.apple.com": "Apple Podcasts",
        "music.apple.com": "Apple Music",
        "itunes.apple.com": "Apple/iTunes",
        "music.amazon.com": "Amazon Music",
        "audible.com": "Audible",
        "pocketcasts.com": "Pocket Casts",
    }

    def can_resolve(self, source: str) -> bool:
        return hostname(source) in self.PLATFORM_HOSTS

    def resolve(self, source: str) -> ResolvedSource:
        host = hostname(source)
        if host in {"podcasts.apple.com", "itunes.apple.com"}:
            return self._resolve_apple_podcast(source)
        if host == "open.spotify.com":
            return self._resolve_spotify_podcast(source)
        return self._resolve_by_page_title(source, self.PLATFORM_HOSTS[host])

    @staticmethod
    def _resolve_apple_podcast(source: str) -> ResolvedSource:
        parsed = urlparse(source)
        episode_ids = parse_qs(parsed.query).get("i")
        collection_match = re.search(r"/id(\d+)", parsed.path)
        lookup_id = episode_ids[0] if episode_ids else (collection_match.group(1) if collection_match else None)
        if not lookup_id:
            raise UnsupportedSourceError("Apple Podcasts URL did not include a podcast or episode id.")

        try:
            data = http_json(f"https://itunes.apple.com/lookup?id={lookup_id}&entity=podcastEpisode")
        except MetadataResolutionError:
            metadata = YtDlpMetadataResolver._open_graph_metadata(source)
            title = metadata.get("title") or title_from_url_slug(source)
            if title:
                return PodcastIndexClient.from_env().resolve_show_or_episode(title=title, episode_title=None, source=source)
            raise
        results = data.get("results") or []
        episode = next((item for item in results if item.get("wrapperType") == "podcastEpisode"), None)
        collection = next((item for item in results if item.get("wrapperType") == "track" and item.get("kind") == "podcast"), None)

        if episode and episode.get("episodeUrl"):
            track = Track(
                title=episode.get("trackName") or episode.get("collectionName") or "Podcast episode",
                artist=episode.get("artistName") or episode.get("collectionName"),
                album=episode.get("collectionName"),
                duration_seconds=round(episode["trackTimeMillis"] / 1000) if episode.get("trackTimeMillis") else None,
                source_url=episode.get("episodeUrl"),
                platform="podcast",
                metadata={
                    "description": episode.get("description") or episode.get("shortDescription"),
                    "feed_url": episode.get("feedUrl"),
                    "source_page": source,
                    "artists": normalize_artist_values(episode.get("artistName") or episode.get("collectionName")),
                },
            )
            return ResolvedSource(source=source, kind="podcast", platform="apple_podcasts", title=track.album or track.title, tracks=[track])

        feed_url = (collection or {}).get("feedUrl") or (episode or {}).get("feedUrl")
        if feed_url:
            resolved = PodcastFeedResolver().resolve(feed_url)
            resolved.source = source
            resolved.platform = "apple_podcasts"
            return resolved

        title = (collection or episode or {}).get("collectionName") or (episode or {}).get("trackName")
        if title:
            return PodcastIndexClient.from_env().resolve_show_or_episode(title=title, episode_title=(episode or {}).get("trackName"), source=source)

        raise UnsupportedSourceError("Could not find an Apple Podcasts RSS feed or episode audio URL.")

    @staticmethod
    def _resolve_spotify_podcast(source: str) -> ResolvedSource:
        kind, spotify_id = SpotifyResolver._parse_url(source)
        if kind not in {"episode", "show"}:
            raise UnsupportedSourceError("Spotify music URLs are not valid podcast links. Choose music mode for songs/albums/playlists.")

        try:
            resolved = SpotifyResolver().resolve(source)
        except UniGrabError:
            metadata = SpotifyResolver._embed_metadata(kind, spotify_id) or YtDlpMetadataResolver._open_graph_metadata(source)
            resolved = SpotifyResolver._fallback_podcast(
                source,
                kind,
                spotify_id,
                title=metadata.get("title"),
                artist=metadata.get("artist"),
            )

        track = resolved.tracks[0] if resolved.tracks else None
        if kind == "episode" and track:
            show_title = track.album or track.artist or resolved.title
        else:
            show_title = resolved.title or (track.album if track else None) or (track.artist if track else None) or (track.title if track else None)
        episode_title = track.title if kind == "episode" and track else None
        if not show_title:
            raise UnsupportedSourceError("Could not read Spotify podcast metadata. Add Spotify API credentials or use the RSS feed URL.")
        return PodcastIndexClient.from_env().resolve_show_or_episode(title=show_title, episode_title=episode_title, source=source)

    @staticmethod
    def _resolve_by_page_title(source: str, platform: str) -> ResolvedSource:
        metadata = YtDlpMetadataResolver._open_graph_metadata(source)
        title = metadata.get("title") or title_from_url_slug(source)
        if not title:
            raise UnsupportedSourceError(f"Could not read {platform} podcast metadata. Use the podcast RSS feed URL instead.")
        return PodcastIndexClient.from_env().resolve_show_or_episode(title=title, episode_title=None, source=source)


class PodcastIndexUrlResolver:
    def can_resolve(self, source: str) -> bool:
        return hostname(source) == "podcastindex.org" and "/podcast/" in urlparse(source).path

    def resolve(self, source: str) -> ResolvedSource:
        parsed = urlparse(source)
        feed_match = re.search(r"/podcast/(\d+)", parsed.path)
        if not feed_match:
            raise UnsupportedSourceError("PodcastIndex URL did not include a podcast id.")

        feed_id = feed_match.group(1)
        episode_ids = parse_qs(parsed.query).get("episode")
        client = PodcastIndexClient.from_env()
        if episode_ids:
            try:
                return client.resolve_episode_by_id(feed_id=feed_id, episode_id=episode_ids[0], source=source)
            except UniGrabError:
                metadata = podcastindex_page_metadata(source)
                if metadata.get("show_title") or metadata.get("episode_title"):
                    return client.resolve_show_or_episode(
                        title=metadata.get("show_title") or metadata.get("episode_title") or feed_id,
                        episode_title=metadata.get("episode_title"),
                        source=source,
                    )
                raise
        try:
            return client.resolve_feed_by_id(feed_id=feed_id, source=source)
        except UniGrabError:
            metadata = podcastindex_page_metadata(source)
            title = metadata.get("show_title") or metadata.get("episode_title")
            if title:
                return client.resolve_show_or_episode(title=title, episode_title=None, source=source)
            raise


class PodcastIndexClient:
    BASE_URL = "https://api.podcastindex.org/api/1.0"

    def __init__(self, api_key: str, api_secret: str) -> None:
        self.api_key = api_key
        self.api_secret = api_secret

    @classmethod
    def from_env(cls) -> "PodcastIndexClient":
        api_key = os.environ.get("PODCASTINDEX_API_KEY")
        api_secret = os.environ.get("PODCASTINDEX_API_SECRET")
        if not api_key or not api_secret:
            return cls("", "")
        return cls(api_key, api_secret)

    def resolve_show_or_episode(self, *, title: str, episode_title: str | None, source: str) -> ResolvedSource:
        feed = self.find_feed(title)
        feed_url = feed.get("url")
        if not feed_url:
            raise UnsupportedSourceError(f"PodcastIndex could not find an RSS feed for `{title}`.")

        resolved = PodcastFeedResolver().resolve(feed_url)
        resolved.source = source
        resolved.platform = "podcastindex"

        if episode_title:
            match = self._find_episode(resolved.tracks, episode_title)
            if match:
                return ResolvedSource(source=source, kind="podcast", platform="podcastindex", title=resolved.title, tracks=[match])

            api_match = self.find_episode(feed.get("id"), episode_title)
            if api_match and api_match.get("enclosureUrl"):
                track = Track(
                    title=api_match.get("title") or episode_title,
                    artist=resolved.title,
                    album=resolved.title,
                    duration_seconds=api_match.get("duration"),
                    source_url=api_match.get("enclosureUrl"),
                    platform="podcast",
                    metadata={
                        "description": api_match.get("description"),
                        "feed_url": feed_url,
                        "source_page": source,
                        "artists": normalize_artist_values(resolved.title),
                    },
                )
                return ResolvedSource(source=source, kind="podcast", platform="podcastindex", title=resolved.title, tracks=[track])

            raise UnsupportedSourceError(f"Found `{resolved.title}`, but could not match the episode `{episode_title}` in its RSS feed.")

        return resolved

    def resolve_feed_by_id(self, *, feed_id: str, source: str) -> ResolvedSource:
        feed = self.feed_by_id(feed_id)
        feed_url = feed.get("url")
        if not feed_url:
            raise UnsupportedSourceError(f"PodcastIndex feed `{feed_id}` did not include an RSS feed URL.")
        resolved = PodcastFeedResolver().resolve(feed_url)
        resolved.source = source
        resolved.platform = "podcastindex"
        return resolved

    def resolve_episode_by_id(self, *, feed_id: str, episode_id: str, source: str) -> ResolvedSource:
        episode = self.episode_by_id(episode_id)
        if episode and episode.get("enclosureUrl"):
            feed_title = episode.get("feedTitle") or episode.get("feed_title") or "Podcast"
            track = Track(
                title=episode.get("title") or f"Podcast episode {episode_id}",
                artist=feed_title,
                album=feed_title,
                duration_seconds=episode.get("duration"),
                source_url=episode.get("enclosureUrl"),
                platform="podcast",
                metadata={
                    "description": episode.get("description"),
                    "feed_url": episode.get("feedUrl"),
                    "source_page": source,
                    "artists": normalize_artist_values(feed_title),
                },
            )
            return ResolvedSource(source=source, kind="podcast", platform="podcastindex", title=feed_title, tracks=[track])

        feed_resolved = self.resolve_feed_by_id(feed_id=feed_id, source=source)
        return self._match_episode_id_from_feed(feed_resolved, episode_id, source)

    def feed_by_id(self, feed_id: str) -> dict:
        if self.api_key and self.api_secret:
            data = self.get("/podcasts/byfeedid", {"id": str(feed_id)})
            return data.get("feed") or {}

        data = self.public_get("/lookup", {"entity": "podcast", "id": str(feed_id)})
        feeds = self._feeds_from_public_search(data)
        if feeds:
            return feeds[0]
        return {}

    def episode_by_id(self, episode_id: str) -> dict | None:
        if not self.api_key or not self.api_secret:
            return None
        data = self.get("/episodes/byid", {"id": str(episode_id)})
        return data.get("episode") or data.get("item")

    @staticmethod
    def _match_episode_id_from_feed(resolved: ResolvedSource, episode_id: str, source: str) -> ResolvedSource:
        for track in resolved.tracks:
            metadata_ids = {
                str(track.metadata.get("podcastindex_episode_id") or ""),
                str(track.metadata.get("guid") or ""),
            }
            if str(episode_id) in metadata_ids:
                return ResolvedSource(source=source, kind="podcast", platform="podcastindex", title=resolved.title, tracks=[track])
        raise UnsupportedSourceError(
            f"PodcastIndex episode `{episode_id}` needs authenticated episode lookup. "
            "Set PODCASTINDEX_API_KEY and PODCASTINDEX_API_SECRET, or use the podcast RSS feed URL."
        )

    def find_feed(self, title: str) -> dict:
        if self.api_key and self.api_secret:
            data = self.get("/search/byterm", {"q": title, "max": "10", "clean": "true"})
            feeds = data.get("feeds") or []
        else:
            data = self.public_get("/search", {"term": title, "media": "podcast", "entity": "podcast"})
            feeds = self._feeds_from_public_search(data)
        if not feeds:
            raise UnsupportedSourceError(f"PodcastIndex found no podcast feed for `{title}`.")
        title_key = normalize_match_text(title)
        return max(feeds, key=lambda feed: similarity_score(title_key, normalize_match_text(feed.get("title") or "")))

    def find_episode(self, feed_id, episode_title: str) -> dict | None:
        if not feed_id:
            return None
        data = self.get("/episodes/byfeedid", {"id": str(feed_id), "max": "1000"})
        episodes = data.get("items") or []
        if not episodes:
            return None
        title_key = normalize_match_text(episode_title)
        return max(episodes, key=lambda item: similarity_score(title_key, normalize_match_text(item.get("title") or "")))

    @staticmethod
    def _find_episode(tracks: list[Track], episode_title: str) -> Track | None:
        title_key = normalize_match_text(episode_title)
        if not title_key:
            return None
        best = max(tracks, key=lambda track: similarity_score(title_key, normalize_match_text(track.title)), default=None)
        if best and similarity_score(title_key, normalize_match_text(best.title)) >= 0.55:
            return best
        return None

    def get(self, path: str, params: dict[str, str]) -> dict:
        if not self.api_key or not self.api_secret:
            raise DependencyMissingError(
                "Episode-level PodcastIndex lookup needs PODCASTINDEX_API_KEY and PODCASTINDEX_API_SECRET. "
                "Without keys, UniGrab can still try show-level RSS lookup."
            )
        auth_date = str(int(time.time()))
        digest = hashlib.sha1((self.api_key + self.api_secret + auth_date).encode("utf-8")).hexdigest()
        headers = {
            "User-Agent": DEFAULT_USER_AGENT,
            "X-Auth-Date": auth_date,
            "X-Auth-Key": self.api_key,
            "Authorization": digest,
        }
        return http_json(f"{self.BASE_URL}{path}?{urlencode(params)}", headers=headers)

    @staticmethod
    def public_get(path: str, params: dict[str, str]) -> dict:
        return http_json(f"https://itunes.apple.com{path}?{urlencode(params)}")

    @staticmethod
    def _feeds_from_public_search(data: dict) -> list[dict]:
        feeds = []
        for item in data.get("results") or []:
            feed_url = item.get("feedUrl") or item.get("feedURL") or item.get("url")
            if not feed_url:
                continue
            feeds.append(
                {
                    "id": item.get("trackId") or item.get("collectionId"),
                    "title": item.get("collectionName") or item.get("trackName"),
                    "url": feed_url,
                    "author": item.get("artistName"),
                }
            )
        return feeds


class PodcastFeedResolver:
    def can_resolve(self, source: str) -> bool:
        if not is_url(source):
            return False
        parsed = urlparse(source)
        path = parsed.path.lower()
        return path.endswith((".xml", ".rss")) or "rss" in path or "feed" in path

    def resolve(self, source: str) -> ResolvedSource:
        page = http_text(source)
        try:
            root = ET.fromstring(page)
        except ET.ParseError as exc:
            raise MetadataResolutionError(f"Podcast feed is not valid XML: {exc}") from exc

        channel = root.find("channel")
        if channel is None:
            channel = root.find("{http://www.w3.org/2005/Atom}feed")
        if channel is None:
            raise MetadataResolutionError("Podcast feed did not include a channel/feed element.")

        title = text_or_none(channel.find("title")) or text_or_none(channel.find("{http://www.w3.org/2005/Atom}title")) or "Podcast"
        items = channel.findall("item")
        tracks = [self._track_from_rss_item(item, source) for item in items]
        if not tracks:
            entries = channel.findall("{http://www.w3.org/2005/Atom}entry")
            tracks = [self._track_from_atom_entry(entry, source) for entry in entries]

        return ResolvedSource(source=source, kind="podcast_feed", platform="podcast", title=title, tracks=[track for track in tracks if track.source_url])

    @staticmethod
    def _track_from_rss_item(item: ET.Element, source: str) -> Track:
        enclosure = item.find("enclosure")
        url = enclosure.attrib.get("url") if enclosure is not None else None
        title = text_or_none(item.find("title")) or url or "Podcast episode"
        description = text_or_none(item.find("description"))
        duration = text_or_none(item.find("{http://www.itunes.com/dtds/podcast-1.0.dtd}duration"))
        author = text_or_none(item.find("{http://www.itunes.com/dtds/podcast-1.0.dtd}author")) or text_or_none(item.find("author"))
        return Track(
            title=html.unescape(title),
            artist=author,
            album=None,
            duration_seconds=parse_duration(duration),
            source_url=url,
            platform="podcast",
            metadata={"description": description, "feed_url": source, "artists": normalize_artist_values(author)},
        )

    @staticmethod
    def _track_from_atom_entry(entry: ET.Element, source: str) -> Track:
        url = None
        for link in entry.findall("{http://www.w3.org/2005/Atom}link"):
            if link.attrib.get("rel") in {"enclosure", "alternate"} and link.attrib.get("href"):
                url = link.attrib["href"]
                break
        title = text_or_none(entry.find("{http://www.w3.org/2005/Atom}title")) or url or "Podcast episode"
        author = text_or_none(entry.find("{http://www.w3.org/2005/Atom}author/{http://www.w3.org/2005/Atom}name"))
        return Track(
            title=html.unescape(title),
            artist=author,
            source_url=url,
            platform="podcast",
            metadata={"feed_url": source, "artists": normalize_artist_values(author)},
        )


class PodcastQueryResolver:
    def can_resolve(self, source: str) -> bool:
        return not is_url(source)

    def resolve(self, source: str) -> ResolvedSource:
        print(f"Searching PodcastIndex for: {source}")
        client = PodcastIndexClient.from_env()
        feed = client.find_feed(source)
        feed_url = feed.get("url")
        if not feed_url:
            raise UnsupportedSourceError(f"PodcastIndex could not find an RSS feed for `{source}`.")
        
        resolved = PodcastFeedResolver().resolve(feed_url)
        resolved.source = source
        resolved.platform = "podcastindex"
        return resolved


class YtDlpMetadataResolver:
    DIRECT_HOST_HINTS = {
        "youtube.com",
        "youtu.be",
        "music.youtube.com",
        "soundcloud.com",
        "bandcamp.com",
        "jiosaavn.com",
        "gaana.com",
        "dailymotion.com",
    }

    def can_resolve(self, source: str) -> bool:
        return is_url(source) and not SpotifyResolver.is_spotify(source) and not AppleMusicResolver.is_apple(source)

    def resolve(self, source: str) -> ResolvedSource:
        metadata_source = self._single_video_url(source) if self._is_youtube_single(source) else source
        playlist_args = ["--flat-playlist"] if self._is_explicit_playlist(source) else ["--no-playlist"]
        try:
            info = run_json_command([*yt_dlp_command(), "--dump-single-json", *playlist_args, "--no-warnings", metadata_source])
        except DependencyMissingError:
            return self._fallback_resolved_source(metadata_source)
        except MetadataResolutionError:
            return self._fallback_resolved_source(metadata_source)
        entries = info.get("entries")
        if entries:
            tracks = [self._track_from_yt_info(entry, metadata_source) for entry in entries if entry]
            return ResolvedSource(
                source=metadata_source,
                kind="playlist",
                platform=hostname(metadata_source) or "yt-dlp",
                title=info.get("title"),
                tracks=tracks,
            )

        return ResolvedSource(
            source=metadata_source,
            kind="track",
            platform=hostname(metadata_source) or "yt-dlp",
            title=info.get("title"),
            tracks=[self._track_from_yt_info(info, metadata_source)],
        )

    @staticmethod
    def _is_youtube_host(source: str) -> bool:
        return hostname(source) in {"youtube.com", "youtu.be", "music.youtube.com"}

    @classmethod
    def _is_youtube_single(cls, source: str) -> bool:
        if not cls._is_youtube_host(source):
            return False
        parsed = urlparse(source)
        query = parse_qs(parsed.query)
        return parsed.netloc.endswith("youtu.be") or bool(query.get("v"))

    @classmethod
    def _is_explicit_playlist(cls, source: str) -> bool:
        if not cls._is_youtube_host(source):
            return True
        parsed = urlparse(source)
        query = parse_qs(parsed.query)
        path = parsed.path.rstrip("/")
        return path == "/playlist" or (bool(query.get("list")) and not query.get("v"))

    @classmethod
    def _single_video_url(cls, source: str) -> str:
        if not cls._is_youtube_single(source):
            return source
        parsed = urlparse(source)
        if hostname(source) == "youtu.be":
            return source
        query = [(key, value) for key, value in parse_qsl(parsed.query, keep_blank_values=True) if key in {"v", "t", "start"}]
        return urlunparse(parsed._replace(query=urlencode(query)))

    @staticmethod
    def _fallback_title(source: str) -> str:
        parsed = urlparse(source)
        video_id = parse_qs(parsed.query).get("v")
        if video_id:
            return video_id[0]
        return parsed.path.strip("/").split("/")[-1] or "download"

    @classmethod
    def _fallback_resolved_source(cls, source: str) -> ResolvedSource:
        platform = hostname(source) or "web"
        metadata = cls._youtube_oembed_metadata(source) if cls._is_youtube_host(source) else {}
        metadata = metadata or cls._open_graph_metadata(source)
        title = metadata.get("title") or cls._fallback_title(source)
        artist = metadata.get("artist")
        album = metadata.get("album")
        return ResolvedSource(
            source=source,
            kind="track",
            platform=platform,
            title=title,
            tracks=[
                Track(
                    title=title,
                    artist=artist,
                    album=album,
                    source_url=source,
                    platform=platform,
                    metadata={"metadata_source": metadata.get("metadata_source", "fallback")},
                )
            ],
        )

    @staticmethod
    def _youtube_oembed_metadata(source: str) -> dict[str, str]:
        try:
            data = http_json(
                "https://www.youtube.com/oembed?" + urlencode({"url": source, "format": "json"}),
            )
        except MetadataResolutionError:
            return {}

        return {
            key: value
            for key, value in {
                "title": data.get("title"),
                "artist": data.get("author_name"),
                "metadata_source": "youtube_oembed",
            }.items()
            if value
        }

    @staticmethod
    def _open_graph_metadata(source: str) -> dict[str, str]:
        try:
            page = http_text(source)
        except MetadataResolutionError:
            return {}

        def meta(*names: str) -> str | None:
            for name in names:
                patterns = [
                    rf'<meta[^>]+property=["\']{re.escape(name)}["\'][^>]+content=["\']([^"\']+)["\']',
                    rf'<meta[^>]+name=["\']{re.escape(name)}["\'][^>]+content=["\']([^"\']+)["\']',
                    rf'<meta[^>]+content=["\']([^"\']+)["\'][^>]+property=["\']{re.escape(name)}["\']',
                    rf'<meta[^>]+content=["\']([^"\']+)["\'][^>]+name=["\']{re.escape(name)}["\']',
                ]
                for pattern in patterns:
                    match = re.search(pattern, page, flags=re.IGNORECASE)
                    if match:
                        return html.unescape(match.group(1)).strip()
            return None

        title = meta("music:song", "og:title", "twitter:title")
        artist = meta("music:musician", "music:creator", "artist", "twitter:creator")
        album = meta("music:album", "album")
        return {
            key: value
            for key, value in {
                "title": title,
                "artist": artist,
                "album": album,
                "metadata_source": "open_graph",
            }.items()
            if value
        }

    @staticmethod
    def _track_from_yt_info(info: dict, source: str) -> Track:
        artists = YtDlpMetadataResolver._artist_values(info)
        artist = ", ".join(artists) if artists else None
        title = info.get("track") or info.get("title") or source
        url = info.get("webpage_url") or info.get("url") or source
        if not is_url(url) and hostname(source) in {"youtube.com", "youtu.be", "music.youtube.com"}:
            video_id = info.get("id") or url
            url = f"https://www.youtube.com/watch?v={video_id}"
        return Track(
            title=title,
            artist=artist,
            album=info.get("album"),
            duration_seconds=info.get("duration"),
            source_url=url,
            platform=hostname(source) or "yt-dlp",
            track_number=info.get("playlist_index"),
            metadata={"id": info.get("id"), "artists": artists},
        )

    @staticmethod
    def _artist_values(info: dict) -> list[str]:
        candidates = [
            info.get("artists"),
            info.get("artist"),
            info.get("creators"),
            info.get("creator"),
            info.get("uploader"),
            info.get("channel"),
        ]
        for candidate in candidates:
            values = normalize_artist_values(candidate)
            if values:
                return values
        return []


@dataclass(slots=True)
class SpotifyResolver:
    token: str | None = None

    @staticmethod
    def is_spotify(source: str) -> bool:
        return hostname(source) == "open.spotify.com"

    def can_resolve(self, source: str) -> bool:
        return self.is_spotify(source)

    def resolve(self, source: str) -> ResolvedSource:
        kind, spotify_id = self._parse_url(source)
        try:
            token = self.token or self._client_credentials_token()
        except (DependencyMissingError, MetadataResolutionError):
            return self._resolve_without_api(source, kind, spotify_id)

        if kind == "track":
            track = self._get_json(f"https://api.spotify.com/v1/tracks/{spotify_id}", token)
            return ResolvedSource(source=source, kind="track", platform="spotify", title=track.get("name"), tracks=[self._track(track)])

        if kind == "album":
            album = self._get_json(f"https://api.spotify.com/v1/albums/{spotify_id}", token)
            tracks = []
            for item in album.get("tracks", {}).get("items", []):
                item["album"] = album
                tracks.append(self._track(item))
            return ResolvedSource(source=source, kind="album", platform="spotify", title=album.get("name"), tracks=tracks)

        if kind == "playlist":
            playlist = self._get_json(f"https://api.spotify.com/v1/playlists/{spotify_id}", token)
            tracks = []
            page = playlist.get("tracks", {})
            while page:
                for item in page.get("items", []):
                    track = item.get("track")
                    if track and track.get("type") == "track":
                        tracks.append(self._track(track))
                next_url = page.get("next")
                page = self._get_json(next_url, token) if next_url else None
            return ResolvedSource(source=source, kind="playlist", platform="spotify", title=playlist.get("name"), tracks=tracks)

        if kind == "episode":
            episode = self._get_json(f"https://api.spotify.com/v1/episodes/{spotify_id}?market=US", token)
            track = self._episode(episode, source)
            return ResolvedSource(source=source, kind="podcast", platform="spotify", title=track.album or track.title, tracks=[track])

        if kind == "show":
            show = self._get_json(f"https://api.spotify.com/v1/shows/{spotify_id}?market=US", token)
            tracks = []
            page = show.get("episodes", {})
            while page:
                for item in page.get("items", []):
                    if item:
                        item["show"] = show
                        tracks.append(self._episode(item, source))
                next_url = page.get("next")
                page = self._get_json(next_url, token) if next_url else None
            return ResolvedSource(source=source, kind="podcast_feed", platform="spotify", title=show.get("name"), tracks=tracks)

        raise UnsupportedSourceError(f"Unsupported Spotify URL kind: {kind}")

    @staticmethod
    def _parse_url(source: str) -> tuple[str, str]:
        parsed = urlparse(source)
        match = re.search(r"/(track|album|playlist|episode|show)/([A-Za-z0-9]+)", parsed.path)
        if not match:
            raise UnsupportedSourceError("Expected a Spotify track, album, playlist, episode, or show URL.")
        return match.group(1), match.group(2)

    @staticmethod
    def _client_credentials_token() -> str:
        client_id = os.environ.get("SPOTIFY_CLIENT_ID")
        client_secret = os.environ.get("SPOTIFY_CLIENT_SECRET")
        if not client_id or not client_secret:
            raise DependencyMissingError(
                "Spotify metadata needs SPOTIFY_CLIENT_ID and SPOTIFY_CLIENT_SECRET. "
                "Spotify audio is not downloaded; the API is used only for metadata."
            )

        credentials = base64.b64encode(f"{client_id}:{client_secret}".encode("utf-8")).decode("ascii")
        payload = urlencode({"grant_type": "client_credentials"}).encode("ascii")
        data = http_json(
            "https://accounts.spotify.com/api/token",
            method="POST",
            headers={
                "Authorization": f"Basic {credentials}",
                "Content-Type": "application/x-www-form-urlencoded",
            },
            body=payload,
        )
        token = data.get("access_token")
        if not token:
            raise MetadataResolutionError("Spotify token response did not include access_token.")
        return token

    @staticmethod
    def _get_json(url: str, token: str) -> dict:
        return http_json(url, headers={"Authorization": f"Bearer {token}"})

    @staticmethod
    def _track(item: dict) -> Track:
        album = item.get("album") or {}
        artist_names = [artist.get("name", "") for artist in item.get("artists", []) if artist.get("name")]
        artists = ", ".join(artist_names)
        external_ids = item.get("external_ids") or {}
        return Track(
            title=item.get("name") or "Unknown title",
            artist=artists or None,
            album=album.get("name"),
            duration_seconds=round(item["duration_ms"] / 1000) if item.get("duration_ms") else None,
            isrc=external_ids.get("isrc"),
            source_url=(item.get("external_urls") or {}).get("spotify"),
            platform="spotify",
            track_number=item.get("track_number"),
            metadata={"spotify_id": item.get("id"), "artists": artist_names},
        )

    @staticmethod
    def _episode(item: dict, source: str) -> Track:
        show = item.get("show") or {}
        publisher = show.get("publisher")
        show_name = show.get("name")
        return Track(
            title=item.get("name") or "Spotify episode",
            artist=publisher,
            album=show_name,
            duration_seconds=round(item["duration_ms"] / 1000) if item.get("duration_ms") else None,
            source_url=(item.get("external_urls") or {}).get("spotify") or source,
            platform="spotify",
            metadata={
                "spotify_id": item.get("id"),
                "description": item.get("description"),
                "release_date": item.get("release_date"),
                "artists": normalize_artist_values(publisher),
            },
        )

    @staticmethod
    def _resolve_without_api(source: str, kind: str, spotify_id: str) -> ResolvedSource:
        try:
            data = http_json(
                f"https://open.spotify.com/oembed?{urlencode({'url': source})}",
            )
        except MetadataResolutionError:
            if kind in {"episode", "show"}:
                metadata = SpotifyResolver._embed_metadata(kind, spotify_id) or YtDlpMetadataResolver._open_graph_metadata(source)
                return SpotifyResolver._fallback_podcast(
                    source,
                    kind,
                    spotify_id,
                    title=metadata.get("title"),
                    artist=metadata.get("artist"),
                )
            raise
        title = data.get("title") or "Spotify item"
        clean_title = SpotifyResolver._clean_oembed_title(title)

        if kind == "track":
            track_title, artist = SpotifyResolver._split_spotify_title(clean_title)
            track = Track(
                title=track_title,
                artist=artist,
                source_url=source,
                platform="spotify",
                metadata={
                    "spotify_id": spotify_id,
                    "artists": normalize_artist_values(artist),
                    "thumbnail_url": data.get("thumbnail_url"),
                    "metadata_source": "spotify_oembed",
                },
            )
            return ResolvedSource(source=source, kind="track", platform="spotify", title=track_title, tracks=[track])

        if kind == "episode":
            track = Track(
                title=clean_title,
                artist=data.get("author_name"),
                source_url=source,
                platform="spotify",
                metadata={
                    "spotify_id": spotify_id,
                    "artists": normalize_artist_values(data.get("author_name")),
                    "thumbnail_url": data.get("thumbnail_url"),
                    "metadata_source": "spotify_oembed",
                },
            )
            return ResolvedSource(source=source, kind="podcast", platform="spotify", title=clean_title, tracks=[track])

        if kind == "show":
            track = Track(
                title=clean_title,
                artist=data.get("author_name"),
                source_url=source,
                platform="spotify",
                metadata={
                    "spotify_id": spotify_id,
                    "artists": normalize_artist_values(data.get("author_name")),
                    "thumbnail_url": data.get("thumbnail_url"),
                    "metadata_source": "spotify_oembed",
                },
            )
            return ResolvedSource(source=source, kind="podcast_feed", platform="spotify", title=clean_title, tracks=[track])

        return ResolvedSource(
            source=source,
            kind=kind,
            platform="spotify",
            title=clean_title,
            tracks=[],
        )

    @staticmethod
    def _fallback_podcast(source: str, kind: str, spotify_id: str, *, title: str | None = None, artist: str | None = None) -> ResolvedSource:
        title = SpotifyResolver._clean_oembed_title(title or f"Spotify {kind} {spotify_id}")
        track = Track(
            title=title,
            artist=artist,
            source_url=source,
            platform="spotify",
            metadata={
                "spotify_id": spotify_id,
                "artists": normalize_artist_values(artist),
                "metadata_source": "spotify_page" if title else "spotify_url",
            },
        )
        resolved_kind = "podcast_feed" if kind == "show" else "podcast"
        return ResolvedSource(source=source, kind=resolved_kind, platform="spotify", title=title, tracks=[track])

    @staticmethod
    def _embed_metadata(kind: str, spotify_id: str) -> dict[str, str]:
        try:
            page = http_text(
                f"https://open.spotify.com/embed/{kind}/{spotify_id}",
            )
        except MetadataResolutionError:
            return {}

        if "not currently available" in page.lower():
            return {}

        match = re.search(r'<script id="__NEXT_DATA__" type="application/json">(.*?)</script>', page)
        if not match:
            return {}

        try:
            data = json.loads(html.unescape(match.group(1)))
        except json.DecodeError:
            return {}

        strings: dict[str, str] = {}

        def walk(value) -> None:
            if isinstance(value, dict):
                for key, item in value.items():
                    lowered = str(key).lower()
                    if isinstance(item, str) and item.strip():
                        if lowered in {"name", "title"} and "title" not in strings:
                            strings["title"] = item.strip()
                        elif lowered in {"subtitle", "publisher", "author", "artist"} and "artist" not in strings:
                            strings["artist"] = item.strip()
                        elif lowered == "description" and "description" not in strings:
                            strings["description"] = item.strip()
                    walk(item)
            elif isinstance(value, list):
                for item in value:
                    walk(item)

        walk(data)
        return strings

    @staticmethod
    def _clean_oembed_title(title: str) -> str:
        title = html.unescape(title).strip()
        return re.sub(r"\s*\|\s*Spotify\s*$", "", title, flags=re.IGNORECASE).strip()

    @staticmethod
    def _split_spotify_title(title: str) -> tuple[str, str | None]:
        patterns = [
            r"^(?P<title>.+?)\s+-\s+song and lyrics by\s+(?P<artist>.+)$",
            r"^(?P<title>.+?)\s+-\s+song by\s+(?P<artist>.+)$",
            r"^(?P<title>.+?)\s+by\s+(?P<artist>.+)$",
        ]
        for pattern in patterns:
            match = re.match(pattern, title, flags=re.IGNORECASE)
            if match:
                return match.group("title").strip(), match.group("artist").strip()
        return title, None


class AppleMusicResolver:
    @staticmethod
    def is_apple(source: str) -> bool:
        host = hostname(source)
        return host in {"music.apple.com", "itunes.apple.com"}

    def can_resolve(self, source: str) -> bool:
        return self.is_apple(source)

    def resolve(self, source: str) -> ResolvedSource:
        track_id = self._track_id(source)
        if not track_id:
            raise UnsupportedSourceError(
                "Apple Music support currently needs a track URL containing an `i=` song id. "
                "Apple playlists require a developer token and are not downloaded from Apple Music."
            )

        data = http_json(f"https://itunes.apple.com/lookup?id={track_id}&entity=song")
        songs = [item for item in data.get("results", []) if item.get("wrapperType") == "track"]
        if not songs:
            raise MetadataResolutionError("Apple/iTunes lookup did not return a song.")

        tracks = [self._track(item) for item in songs]
        return ResolvedSource(source=source, kind="track", platform="apple_music", title=tracks[0].title, tracks=tracks[:1])

    @staticmethod
    def _track_id(source: str) -> str | None:
        parsed = urlparse(source)
        query_id = parse_qs(parsed.query).get("i")
        if query_id:
            return query_id[0]
        match = re.search(r"/(\d+)(?:[/?#]|$)", parsed.path)
        return match.group(1) if match else None

    @staticmethod
    def _track(item: dict) -> Track:
        return Track(
            title=item.get("trackName") or "Unknown title",
            artist=item.get("artistName"),
            album=item.get("collectionName"),
            duration_seconds=round(item["trackTimeMillis"] / 1000) if item.get("trackTimeMillis") else None,
            isrc=item.get("isrc"),
            source_url=item.get("trackViewUrl"),
            platform="apple_music",
            track_number=item.get("trackNumber"),
            metadata={"itunes_track_id": item.get("trackId"), "artists": normalize_artist_values(item.get("artistName"))},
        )


def normalize_artist_values(value) -> list[str]:
    if not value:
        return []
    if isinstance(value, list):
        names = []
        for item in value:
            if isinstance(item, dict):
                item = item.get("name")
            names.extend(normalize_artist_values(item))
        return unique_preserving_order(names)
    if isinstance(value, tuple):
        return normalize_artist_values(list(value))
    if not isinstance(value, str):
        return []

    parts = re.split(r"\s*(?:;|,|\bfeat\.?|\bfeaturing\b|\bwith\b)\s*", value, flags=re.IGNORECASE)
    return unique_preserving_order(part.strip() for part in parts if part.strip())


def unique_preserving_order(values) -> list[str]:
    seen = set()
    result = []
    for value in values:
        key = value.casefold()
        if key not in seen:
            seen.add(key)
            result.append(value)
    return result


def normalize_match_text(value: str | None) -> str:
    value = html.unescape(value or "").casefold()
    value = re.sub(r"[^a-z0-9]+", " ", value)
    return re.sub(r"\s+", " ", value).strip()


def similarity_score(left: str, right: str) -> float:
    if not left or not right:
        return 0.0
    if left == right:
        return 1.0
    left_words = set(left.split())
    right_words = set(right.split())
    overlap = len(left_words & right_words)
    union = len(left_words | right_words) or 1
    containment = overlap / max(min(len(left_words), len(right_words)), 1)
    jaccard = overlap / union
    return max(jaccard, containment * 0.85)


def text_or_none(element: ET.Element | None) -> str | None:
    if element is None or element.text is None:
        return None
    value = element.text.strip()
    return value or None


def parse_duration(value: str | None) -> int | None:
    if not value:
        return None
    value = value.strip()
    if value.isdigit():
        return int(value)
    parts = value.split(":")
    if not all(part.isdigit() for part in parts):
        return None
    seconds = 0
    for part in parts:
        seconds = seconds * 60 + int(part)
    return seconds

SUPPORTED_FORMATS = {"aac", "alac", "flac", "m4a", "mp3", "ogg", "opus", "vorbis", "wav"}
FORMAT_CODECS = {
    "aac": "aac",
    "alac": "alac",
    "flac": "flac",
    "m4a": "m4a",
    "mp3": "mp3",
    "ogg": "vorbis",
    "opus": "opus",
    "vorbis": "vorbis",
    "wav": "wav",
}
QUALITY_VALUES = {
    "best": "0",
    "high": "2",
    "medium": "5",
    "low": "9",
}
VIDEO_QUALITY_HEIGHTS = {
    "144p": 144,
    "240p": 240,
    "360p": 360,
    "480p": 480,
    "720p": 720,
    "1080p": 1080,
    "1440p": 1440,
    "2k": 1440,
    "4k": 2160,
    "2160p": 2160,
    "8k": 4320,
    "4320p": 4320,
    "best": 0,
}


@dataclass(slots=True)
class DownloadOptions:
    media_type: str = "music"
    audio_format: str = "mp3"
    video_format: str = "mp4"
    quality: str = "best"
    video_quality: str = "best"
    output_dir: Path = Path("downloads")
    limit: int | None = None
    direct: bool = False
    dry_run: bool = False
    embed_thumbnail: bool = True
    fragment_threads: int = 4
    cookies_file: str | None = None
    geo_bypass: bool = False


SUPPORTED_MEDIA_TYPES = {"music", "podcast", "video"}
SUPPORTED_VIDEO_FORMATS = {"mp4", "mkv", "webm"}
MEDIA_FOLDER_NAMES = {
    "music": "music",
    "podcast": "podcasts",
    "video": "videos",
}


@dataclass(slots=True)
class DownloadResult:
    track: Track
    command: list[str]
    output_path: Path | None = None


class DownloadReporter(Protocol):
    def start_collection(self, resolved: ResolvedSource, output_dir: Path, total_tracks: int) -> None:
        ...

    def start_track(self, track: Track, index: int, total_tracks: int) -> None:
        ...

    def progress(self, index: int, total_tracks: int, fraction: float | None) -> None:
        ...

    def finish_track(self, result: DownloadResult, index: int, total_tracks: int) -> None:
        ...


class YtDlpDownloader:
    def download(self, resolved: ResolvedSource, options: DownloadOptions, reporter: DownloadReporter | None = None) -> list[DownloadResult]:
        self._validate_options(options)
        output_dir = self.output_dir_for(resolved, options)
        if not options.dry_run:
            ensure_directory(output_dir)
        results = []
        tracks = resolved.tracks[: options.limit] if options.limit else resolved.tracks
        if reporter:
            reporter.start_collection(resolved, output_dir, len(tracks))

        for index, track in enumerate(tracks, start=1):
            self._normalize_track_number(resolved, track, index)
            command = self.build_command(track, options, index=index, output_dir=output_dir)
            result = DownloadResult(track=track, command=command)
            if reporter and options.dry_run:
                reporter.start_track(track, index, len(tracks))
            if not options.dry_run:
                if reporter:
                    reporter.start_track(track, index, len(tracks))
                if options.media_type == "podcast" and track.source_url and is_url(track.source_url):
                    result.output_path = self._download_direct_audio(track, options, output_dir, reporter, index, len(tracks))
                else:
                    result.output_path = self._run(command, reporter=reporter, index=index, total_tracks=len(tracks))
                self._tag_file(result.output_path, track)
                self._burn_subtitles(result.output_path, options)
            if reporter:
                reporter.finish_track(result, index, len(tracks))
            results.append(result)

        return results

    def build_command(self, track: Track, options: DownloadOptions, *, index: int = 1, output_dir: Path | None = None) -> list[str]:
        target = self._target_for_track(track, options)
        stem = self._output_stem(track, index)
        output_template = str((output_dir or options.output_dir) / f"{stem}.%(ext)s")

        args = [
            *yt_dlp_command(required=not options.dry_run),
            "--no-playlist",
            "--format",
            self._format_selector(options),
            "--output",
            output_template,
            "--embed-metadata",
            "--no-mtime",
            "--no-warnings",
            "--newline",
            "--print",
            "after_move:filepath",
        ]

        if options.embed_thumbnail and options.media_type != "video":
            args.append("--embed-thumbnail")

        if options.media_type in {"music", "podcast"}:
            args.extend(
                [
                    "--extract-audio",
                    "--audio-format",
                    self._audio_codec(options.audio_format),
                    "--audio-quality",
                    QUALITY_VALUES[options.quality],
                ]
            )
        else:
            args.extend(["--merge-output-format", options.video_format])
        args.extend(["--concurrent-fragments", str(options.fragment_threads)])

        ffmpeg_path = bundled_ffmpeg_path(required=False)
        if ffmpeg_path:
            args.extend(["--ffmpeg-location", ffmpeg_path])

        if options.cookies_file:
            args.extend(["--cookies", options.cookies_file])
        if options.geo_bypass:
            args.append("--geo-bypass")
        if options.media_type == "video":
            args.extend(["--write-subs", "--write-auto-subs"])
        args.append(target)
        return args

    @staticmethod
    def output_dir_for(resolved: ResolvedSource, options: DownloadOptions) -> Path:
        media_dir = options.output_dir / MEDIA_FOLDER_NAMES[options.media_type]
        if resolved.kind in {"playlist", "album"}:
            return media_dir / clean_filename(resolved.title or resolved.kind, fallback=resolved.kind)
        if resolved.kind in {"podcast", "podcast_feed"} and resolved.title:
            return media_dir / clean_filename(resolved.title, fallback="podcast")
        return media_dir

    @staticmethod
    def _target_for_track(track: Track, options: DownloadOptions) -> str:
        if options.direct and track.source_url and is_url(track.source_url):
            return track.source_url
        if track.platform in {"spotify", "apple_music"}:
            return f"ytsearch1:{track.query or track.title}"
        if track.source_url and is_url(track.source_url):
            return track.source_url
        return f"ytsearch1:{track.query or track.title}"

    @staticmethod
    def _output_stem(track: Track, index: int) -> str:
        return clean_filename(track.title)

    @staticmethod
    def _validate_options(options: DownloadOptions) -> None:
        if options.media_type not in SUPPORTED_MEDIA_TYPES:
            raise UniGrabError(f"Unsupported media type `{options.media_type}`. Use one of: {', '.join(sorted(SUPPORTED_MEDIA_TYPES))}.")
        if options.audio_format not in SUPPORTED_FORMATS:
            raise UniGrabError(f"Unsupported format `{options.audio_format}`. Use one of: {', '.join(sorted(SUPPORTED_FORMATS))}.")
        if options.video_format not in SUPPORTED_VIDEO_FORMATS:
            raise UniGrabError(f"Unsupported video format `{options.video_format}`. Use one of: {', '.join(sorted(SUPPORTED_VIDEO_FORMATS))}.")
        if options.quality not in QUALITY_VALUES:
            raise UniGrabError(f"Unsupported quality `{options.quality}`. Use one of: {', '.join(QUALITY_VALUES)}.")
        if options.video_quality not in VIDEO_QUALITY_HEIGHTS:
            raise UniGrabError(f"Unsupported video quality `{options.video_quality}`. Use one of: {', '.join(VIDEO_QUALITY_HEIGHTS)}.")
        if options.fragment_threads < 1:
            raise UniGrabError("Download threads must be at least 1.")
        if not options.dry_run:
            yt_dlp_command()
            bundled_ffmpeg_path(required=True)

    @staticmethod
    def _run(
        command: list[str],
        *,
        reporter: DownloadReporter | None = None,
        index: int = 1,
        total_tracks: int = 1,
    ) -> Path | None:
        try:
            process = subprocess.Popen(
                command,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                encoding="utf-8",
                errors="replace",
            )
        except FileNotFoundError as exc:
            raise DependencyMissingError("Could not start the Python yt-dlp runner.") from exc

        output_lines: list[str] = []
        assert process.stdout is not None
        for line in process.stdout:
            line = line.strip()
            if not line:
                continue
            output_lines.append(line)
            fraction = YtDlpDownloader._progress_fraction(line)
            if reporter and fraction is not None:
                reporter.progress(index, total_tracks, fraction)

        return_code = process.wait()
        if return_code:
            message = "\n".join(output_lines[-20:]).strip()
            raise UniGrabError(message or f"`yt-dlp` exited with {return_code}.")

        for line in reversed(output_lines):
            path = Path(line)
            if path.exists():
                return path
        return None

    @staticmethod
    def _download_direct_audio(
        track: Track,
        options: DownloadOptions,
        output_dir: Path,
        reporter: DownloadReporter | None,
        index: int,
        total_tracks: int,
    ) -> Path:
        source = track.source_url
        if not source:
            raise UniGrabError("Podcast episode does not include an audio URL.")

        source = YtDlpDownloader._unwrap_podcast_audio_url(source)
        ensure_directory(output_dir)
        source_extension = YtDlpDownloader._audio_extension_from_url(source) or options.audio_format
        source_path = output_dir / f"{YtDlpDownloader._output_stem(track, index)}.{source_extension}"
        curl = shutil.which("curl.exe") or shutil.which("curl")
        if curl:
            try:
                YtDlpDownloader._download_direct_audio_with_curl(source, source_path, reporter, index, total_tracks)
            except Exception:
                # Fall back to native Python urllib download if curl fails (e.g. connection reset)
                YtDlpDownloader._download_direct_audio_with_python(source, source_path, reporter, index, total_tracks)
        else:
            YtDlpDownloader._download_direct_audio_with_python(source, source_path, reporter, index, total_tracks)

        if reporter:
            reporter.progress(index, total_tracks, 1.0)

        target_extension = YtDlpDownloader._output_extension(options.audio_format)
        if source_path.suffix.lower().lstrip(".") == target_extension:
            return source_path

        target_path = source_path.with_suffix(f".{target_extension}")
        ffmpeg_path = bundled_ffmpeg_path(required=True)

        # Build quality argument for lossy formats (mp3, aac, m4a, ogg, vorbis, opus)
        quality_args = []
        if target_extension in {"mp3", "aac", "m4a", "ogg", "vorbis", "opus"}:
            quality_args = ["-q:a", QUALITY_VALUES[options.quality]]

        try:
            subprocess.run(
                [ffmpeg_path, "-y", "-i", str(source_path), "-vn", *quality_args, str(target_path)],
                check=True,
                capture_output=True,
            )
        except subprocess.CalledProcessError as exc:
            message = (exc.stderr or exc.stdout or b"").decode("utf-8", errors="replace").strip()
            raise UniGrabError(message or "Podcast audio conversion failed.") from exc

        source_path.unlink(missing_ok=True)
        return target_path

    @staticmethod
    def _download_direct_audio_with_curl(
        source: str,
        output_path: Path,
        reporter: DownloadReporter | None,
        index: int,
        total_tracks: int,
    ) -> None:
        command = [
            shutil.which("curl.exe") or shutil.which("curl") or "curl",
            "-4",
            "-L",
            "--fail",
            "--show-error",
            "-A",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
            "--output",
            str(output_path),
            source,
        ]
        try:
            process = subprocess.Popen(
                command,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.PIPE,
                text=True,
                encoding="utf-8",
                errors="replace",
            )
        except FileNotFoundError as exc:
            raise DependencyMissingError("Could not start curl for podcast download.") from exc

        messages: list[str] = []
        buffer = ""
        assert process.stderr is not None
        while True:
            char = process.stderr.read(1)
            if not char:
                break
            if char in {"\r", "\n"}:
                line = buffer.strip()
                buffer = ""
                if not line:
                    continue
                messages.append(line)
                fraction = YtDlpDownloader._curl_progress_fraction(line)
                if reporter and fraction is not None:
                    reporter.progress(index, total_tracks, fraction)
            else:
                buffer += char

        return_code = process.wait()
        if return_code:
            output_path.unlink(missing_ok=True)
            message = "\n".join(messages[-10:]).strip()
            raise UniGrabError(message or f"Podcast download failed with curl exit code {return_code}.")

    @staticmethod
    def _unwrap_podcast_audio_url(source: str) -> str:
        from urllib.parse import unquote
        lower_source = source.lower()
        for prefix in ["https%3a%2f%2f", "http%3a%2f%2f"]:
            idx = lower_source.find(prefix)
            if idx != -1:
                decoded = unquote(source[idx:])
                if is_url(decoded):
                    return YtDlpDownloader._unwrap_podcast_audio_url(decoded)

        parsed = urlparse(source)
        wrapper_hosts = {
            hostname(source),
            "tracking.swap.fm",
            "prfx.byspotify.com",
            "play.podtrac.com",
            "podtrac.com",
        }
        segments = [segment for segment in parsed.path.split("/") if segment]
        for index, segment in enumerate(segments):
            if "." not in segment or segment.lower() in wrapper_hosts or segment.lower().endswith((".mp3", ".m4a", ".aac", ".ogg", ".opus", ".wav")):
                continue
            rest = "/".join(segments[index:])
            if re.search(r"\.(?:mp3|m4a|aac|ogg|oga|opus|wav)(?:$|/)", rest, flags=re.IGNORECASE):
                path = "/" + "/".join(segments[index + 1:])
                return urlunparse(("https", segment, path, "", parsed.query, ""))
        return source

    @staticmethod
    def _download_direct_audio_with_python(
        source: str,
        output_path: Path,
        reporter: DownloadReporter | None,
        index: int,
        total_tracks: int,
    ) -> None:
        request = Request(source, headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"})
        try:
            with urlopen(request, timeout=60) as response:
                content_length = response.headers.get("Content-Length")
                total_bytes = int(content_length) if content_length and content_length.isdigit() else None
                downloaded = 0
                with output_path.open("wb") as file:
                    while True:
                        chunk = response.read(1024 * 256)
                        if not chunk:
                            break
                        file.write(chunk)
                        downloaded += len(chunk)
                        if reporter and total_bytes:
                            reporter.progress(index, total_tracks, min(downloaded / total_bytes, 1.0))
        except Exception as exc:
            output_path.unlink(missing_ok=True)
            raise UniGrabError(f"Podcast download failed: {exc}") from exc

    @staticmethod
    def _audio_extension_from_url(source: str) -> str | None:
        suffix = Path(urlparse(source).path).suffix.lower().lstrip(".")
        if suffix == "oga":
            return "ogg"
        if suffix in SUPPORTED_FORMATS:
            return suffix
        return None

    @staticmethod
    def _output_extension(audio_format: str) -> str:
        return {"alac": "m4a", "vorbis": "ogg"}.get(audio_format, audio_format)

    @staticmethod
    def _progress_fraction(line: str) -> float | None:
        match = re.search(r"\[download\]\s+(\d+(?:\.\d+)?)%", line)
        if not match:
            return None
        return min(float(match.group(1)) / 100, 1.0)

    @staticmethod
    def _curl_progress_fraction(line: str) -> float | None:
        match = re.match(r"\s*(\d{1,3})\s+", line)
        if not match:
            return None
        value = int(match.group(1))
        if value > 100:
            return None
        return value / 100

    @staticmethod
    def _normalize_track_number(resolved: ResolvedSource, track: Track, index: int) -> None:
        if resolved.kind == "playlist":
            track.track_number = index
        elif resolved.kind == "album" and track.track_number is None:
            track.track_number = index
        elif resolved.kind == "track":
            track.track_number = None

    @staticmethod
    def _artist_values_from_track(track: Track) -> list[str]:
        metadata_artists = track.metadata.get("artists")
        values = YtDlpDownloader._normalize_artist_values(metadata_artists)
        if values:
            return values
        return YtDlpDownloader._normalize_artist_values(track.artist)

    @staticmethod
    def _normalize_artist_values(value) -> list[str]:
        if not value:
            return []
        if isinstance(value, list):
            names = []
            for item in value:
                if isinstance(item, dict):
                    item = item.get("name")
                names.extend(YtDlpDownloader._normalize_artist_values(item))
            return YtDlpDownloader._unique_values(names)
        if isinstance(value, tuple):
            return YtDlpDownloader._normalize_artist_values(list(value))
        if not isinstance(value, str):
            return []

        parts = re.split(r"\s*(?:;|,|\bfeat\.?|\bfeaturing\b|\bwith\b)\s*", value, flags=re.IGNORECASE)
        return YtDlpDownloader._unique_values(part.strip() for part in parts if part.strip())

    @staticmethod
    def _unique_values(values) -> list[str]:
        seen = set()
        result = []
        for value in values:
            key = value.casefold()
            if key not in seen:
                seen.add(key)
                result.append(value)
        return result

    @staticmethod
    def _audio_codec(audio_format: str) -> str:
        return FORMAT_CODECS[audio_format]

    @staticmethod
    def _format_selector(options: DownloadOptions) -> str:
        if options.media_type == "video":
            height = VIDEO_QUALITY_HEIGHTS[options.video_quality]
            if height == 0:
                return "bestvideo+bestaudio/best"
            return f"bv*[height<={height}]+ba/best[height<={height}]/best"
        return "bestaudio/best"

    @staticmethod
    def _tag_file(output_path: Path | None, track: Track) -> None:
        if not output_path or not output_path.exists():
            return

        try:
            from mutagen import File
        except ImportError:
            return

        audio = File(output_path, easy=True)
        if audio is None:
            return

        artist_values = YtDlpDownloader._artist_values_from_track(track)
        tags = {
            "title": [track.title],
            "artist": artist_values or None,
            "album": [track.album] if track.album else None,
            "tracknumber": [str(track.track_number)] if track.track_number else None,
            "isrc": [track.isrc] if track.isrc else None,
        }

        changed = False
        for key, value in tags.items():
            if value:
                try:
                    audio[key] = value
                    changed = True
                except Exception:
                    continue
            elif key == "tracknumber" and key in audio:
                try:
                    del audio[key]
                    changed = True
                except Exception:
                    continue

        if changed:
            audio.save()

    @staticmethod
    def _burn_subtitles(output_path: Path | None, options: DownloadOptions) -> None:
        if not output_path or not output_path.exists() or options.media_type != "video":
            return

        parent_dir = output_path.parent
        stem = output_path.stem

        try:
            files = os.listdir(parent_dir)
        except Exception:
            return

        sub_files = []
        for filename in files:
            name_lower = filename.lower()
            if (name_lower.startswith(stem.lower() + ".") and 
                (name_lower.endswith(".srt") or name_lower.endswith(".vtt"))):
                sub_files.append(parent_dir / filename)

        if not sub_files:
            return

        sub_file = None
        for f in sub_files:
            if ".en." in f.name.lower():
                sub_file = f
                break
        if not sub_file:
            sub_file = sub_files[0]

        import uuid
        unique_id = uuid.uuid4().hex[:8]

        temp_video_in = parent_dir / f"__temp_in_{unique_id}.mp4"
        temp_sub_in = parent_dir / f"__temp_sub_{unique_id}{sub_file.suffix.lower()}"
        temp_srt = parent_dir / f"__temp_sub_{unique_id}.srt"
        temp_video_out = parent_dir / f"__temp_out_{unique_id}.mp4"

        ffmpeg_path = bundled_ffmpeg_path(required=True)

        try:
            output_path.rename(temp_video_in)
            shutil.copy2(sub_file, temp_sub_in)

            if temp_sub_in.suffix.lower() == ".vtt":
                subprocess.run(
                    [ffmpeg_path, "-y", "-i", temp_sub_in.name, temp_srt.name],
                    cwd=parent_dir,
                    check=True,
                    capture_output=True,
                )
            else:
                shutil.copy2(temp_sub_in, temp_srt)

            subprocess.run(
                [
                    ffmpeg_path,
                    "-y",
                    "-i",
                    temp_video_in.name,
                    "-vf",
                    f"subtitles={temp_srt.name}",
                    "-c:v",
                    "libx264",
                    "-crf",
                    "23",
                    "-preset",
                    "veryfast",
                    "-c:a",
                    "copy",
                    temp_video_out.name,
                ],
                cwd=parent_dir,
                check=True,
                capture_output=True,
            )

            if temp_video_out.exists():
                temp_video_out.replace(output_path)

        except Exception as exc:
            if temp_video_in.exists() and not output_path.exists():
                try:
                    temp_video_in.rename(output_path)
                except Exception:
                    pass
            print(f"\nWarning: Failed to burn subtitles into video: {exc}")

        finally:
            for path in [temp_video_in, temp_sub_in, temp_srt, temp_video_out]:
                if path and path.exists():
                    try:
                        path.unlink()
                    except Exception:
                        pass

            for f in sub_files:
                try:
                    f.unlink()
                except Exception:
                    pass
            if sub_file.suffix.lower() == ".vtt":
                try:
                    (parent_dir / (sub_file.stem + ".srt")).unlink()
                except Exception:
                    pass

REQUIRED_PACKAGES = {
    "yt_dlp": "yt-dlp",
    "imageio_ffmpeg": "imageio-ffmpeg",
    "mutagen": "mutagen",
}


def ensure_dependencies() -> None:
    missing = [
        pip_name
        for module_name, pip_name in REQUIRED_PACKAGES.items()
        if importlib.util.find_spec(module_name) is None
    ]
    if not missing:
        return

    print(f"Installing missing dependencies: {', '.join(missing)} ...")
    try:
        subprocess.run(
            [sys.executable, "-m", "pip", "install", "--quiet", *missing],
            check=True,
            capture_output=True,
            text=True,
        )
    except subprocess.CalledProcessError as exc:
        message = (exc.stderr or exc.stdout or "").strip()
        print(f"Auto-install failed: {message}", file=sys.stderr)
        print(f"Please run manually: python -m pip install -r requirements.txt", file=sys.stderr)
        raise SystemExit(1)
    except FileNotFoundError:
        print("Could not find pip. Please run: python -m pip install -r requirements.txt", file=sys.stderr)
        raise SystemExit(1)

    importlib.invalidate_caches()
    print("Dependencies installed successfully.\n")


def main(argv: list[str] | None = None) -> int:
    if argv is None:
        argv = sys.argv[1:]
    if not argv:
        return interactive()

    parser = build_parser()
    args = parser.parse_args(argv)

    try:
        if args.command in {"download", "metadata"}:
            ensure_dependencies()
        if args.command == "doctor":
            return doctor()
        if args.command == "formats":
            return formats()
        if args.command == "sources":
            return sources()
        if args.command == "metadata":
            return metadata(args)
        if args.command == "download":
            return download(args)
    except UniGrabError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2
    except KeyboardInterrupt:
        print("Interrupted.", file=sys.stderr)
        return 130

    parser.print_help()
    return 1


BANNER = r"""
                _                 __    
  __  ______   (_)___ __________ _/ /_   
 / / / / __ \ / / __ `/ ___/ __ `/ __ \  
/ /_/ / / / // / /_/ / /  / /_/ / /_/ /  
\__,_/_/ /_//_/\__, /_/   \__,_/_.___/   
              /____/                     
"""


def interactive() -> int:
    print(BANNER)
    print("Enter q, quit, or exit at any prompt to close.\n")

    while True:
        try:
            mode = prompt_mode()
            if mode is None:
                return 0
            source = input(f"Enter the {source_prompt_for_mode(mode)} : ").strip()
        except EOFError:
            print()
            return 0
        except KeyboardInterrupt:
            print("\nInterrupted.")
            return 130

        if source.lower() in {"q", "quit", "exit"}:
            return 0
        if not source:
            print("No link entered.\n")
            continue

        try:
            ensure_dependencies()
            media_type = media_type_from_mode(mode)
            resolved = ResolverRegistry(media_type=media_type).resolve(source)
            print_interactive_metadata(resolved, media_type)
            options = DownloadOptions(
                media_type=media_type,
                output_dir=Path("downloads"),
                cookies_file=os.environ.get("UNIGRAB_COOKIES"),
            )
            reporter = ReferenceDownloadReporter(options)
            YtDlpDownloader().download(resolved, options, reporter=reporter)
        except UniGrabError as exc:
            print(f"error: {exc}")
        print()


def prompt_mode() -> str | None:
    print("Select download type:")
    print("  1. Music")
    print("  2. Video")
    print("  3. Podcast")
    choice = input("Enter choice [1-3/m/v/p] : ").strip().lower()
    if choice in {"q", "quit", "exit"}:
        return None
    modes = {
        "1": "m",
        "m": "m",
        "music": "m",
        "2": "v",
        "v": "v",
        "video": "v",
        "3": "p",
        "p": "p",
        "podcast": "p",
    }
    mode = modes.get(choice)
    if mode:
        return mode
    print("Please choose 1, 2, 3, m, v, or p.\n")
    return prompt_mode()


def source_prompt_for_mode(mode: str) -> str:
    return {
        "m": "Album/Song/Playlist URL",
        "v": "Video URL",
        "p": "Podcast Feed/Episode URL",
    }[mode]


def print_interactive_metadata(resolved: ResolvedSource, media_type: str) -> None:
    print()
    if len(resolved.tracks) == 1:
        track = resolved.tracks[0]
        title_label = {
            "music": "Song name",
            "video": "Video name",
            "podcast": "Episode name",
        }[media_type]
        if media_type == "video":
            lines = [
                (title_label, track.title),
                ("Channel", track.artist),
                ("Duration", format_duration(track.duration_seconds)),
            ]
        elif media_type == "podcast":
            lines = [
                (title_label, track.title),
                ("Podcast", track.album or resolved.title),
                ("Author", track.artist),
                ("Duration", format_duration(track.duration_seconds)),
            ]
        else:
            lines = [
                (title_label, track.title),
                ("Artist(s) name", track.artist),
                ("Composer", metadata_value(track, "composer", "creator")),
                ("Album name", track.album or resolved.title),
                ("Year", metadata_value(track, "year", "release_year", "release_date")),
                ("Has lyrics?", metadata_bool(track, "has_lyrics", "lyrics")),
            ]
    else:
        title_label = {
            "music": "Playlist name",
            "video": "Playlist name",
            "podcast": "Podcast name",
        }[media_type]
        lines = [
            (title_label, resolved.title or resolved.source),
            ("Track count", str(len(resolved.tracks))),
            ("Source", resolved.platform),
        ]

    width = max(len(label) for label, _ in lines)
    for label, value in lines:
        if value:
            print(f"{label.rjust(width)} : {value}")
    print()


def metadata_value(track: Track, *keys: str) -> str | None:
    for key in keys:
        value = track.metadata.get(key)
        if isinstance(value, list):
            value = ", ".join(str(item) for item in value if item)
        if value:
            return str(value)
    return None


def metadata_bool(track: Track, *keys: str) -> str | None:
    for key in keys:
        value = track.metadata.get(key)
        if value is not None:
            return str(bool(value)).lower()
    return None


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="unigrab",
        description="UniGrab downloads music, videos, and podcasts.",
        epilog="Modes: m=music, v=video, p=podcast",
    )
    subparsers = parser.add_subparsers(dest="command")

    metadata_parser = subparsers.add_parser("metadata", help="Resolve metadata for a URL or search query.")
    metadata_parser.add_argument("source")
    metadata_parser.add_argument("--json", action="store_true", help="Print machine-readable JSON.")

    download_parser = subparsers.add_parser(
        "download",
        help="Download music, video, or podcast media.",
    )
    download_parser.add_argument("mode", choices=["m", "v", "p"], help="What to download: m=music, v=video, p=podcast.")
    download_parser.add_argument("source", help="URL, feed URL, playlist URL, or search text.")
    download_parser.add_argument("--format", default="mp3", choices=sorted(SUPPORTED_FORMATS), help="Output audio format.")
    download_parser.add_argument("--video-format", default="mp4", choices=sorted(SUPPORTED_VIDEO_FORMATS), help="Output video container.")
    download_parser.add_argument("--quality", default="best", choices=list(QUALITY_VALUES), help="Audio conversion quality.")
    download_parser.add_argument("--video-quality", default="best", choices=list(VIDEO_QUALITY_HEIGHTS), help="Video quality: 144p to 8k, or 'best' for highest available.")
    download_parser.add_argument("--out", default="downloads", help="Output directory.")
    download_parser.add_argument("--limit", type=int, help="Limit playlist/album downloads.")
    download_parser.add_argument("--direct", action="store_true", help="Download the resolved source URL directly instead of searching.")
    download_parser.add_argument("--dry-run", action="store_true", help="Preview the download command without running it.")
    download_parser.add_argument("--no-thumbnail", action="store_true", help="Do not embed thumbnails.")
    download_parser.add_argument("--threads", type=int, default=4, help="Concurrent fragment download threads.")

    download_parser.add_argument("--cookies", default=None, help="Path to cookies.txt file (Netscape format) for authenticated downloads.")
    download_parser.add_argument("--geo-bypass", action="store_true", help="Bypass geographic restrictions on content.")

    subparsers.add_parser("doctor", help="Check required runtime components.")
    subparsers.add_parser("formats", help="List supported output formats and quality levels.")
    subparsers.add_parser("sources", help="List all supported sites and sources.")
    return parser


def metadata(args: argparse.Namespace) -> int:
    resolved = ResolverRegistry().resolve(args.source)
    if args.json:
        print(json.dumps(resolved.to_dict(), indent=2, ensure_ascii=False))
        return 0

    print(f"{resolved.platform} {resolved.kind}: {resolved.title or resolved.source}")
    for index, track in enumerate(resolved.tracks, start=1):
        artist = f"{track.artist} - " if track.artist else ""
        album = f" [{track.album}]" if track.album else ""
        print(f"{index:02d}. {artist}{track.title}{album}")
    return 0


def download(args: argparse.Namespace) -> int:
    media_type = media_type_from_mode(args.mode)
    resolved = ResolverRegistry(media_type=media_type).resolve(args.source)
    cookies = args.cookies or os.environ.get("UNIGRAB_COOKIES")
    options = DownloadOptions(
        audio_format=args.format,
        video_format=args.video_format,
        media_type=media_type,
        quality=args.quality,
        video_quality=args.video_quality,
        output_dir=Path(args.out),
        limit=args.limit,
        direct=args.direct,
        dry_run=args.dry_run,
        embed_thumbnail=not args.no_thumbnail,
        fragment_threads=args.threads,
        cookies_file=cookies,
        geo_bypass=args.geo_bypass,
    )
    reporter = None if args.dry_run else ConsoleDownloadReporter(options)
    results = YtDlpDownloader().download(resolved, options, reporter=reporter)
    if args.dry_run:
        for result in results:
            print(shell_join(result.command))
    return 0


def media_type_from_mode(mode: str) -> str:
    return {"m": "music", "v": "video", "p": "podcast"}[mode]

class ConsoleDownloadReporter:
    def __init__(self, options: DownloadOptions) -> None:
        self.options = options
        self.last_line_length = 0

    def start_collection(self, resolved: ResolvedSource, output_dir: Path, total_tracks: int) -> None:
        if self.options.media_type == "video":
            label = "Video playlist" if total_tracks > 1 else "Video"
        elif self.options.media_type == "podcast":
            label = "Podcast feed" if total_tracks > 1 else "Podcast"
        else:
            label = "Playlist" if total_tracks > 1 else "Song"
        print(f"{label}: {resolved.title or resolved.source}")
        print(f"Tracks: {total_tracks}")
        if self.options.media_type == "video":
            print(f"Format: {self.options.video_format} video ({self.options.video_quality})")
        else:
            print(f"Format: {self.options.audio_format} audio ({self.options.quality})")
        print(f"Folder: {output_dir}")

    def start_track(self, track: Track, index: int, total_tracks: int) -> None:
        print()
        heading = f"Track {index}/{total_tracks}" if total_tracks > 1 else "Track"
        print(heading)
        self._metadata_line("Title", track.title)
        self._metadata_line("Artist", track.artist)
        self._metadata_line("Album", track.album)
        self._metadata_line("#", str(track.track_number) if track.track_number else None)
        self._metadata_line("Duration", format_duration(track.duration_seconds))
        self._metadata_line("ISRC", track.isrc)
        self.progress(index, total_tracks, 0.0)

    def progress(self, index: int, total_tracks: int, fraction: float | None) -> None:
        current = progress_bar(fraction)
        if total_tracks > 1:
            current_fraction = fraction or 0.0
            overall_fraction = ((index - 1) + current_fraction) / total_tracks
            line = f"Current {current}  Overall {progress_bar(overall_fraction)}"
        else:
            line = f"Current {current}"
        self._rewrite_line(line)

    def finish_track(self, result: DownloadResult, index: int, total_tracks: int) -> None:
        self.progress(index, total_tracks, 1.0)
        print()
        if result.output_path:
            print(f"Downloaded: {result.output_path}")
        else:
            print("Downloaded")

    def _metadata_line(self, label: str, value: str | None) -> None:
        if value:
            print(f"  {label}: {value}")

    def _rewrite_line(self, line: str) -> None:
        padding = " " * max(self.last_line_length - len(line), 0)
        sys.stdout.write(f"\r{line}{padding}")
        sys.stdout.flush()
        self.last_line_length = len(line)


class ReferenceDownloadReporter:
    def __init__(self, options: DownloadOptions) -> None:
        self.options = options
        self.last_line_length = 0

    def start_collection(self, resolved: ResolvedSource, output_dir: Path, total_tracks: int) -> None:
        if self.options.media_type in {"music", "podcast"} and self.options.embed_thumbnail:
            print("Downloading the cover...")
            print()

    def start_track(self, track: Track, index: int, total_tracks: int) -> None:
        label = "Downloading"
        if total_tracks > 1:
            print(f"{label} : {index}. {track.title}...")
        else:
            print(f"{label} : {track.title}...")
        self.last_line_length = 0
        self.progress(index, total_tracks, 0.0)

    def progress(self, index: int, total_tracks: int, fraction: float | None) -> None:
        current = progress_bar(fraction)
        if total_tracks > 1:
            current_fraction = fraction or 0.0
            overall_fraction = ((index - 1) + current_fraction) / total_tracks
            line = f"  Current {current}  Overall {progress_bar(overall_fraction)}"
        else:
            line = f"  Current {current}"
        self._rewrite_line(line)

    def finish_track(self, result: DownloadResult, index: int, total_tracks: int) -> None:
        self.progress(index, total_tracks, 1.0)
        print()
        if self.options.media_type in {"music", "podcast"}:
            print("Tagging metadata...")
        print("Done.")

    def _rewrite_line(self, line: str) -> None:
        padding = " " * max(self.last_line_length - len(line), 0)
        sys.stdout.write(f"\r{line}{padding}")
        sys.stdout.flush()
        self.last_line_length = len(line)


def progress_bar(fraction: float | None, width: int = 24) -> str:
    if fraction is None:
        return f"[{'?' * width}]   --.-%"
    fraction = max(0.0, min(fraction, 1.0))
    filled = round(width * fraction)
    empty = width - filled
    return f"[{'#' * filled}{'-' * empty}] {fraction * 100:5.1f}%"


def format_duration(seconds: int | float | None) -> str | None:
    if seconds is None:
        return None
    seconds = round(seconds)
    minutes, seconds = divmod(seconds, 60)
    hours, minutes = divmod(minutes, 60)
    if hours:
        return f"{hours}:{minutes:02d}:{seconds:02d}"
    return f"{minutes}:{seconds:02d}"


def doctor() -> int:
    yt_dlp_status = python_package_status("yt_dlp")
    ffmpeg_path = bundled_ffmpeg_path(required=False)
    ffmpeg_status = ffmpeg_path or "missing"

    print(f"download engine: {yt_dlp_status}")
    print(f"bundled ffmpeg: {ffmpeg_status}")

    return 0 if yt_dlp_status == "installed" and ffmpeg_path else 1


def formats() -> int:
    print("Audio formats: " + ", ".join(sorted(SUPPORTED_FORMATS)))
    print("Video formats: " + ", ".join(sorted(SUPPORTED_VIDEO_FORMATS)))
    print("Audio quality: " + ", ".join(QUALITY_VALUES))
    print("Video quality: " + ", ".join(VIDEO_QUALITY_HEIGHTS))
    return 0


def shell_join(command: list[str]) -> str:
    return " ".join(quote_arg(part) for part in command)


def quote_arg(value: str) -> str:
    if not value or any(char.isspace() or char in '"&()[]{}^=;!+\',' for char in value):
        return "'" + value.replace("'", "''") + "'"
    return value



def sources() -> int:
    """List all supported sites and sources."""
    print("UniGrab Supported Sources")
    print("=" * 50)
    print()
    print("Built-in Resolvers:")
    print("  - Spotify          (tracks, albums, playlists, podcasts)")
    print("  - Apple Music      (tracks via iTunes API)")
    print("  - Internet Archive (archive.org public domain media)")
    print("  - M3U/M3U8         (IPTV playlists, HLS streams, local files)")
    print("  - Podcast Feeds    (RSS/Atom feeds, Apple Podcasts, Podcast Index)")
    print()

    try:
        import yt_dlp
        extractors = yt_dlp.list_extractors()
        site_names = sorted({
            e.IE_NAME.split(":")[0]
            for e in extractors
            if hasattr(e, "IE_NAME")
            and e.IE_NAME not in {"generic", "Generic"}
            and not e.IE_NAME.startswith("generic:")
        })
        print(f"yt-dlp Supported Sites ({len(site_names)} extractors):")
        col_width = max(len(name) for name in site_names) + 2
        cols = max(1, 80 // col_width)
        for i in range(0, len(site_names), cols):
            row = site_names[i:i + cols]
            print("  " + "".join(name.ljust(col_width) for name in row))
    except ImportError:
        print("yt-dlp Sites: (install yt-dlp to see full list)")
        print("  Supports 1,800+ public websites including YouTube, Vimeo,")
        print("  Twitch, Reddit, Twitter/X, TikTok, Dailymotion, and more.")
    print()
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
