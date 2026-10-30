import {
  atomicEditorTheme,
  atomicMarkdownSyntax,
  highlightMarkdown,
  inlinePreview,
  readOnlyExtension,
  tables,
} from '@atomic-editor/editor';
import { drawSelection, EditorView, keymap } from '@codemirror/view';
import { Compartment, EditorState } from '@codemirror/state';
import {
  defaultKeymap,
  history,
  historyKeymap,
  redo,
  redoDepth,
  undo,
  undoDepth,
} from '@codemirror/commands';
import { markdown, markdownKeymap, markdownLanguage } from '@codemirror/lang-markdown';
import { search, searchKeymap } from '@codemirror/search';
import { SourceDocument } from './source.mjs';
import { createSourceHistory } from './source-history.mjs';
import { editorPhrases } from './phrases.mjs';
import './style.css';

const mount = document.getElementById('reader-markdown');
const fallback = document.getElementById('fallback');
const root = document.documentElement;
const editable = new Compartment();
const presentation = new Compartment();
const theme = new Compartment();
const sourceHistory = createSourceHistory(() => source);
const rawField = sourceHistory.rawField;

let view = null;
let source = new SourceDocument('');
let documentId = '';
let sourceMode = false;
let readOnly = false;
let loaded = false;
let composing = false;
let compositionEndedAt = -Infinity;
let lastCanUndo = false;
let lastCanRedo = false;
let loadToken = 0;

function send(event) {
  try {
    window.Native?.postMessage(JSON.stringify(event));
  } catch (error) {
    console.error('ReaderMarkdown bridge failed', error);
  }
}

function currentHistory() {
  if (!view) return { canUndo: false, canRedo: false };
  return {
    canUndo: undoDepth(view.state) > 0,
    canRedo: redoDepth(view.state) > 0,
  };
}

function emitHistory(force = false) {
  if (!loaded || !view) return;
  const { canUndo, canRedo } = currentHistory();
  if (!force && canUndo === lastCanUndo && canRedo === lastCanRedo) return;
  lastCanUndo = canUndo;
  lastCanRedo = canRedo;
  send({ type: 'history', id: documentId, canUndo, canRedo });
}

function isComposing() {
  // compositionStarted covers Android's pre-change composition state; the
  // short ending grace closes the mutation-observer gap after compositionend.
  return composing || Boolean(view?.composing || view?.compositionStarted) ||
    performance.now() - compositionEndedAt < 120;
}

function validLink(value) {
  try {
    const url = new URL(String(value));
    return ['http:', 'https:', 'mailto:'].includes(url.protocol) ? url.href : null;
  } catch {
    return null;
  }
}

function openLink(value) {
  const url = validLink(value);
  if (url && loaded) send({ type: 'link', id: documentId, url });
}

function presentationExtension(mode = sourceMode) {
  if (mode) return [];
  return [
    inlinePreview({ onLinkClick: openLink }),
    tables({ onLinkClick: openLink }),
  ];
}

function editableExtension(locked = readOnly) {
  return readOnlyExtension(locked);
}

