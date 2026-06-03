# UniGrab

A clean CLI for downloading music, videos, and podcasts from popular media sites and RSS/Atom podcast feeds.

This project does not bypass DRM, subscriptions, paywalls, or platform access controls. Use it only for media you own, created, licensed, or otherwise have permission to download.

## Features

- Explicit modes: `m` for music, `v` for video, `p` for podcast.
- Separate output folders: `downloads/music`, `downloads/videos`, and `downloads/podcasts`.
- **1,800+ supported sites** via yt-dlp, including YouTube, Instagram, Vimeo, TikTok, Twitch, Reddit, Twitter/X, Facebook, SoundCloud, and many more.
- **Internet Archive** (archive.org) public domain media with rich metadata extraction.
- **M3U/M3U8 playlist support** for IPTV streams, HLS playlists, and local media lists.
- Playlist, album, and podcast feed downloads create a nested folder with the collection name.
- Music and podcast formats: `aac`, `alac`, `flac`, `m4a`, `mp3`, `ogg`, `opus`, `vorbis`, `wav`.
- Video containers: `mp4`, `mkv`, `webm`.
- Video quality from `144p` to `8k`, plus `best` for highest available.
- **Cookie support** for authenticated downloads (age-restricted content, subscriber VODs).
- **Geo-bypass** for region-restricted but freely available content.
- **Subtitle downloads** for videos when available.
- Per-item metadata display and progress bars.
- File names use only the media title; metadata is written into file tags when possible.
- Uses `yt-dlp` and `imageio-ffmpeg` from `requirements.txt`.

## Install

```powershell
python -m pip install -r requirements.txt
```

## Usage

Start the interactive downloader:

```powershell
python unigrab.py
```

Choose music, video, or podcast, paste the link, and UniGrab will continue with the download.

You can also use direct commands. The first argument after `download` chooses what kind of file to download:

| Mode | Use for | Output folder |
| --- | --- | --- |
| `m` | Music, songs, albums, music playlists | `downloads/music` |
| `v` | Videos from supported websites | `downloads/videos` |
| `p` | Podcast episodes and feeds | `downloads/podcasts` |

Music:

```powershell
python unigrab.py download m "https://music.youtube.com/watch?v=..." --format mp3 --quality high
```

Video:

```powershell
python unigrab.py download v "https://www.youtube.com/watch?v=..." --video-format mp4 --video-quality 1080p
```

Video mode can download from supported sources such as YouTube, Instagram, Vimeo, X/Twitter, Facebook, Twitch, TikTok, and many more when your access is permitted. Dailymotion is intentionally not supported.

Download in 4K or best available quality:

```powershell
python unigrab.py download v "https://www.youtube.com/watch?v=..." --video-quality 4k
python unigrab.py download v "https://www.youtube.com/watch?v=..." --video-quality best
```

Internet Archive public domain content:

```powershell
python unigrab.py download v "https://archive.org/details/BigBuckBunny_124" --video-quality 720p
```

M3U/IPTV playlists (remote or local):

```powershell
python unigrab.py download v "https://example.com/streams.m3u8"
python unigrab.py download v "./my_playlist.m3u"
```

Podcast feed:

```powershell
python unigrab.py download p "https://example.com/feed.xml" --format m4a
```

Podcast mode supports RSS feeds and direct episode audio URLs. Spotify, Apple Podcasts, Amazon Music, Audible, and similar platform pages are resolved to real RSS/audio when possible through PodcastIndex public search. For more accurate episode-level matching, set PodcastIndex credentials:

```powershell
$env:PODCASTINDEX_API_KEY="your-api-key"
$env:PODCASTINDEX_API_SECRET="your-api-secret"
```

## Advanced Options

### Cookies for authenticated content

Use `--cookies` to pass a Netscape-format cookies.txt file for content that requires login (age-restricted YouTube, subscriber Twitch VODs you have access to, etc.):

```powershell
python unigrab.py download v "https://www.youtube.com/watch?v=..." --cookies cookies.txt
```

Or set the environment variable for repeated use:

```powershell
$env:UNIGRAB_COOKIES="C:\path\to\cookies.txt"
python unigrab.py download v "https://www.youtube.com/watch?v=..."
```

### Geo-bypass

For content freely available in other regions but blocked in yours:

```powershell
python unigrab.py download v "URL" --geo-bypass
```

### Subtitles

Download subtitles along with video (converted to SRT):

```powershell
python unigrab.py download v "URL" --subs
```

## Utility Commands

List supported formats and quality levels:

```powershell
python unigrab.py formats
```

List all supported sites (1,800+ via yt-dlp, plus UniGrab built-in resolvers):

```powershell
python unigrab.py sources
```

Check dependencies:

```powershell
python unigrab.py doctor
```

Windows users should quote URLs containing `&`:

```powershell
python unigrab.py download m "https://music.youtube.com/watch?v=fwlJDTsmr58&list=RDAMVMfwlJDTsmr58" --format mp3
```

## Spotify and Apple Music Metadata

Spotify and Apple Music streaming audio is not downloaded from those services. They are used only as metadata sources where public metadata or official APIs allow it.

For full Spotify playlist/album metadata:

```powershell
$env:SPOTIFY_CLIENT_ID="your-client-id"
$env:SPOTIFY_CLIENT_SECRET="your-client-secret"
```
