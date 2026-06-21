"""MP4 → MP3 audio extraction using FFmpeg."""

import subprocess
import os
import re
import platform
import sys


def _ffmpeg_binary():
    """Return path to ffmpeg binary. On Android, use bundled binary; otherwise system ffmpeg."""
    if hasattr(sys, 'android'):
        import shutil
        from android.storage import app_storage_path

        app_dir = app_storage_path()
        ffmpeg_dest = os.path.join(app_dir, 'ffmpeg')

        # First run: extract bundled ffmpeg from APK assets to writable app dir
        if not os.path.exists(ffmpeg_dest):
            arch = platform.machine()  # 'aarch64' or 'armv7l'
            arch_map = {'aarch64': 'arm64-v8a', 'armv7l': 'armeabi-v7a'}
            arch_dir = arch_map.get(arch, 'arm64-v8a')
            src = os.path.join(app_dir, 'ffmpeg', arch_dir, 'ffmpeg')
            # Assets may be extracted flatly, try multiple possible locations
            possible_srcs = [
                os.path.join(app_dir, 'ffmpeg', arch_dir, 'ffmpeg'),
                os.path.join(app_dir, 'ffmpeg'),
                os.path.join(os.path.dirname(app_dir), 'files', 'ffmpeg', arch_dir, 'ffmpeg'),
            ]
            for src in possible_srcs:
                if os.path.exists(src) and src != ffmpeg_dest:
                    shutil.copy2(src, ffmpeg_dest)
                    os.chmod(ffmpeg_dest, 0o755)
                    break

        return ffmpeg_dest if os.path.exists(ffmpeg_dest) else 'ffmpeg'
    return 'ffmpeg'


def extract_audio(input_path, output_dir=None, quality='320k', progress_callback=None):
    """Extract audio from MP4 and encode to MP3.

    Args:
        input_path: Path to input MP4 file.
        output_dir: Directory for output MP3. Defaults to same dir as input.
        quality: MP3 bitrate (default '320k' for highest quality).
        progress_callback: Optional callable(duration_sec, current_sec) for progress.

    Returns:
        Path to output MP3 file on success.

    Raises:
        RuntimeError: If FFmpeg fails.
    """
    if not os.path.exists(input_path):
        raise FileNotFoundError(f'Input file not found: {input_path}')

    input_name = os.path.splitext(os.path.basename(input_path))[0]
    if output_dir is None:
        output_dir = os.path.dirname(input_path) or '.'
    output_path = os.path.join(output_dir, f'{input_name}.mp3')

    # Avoid overwriting: append number if file exists
    base = output_path
    counter = 1
    while os.path.exists(output_path):
        name, ext = os.path.splitext(base)
        output_path = f'{name}_{counter}{ext}'
        counter += 1

    # Get video duration for progress
    duration = _get_duration(input_path)

    ffmpeg = _ffmpeg_binary()
    cmd = [
        ffmpeg, '-y', '-i', input_path,
        '-vn',                    # no video
        '-c:a', 'libmp3lame',     # LAME MP3 encoder
        '-b:a', quality,          # bitrate
        '-q:a', '0',              # highest LAME quality
        '-map_metadata', '0',     # copy metadata from source
        '-progress', 'pipe:1',    # machine-readable progress to stdout
        '-nostats',               # no interactive stats
        output_path
    ]

    proc = subprocess.Popen(
        cmd,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        bufsize=1
    )

    last_time = 0
    for line in proc.stdout:
        match = re.search(r'out_time_us=(\d+)', line)
        if match and duration > 0 and progress_callback:
            current = int(match.group(1)) / 1_000_000
            if current - last_time >= 0.5:  # throttle updates
                last_time = current
                progress_callback(duration, current)

    proc.wait()

    if proc.returncode != 0:
        stderr_output = proc.stderr.read() if proc.stderr else ''
        raise RuntimeError(f'FFmpeg failed (code {proc.returncode}): {stderr_output[-500:]}')

    return output_path


def _get_duration(path):
    """Get media duration in seconds using ffmpeg."""
    try:
        result = subprocess.run(
            [_ffmpeg_binary(), '-i', path],
            capture_output=True, text=True
        )
        for line in result.stderr.split('\n'):
            if 'Duration' in line:
                # Format: "  Duration: 00:00:05.01, start: ..."
                time_str = line.strip().split('Duration: ')[1].split(',')[0]
                h, m, s = time_str.split(':')
                return float(h) * 3600 + float(m) * 60 + float(s)
    except Exception:
        pass
    return 0
