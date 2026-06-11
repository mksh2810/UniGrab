import os
import sys
import uuid
import time
import threading
import subprocess
import importlib.util
import re
import queue
import urllib.parse
from pathlib import Path

# Automatically install flask if missing
try:
    import flask
except ImportError:
    print("Installing flask dependency...")
    subprocess.run([sys.executable, "-m", "pip", "install", "flask"], check=True)
    importlib.invalidate_caches()

from flask import Flask, request, jsonify, render_template, send_from_directory, send_file

from unigrab import (
    ResolverRegistry, YtDlpDownloader, DownloadOptions, 
    Track, ResolvedSource, UniGrabError, doctor,
    SUPPORTED_FORMATS, SUPPORTED_VIDEO_FORMATS, QUALITY_VALUES, VIDEO_QUALITY_HEIGHTS,
    is_adult_site, get_cookies_file
)

app = Flask(__name__)

# Music-only platforms that should be blocked from video mode
AUDIO_ONLY_HOSTS = {
    "music.youtube.com",
    "open.spotify.com",
    "music.apple.com",
    "itunes.apple.com",
    "soundcloud.com",
    "bandcamp.com",
    "jiosaavn.com",
    "gaana.com",
}

# Queue and background workers for sequential downloads
download_queue = queue.Queue()
active_processes = {}
active_processes_lock = threading.Lock()

# Global memory database for active download tasks
tasks = {}
tasks_lock = threading.Lock()

# Ensure downloads folders exist
Path("downloads/music").mkdir(parents=True, exist_ok=True)
Path("downloads/videos").mkdir(parents=True, exist_ok=True)

LIBRARY_JSON_PATH = Path("downloads/library.json")

