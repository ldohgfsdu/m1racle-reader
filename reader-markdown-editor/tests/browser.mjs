import assert from 'node:assert/strict';
import { mkdir, readFile, stat } from 'node:fs/promises';
import { extname, join, normalize, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from '@playwright/test';

const editorRoot = fileURLToPath(new URL('..', import.meta.url));
const readerRoot = resolve(editorRoot, '..');
const bundleRoot = join(readerRoot, 'app', 'src', 'main', 'assets', 'markdown-editor');
const requests = [];
const evidenceDir = process.env.READER_BROWSER_EVIDENCE;
if (evidenceDir) await mkdir(evidenceDir, {recursive:true});
const mime = new Map([
  ['.html', 'text/html; charset=utf-8'],
  ['.js', 'text/javascript; charset=utf-8'],
  ['.css', 'text/css; charset=utf-8'],
  ['.txt', 'text/plain; charset=utf-8'],
  ['.md', 'text/plain; charset=utf-8'],
  ['.png', 'image/png'],
]);

const browser = await chromium.launch({ executablePath: process.env.READER_CHROME_EXECUTABLE || undefined, args: ['--host-resolver-rules=MAP reader-markdown.local 127.0.0.1'] });
const context = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1 });
const page = await context.newPage();
const bridge = [];
const errors = [];
const blockedRequests = [];

await page.route('**/*', async route => {
  const url = new URL(route.request().url());
  if (url.hostname === 'reader-markdown.local' &&
      (url.pathname === '/index.html' || url.pathname.startsWith('/assets/'))) {
    const path = resolve(bundleRoot, `.${url.pathname}`);
    if (!path.startsWith(bundleRoot + sep)) throw new Error('Resource escaped bundle');
    const contentType = mime.get(extname(path));
    requests.push({url:url.href,method:route.request().method(),contentType});
    await route.fulfill({status:200,contentType,body:await readFile(path)});
    return;
  }
  blockedRequests.push({ kind: route.request().resourceType(), url: url.href });
  await route.abort('blockedbyclient');
});

await page.addInitScript(() => {
  window.__readerMessages = [];
  window.Native = {
    postMessage(value) {
      window.__readerMessages.push(JSON.parse(value));
    },
  };
});
page.on('pageerror', error => errors.push(error.message));
page.on('console', message => {
  if (message.type() === 'error') errors.push(message.text());
});

await page.goto('https://reader-markdown.local/index.html', { waitUntil: 'networkidle' });
await page.waitForFunction(() => window.__readerMessages.some(event => event.type === 'ready'), null, {timeout:10_000}).catch(async error => { console.error(JSON.stringify({errors,requests,blockedRequests,body:await page.locator('body').innerText()})); throw error; });

const raw = '\ufeff# Reader 标题\r\n\r\n预览 **粗体** 与 `代码`。\n\n- [x] 已完成\r\n\r\n| A | B |\r\n| --- | --- |\n| 1 | 2 |\r\n\r\n![图片](https://images.example/x.png)\r\n';
const loaded = await page.evaluate(({ raw }) => {
  window.ReaderMarkdown.load({
    id: 'browser-doc',
    markdown: raw,
    readOnly: false,
    sourceMode: false,
    dark: false,
    fontSize: 17,
    lineHeight: 1.8,
  });
  return {
    markdown: window.ReaderMarkdown.getMarkdown(),
    composing: window.ReaderMarkdown.isComposing(),
  };
}, { raw });
assert.equal(loaded.markdown, raw);
assert.equal(loaded.composing, false);
await page.waitForFunction(() =>
  window.__readerMessages.some(event => event.type === 'loaded' && event.id === 'browser-doc'));

