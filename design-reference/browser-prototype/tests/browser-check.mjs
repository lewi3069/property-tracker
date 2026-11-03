// Optional browser QA: provide an installed Playwright package via FIELDVIEW_PLAYWRIGHT.
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { mkdir, readFile } from 'node:fs/promises';
const require = createRequire(import.meta.url);
const { chromium } = require(process.env.FIELDVIEW_PLAYWRIGHT || 'playwright');
const browser = await chromium.launch(process.env.FIELDVIEW_BROWSER
  ? { executablePath: process.env.FIELDVIEW_BROWSER, headless: true }
  : { channel: 'msedge', headless: true });
const page = await browser.newPage({ viewport: { width: 1600, height: 1040 }, deviceScaleFactor: 1 });
const errors = [];
page.on('pageerror', error => errors.push(error.message));
page.on('console', message => { if (message.type() === 'error') errors.push(message.text()); });
await mkdir('test-results', { recursive: true });

try {
  // Serve fixtures in process so browser QA needs no network or running server.
  const fixtures = new Map([
    ['/', ['../index.html', 'text/html']],
    ['/src/styles.css', ['../src/styles.css', 'text/css']],
    ['/src/app.mjs', ['../src/app.mjs', 'text/javascript']],
    ['/src/config.mjs', ['../src/config.mjs', 'text/javascript']],
    ['/src/view-controller.mjs', ['../src/view-controller.mjs', 'text/javascript']],
  ]);
  await page.route('**/*', async route => {
    const fixture = fixtures.get(new URL(route.request().url()).pathname);
    if (!fixture) return route.fulfill({ status: 404, body: 'Not found' });
    return route.fulfill({ contentType: fixture[1], body: await readFile(new URL(fixture[0], import.meta.url)) });
  });
  await page.clock.install({ time: new Date('2026-11-02T15:30:00Z') });
  await page.goto('http://fieldview.test');
  const layout = () => page.locator('#camera-stage').getAttribute('data-layout');
  const featured = () => page.locator('.camera-tile.featured').evaluateAll(elements => elements.map(el => el.dataset.camera));
  const sendMotion = async id => { await page.locator('#activity-camera').selectOption(id); await page.locator('#send-motion').click(); };
  assert.equal(await layout(), 'grid');
  assert.equal(await page.locator('.camera-tile:visible').count(), 4);
  await page.screenshot({ path: 'test-results/overview.png', fullPage: true });

  await sendMotion('barn');
  assert.equal(await layout(), 'single');
  assert.deepEqual(await featured(), ['barn']);
  await page.screenshot({ path: 'test-results/activity.png', fullPage: true });
  await page.locator('[data-camera="barn"]').click();
  await sendMotion('pasture');
  assert.equal(await layout(), 'full');
  assert.equal(await page.locator('.camera-tile:visible').count(), 1);
  await page.locator('#auto-mode').click();
  assert.equal(await layout(), 'dual');

  await page.locator('#clear-events').click();
  await page.locator('#zone').selectOption('pasture');
  await page.locator('#send-location').click();
  assert.equal(await layout(), 'grid');
  await page.clock.fastForward(5100);
  assert.equal(await layout(), 'single');
  assert.deepEqual(await featured(), ['pasture']);
  await sendMotion('barn');
  assert.deepEqual(await featured(), ['barn']);
  await sendMotion('yard');
  await page.clock.fastForward(8100);
  assert.equal(await layout(), 'dual');
  assert.equal((await featured()).length, 2);
  await page.clock.fastForward(31000);
  assert.deepEqual(await featured(), ['pasture']);

  await page.locator('#clear-events').click();
  await page.locator('[data-camera="barn"]').focus();
  await page.keyboard.press('ArrowRight');
  assert.equal(await page.locator(':focus').getAttribute('data-camera'), 'yard');
  await page.keyboard.press('Enter');
  assert.equal(await layout(), 'full');
  await page.keyboard.press('Escape');
  assert.equal(await layout(), 'grid');
  await page.locator('#grid-mode').click();
  await sendMotion('barn');
  assert.equal(await layout(), 'grid');

  await page.locator('#clear-events').click();
  await page.locator('#display-profile').selectOption('large');
  assert.equal(await page.locator('.camera-tile:visible').count(), 9);
  await page.screenshot({ path: 'test-results/large-screen.png', fullPage: true });
  await page.locator('#tile-count').selectOption('6');
  assert.equal(await page.locator('.camera-tile:visible').count(), 6);
  await sendMotion('house');
  assert.deepEqual(await featured(), ['house']);
  assert.equal(await page.locator('[data-camera="house"]').isVisible(), true);
  assert.equal(await page.locator('.camera-tile:visible').count(), 6);
  await page.locator('#display-profile').selectOption('compact');
  assert.equal(await page.locator('.camera-tile:visible').count(), 1);
  assert.deepEqual(await featured(), ['house']);
  await page.locator('#display-profile').selectOption('large');
  assert.equal(await page.locator('#tile-count').inputValue(), '6');
  await page.reload();
  assert.equal(await page.locator('#display-profile').inputValue(), 'large');
  assert.equal(await page.locator('.camera-tile:visible').count(), 6);
  await page.locator('#display-profile').selectOption('office');
  assert.equal(await page.locator('#tile-count').inputValue(), '4');
  for (const count of ['1', '2', '4', '6', '9']) {
    await page.locator('#tile-count').selectOption(count);
    await page.locator('#clear-events').click();
    await sendMotion('house');
    await sendMotion('yard');
    await page.clock.fastForward(8100);
    const bounds = await page.locator('#camera-stage').boundingBox();
    for (const tile of await page.locator('.camera-tile:visible').all()) {
      const rect = await tile.boundingBox();
      assert.ok(rect.width > 0 && rect.height > 0);
      assert.ok(rect.x >= bounds.x - 1 && rect.y >= bounds.y - 1);
      assert.ok(rect.x + rect.width <= bounds.x + bounds.width + 1);
      assert.ok(rect.y + rect.height <= bounds.y + bounds.height + 1);
    }
    assert.equal(await page.locator('.camera-tile:visible').count(), Number(count));
  }
  await page.locator('#tile-count').selectOption('4');

  await page.setViewportSize({ width: 390, height: 844 });
  await page.locator('#clear-events').click();
  await page.screenshot({ path: 'test-results/mobile.png', fullPage: true });
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), true);
  assert.deepEqual(errors, []);
  console.log('Browser checks passed: view switching, keyboard, saved display profiles, 1/2/4/6/9 tile bounds, hidden camera promotion, mobile, no runtime errors.');
} finally {
  await browser.close();
}
