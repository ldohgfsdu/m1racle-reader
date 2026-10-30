// Atomic's table widget assigns Markdown image URLs to <img>. Reader has no
// adjacent-resource authorization, so replace those elements with inert local
// placeholders before bundling while preserving the alt text.
export function readerTablePolicy() {
  return {
    name: 'reader-table-image-policy',
    enforce: 'pre',
    transform(source, id) {
      const file = id.replaceAll('\\', '/').split('?')[0];
      if (!file.endsWith('/@atomic-editor/editor/dist/table-widget.js')) return null;
      const before = `const img = document.createElement('img');
        img.src = src;
        img.alt = alt;
        img.loading = 'lazy';
        img.className = 'cm-atomic-table-cell-image';`;
      const after = `const img = document.createElement('span');
        img.className = 'cm-atomic-table-cell-image cm-reader-table-image-placeholder';
        const unavailable = document.createElement('span');
        unavailable.textContent = '图片暂不可用';
        img.append(unavailable);
        if (alt) {
            const detail = document.createElement('span');
            detail.className = 'cm-reader-table-image-placeholder-alt';
            detail.textContent = alt;
            img.append(detail);
        }
        img.setAttribute('role', 'img');
        img.setAttribute('aria-label', alt ? \`图片暂不可用：\${alt}\` : '图片暂不可用');`;
      if (!source.includes(before)) {
        throw new Error('Atomic table image target changed; review scripts/table-policy.mjs.');
      }
      return { code: source.replace(before, after), map: null };
    },
  };
}