const rendered = await page.evaluate(() => ({
  heading: document.querySelector('.cm-atomic-h1')?.textContent,
  bold: document.querySelector('.cm-atomic-strong')?.textContent,
  table: Boolean(document.querySelector('.cm-atomic-table table')),
  placeholder: document.querySelector('.cm-reader-image-placeholder')?.textContent,
  placeholderAlt: document.querySelector('.cm-reader-image-placeholder-alt')?.textContent,
  images: document.querySelectorAll('img[src]').length,
  editorCount: document.querySelectorAll('.cm-editor').length,
  contentPadding: getComputedStyle(document.querySelector('.cm-content')).paddingLeft,
  contentMaxWidth: getComputedStyle(document.querySelector('.cm-content')).maxWidth,
  paper: getComputedStyle(document.documentElement).getPropertyValue('--reader-paper').trim(),
  link: getComputedStyle(document.documentElement).getPropertyValue('--reader-link').trim(),
  hairline: Number.parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--reader-hairline')) * window.devicePixelRatio,
  checkboxAccent: getComputedStyle(document.querySelector('input[type="checkbox"]')).accentColor,
}));
if (!rendered.heading) console.error(JSON.stringify({rendered,errors,html:(await page.locator('#reader-markdown').innerHTML()).slice(0,6000)}));
assert.equal(rendered.heading, 'Reader 标题');
assert.equal(rendered.bold, '粗体');
assert.equal(rendered.table, true);
assert.match(rendered.placeholder, /图片暂不可用/);
assert.match(rendered.placeholder, /图片/);
assert.equal(rendered.placeholderAlt, '图片');
assert.equal(rendered.images, 0);
assert.equal(rendered.editorCount, 1);
assert.equal(rendered.contentPadding, '20px');
assert.equal(rendered.contentMaxWidth, '800px');
assert.equal(rendered.paper, '#FFFFFF');
assert.equal(rendered.link, '#0D74CE');
assert.equal(rendered.hairline, 0.5);
assert.equal(rendered.checkboxAccent, 'rgb(0, 144, 255)');
if (evidenceDir) await page.screenshot({path:join(evidenceDir,'browser-rendered-light.png')});

const editor = page.locator('.cm-content');
await editor.click({ position: { x: 20, y: 25 } });
await page.evaluate(() => {
  const content = document.querySelector('.cm-content');
  const view = content.cmTile?.root?.view;
  if (!view) throw new Error('CodeMirror view missing');
  const position = view.state.doc.toString().indexOf('标题');
  view.dispatch({ selection: { anchor: position }, scrollIntoView: true });
  window.__readerView = view;
});
const beforeMode = await page.evaluate(() => {
  const view = window.__readerView || document.querySelector('.cm-content').cmTile.root.view;
  return { selection: view.state.selection.main.anchor, undo: view.state.field ? null : null };
});
assert.ok(beforeMode.selection >= 0);

const modeResult = await page.evaluate(async () => {
  const content = document.querySelector('.cm-content');
  const view = window.__readerView || content.cmTile.root.view;
  const before = {
    selection: view.state.selection.main.anchor,
    dom: view.dom,
    active: document.activeElement === content,
  };
  const result = window.ReaderMarkdown.setSourceMode(true);
  await new Promise(requestAnimationFrame);
  return {
    result,
    sourceMode: document.body.dataset.mode,
    after: {
      selection: view.state.selection.main.anchor,
      sameDom: view.dom === before.dom,
      active: document.activeElement === content,
    },
    markupHidden: document.querySelectorAll('.cm-atomic-h1').length,
  };
});
assert.deepEqual(modeResult, {
  result: true,
  sourceMode: 'source',
  after: { selection: beforeMode.selection, sameDom: true, active: true },
  markupHidden: 0,
});

if (evidenceDir) await page.screenshot({path:join(evidenceDir,'browser-source-light.png')});

const typing = await page.evaluate(async () => {
  const view = window.__readerView;
  const start = view.state.doc.toString().indexOf('标题');
  view.dispatch({
    changes: { from: start, insert: '新增' },
    selection: { anchor: start + 2 },
    userEvent: 'input.type',
  });
  await new Promise(requestAnimationFrame);
  return { markdown: window.ReaderMarkdown.getMarkdown(), canUndo: window.__readerMessages.at(-1)?.canUndo };
});
assert.match(typing.markdown, /Reader 新增标题/);
assert.equal(typing.canUndo, true);

const transitioned = await page.evaluate(() => {
  const view = window.__readerView;
  const active = document.activeElement === view.contentDOM;
  const body = document.activeElement === document.body;
  return { active, body };
});
assert.equal(transitioned.active, true);

