import test from 'node:test';
import assert from 'node:assert/strict';
import { ViewController } from '../src/view-controller.mjs';

const make = () => new ViewController({ cameras: ['barn', 'yard', 'pasture', 'gate'],
  zoneCameras: { barn: ['barn'], yard: ['yard'], pasture: ['pasture'] } });

test('motion enlarges a camera and expires back to overview', () => {
  const c = make();
  assert.equal(c.snapshot(0).reason, 'overview');
  c.motion('barn', 1000);
  assert.deepEqual(c.snapshot(1000).ids, ['barn']);
  assert.equal(c.snapshot(31000).reason, 'overview');
});

test('manual pin wins, and auto resumes current activity', () => {
  const c = make();
  c.setMode('pin', 'gate');
  c.motion('barn', 0);
  assert.deepEqual(c.snapshot(1000).ids, ['gate']);
  c.setMode('auto');
  assert.deepEqual(c.snapshot(1000).ids, ['barn']);
});

test('horse presence must be stable and motion preempts it immediately', () => {
  const c = make();
  c.location('horse-1', 'pasture', 0);
  assert.equal(c.snapshot(4999).reason, 'overview');
  assert.deepEqual(c.snapshot(5000).ids, ['pasture']);
  c.motion('barn', 5001);
  assert.deepEqual(c.snapshot(5001).ids, ['barn']);
  assert.deepEqual(c.snapshot(35001).ids, ['pasture']);
});

test('repeated location reports do not restart the stability timer', () => {
  const c = make();
  c.location('horse-1', 'yard', 0);
  c.location('horse-1', 'yard', 4000);
  assert.deepEqual(c.snapshot(5000).ids, ['yard']);
});

test('location jitter, lost signals, and stale reports do not select a false zone', () => {
  const c = make();
  c.location('horse-1', 'pasture', 0);
  c.location('horse-1', 'yard', 4000);
  assert.equal(c.snapshot(5000).reason, 'overview');
  assert.deepEqual(c.snapshot(9000).ids, ['yard']);
  c.location('horse-1', 'unknown', 9001);
  assert.equal(c.snapshot(9001).reason, 'overview');
  c.location('horse-1', 'barn', 10000);
  assert.deepEqual(c.snapshot(15000).ids, ['barn']);
  assert.equal(c.snapshot(70000).reason, 'overview');
});

test('two horses can select two different camera views', () => {
  const c = make();
  c.location('horse-1', 'yard', 0);
  c.location('horse-2', 'pasture', 0);
  assert.deepEqual(c.snapshot(5000).ids, ['yard', 'pasture']);
});

test('equal-priority activity observes dwell; the latest two then enlarge', () => {
  const c = make();
  c.motion('barn', 0);
  c.snapshot(0);
  c.motion('yard', 1000);
  c.motion('pasture', 2000);
  assert.deepEqual(c.snapshot(2000).ids, ['barn']);
  assert.deepEqual(c.snapshot(8000).ids, ['pasture', 'yard']);
});

test('manual grid stays put, unknown cameras are ignored, reset clears events', () => {
  const c = make();
  assert.equal(c.motion('missing', 0), false);
  assert.equal(c.setMode('pin', 'missing'), false);
  c.setMode('grid');
  c.motion('barn', 0);
  assert.equal(c.snapshot(1000).reason, 'grid');
  c.clearEvents();
  c.setMode('auto');
  assert.equal(c.snapshot(1000).reason, 'overview');
});

test('a camera outside the overview is promoted within the screen limit', () => {
  const c = make();
  c.setVisibleLimit(2);
  assert.deepEqual(c.snapshot(0).visibleIds, ['barn', 'yard']);
  c.motion('gate', 1);
  assert.deepEqual(c.snapshot(1).visibleIds, ['gate', 'barn']);
  assert.deepEqual(c.snapshot(30001).visibleIds, ['barn', 'yard']);
});

test('changing capacity applies immediately while events and pinning survive', () => {
  const c = make();
  c.motion('gate', 0);
  c.motion('yard', 1);
  assert.equal(c.snapshot(1).ids.length, 2);
  c.setVisibleLimit(1);
  assert.deepEqual(c.snapshot(2).visibleIds, ['yard']);
  assert.deepEqual(c.snapshot(2).ids, ['yard']);
  c.setMode('pin', 'pasture');
  c.setVisibleLimit(4);
  assert.deepEqual(c.snapshot(3).visibleIds, ['pasture']);
  c.setMode('auto');
  assert.equal(c.snapshot(3).visibleIds.length, 4);
  assert.equal(c.snapshot(3).ids.length, 2);
});

test('capacity is bounded by the catalog and invalid limits leave it unchanged', () => {
  const c = make();
  c.setVisibleLimit(9);
  assert.equal(c.snapshot(0).visibleIds.length, 4);
  for (const invalid of [0, -1, 1.5, NaN, Infinity, '2']) assert.equal(c.setVisibleLimit(invalid), false);
  assert.equal(c.snapshot(0).visibleIds.length, 4);
});

test('two horse locations obey a one-camera display preference', () => {
  const c = make();
  c.setVisibleLimit(1);
  c.location('horse-1', 'yard', 0);
  c.location('horse-2', 'pasture', 0);
  assert.deepEqual(c.snapshot(5000).visibleIds, ['yard']);
  assert.deepEqual(c.snapshot(5000).presenceIds, ['yard', 'pasture']);
});
