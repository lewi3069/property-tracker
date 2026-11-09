import copy
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('native_comparison', Path(__file__).with_name('compare-native-memory.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def receipt(hour, allocation):
    return dict(utc=f'2026-11-09T{hour:02}:00:00+00:00', screen='private-screen', boot_id='private-boot',
                pid='123', process_start_ticks=100, version='0.2.6', code=27,
                process_age_seconds=hour * 3600,
                native_heap_row=f'Native Heap 100 50 0 1 150 {allocation + 500} {allocation} 500',
                dalvik_heap_row='Dalvik Heap 10 10 0 0 10 5000 2000 3000',
                process_status=dict(VmRSS=204800, VmSwap=10240),
                health_records=[dict(camera='private-camera', session='private-session', attempt=1,
                                     event='health', at=hour * 3600 + offset, native_displayed=count,
                                     tile_width=480, tile_height=270, tile_resizes=1)
                                for offset, count in ((0, 100), (30, 700))],
                password='must-never-be-copied', raw='rtsp://user:secret@private-host')


class NativeComparisonTest(unittest.TestCase):
    def test_same_process_growth_has_separate_accounting_and_no_identifiers(self):
        result = module.compare(receipt(10, 512000), receipt(14, 716800))
        self.assertEqual(dict(before=500.0, after=700.0, delta=200.0, change_per_hour=50.0),
                         result['native_alloc_mib'])
        self.assertEqual(0, result['rss_mib']['delta'])
        self.assertEqual(10, result['process_swap_mib']['after'])
        self.assertEqual(1, result['matching_attempts_at_endpoints'])
        self.assertEqual(1, result['recent_health_after']['attempts_with_advancing_counters'])
        for private in ('private-', 'secret', 'password', 'must-never', 'rtsp://'):
            self.assertNotIn(private, str(result))

    def test_restarts_reboots_versions_and_missing_identity_are_rejected(self):
        first, second = receipt(10, 512000), receipt(14, 716800)
        for name in ('screen', 'boot_id', 'pid', 'process_start_ticks', 'version', 'code'):
            with self.subTest(field=name):
                changed = copy.deepcopy(second)
                changed[name] = 'changed'
                with self.assertRaises(ValueError):
                    module.compare(first, changed)
                del changed[name]
                with self.assertRaises(ValueError):
                    module.compare(first, changed)

    def test_time_and_heap_column_mismatches_are_rejected(self):
        for change in ({'utc': '2026-11-09T09:00:00+00:00'}, {'utc': '2026-11-09T14:00:00'},
                       {'process_age_seconds': 1}, {'native_heap_row': 'Native Heap 1 2 3 4 5 6'}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                module.compare(receipt(10, 512000), dict(receipt(14, 716800), **change))

    def test_resizing_and_replacements_are_not_blended_with_steady_attempts(self):
        first, second = receipt(10, 512000), receipt(14, 716800)
        second['health_records'][0]['tile_resizes'] = 3
        second['health_records'][1]['tile_resizes'] = 4
        second['health_records'][1]['tile_width'] = 960
        result = module.compare(first, second)
        self.assertEqual(3, result['cumulative_parent_resize_increase'])
        self.assertEqual(0, result['recent_health_after']['attempts_with_unchanged_sampled_geometry'])
        for row in second['health_records']:
            row['session'] = 'replacement'
        result = module.compare(first, second)
        self.assertEqual(0, result['matching_attempts_at_endpoints'])
        self.assertEqual(0, result['attempts_with_comparable_resize_counters'])


if __name__ == '__main__':
    unittest.main()
