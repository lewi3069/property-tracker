"""Read-only Property Tracker/Plex diagnostics. Never launches, stops, or kills an app."""
import argparse
import datetime as dt
import json
import pathlib
import re
import shlex
import subprocess
import time

ROOT = pathlib.Path(__file__).resolve().parents[1]
PACKAGES = ("com.fieldview.tv", "com.plexapp.android")
PROCESSES = (*PACKAGES, "media.codec", "surfaceflinger")
SURFACES = {
    "camera": "SurfaceView - Fieldview floating camera",
    "plex": "SurfaceView - com.plexapp.android/tv.plex.app.MainActivity",
}


def surface_progress(text):
    frames = []
    for line in text.splitlines():
        fields = line.split()
        if len(fields) == 3 and all(field.isdigit() for field in fields):
            values = [int(field) for field in fields]
            if all(0 < value < 9223372036854775807 for value in values):
                frames.append(values)
    return {"valid_frames": len(frames), "latest_present_ns": max((row[1] for row in frames), default=None)}


def plex_playback_states(text):
    keep = False
    states = []
    for line in text.splitlines():
        if "package=" in line:
            keep = "package=com.plexapp.android " in line or line.strip().endswith("package=com.plexapp.android")
        if keep and "state=PlaybackState" in line:
            state = {name: float(value) for name, value in re.findall(r"\b(state|position|speed|updated)=(-?[\d.]+)", line)}
            if state not in states:
                states.append(state)
    return states


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="ADB device serial or IP:port")
    parser.add_argument("--minutes", type=float, default=15)
    parser.add_argument("--interval", type=float, default=15)
    args = parser.parse_args()
    if args.minutes <= 0 or args.interval < 5:
        parser.error("minutes must be positive and interval must be at least 5 seconds")
    adb = ROOT / ".tools/android-sdk/platform-tools/adb.exe"
    if not adb.is_file():
        parser.error("Project Android SDK/ADB is missing")
    output = ROOT / ".tools/diagnostics" / ("plex-passive-" + dt.datetime.now().strftime("%Y%m%d-%H%M%S") + ".local.jsonl")
    output.parent.mkdir(parents=True, exist_ok=True)

    def read(*command):
        try:
            result = subprocess.run([str(adb), "-s", args.serial, *command], capture_output=True,
                                    text=True, encoding="utf-8", errors="replace", timeout=12)
            return result.stdout if result.returncode == 0 else ""
        except subprocess.TimeoutExpired:
            return ""

    with output.open("w", encoding="utf-8") as report:
        def record(kind, **values):
            item = {"time_utc": dt.datetime.now(dt.timezone.utc).isoformat(), "kind": kind, **values}
            report.write(json.dumps(item) + "\n")
            report.flush()

        device_since = read("shell", "date", r"+%m-%d\ %H:%M:%S.000").strip()
        # Fall back to full-buffer event timestamps if a device's date format differs.
        since_args = ["-T", device_since] if re.fullmatch(r"\d\d-\d\d \d\d:\d\d:\d\d\.000", device_since) else []
        record("start", serial=args.serial, minutes=args.minutes, interval_seconds=args.interval,
               model=read("shell", "getprop", "ro.product.model").strip(), device_start=device_since,
               mode="read-only; no UI actions, process termination, or service control")
        print(f"Read-only monitoring; Ctrl+C stops collection. Report: {output}", flush=True)
        started = time.monotonic()
        try:
            while time.monotonic() - started < args.minutes * 60:
                sample_start = time.monotonic()
                pids = {package: read("shell", "pidof", package).split() for package in PROCESSES}
                process_memory = {}
                for name, values in pids.items():
                    if len(values) == 1 and values[0].isdigit():
                        process_memory[name] = {key: int(value) for key, value in re.findall(
                            r"^(VmRSS|VmSwap):\s+(\d+) kB", read("shell", "cat", "/proc/" + values[0] + "/status"), re.MULTILINE)}
                memory = {name: int(value) for name, value in re.findall(
                    r"^(MemTotal|MemAvailable|MemFree|SwapTotal|SwapFree):\s+(\d+) kB",
                    read("shell", "cat", "/proc/meminfo"), re.MULTILINE)}
                windows = read("shell", "dumpsys", "window", "windows")
                focus_line = next((line for line in windows.splitlines() if "mCurrentFocus=" in line), "")
                focus = re.search(r"\s([A-Za-z0-9_.]+)/", focus_line)
                surfaces = {name: surface_progress(read("shell", "dumpsys SurfaceFlinger --latency " + shlex.quote(layer)))
                            for name, layer in SURFACES.items()}
                plex_states = plex_playback_states(read("shell", "dumpsys", "media_session"))
                # Missing responses are not evidence that an app exited or a surface froze.
                incomplete = not memory or not windows or any(not state for state in process_memory.values())
                record("sample", elapsed_seconds=round(time.monotonic() - started), pids=pids,
                       memory_kib=memory, process_memory_kib=process_memory, surfaces=surfaces,
                       plex_playback_states=plex_states, incomplete_device_data=incomplete,
                       foreground_package=focus.group(1) if focus else None,
                       floating_window_present="Fieldview floating camera" in windows)
                print(f"{round(time.monotonic()-started)}s: Property Tracker={pids[PACKAGES[0]] or 'absent'}, "
                      f"Plex={pids[PACKAGES[1]] or 'absent'}, float={'Fieldview floating camera' in windows}", flush=True)
                time.sleep(max(0, args.interval - (time.monotonic() - sample_start)))
        except KeyboardInterrupt:
            print("Collection stopped; saving lifecycle evidence.", flush=True)
        finally:
            # Save only package lifecycle metadata, not playback titles, command extras, or URLs.
            events = read("logcat", "-b", "events", "-d", "-v", "threadtime", *since_args)
            for line in events.splitlines():
                package = next((p for p in PACKAGES if p in line), None)
                event = re.search(r"\b(am_proc_start|am_proc_died|am_schedule_service_restart|am_resume_activity|am_crash|am_anr):", line)
                if package and event:
                    record("lifecycle", device_time=line[:18], package=package, event=event.group(1))
            logs = read("logcat", "-d", "-v", "threadtime", *since_args,
                        "FieldviewOverlay:I", "FieldviewPlayer:I", "*:S")
            for line in logs.splitlines():
                if re.search(r"Fieldview(Overlay|Player)\s*:", line):
                    # Application messages use fixed reasons. Strip URLs defensively.
                    record("fieldview_log", message=re.sub(r"\b\w+://\S+", "[URL omitted]", line))
            record("stop", elapsed_seconds=round(time.monotonic() - started))
    print(f"Saved {output}", flush=True)


if __name__ == "__main__":
    main()
