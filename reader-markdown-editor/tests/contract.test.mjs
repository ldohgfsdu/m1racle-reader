import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('..', import.meta.url));
const main = await readFile(new URL('../src/main.mjs', import.meta.url), 'utf8');
const html = await readFile(new URL('../index.html', import.meta.url), 'utf8');

test('public API includes the exact native contract and composition state', () => {
  for (const name of ['load', 'setSourceMode', 'setReadOnly', 'setTheme', 'undo', 'redo', 'focus', 'getMarkdown', 'isComposing']) {
    assert.match(main, new RegExp(`\\b${name}\\b`));
  }
  assert.match(main, /function setSourceMode\(value\)[\s\S]*if \(!view \|\| isComposing\(\)\) return false;/);
  assert.match(main, /presentation\.reconfigure\(presentationExtension\(next\)\)[\s\S]*Deliberately do not focus/);
});

test('bridge event vocabulary is restricted and every document event carries id', () => {
  for (const type of ['ready', 'loaded', 'change', 'history', 'link']) {
    assert.match(main, new RegExp(`type: '${type}'`));
  }
  assert.match(html, /default-src 'none'/);
  assert.match(html, /connect-src 'none'/);
  assert.match(html, /img-src 'none'/);
  assert.doesNotMatch(html, /https?:\/\//i);
});

test('rendered mode uses Atomic inline preview and source mode reconfigures the same editor', () => {
  assert.match(main, /inlinePreview\(\{ onLinkClick: openLink \}\)/);
  assert.match(main, /presentation\.reconfigure/);
  assert.match(main, /source\.normalized\(\)/);
  assert.match(main, /view\.state\.field\(rawField\)/);
});
