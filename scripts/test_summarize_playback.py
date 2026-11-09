import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location('playback_summary', pathlib.Path(__file__).with_name('summarize-playback.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def event(time, camera, event, extra=''):
    return f'{time:.3f} 100 100 I FieldviewPlayer: camera={camera} session=abcdef12 attempt=1 view=wall profile=sub event={event} {extra}'


class PlaybackSummaryTest(unittest.TestCase):
    def test_concurrency_and_submission_rate_are_separate_from_presentation(self):
        result = module.summarize([
            event(1, 'one', 'start', 'renderer=surface'), event(2, 'two', 'start', 'renderer=surface'),
            event(3, 'one', 'first_frame', 'startup_ms=2000'), event(4, 'three', 'start', 'renderer=texture'),
            event(31, 'one', 'health', 'submitted_frames=100 decoder_rendered=100 native_heap_kib=1000'),
            event(61, 'one', 'health', 'submitted_frames=700 decoder_rendered=690 native_heap_kib=900')])
        self.assertEqual(2, result['max_inflight_starts_seen'])
        self.assertEqual(20.0, result['attempts'][0]['latest_submitted_fps'])
        self.assertEqual('not_measured', result['presentation_evidence'])

    def test_native_displayed_counter_keeps_presentation_caveat(self):
        result = module.summarize([
            event(1, 'one', 'start', 'renderer=texture engine=native'),
            event(31, 'one', 'health', 'native_displayed=100 native_decoded=200'),
            event(61, 'one', 'health', 'native_displayed=700 native_decoded=1400 native_demux_bytes=10000 native_media_time_ms=30000 tile_width=960 tile_height=540 tile_resizes=2')])
        self.assertEqual('native', result['attempts'][0]['engine'])
        self.assertEqual(10000, result['attempts'][0]['last_health']['native_demux_bytes'])
        self.assertEqual(30000, result['attempts'][0]['last_health']['native_media_time_ms'])
        self.assertEqual(960, result['attempts'][0]['last_health']['tile_width'])
        self.assertEqual(540, result['attempts'][0]['last_health']['tile_height'])
        self.assertEqual(2, result['attempts'][0]['last_health']['tile_resizes'])
        self.assertEqual(20.0, result['attempts'][0]['latest_native_displayed_fps'])
        self.assertEqual('not_measured', result['presentation_evidence'])

    def test_raw_errors_urls_and_unknown_fields_are_not_copied(self):
        result = module.summarize(['unstructured error rtsp://user:password@host',
                                  event(1, 'test', 'failure', 'reason=rtsp://user:password@host'),
                                  event(2, 'test', 'health', 'submitted_frames=100 password=private')])
        rendered = str(result)
        self.assertNotIn('password', rendered)
        self.assertNotIn('private', rendered)
        self.assertNotIn('rtsp://', rendered)

    def test_cleanup_failures_are_counted_without_inventing_camera_attempts_or_copying_messages(self):
        prefix = '1.000 100 100 W FieldviewPlayer: '
        result = module.summarize([
            prefix + 'event=retirement_failed',
            prefix + 'event=retirement_failed stage=runtime detail=rtsp://user:password@host',
            prefix + 'event=retirement_failed stage=media',
            prefix + 'event=retirement_failed stage=player',
            prefix + 'event=retirement_failed stage=views',
            prefix + 'event=retirement_failed stage=runtime-secret',
            prefix + 'event=unknown stage=runtime password=private'])
        self.assertEqual({'retirement_failed': 6}, result['global_events'])
        self.assertEqual({'runtime': 1, 'media': 1, 'player': 1, 'views': 1},
                         result['retirement_failures_by_stage'])
        self.assertEqual([], result['attempts'])
        self.assertEqual(0, result['max_inflight_starts_seen'])
        rendered = str(result)
        for raw in ('password', 'private', 'rtsp://', 'secret', 'unknown'):
            self.assertNotIn(raw, rendered)


if __name__ == '__main__':
    unittest.main()
