import { createHash } from 'node:crypto';
import { readFile, readdir } from 'node:fs/promises';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const editorRoot = fileURLToPath(new URL('..', import.meta.url));
const readerRoot = join(editorRoot, '..');
const target = join(readerRoot, 'app', 'src', 'main', 'assets', 'markdown-editor');
const manifest = JSON.parse(await readFile(join(target, 'build-manifest.json'), 'utf8'));
const failures = [];

for (const item of manifest.files) {
  const path = join(target, ...item.path.split('/'));
  try {
    const data = await readFile(path);
    const hash = createHash('sha256').update(data).digest('hex');
    if (data.byteLength !== item.bytes) failures.push(`${item.path}: size mismatch`);
    if (hash !== item.sha256) failures.push(`${item.path}: sha256 mismatch`);
  } catch {
    failures.push(`${item.path}: missing`);
  }
}

const index = await readFile(join(target, 'index.html'), 'utf8');
if (!index.includes("default-src 'none'")) failures.push('CSP default-src is not none');
if (!index.includes("connect-src 'none'")) failures.push('CSP connect-src is not none');
if (!index.includes("img-src 'none'")) failures.push('CSP img-src is not none');
if (manifest.origin !== 'https://reader-markdown.local/') failures.push('origin mismatch');
if (manifest.files.some(item => /\.(?:woff2?|ttf|otf)$/i.test(item.path))) failures.push('unexpected embedded font asset');

const scriptPath = manifest.files.find(item => /^assets\/app-.*\.js$/.test(item.path));
const cssPath = manifest.files.find(item => /^assets\/.*\.css$/.test(item.path));
if (!scriptPath || !cssPath) failures.push('expected one hashed script and stylesheet');
if (scriptPath) {
  const script = await readFile(join(target, ...scriptPath.path.split('/')), 'utf8');
  for (const marker of ['ReaderMarkdown', 'ready', 'loaded', 'change', 'history', 'link']) {
    if (!script.includes(marker)) failures.push(`bundle missing marker ${marker}`);
  }
  if (/fetch\s*\(|XMLHttpRequest|new\s+WebSocket|sendBeacon/.test(script)) failures.push('bundle contains a network API');
  if (/file:\/\/|__READER_PREVIEW_RUNTIME__/.test(script)) failures.push('bundle contains an unresolved build-machine runtime import');
}
if (cssPath) {
  const css = (await readFile(join(target, ...cssPath.path.split('/')), 'utf8'))
    .toLowerCase().replaceAll(/\s+/g, '');
  for (const marker of [
    'max-width:800px',
    'padding:28px 20px',
    'overflow-x:auto',
    '--radix-blue-9:#0090ff',
    '--radix-gray-12:#202020',
    '--reader-paper:var(--radix-white)',
    '--reader-body:var(--radix-gray-12)',
    '--reader-surface:var(--radix-gray-2)',
    '--reader-line:var(--radix-gray-6)',
    '--reader-link:var(--radix-blue-11)',
    '--reader-paper:var(--radix-gray-dark-1)',
    '--reader-body:var(--radix-gray-dark-12)',
    '--reader-surface:var(--radix-gray-dark-2)',
    '--reader-line:var(--radix-gray-dark-6)',
    '--reader-link:var(--radix-blue-dark-11)',
  ]) {
    if (!css.replaceAll(' ', '').includes(marker.replaceAll(' ', ''))) failures.push(`stylesheet missing ${marker}`);
  }
}
const entries = (await readdir(join(target, 'assets'))).sort();
if (entries.some(name => !/\.(?:js|css)$/.test(name))) failures.push(`unexpected runtime asset: ${entries.join(', ')}`);
if (failures.length) {
  console.error(failures.join('\n'));
  process.exit(1);
}
console.log(`Verified ${manifest.files.length} bundle files and the ${manifest.origin} contract`);
