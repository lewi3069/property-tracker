"""Summarize 0.2.3 FieldviewPlayer epoch logs without copying raw log messages."""
import argparse
import json
import pathlib
import re

EVENT = re.compile(r'^\s*(\d+\.\d+)\s+\d+\s+\d+\s+[A-Z]\s+FieldviewPlayer:\s+camera=([A-Za-z0-9_-]{1,80}) session=([a-f0-9]{8}) attempt=(\d+) view=(wall|float) profile=(main|sub) event=([a-z_]+)\b(.*)$')
INTEGER_FIELDS = ('startup_ms', 'state', 'frame_silent_ms', 'buffered_ms', 'submitted_frames',
                  'decoder_rendered', 'dropped', 'media_buffer_bytes', 'native_heap_kib', 'java_heap_kib')


def summarize(lines):
    attempts = {}
    inflight = set()
    maximum = 0
    for line in lines:
        match = EVENT.match(line)
        if not match:
            continue
        timestamp, camera, session, generation, view, profile, event, rest = match.groups()
        if event not in ('start', 'decoder', 'first_frame', 'health', 'stalled', 'failure', 'retry', 'release', 'stopped'):
            continue
        timestamp = float(timestamp)
        key = (camera, session, int(generation))
        row = attempts.setdefault(key, dict(camera=camera, session=session, attempt=int(generation), view=view,
                                            profile=profile, first_event_at=timestamp, events={}))
        row['events'][event] = row['events'].get(event, 0) + 1
        row['last_event_at'] = timestamp
        if event == 'start':
            inflight.add(key)
            maximum = max(maximum, len(inflight))
            renderer = re.search(r'\brenderer=(surface|texture)\b', rest)
            if renderer:
                row['renderer'] = renderer[1]
        elif event in ('first_frame', 'failure', 'release', 'retry', 'stopped'):
            inflight.discard(key)
        numbers = {}
        for name in INTEGER_FIELDS:
            value = re.search(r'\b' + name + r'=(-?\d+)\b', rest)
            if value:
                numbers[name] = int(value[1])
        if event == 'first_frame' and 'startup_ms' in numbers:
            row['startup_ms'] = numbers['startup_ms']
        if event == 'health':
            previous = row.get('last_health')
            if previous and timestamp > previous['at'] and 'submitted_frames' in previous and 'submitted_frames' in numbers:
                delta = numbers['submitted_frames'] - previous['submitted_frames']
                if delta >= 0:
                    row['latest_submitted_fps'] = round(delta / (timestamp - previous['at']), 2)
            row['last_health'] = dict(at=timestamp, **numbers)
    return dict(attempts=list(attempts.values()), max_inflight_starts_seen=maximum,
                pending_starts_at_end=len(inflight), presentation_evidence='not_measured',
                scope='Only complete 0.2.3 diagnostic events in the supplied log; partial captures can undercount startup concurrency.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('log', type=pathlib.Path, help='adb logcat -d -v epoch -s FieldviewPlayer capture')
    parser.add_argument('--output', type=pathlib.Path)
    args = parser.parse_args()
    result = json.dumps(summarize(args.log.read_text(encoding='utf-8', errors='replace').splitlines()), indent=2)
    if args.output:
        args.output.write_text(result + '\n', encoding='utf-8')
    else:
        print(result)


if __name__ == '__main__':
    main()
