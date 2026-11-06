"""Render the same policy shipped in the app as a simple public HTML page."""
from pathlib import Path
from html import escape
ROOT = Path(__file__).resolve().parents[1]
policy = (ROOT / 'mobile/src/main/assets/privacy.txt').read_text(encoding='utf-8')
blocks = policy.strip().split('\n\n')
parts = ['<h1>' + escape(blocks[0].split('\n')[0]) + '</h1>', '<p>' + escape(blocks[0].split('\n')[1]) + '</p>']
for block in blocks[1:]:
    if '\n' not in block:
        parts.append('<p>' + escape(block) + '</p>')
        continue
    heading, body = block.split('\n', 1)
    parts += ['<h2>' + escape(heading) + '</h2>', '<p>' + escape(body) + '</p>']
document = '''<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Property Tracker Privacy Policy</title><style>body{max-width:760px;margin:40px auto;padding:0 20px;font:17px/1.6 system-ui,sans-serif;color:#17211c;background:#f7faf6}h1,h2{line-height:1.25}h2{font-size:1.25em;margin-top:1.8em}p{overflow-wrap:anywhere}</style></head><body>''' + '\n'.join(parts) + '</body></html>\n'
(ROOT / 'docs/privacy.html').write_text(document, encoding='utf-8')
print('Generated docs/privacy.html from the in-app policy.')
