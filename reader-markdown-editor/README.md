# Reader Markdown editor

This directory is the rebuildable source of the Reader Markdown WebView bundle. The native Compose shell owns navigation, toolbars, files, and persistence. The page owns one CodeMirror 6 instance and exposes `window.ReaderMarkdown`.

## Rebuild

The build script first uses `reader-markdown-editor/node_modules` from this Reader project. If that directory is absent, it can reuse an existing Note dependency tree for this development pass:

```powershell
cd "D:\03_M1racle Reader\reader-audit-fixes-20261024"
node reader-markdown-editor\scripts\build.mjs
node reader-markdown-editor\scripts\verify.mjs
```

For a clean Reader checkout, run `npm ci` inside `reader-markdown-editor/` and then `npm run build`. The dependency closure is captured in this directory's `package-lock.json`; no permanent Note path is required. `M1RACLE_NOTE_ROOT` can optionally point at a compatible existing dependency install. `THIRD_PARTY_NOTICES.md` lists the runtime licenses. A build creates `app/src/main/assets/markdown-editor/` from scratch. No file access, network access, arbitrary URL loading, or Note application UI is bundled.

## Native contract

Load `https://reader-markdown.local/index.html` with JavaScript enabled and a bridge named `Native`. The bundle contains `index.html`, one JavaScript file, one stylesheet, and license notices. It uses platform fonts and downloads no fonts. The origin handler should serve `index.html` and direct `/assets/*` children only, rejecting all other paths and requests.

After the frontend sends `{"type":"ready"}`, call `load` exactly once for each document:

```js
window.ReaderMarkdown.load({
  id: "document-id",
  markdown: rawStoredText,
  readOnly: false,
  sourceMode: false,
  dark: false,
  fontSize: 17,
  lineHeight: 1.8
})
```

`fontSize` is CSS pixels. Native supplies `17 * ReaderSettings.fontScale`; `lineHeight` remains a ratio. `fontFamily` is optional and must be an installed font-family name.

The host may call:

- `setSourceMode(boolean): boolean` switches the existing editor by reconfiguring presentation. It returns `false` while IME composition is active and does not focus the editor or open the keyboard.
- `setReadOnly(boolean)`, `setTheme({dark,fontSize,lineHeight,fontFamily?})`, `undo()`, `redo()`, `focus()`.
- `getMarkdown(): string` returns the current source. During composition it also returns the current edit buffer; the host should use `isComposing()` before save/back and ask the user to finish input.
- `isComposing(): boolean` is `true` while an IME composition has started, including the pending moment just after `compositionend`.

The page sends compact JSON strings through `Native.postMessage`:

```text
{"type":"ready"}
{"type":"loaded","id":"document-id"}
{"type":"change","id":"document-id","markdown":"...","canUndo":true,"canRedo":false}
{"type":"history","id":"document-id","canUndo":true,"canRedo":false}
{"type":"link","id":"document-id","url":"https://example.com/"}
```

`change` and `history` are emitted only for the loaded document. Link URLs reach native for an explicit host-owned open action; the page never navigates.

## Fidelity and security

The shared source layer is copied from M1racle Note. CodeMirror edits are mapped back to the original string, preserving an initial BOM, CRLF, lone CR, and mixed line endings. Undo and redo keep the exact original bytes for untouched regions. Rendered and source modes reconfigure the same editor, so history, selection, and scroll state survive mode changes.

The Compose host must enforce `https://reader-markdown.local/`; this bundle uses relative hashed assets under that origin. It makes no network requests. Because this iteration has no adjacent-file authorization, every Markdown image renders as an inert local placeholder and the source stays lossless.

## Verification

`npm test` runs source, history, bridge-contract, theme, and resource-policy tests. `npm run test:browser` builds the same source and exercises real Chromium editing, atomic inline preview, source-mode switching, undo/redo, BOM and mixed-EOL behavior, and blocked resource requests. Browser evidence is not Android WebView or physical IME acceptance.