function safeFontFamily(value) {
  if (typeof value !== 'string') return null;
  const family = value.trim();
  if (!family || family.length > 200 || /[;{}]|url\s*\(/i.test(family)) return null;
  return family;
}

function finiteInRange(value, minimum, maximum, fallback) {
  const number = Number(value);
  return Number.isFinite(number) ? Math.min(maximum, Math.max(minimum, number)) : fallback;
}

function setTheme(options = {}) {
  const dark = Boolean(options.dark);
  root.dataset.theme = dark ? 'dark' : 'light';
  root.style.colorScheme = dark ? 'dark' : 'light';
  root.style.setProperty('--reader-hairline', `${0.5 / (window.devicePixelRatio || 1)}px`);
  const fontSize = finiteInRange(options.fontSize, 12, 32, 17);
  const lineHeight = finiteInRange(options.lineHeight, 1.2, 2.4, 1.8);
  const fontFamily = safeFontFamily(options.fontFamily);
  root.style.setProperty('--reader-font-size', `${fontSize}px`);
  root.style.setProperty('--reader-line-height', String(lineHeight));
  if (fontFamily) root.style.setProperty('--reader-font-family', fontFamily);
  else root.style.removeProperty('--reader-font-family');
  view?.dispatch({ effects: theme.reconfigure(themeExtension()) });
}

function themeExtension() {
  return EditorView.theme({
    '.cm-scroller': {
      fontFamily: 'var(--reader-font-family)',
      fontSize: 'var(--reader-font-size)',
      lineHeight: 'var(--reader-line-height)',
    },
    '.cm-content': {
      fontFamily: 'var(--reader-font-family)',
      fontSize: 'var(--reader-font-size)',
      lineHeight: 'var(--reader-line-height)',
    },
  });
}

function observeComposition(editor) {
  const content = editor.contentDOM;
  content.addEventListener('compositionstart', () => {
    composing = true;
    document.body.dataset.composing = 'true';
  });
  content.addEventListener('compositionupdate', () => {
    composing = true;
    document.body.dataset.composing = 'true';
  });
  content.addEventListener('compositionend', () => {
    composing = false;
    compositionEndedAt = performance.now();
    document.body.dataset.composing = 'false';
  });
}

function updates(viewUpdate) {
  view = viewUpdate.view;
  const nextRaw = viewUpdate.state.field(rawField);
  if (nextRaw !== source.raw) {
    source.raw = nextRaw;
    if (loaded) {
      const { canUndo, canRedo } = currentHistory();
      lastCanUndo = canUndo;
      lastCanRedo = canRedo;
      send({ type: 'change', id: documentId, markdown: nextRaw, canUndo, canRedo });
    }
  }
  if (viewUpdate.docChanged || viewUpdate.transactions.some(transaction => transaction.reconfigured)) {
    emitHistory();
  }
}

function buildExtensions() {
  return [
    history(),
    drawSelection(),
    EditorView.lineWrapping,
    EditorView.contentAttributes.of({
      'aria-label': 'Markdown 编辑区',
      autocapitalize: 'sentences',
      spellcheck: 'true',
    }),
    EditorState.phrases.of(editorPhrases),
    EditorState.allowMultipleSelections.of(true),
    EditorState.transactionFilter.of(transaction =>
      transaction.docChanged && transaction.startState.readOnly ? [] : transaction),
    markdown({ base: markdownLanguage, extensions: highlightMarkdown }),
    atomicEditorTheme,
    atomicMarkdownSyntax,
    search(),
    keymap.of([...historyKeymap, ...searchKeymap, ...markdownKeymap, ...defaultKeymap]),
    editable.of(editableExtension()),
    presentation.of(presentationExtension()),
    theme.of(themeExtension()),
    ...sourceHistory.extensions,
    EditorView.updateListener.of(updates),
  ];
}

function load(options) {
  if (!options || typeof options !== 'object') throw new TypeError('ReaderMarkdown.load requires an object');
  if (typeof options.id !== 'string' || !options.id) throw new TypeError('ReaderMarkdown.load requires id');
  if (typeof options.markdown !== 'string') throw new TypeError('ReaderMarkdown.load requires markdown');
  const token = ++loadToken;
  if (view) {
    view.destroy();
    view = null;
  }
  documentId = options.id;
  source = new SourceDocument(options.markdown);
  sourceMode = Boolean(options.sourceMode);
  readOnly = Boolean(options.readOnly);
  loaded = false;
  lastCanUndo = false;
  lastCanRedo = false;
  setTheme(options);
  document.body.dataset.mode = sourceMode ? 'source' : 'rendered';
  document.body.dataset.readOnly = readOnly ? 'true' : 'false';
  document.body.classList.remove('is-loading');
  fallback?.remove();
  view = new EditorView({
    parent: mount,
    state: EditorState.create({
      doc: source.normalized(),
      extensions: buildExtensions(),
    }),
  });
  observeComposition(view);
  loaded = true;
  emitHistory(true);
  requestAnimationFrame(() => requestAnimationFrame(() => {
    if (token === loadToken && view) {
      const line = view.state.doc.line(Math.min(view.state.doc.lines, Math.max(1, Math.trunc(Number(options.initialLine) || 1))));
      view.dispatch({ selection: { anchor: line.from }, effects: EditorView.scrollIntoView(line.from, { y: 'start' }) });
      send({ type: 'loaded', id: documentId });
    }
  }));
  return true;
}

function setSourceMode(value) {
  if (!view || isComposing()) return false;
  const next = Boolean(value);
  if (next === sourceMode) {
    document.body.dataset.mode = next ? 'source' : 'rendered';
    return true;
  }
  view.dispatch({ effects: presentation.reconfigure(presentationExtension(next)) });
  sourceMode = next;
  document.body.dataset.mode = next ? 'source' : 'rendered';
  emitHistory();
  // Deliberately do not focus. The native Compose shell owns keyboard intent.
  return true;
}

function setReadOnly(value) {
  if (!view) return;
  readOnly = Boolean(value);
  document.body.dataset.readOnly = readOnly ? 'true' : 'false';
  view.dispatch({ effects: editable.reconfigure(editableExtension(readOnly)) });
}

const api = Object.freeze({
  load,
  setSourceMode,
  setReadOnly,
  setTheme,
  undo() {
    if (view && !isComposing()) undo(view);
  },
  redo() {
    if (view && !isComposing()) redo(view);
  },
  focus() {
    view?.focus();
  },
  getMarkdown() {
    return view ? view.state.field(rawField) : source.raw;
  },
  isComposing,
});

window.ReaderMarkdown = api;
send({ type: 'ready' });
