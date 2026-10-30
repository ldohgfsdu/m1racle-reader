import test from 'node:test';
import assert from 'node:assert/strict';
import { EditorState } from '@codemirror/state';
import { history, redo, undo, undoDepth } from '@codemirror/commands';
import { SourceDocument } from '../src/source.mjs';
import { createSourceHistory } from '../src/source-history.mjs';
function fixture(raw) {
  const source = new SourceDocument(raw);
  const { rawField, extensions } = createSourceHistory(() => source);
  let state = EditorState.create({ doc: source.normalized(), extensions: [history(), ...extensions] });
  return {
    source,
    get state() { return state; },
    dispatch(spec) {
      const transaction = spec.startState ? spec : state.update(spec);
      state = transaction.state;
      source.raw = state.field(rawField);
      assert.equal(source.normalized(), state.doc.toString());
    },
  };
}

test('CodeMirror undo and redo preserve BOM and every untouched newline byte', () => {
  const raw = '\ufeff第一行\r\n第二行\n第三行\r第四行\r\n';
  const editor = fixture(raw);
  const position = editor.state.doc.toString().indexOf('第二行') + 1;
  const offset = editor.source.offset(position);
  editor.dispatch({ changes: { from: position, insert: '新增😺' }, userEvent: 'input.type' });
  const edited = raw.slice(0, offset) + '新增😺' + raw.slice(offset);
  assert.equal(editor.source.raw, edited);
  assert.ok(undo(editor));
  assert.equal(editor.source.raw, raw);
  assert.ok(redo(editor));
  assert.equal(editor.source.raw, edited);
});

test('a composed replacement is one undo group and restores mixed endings', () => {
  const raw = '\ufeff甲\r\n乙\n丙\r丁\r\n';
  const editor = fixture(raw);
  const at = editor.state.doc.toString().indexOf('乙') + 1;
  editor.dispatch({ changes: { from: at, to: at + 1, insert: 'n' }, userEvent: 'input.type.compose.start' });
  editor.dispatch({ changes: { from: at, to: at + 1, insert: '你好' }, userEvent: 'input.type.compose' });
  assert.equal(undoDepth(editor.state), 1);
  const edited = editor.source.raw;
  assert.ok(undo(editor));
  assert.equal(editor.source.raw, raw);
  assert.ok(redo(editor));
  assert.equal(editor.source.raw, edited);
});