def load_library_db():
    if LIBRARY_JSON_PATH.exists():
        try:
            import json
            with open(LIBRARY_JSON_PATH, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            return {}
    return {}

def save_library_db(db):
    try:
        import json
        LIBRARY_JSON_PATH.parent.mkdir(parents=True, exist_ok=True)
        with open(LIBRARY_JSON_PATH, "w", encoding="utf-8") as f:
            json.dump(db, f, indent=4, ensure_ascii=False)
    except Exception as e:
        print(f"Error saving library DB: {e}")


class WebDownloadReporter:
    def __init__(self, task_id):
        self.task_id = task_id
        self.last_progress_time = time.time()
        self.last_progress_fraction = 0.0

    def register_process(self, process):
        with active_processes_lock:
            active_processes[self.task_id] = process

    def is_cancelled(self):
        with tasks_lock:
            t = tasks.get(self.task_id)
            return t and t.get("status") == "cancelled"

    def start_collection(self, resolved, output_dir, total_tracks):
        with tasks_lock:
            t = tasks.get(self.task_id)
            if t:
                t["total_tracks"] = total_tracks
                t["current_track"] = 1
                t["logs"].append(f"Starting download collection: {resolved.title or 'Unknown Collection'}")
                t["logs"].append(f"Target folder: {output_dir}")

    def start_track(self, track, index, total_tracks):
        with tasks_lock:
            t = tasks.get(self.task_id)
            if t:
                t["current_track"] = index
                t["current_progress"] = 0.0
                t["logs"].append(f"\n--- Downloading Track {index}/{total_tracks} ---")
                t["logs"].append(f"Title: {track.title}")
                if track.artist:
                    t["logs"].append(f"Artist: {track.artist}")
        self.last_progress_time = time.time()
        self.last_progress_fraction = 0.0

    def progress(self, index, total_tracks, fraction):
        now = time.time()
        with tasks_lock:
            t = tasks.get(self.task_id)
            if t:
                t["current_progress"] = fraction or 0.0
                current_fraction = fraction or 0.0
                
                # Calculate speed and ETA internally
                time_diff = now - self.last_progress_time
                progress_diff = current_fraction - self.last_progress_fraction
                if time_diff > 0.5 and progress_diff > 0:
                    # Very rough estimate of speed if not supplied in yt-dlp logs
                    self.last_progress_time = now
                    self.last_progress_fraction = current_fraction

                overall = ((index - 1) + current_fraction) / total_tracks
                t["overall_progress"] = overall

    def log(self, message):
        with tasks_lock:
            t = tasks.get(self.task_id)
            if t:
                # Intercept speed and ETA from stdout logs
                # Example: [download]  12.0% of  10.00MiB at  2.40MiB/s ETA 00:03
                speed_match = re.search(r"at\s+([^\s]+)\s+ETA\s+([^\s]+)", message)
                if speed_match:
                    t["speed"] = speed_match.group(1)
                    t["eta"] = speed_match.group(2)
                
                percent_match = re.search(r"\[download\]\s+(\d+(?:\.\d+)?)%", message)
                if percent_match:
                    pct = float(percent_match.group(1)) / 100.0
                    t["current_progress"] = pct
                    overall = ((t["current_track"] - 1) + pct) / t["total_tracks"]
                    t["overall_progress"] = overall
                
                is_progress = "[download]" in message and "%" in message
                if is_progress and t["logs"] and "[download]" in t["logs"][-1] and "%" in t["logs"][-1]:
                    t["logs"][-1] = message
                else:
                    t["logs"].append(message)
                
                # Cap logs size
                if len(t["logs"]) > 200:
                    t["logs"].pop(0)

    def finish_track(self, result, index, total_tracks):
        with tasks_lock:
            t = tasks.get(self.task_id)
            if t:
                t["current_progress"] = 1.0
                overall = index / total_tracks
                t["overall_progress"] = overall
                t["logs"].append(f"Completed: {result.track.title}")
                if result.output_path:
                    filepath = str(result.output_path.resolve())
                    web_path = f"/api/serve-media?path={urllib.parse.quote(filepath)}"
                    
                    thumbnail_url = t.get("thumbnail_url")
                    
                    t["results"].append({
                        "title": result.track.title,
                        "artist": result.track.artist or "Unknown",
                        "filepath": filepath,
                        "webpath": web_path,
                        "filename": result.output_path.name
                    })
                    
                    # Save to persistent library.json
                    db = load_library_db()
                    db[filepath] = {
                        "filename": result.output_path.name,
                        "filepath": filepath,
                        "webpath": web_path,
                        "thumbnail_url": thumbnail_url,
                        "title": result.track.title,
                        "artist": result.track.artist or "Unknown",
                        "type": "music" if t.get("mode") == "m" else "video",
                        "created_at": time.time()
                    }
                    save_library_db(db)


def run_download_thread(task_id, resolved, options):
    reporter = WebDownloadReporter(task_id)
    downloader = YtDlpDownloader()
    try:
        downloader.download(resolved, options, reporter=reporter)
        with tasks_lock:
            t = tasks.get(task_id)
            if t:
                if t["status"] == "cancelled":
                    t["logs"].append("\nDownload cancelled by user.")
                else:
                    t["status"] = "completed"
                    t["overall_progress"] = 1.0
                    t["current_progress"] = 1.0
                    t["logs"].append("\nDownload collection completed successfully!")
    except Exception as e:
        with tasks_lock:
            t = tasks.get(task_id)
            if t and t["status"] == "cancelled":
                t["logs"].append("\nDownload cancelled by user.")
                return
        import traceback
        traceback.print_exc()
        with tasks_lock:
            t = tasks.get(task_id)
            if t:
                if t["status"] != "cancelled":
                    t["status"] = "failed"
                    t["error"] = str(e)
                    t["logs"].append(f"\nFailed: {str(e)}")
    finally:
        with active_processes_lock:
            active_processes.pop(task_id, None)


def scan_downloads(download_dir_str=None):
    """Scans downloads folder recursively to return list of completed files."""
    results = []
    if download_dir_str:
        base_dir = Path(download_dir_str).resolve()
    else:
        base_dir = Path("downloads").resolve()

    if not base_dir.exists():
        return results

    db = load_library_db()

    allowed_exts = {
        # Audio
        ".mp3", ".aac", ".flac", ".m4a", ".wav", ".ogg", ".opus", ".vorbis", ".alac",
        # Video
        ".mp4", ".mkv", ".webm", ".mov", ".avi", ".flv"
    }
    
    audio_exts = {".mp3", ".aac", ".flac", ".m4a", ".wav", ".ogg", ".opus", ".vorbis", ".alac"}

    for path in base_dir.rglob("*"):
        if path.is_file() and path.suffix.lower() in allowed_exts:
            try:
                resolved_path = path.resolve()
                filepath_str = str(resolved_path)
                file_type = "music" if resolved_path.suffix.lower() in audio_exts or "music" in resolved_path.parts else "video"
                stat = resolved_path.stat()
                web_path = f"/api/serve-media?path={urllib.parse.quote(filepath_str)}"
                
                db_entry = db.get(filepath_str, {})
                thumbnail_url = db_entry.get("thumbnail_url")
                
                results.append({
                    "filename": resolved_path.name,
                    "filepath": filepath_str,
                    "webpath": web_path,
                    "type": file_type,
                    "size_mb": round(stat.st_size / (1024 * 1024), 2),
                    "created_at": stat.st_mtime,
                    "thumbnail_url": thumbnail_url
                })
            except Exception:
                continue
    # Sort by creation time descending (newest first)
    results.sort(key=lambda x: x["created_at"], reverse=True)
    return results


# Routes

@app.route("/")
def index():
    return render_template("index.html")


@app.route("/downloads/<path:filename>")
def serve_download(filename):
    """Serve completed files directly so they can be played in browser."""
    return send_from_directory("downloads", filename)


def _extract_host(source):
    """Extract the hostname from a URL, stripping www. prefix."""
    try:
        from urllib.parse import urlparse as _urlparse
        parsed = _urlparse(source)
        return (parsed.hostname or "").lower().removeprefix("www.")
    except Exception:
        return ""


@app.route("/api/resolve", methods=["POST"])
def api_resolve():
    data = request.json or {}
    source = data.get("source", "").strip()
    mode = data.get("mode", "m") # m for music, v for video
    
    if not source:
        return jsonify({"error": "Link or search query is required"}), 400

    # Block music-only platforms in video mode
    if mode == "v":
        host = _extract_host(source)
        if host in AUDIO_ONLY_HOSTS:
            platform_name = host.replace("open.", "").replace("music.", "").split(".")[0].capitalize()
            return jsonify({"error": f"{platform_name} is an audio-only platform. Please switch to Audio Mode."}), 400

    # Set Spotify API credentials if passed from settings
    spotify_id = data.get("spotify_id", "").strip()
    spotify_secret = data.get("spotify_secret", "").strip()
    if spotify_id:
        os.environ["SPOTIFY_CLIENT_ID"] = spotify_id
    if spotify_secret:
        os.environ["SPOTIFY_CLIENT_SECRET"] = spotify_secret

    media_type = "music" if mode == "m" else "video"
    proxy = data.get("proxy", "").strip() or None
    cookies = get_cookies_file(data.get("cookies", "").strip() or None, source_url=source)
    
    try:
        registry = ResolverRegistry(media_type=media_type)
        resolved = registry.resolve(source, proxy=proxy, cookies=cookies)
        
        thumbnail_url = None
        available_resolutions = []
        if resolved.tracks:
            thumbnail_url = resolved.tracks[0].metadata.get("thumbnail") or resolved.tracks[0].metadata.get("thumbnail_url")
            available_resolutions = resolved.tracks[0].metadata.get("available_resolutions", [])

        return jsonify({
            "source": resolved.source,
            "kind": resolved.kind,
            "platform": resolved.platform,
            "title": resolved.title or resolved.source,
            "tracks": [t.to_dict() for t in resolved.tracks],
            "thumbnail_url": thumbnail_url,
            "available_resolutions": available_resolutions,
            "is_adult": is_adult_site(source)
        })
    except UniGrabError as e:
        return jsonify({"error": str(e)}), 400
    except Exception as e:
        return jsonify({"error": f"Resolution error: {str(e)}"}), 500


@app.route("/api/download", methods=["POST"])
def api_download():
    data = request.json or {}
    source = data.get("source", "").strip()
    mode = data.get("mode", "m") # m or v
    
    if not source:
        return jsonify({"error": "Source link is required"}), 400

    # Block music-only platforms in video mode
    if mode == "v":
        host = _extract_host(source)
        if host in AUDIO_ONLY_HOSTS:
            platform_name = host.replace("open.", "").replace("music.", "").split(".")[0].capitalize()
            return jsonify({"error": f"{platform_name} is an audio-only platform. Please switch to Audio Mode."}), 400

    # Set Spotify API credentials if passed from settings
    spotify_id = data.get("spotify_id", "").strip()
    spotify_secret = data.get("spotify_secret", "").strip()
    if spotify_id:
        os.environ["SPOTIFY_CLIENT_ID"] = spotify_id
    if spotify_secret:
        os.environ["SPOTIFY_CLIENT_SECRET"] = spotify_secret
        
    media_type = "music" if mode == "m" else "video"
    
    # Extract options with defaults
    audio_format = data.get("format", "mp3")
    video_format = data.get("video_format", "mp4")
    quality = data.get("quality", "best")
    video_quality = data.get("video_quality", "best")
    
    # Sanitize quality parameters based on media type
    if media_type == "music":
        video_quality = "best"
        if quality not in QUALITY_VALUES:
            quality = "best"
    else:
        quality = "best"
        if video_quality not in VIDEO_QUALITY_HEIGHTS:
            video_quality = "best"
    limit = data.get("limit")
    if limit is not None:
        try:
            limit = int(limit)
        except ValueError:
            limit = None
            
    direct = bool(data.get("direct", False))
    no_thumbnail = bool(data.get("no_thumbnail", False))
    threads = int(data.get("threads", 4))
    cookies = get_cookies_file(data.get("cookies", "").strip() or None, source_url=source)
    geo_bypass = bool(data.get("geo_bypass", False))
    subtitles = bool(data.get("subtitles", False))
    download_dir = data.get("download_dir", "").strip()
    
    if download_dir:
        output_dir = Path(download_dir)
    else:
        output_dir = Path("downloads")
    
    proxy = data.get("proxy", "").strip() or None
    
    try:
        registry = ResolverRegistry(media_type=media_type)
        resolved = registry.resolve(source, proxy=proxy, cookies=cookies)
        
        options = DownloadOptions(
            audio_format=audio_format,
            video_format=video_format,
            media_type=media_type,
            quality=quality,
            video_quality=video_quality,
            output_dir=output_dir,
            limit=limit,
            direct=direct,
            embed_thumbnail=not no_thumbnail,
            fragment_threads=threads,
            cookies_file=cookies,
            geo_bypass=geo_bypass,
            subtitles=subtitles,
            proxy=proxy
        )
        
        thumbnail_url = None
        if resolved.tracks:
            thumbnail_url = resolved.tracks[0].metadata.get("thumbnail") or resolved.tracks[0].metadata.get("thumbnail_url")

        task_id = str(uuid.uuid4())
        
        with tasks_lock:
            tasks[task_id] = {
                "task_id": task_id,
                "status": "pending",
                "source": source,
                "title": resolved.title or source,
                "mode": mode,
                "thumbnail_url": thumbnail_url,
                "current_track": 0,
                "total_tracks": len(resolved.tracks),
                "current_progress": 0.0,
                "overall_progress": 0.0,
                "speed": "0 KB/s",
                "eta": "--:--",
                "logs": ["Task queued. Waiting for other downloads to finish..."],
                "results": [],
                "error": None
            }
            
        # Add to download queue
        download_queue.put((task_id, resolved, options))
        
        return jsonify({"task_id": task_id, "title": resolved.title})
    except UniGrabError as e:
        return jsonify({"error": str(e)}), 400
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route("/api/status/<task_id>", methods=["GET"])
def api_status(task_id):
    with tasks_lock:
        task = tasks.get(task_id)
        if not task:
            return jsonify({"error": "Task not found"}), 404
        return jsonify(task)


@app.route("/api/tasks", methods=["GET"])
def api_get_tasks():
    with tasks_lock:
        return jsonify(tasks)


@app.route("/api/library", methods=["GET"])
def api_library():
    download_dir = request.args.get("download_dir", "").strip()
    try:
        files = scan_downloads(download_dir)
        return jsonify({"files": files})
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route("/api/serve-media")
def api_serve_media():
    file_path = request.args.get("path")
    if not file_path:
        return jsonify({"error": "No path provided"}), 400
        
    path = Path(file_path).resolve()
    
    allowed_exts = {
        ".mp3", ".aac", ".flac", ".m4a", ".wav", ".ogg", ".opus", ".vorbis", ".alac",
        ".mp4", ".mkv", ".webm", ".mov", ".avi", ".flv"
    }
    if path.suffix.lower() not in allowed_exts:
        return jsonify({"error": "File type not allowed"}), 403
        
    if not path.is_file() or not path.exists():
        return jsonify({"error": "File not found"}), 404
        
    return send_file(path)


@app.route("/api/open-folder", methods=["POST"])
def api_open_folder():
    data = request.json or {}
    filepath = data.get("filepath")
    if not filepath:
        return jsonify({"error": "No filepath provided"}), 400
    
    path = Path(filepath).resolve()
    
    # Security check: must be either inside the workspace OR be an existing file with an allowed media extension
    workspace = Path(os.getcwd()).resolve()
    is_safe = False
    try:
        path.relative_to(workspace)
        is_safe = True
    except ValueError:
        pass
        
    allowed_exts = {
        ".mp3", ".aac", ".flac", ".m4a", ".wav", ".ogg", ".opus", ".vorbis", ".alac",
        ".mp4", ".mkv", ".webm", ".mov", ".avi", ".flv"
    }
    if not is_safe:
        if path.is_file() and path.suffix.lower() in allowed_exts:
            is_safe = True
        elif path.is_dir() and path.exists():
            is_safe = True
            
    if not is_safe or not path.exists():
        return jsonify({"error": "Access denied"}), 403
        
    try:
        if sys.platform == "win32":
            if path.is_file():
                subprocess.run(f'explorer /select,"{path}"', shell=True)
            else:
                os.startfile(path)
        elif sys.platform == "darwin":
            subprocess.run(["open", "-R", str(path)])
        else:
            subprocess.run(["xdg-open", str(path.parent)])
        return jsonify({"success": True})
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route("/api/open-file", methods=["POST"])
def api_open_file():
    data = request.json or {}
    filepath = data.get("filepath")
    if not filepath:
        return jsonify({"error": "No filepath provided"}), 400
    
    path = Path(filepath).resolve()
    
    # Security check: must be either inside the workspace OR be an existing file with an allowed media extension
    workspace = Path(os.getcwd()).resolve()
    is_safe = False
    try:
        path.relative_to(workspace)
        is_safe = True
    except ValueError:
        pass
        
    allowed_exts = {
        ".mp3", ".aac", ".flac", ".m4a", ".wav", ".ogg", ".opus", ".vorbis", ".alac",
        ".mp4", ".mkv", ".webm", ".mov", ".avi", ".flv"
    }
    if not is_safe:
        if path.is_file() and path.suffix.lower() in allowed_exts:
            is_safe = True
            
    if not is_safe or not path.exists() or not path.is_file():
        return jsonify({"error": "Access denied"}), 403
        
    try:
        if sys.platform == "win32":
            os.startfile(path)
        elif sys.platform == "darwin":
            subprocess.run(["open", str(path)])
        else:
            subprocess.run(["xdg-open", str(path)])
        return jsonify({"success": True})
    except Exception as e:
        return jsonify({"error": str(e)}), 500


def suspend_process(pid):
    if sys.platform == "win32":
        import ctypes
        handle = ctypes.windll.kernel32.OpenProcess(0x0800, False, pid)
        if handle:
            ctypes.windll.ntdll.NtSuspendProcess(handle)
            ctypes.windll.kernel32.CloseHandle(handle)
    else:
        import os
        import signal
        os.kill(pid, signal.SIGSTOP)


def resume_process(pid):
    if sys.platform == "win32":
        import ctypes
        handle = ctypes.windll.kernel32.OpenProcess(0x0800, False, pid)
        if handle:
            ctypes.windll.ntdll.NtResumeProcess(handle)
            ctypes.windll.kernel32.CloseHandle(handle)
    else:
        import os
        import signal
        os.kill(pid, signal.SIGCONT)


@app.route("/api/task/pause/<task_id>", methods=["POST"])
def api_pause_task(task_id):
    with tasks_lock:
        task = tasks.get(task_id)
        if not task:
            return jsonify({"error": "Task not found"}), 404
        if task["status"] != "downloading":
            return jsonify({"error": "Task is not running"}), 400
        task["status"] = "paused"
        task["logs"].append("[info] Download paused by user.")
        
    with active_processes_lock:
        proc = active_processes.get(task_id)
    if proc:
        try:
            suspend_process(proc.pid)
        except Exception as e:
            return jsonify({"error": f"Failed to pause process: {str(e)}"}), 500
            
    return jsonify({"success": True, "status": "paused"})


@app.route("/api/task/resume/<task_id>", methods=["POST"])
def api_resume_task(task_id):
    with tasks_lock:
        task = tasks.get(task_id)
        if not task:
            return jsonify({"error": "Task not found"}), 404
        if task["status"] != "paused":
            return jsonify({"error": "Task is not paused"}), 400
        task["status"] = "downloading"
        task["logs"].append("[info] Download resumed by user.")
        
    with active_processes_lock:
        proc = active_processes.get(task_id)
    if proc:
        try:
            resume_process(proc.pid)
        except Exception as e:
            return jsonify({"error": f"Failed to resume process: {str(e)}"}), 500
            
    return jsonify({"success": True, "status": "downloading"})


@app.route("/api/task/stop/<task_id>", methods=["POST"])
def api_stop_task(task_id):
    with tasks_lock:
        task = tasks.get(task_id)
        if not task:
            return jsonify({"error": "Task not found"}), 404
        
        prev_status = task["status"]
        task["status"] = "cancelled"
        task["logs"].append("[info] Download cancelled by user.")
        
    with active_processes_lock:
        proc = active_processes.pop(task_id, None)
        
    if proc:
        try:
            if prev_status == "paused":
                try:
                    resume_process(proc.pid)
                except Exception:
                    pass
            # Kill entire process tree to ensure yt-dlp subprocesses are terminated
            try:
                import psutil
                parent = psutil.Process(proc.pid)
                for child in parent.children(recursive=True):
                    child.kill()
                parent.kill()
            except (ImportError, Exception):
                proc.kill()
        except Exception:
            pass
            
    return jsonify({"success": True, "status": "cancelled"})


@app.route("/api/doctor", methods=["GET"])
def api_doctor():
    import shutil
    yt_dlp_status = "installed" if importlib.util.find_spec("yt_dlp") else "missing"
    ffmpeg_exe = shutil.which("ffmpeg")
    try:
        import imageio_ffmpeg
        ffmpeg_status = imageio_ffmpeg.get_ffmpeg_exe() or ffmpeg_exe or "missing"
    except ImportError:
        ffmpeg_status = ffmpeg_exe or "missing"

    return jsonify({
        "download_engine": yt_dlp_status,
        "ffmpeg": ffmpeg_status,
        "status": "ok" if yt_dlp_status == "installed" and ffmpeg_status != "missing" else "error"
    })


@app.route("/api/formats", methods=["GET"])
def api_formats():
    return jsonify({
        "audio_formats": sorted(list(SUPPORTED_FORMATS)),
        "video_formats": sorted(list(SUPPORTED_VIDEO_FORMATS)),
        "audio_qualities": sorted(list(QUALITY_VALUES.keys())),
        "video_qualities": sorted(list(VIDEO_QUALITY_HEIGHTS.keys()))
    })


def run_download_thread_wrapper(task_id, resolved, options):
    try:
        run_download_thread(task_id, resolved, options)
    finally:
        download_queue.task_done()


def download_worker():
    while True:
        try:
            # Check if there is an active downloading task (only count truly running ones)
            with tasks_lock:
                active_count = sum(1 for t in tasks.values() if t["status"] == "downloading")
            
            if active_count < 1:
                # Get the next item from the queue without blocking indefinitely
                try:
                    task_id, resolved, options = download_queue.get(timeout=0.5)
                except queue.Empty:
                    continue
                
                with tasks_lock:
                    task = tasks.get(task_id)
                    if not task or task["status"] == "cancelled":
                        download_queue.task_done()
                        continue
                    
                    task["status"] = "downloading"
                    task["logs"].append("[info] Processing task from queue...")
                
                # Start download thread asynchronously
                threading.Thread(
                    target=run_download_thread_wrapper,
                    args=(task_id, resolved, options),
                    daemon=True
                ).start()
            else:
                # If a download is active, sleep before checking again
                time.sleep(0.3)
        except Exception as e:
            print(f"Error in download worker: {e}")
            time.sleep(1)


if __name__ == "__main__":
    # Start the download queue worker thread
    threading.Thread(target=download_worker, daemon=True).start()
    
    print("UniGrab Web Server starting on http://127.0.0.1:5000")
    app.run(host="127.0.0.1", port=5000, debug=True)
