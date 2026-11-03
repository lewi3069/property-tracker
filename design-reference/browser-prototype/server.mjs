import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const files = new Map([
  ['/', ['index.html', 'text/html; charset=utf-8']],
  ['/index.html', ['index.html', 'text/html; charset=utf-8']],
  ['/src/styles.css', ['src/styles.css', 'text/css; charset=utf-8']],
  ['/src/app.mjs', ['src/app.mjs', 'text/javascript; charset=utf-8']],
  ['/src/config.mjs', ['src/config.mjs', 'text/javascript; charset=utf-8']],
  ['/src/view-controller.mjs', ['src/view-controller.mjs', 'text/javascript; charset=utf-8']],
]);
const port = Number(process.env.FIELDVIEW_PORT || 4173);
if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Invalid FIELDVIEW_PORT');

createServer(async (req, res) => {
  const file = files.get(new URL(req.url, 'http://localhost').pathname);
  if (!file || !['GET', 'HEAD'].includes(req.method)) {
    res.writeHead(404).end('Not found');
    return;
  }
  try {
    const content = await readFile(fileURLToPath(new URL(file[0], import.meta.url)));
    res.writeHead(200, {
      'Content-Type': file[1],
      'Cache-Control': 'no-store',
      'X-Content-Type-Options': 'nosniff',
      'Content-Security-Policy': "default-src 'self'; style-src 'self'; script-src 'self'; img-src 'self' data:; connect-src 'none'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'",
    });
    res.end(req.method === 'HEAD' ? undefined : content);
  } catch {
    res.writeHead(500).end('Unable to read prototype file');
  }
}).listen(port, '127.0.0.1', () => {
  console.log(`Fieldview prototype: http://127.0.0.1:${port}`);
});