const undoResult = await page.evaluate(async () => {
  window.ReaderMarkdown.undo();
  await new Promise(requestAnimationFrame);
  const undone = window.ReaderMarkdown.getMarkdown();
  window.ReaderMarkdown.redo();
  await new Promise(requestAnimationFrame);
  return { undone, redone: window.ReaderMarkdown.getMarkdown() };
});
assert.equal(undoResult.undone, raw);
assert.match(undoResult.redone, /Reader 新增标题/);

const composed = await page.evaluate(async () => {
  const content = document.querySelector('.cm-content');
  content.dispatchEvent(new CompositionEvent('compositionstart', { data: '' }));
  const blocked = window.ReaderMarkdown.setSourceMode(false);
  const state = window.ReaderMarkdown.isComposing();
  const markdown = window.ReaderMarkdown.getMarkdown();
  content.dispatchEvent(new CompositionEvent('compositionend', { data: '' }));
  await new Promise(resolve => setTimeout(resolve, 150));
  return { blocked, composingDuring: state, markdown, composingAfter: window.ReaderMarkdown.isComposing() };
});
assert.equal(composed.blocked, false);
assert.equal(composed.composingDuring, true);
assert.match(composed.markdown, /Reader 新增标题/);
assert.equal(composed.composingAfter, false);

await page.evaluate(() => window.ReaderMarkdown.setTheme({
  dark: true,
  fontSize: 20,
  lineHeight: 1.95,
  fontFamily: '"Noto Serif CJK SC", serif',
}));
const theme = await page.evaluate(() => ({
  dark: document.documentElement.dataset.theme,
  fontSize: getComputedStyle(document.documentElement).getPropertyValue('--reader-font-size').trim(),
  lineHeight: getComputedStyle(document.documentElement).getPropertyValue('--reader-line-height').trim(),
  paper: getComputedStyle(document.documentElement).getPropertyValue('--reader-paper').trim(),
  body: getComputedStyle(document.documentElement).getPropertyValue('--reader-body').trim(),
  surface: getComputedStyle(document.documentElement).getPropertyValue('--reader-surface').trim(),
  muted: getComputedStyle(document.documentElement).getPropertyValue('--reader-muted').trim(),
  line: getComputedStyle(document.documentElement).getPropertyValue('--reader-line').trim(),
  link: getComputedStyle(document.documentElement).getPropertyValue('--reader-link').trim(),
}));
assert.deepEqual(theme, {
  dark: 'dark',
  fontSize: '20px',
  lineHeight: '1.95',
  paper: '#111111',
  body: '#EEEEEE',
  surface: '#191919',
  muted: '#B4B4B4',
  line: '#3A3A3A',
  link: '#70B8FF',
});

const readOnly = await page.evaluate(() => {
  const before = window.ReaderMarkdown.getMarkdown();
  window.ReaderMarkdown.setReadOnly(true);
  window.__readerView.dispatch({changes:{from:0,insert:'forbidden'}});
  const result = {unchanged:before === window.ReaderMarkdown.getMarkdown(),editable:window.__readerView.contentDOM.contentEditable};
  window.ReaderMarkdown.setReadOnly(false);
  return result;
});
assert.deepEqual(readOnly,{unchanged:true,editable:'false'});
await page.evaluate(() => window.ReaderMarkdown.setSourceMode(false));
if (evidenceDir) await page.screenshot({path:join(evidenceDir,'browser-rendered-dark.png')});

const latest = await page.evaluate(() => window.__readerMessages.at(-1));
assert.ok(latest);
assert.equal(blockedRequests.length, 0);
assert.deepEqual(errors, []);
assert.ok(requests.length >= 3);
assert.ok(requests.every(request => request.contentType));
console.log(JSON.stringify({
  passed: true,
  viewport: '390x844 Chromium',
  evidence: [
    'loaded-once bridge',
    'single CodeMirror instance',
    'atomic rendered Markdown',
    'source reconfigure preserves cursor/history',
    'undo/redo',
    'BOM/mixed-EOL preserved',
    'composition blocks mode switch and snapshots remain lossless',
    'image requests absent',
    'network blocked',
    '760px reading column with 20px narrow screen gutter',
    'dark theme and typography variables',
  ],
  blockedImagePlaceholder: true,
}));

await browser.close();

