import test from 'node:test';
import assert from 'node:assert/strict';
import { SourceDocument } from '../src/source.mjs';

test('no-edit source is byte-identical, including BOM and mixed newlines', () => {
  const raw = '\ufeff第一行\r\nsecond\n第三行\r最后一行';
  const source = new SourceDocument(raw);
  assert.equal(source.raw, raw);
  assert.equal(source.normalized(), '第一行\nsecond\n第三行\n最后一行');
  assert.equal(source.offset(0), 1);
});

test('one edit maps through mixed EOL without rewriting untouched source', () => {
  const raw = '\ufeffA\r\nB\nC\rD';
  const source = new SourceDocument(raw);
  const position = source.normalized().indexOf('C') + 1;
  const offset = source.offset(position);
  source.apply([{ from: position, to: position, insert: '新增' }]);
  assert.equal(source.raw, raw.slice(0, offset) + '新增' + raw.slice(offset));
});

test('multi-range edits use old source coordinates and preserve adjacent CR-LF bytes', () => {
  const source = new SourceDocument('a\r\nb\nc\rd');
  source.apply([
    { from: 0, to: 1, insert: 'A' },
    { from: 4, to: 5, insert: 'C' },
  ]);
  assert.equal(source.raw, 'A\r\nb\nC\rd');
  const boundary = new SourceDocument('a\rb\nc');
  boundary.apply([{ from: 2, to: 3, insert: '' }]);
  assert.equal(boundary.normalized(), 'a\n\nc');
  assert.equal(boundary.raw, 'a\r\r\nc');
});


test('visible first-character edits keep the encoding BOM and map CRLF positions', () => {
  const source = new SourceDocument('\ufeff# 标题\r\n正文');
  source.apply([{from:0,to:1,insert:'##'}]);
  assert.equal(source.raw, '\ufeff## 标题\r\n正文');
  source.apply([{from:0,to:source.normalized().length,insert:'新正文'}]);
  assert.equal(source.raw, '\ufeff新正文');
});
