"""Compare two private passive-measurement receipts without exporting their identifiers."""
import argparse
from collections import defaultdict
from datetime import datetime
import json
from pathlib import Path
import re


def heap_alloc(receipt, field, label):
    # API-30 meminfo columns: PSS, private dirty/clean, swap PSS, RSS, size, alloc, free.
    match = re.fullmatch(r'\s*' + label + r'\s+' + r'\s+'.join([r'(\d+)'] * 8) + r'\s*', receipt[field])
    if not match:
        raise ValueError('Unsupported meminfo heap columns; do not guess allocated bytes')
    return int(match[7]) / 1024


def health(receipt):
    groups = defaultdict(list)
    for row in receipt['health_records']:
        if row.get('event') == 'health' and 'native_displayed' in row:
            groups[(row['camera'], row['session'], row['attempt'])].append(row)
    groups = {key: sorted(rows, key=lambda row: row['at']) for key, rows in groups.items()}
    advancing = fixed = 0
    for rows in groups.values():
        first, last = rows[0], rows[-1]
        comparable = last['at'] > first['at']
        advancing += comparable and last['native_displayed'] > first['native_displayed']
        fixed += comparable and all(
            name in first and all(row.get(name) == first[name] for row in rows)
            for name in ('tile_width', 'tile_height', 'tile_resizes'))
    return groups, dict(attempts_observed=len(groups), attempts_with_advancing_counters=advancing,
                        attempts_with_unchanged_sampled_geometry=fixed)


def compare(before, after):
    # A lower reading in a replacement process is not evidence of recovery.
    for name in ('screen', 'boot_id', 'pid', 'process_start_ticks', 'version', 'code'):
        if before.get(name) in (None, '') or before.get(name) != after.get(name):
            raise ValueError('Comparison requires the same screen, boot, process and version')
    start, end = (datetime.fromisoformat(row['utc']) for row in (before, after))
    if start.tzinfo is None or end.tzinfo is None:
        raise ValueError('Measurement timestamps must include a timezone')
    hours = (end - start).total_seconds() / 3600
    if hours <= 0 or after['process_age_seconds'] <= before['process_age_seconds']:
        raise ValueError('Measurements must advance within the same process')

    def delta(first, last):
        return dict(before=round(first, 3), after=round(last, 3), delta=round(last - first, 3))

    native = delta(*(heap_alloc(row, 'native_heap_row', 'Native Heap') for row in (before, after)))
    native['change_per_hour'] = round((heap_alloc(after, 'native_heap_row', 'Native Heap') -
                                       heap_alloc(before, 'native_heap_row', 'Native Heap')) / hours, 3)
    first_groups, first_health = health(before)
    last_groups, last_health = health(after)
    matching = first_groups.keys() & last_groups.keys()
    resize_delta = 0
    comparable_resizes = 0
    for key in matching:
        first = first_groups[key][-1].get('tile_resizes')
        last = last_groups[key][-1].get('tile_resizes')
        if first is not None and last is not None and last >= first:
            resize_delta += last - first
            comparable_resizes += 1
    return dict(
        same_process_and_version=True, elapsed_hours=round(hours, 6), native_alloc_mib=native,
        java_alloc_mib=delta(*(heap_alloc(row, 'dalvik_heap_row', 'Dalvik Heap') for row in (before, after))),
        rss_mib=delta(*(row['process_status']['VmRSS'] / 1024 for row in (before, after))),
        process_swap_mib=delta(*(row['process_status']['VmSwap'] / 1024 for row in (before, after))),
        recent_health_before=first_health, recent_health_after=last_health,
        matching_attempts_at_endpoints=len(matching),
        attempts_with_comparable_resize_counters=comparable_resizes,
        cumulative_parent_resize_increase=resize_delta,
        interpretation='Endpoint allocation change, not leak attribution or a continuous playback result. '
                       'Heap allocation, RSS and swap are separate measures. Parent resizes are not native buffer allocations. '
                       'Uncaptured intervals may contain other activity; nonmatching attempts do not count all replacements.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('before', type=Path)
    parser.add_argument('after', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    try:
        result = compare(*(json.loads(path.read_text(encoding='utf-8')) for path in (args.before, args.after)))
    except (KeyError, TypeError, ValueError) as error:
        # Do not print input values or raw JSON/parser exception text containing credentials.
        parser.exit(2, 'Comparison rejected: missing, incompatible or malformed measurement data.\n')
    rendered = json.dumps(result, indent=2) + '\n'
    if args.output:
        args.output.write_text(rendered, encoding='utf-8')
    else:
        print(rendered, end='')


if __name__ == '__main__':
    main()
