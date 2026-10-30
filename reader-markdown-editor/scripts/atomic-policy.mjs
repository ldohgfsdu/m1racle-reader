import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';

const UPSTREAM_HASH = '7f59ed021f43ae7d73757d4f5e761bd349e4fa3b085718c25920e206123dca10';

// Adapt Atomic 0.6.2 without modifying any installed dependency.
export function readerAtomicPolicy() {
  return {
    name: 'reader-atomic-policy',
    enforce: 'pre',
    transform(source, id) {
      const file = id.replaceAll('\\', '/').split('?')[0];
      if (!file.endsWith('/@atomic-editor/editor/dist/inline-preview.js')) return null;
      const executableSource = source.replace(/\/\/# sourceMappingURL=[^\r\n]*/g, '').trimEnd();
      if (createHash('sha256').update(executableSource).digest('hex') !== UPSTREAM_HASH) {
        throw new Error('Atomic inline-preview changed; review scripts/atomic-policy.mjs.');
      }
      const replace = (before, after) => {
        if (!source.includes(before)) throw new Error(`Atomic adapter target missing: ${before}`);
        source = source.replace(before, after);
      };

      // Rendered editing stays rendered while the caret moves. Explicit source
      // mode removes this extension, which is where delimiters are edited.
      replace('if (view.hasFocus && !readOnly) {', 'if (false /* Reader keeps the rendered block rendered. */) {');
      replace("if (node.name === 'Link' && view.hasFocus) {", "if (false /* Reader edits destinations in source mode. */) {");
      replace('if (nextFrozen && !justUnfroze && !update.docChanged)',
        'if (false /* Reader presentation is stable without pointer freezing. */)');
      replace(`if (justUnfroze ||
            update.docChanged ||
            update.selectionSet ||
            update.focusChanged ||
            treeGrew ||
            readOnlyChanged)`,
      'if (update.docChanged || treeGrew || readOnlyChanged)');

      // No adjacent-file authorization exists in this iteration. The source
      // stays lossless; an inert local placeholder replaces the image URL.
      replace(
        "if (node.name === 'Image' && node.from < node.to) {",
        `if (node.name === 'Image' && node.from < node.to) {
                if (!activeLines.has(doc.lineAt(node.from).number)) {
                    const imageSource = doc.sliceString(node.from, node.to);
                    const alt = /^!\\[([^\\]]*)\\]/.exec(imageSource)?.[1] || "";
                    pushReplace(ranges, doc, node.from, node.to, {
                        widget: new ReaderImagePlaceholderWidget(alt),
                    });
                }
                return false;`,
      );
      replace('function buildInlineDecorations(view) {', String.raw`class ReaderImagePlaceholderWidget extends WidgetType {
    constructor(alt) {
        super();
        this.alt = alt;
    }
    toDOM() {
        const placeholder = document.createElement("span");
        placeholder.className = "cm-reader-image-placeholder";
        const label = document.createElement("span");
        label.className = "cm-reader-image-placeholder-label";
        label.textContent = "图片暂不可用";
        placeholder.append(label);
        if (this.alt) {
            const detail = document.createElement("span");
            detail.className = "cm-reader-image-placeholder-alt";
            detail.textContent = this.alt;
            placeholder.append(detail);
        }
        placeholder.setAttribute("aria-label", this.alt ? "图片暂不可用：" + this.alt : "图片暂不可用");
        placeholder.setAttribute("contenteditable", "false");
        return placeholder;
    }
    eq(other) {
        return other instanceof ReaderImagePlaceholderWidget && other.alt === this.alt;
    }
    ignoreEvent() {
        return true;
    }
}

function buildInlineDecorations(view) {`);

      // The same CM6 atomic-range facility used by Note mobile. Text editing,
      // composition, selection, and transactions remain CodeMirror-owned.
      replace('    decorations: (v) => v.decorations,', `    decorations: (v) => v.decorations,
    provide: plugin => {
        let previous, ranges = Decoration.none;
        return EditorView.atomicRanges.of(view => {
            const decorations = view.plugin(plugin)?.decorations;
            if (decorations !== previous) {
                previous = decorations;
                ranges = decorations ? decorations.update({
                    filter: (from, to, decoration) =>
                        to > from && !decoration.spec.class &&
                        !decoration.spec.attributes && !decoration.spec.widget
                }) : Decoration.none;
            }
            return ranges;
        });
    },`);

      // Incremental rebuild is presentation-only.
      replace('function buildInlineDecorations(view) {', 'function buildInlineDecorations(view, region) {');
      replace(`tree.iterate({
        enter: (node) => {`, `tree.iterate({
        from: region?.from ?? 0,
        to: region?.to ?? doc.length,
        enter: (node) => {
            if (region && (node.to <= region.from || node.from >= region.to)) return false;`);
      replace(`    if (view.hasFocus) {
        const head = state.selection.main.head;`,
      `    if (view.hasFocus && (!region || state.selection.main.head >= region.from && state.selection.main.head <= region.to)) {
        const head = state.selection.main.head;`);
      replace('this.decorations = buildInlineDecorations(view);',
        'this.previewCache = new PreviewCache(view, buildInlineDecorations, {selectionSensitive:false});\n        this.decorations = this.previewCache.decorations;');
      replace('this.decorations = buildInlineDecorations(update.view);',
        'this.decorations = this.previewCache.update(update, treeGrew || readOnlyChanged);');
      const runtime = fileURLToPath(new URL('../src/preview-update.mjs', import.meta.url)).replaceAll('\\', '/');
      return { code: `import {PreviewCache} from ${JSON.stringify(runtime)};\n` + source, map: null };
    },
  };
}
