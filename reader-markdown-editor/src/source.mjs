// CodeMirror coordinates count LF-normalized UTF-16 code units. Preserve the
// original string and map only explicit transaction ranges back to its bytes.
export class SourceDocument {
  #raw;
  #crlf = null;

  constructor(raw) {
    this.raw = raw;
    this.eol = raw.match(/\r\n|\r|\n/)?.[0] ?? '\n';
  }

  get raw() { return this.#raw; }
  set raw(value) {
    if (typeof value !== 'string') throw new TypeError('Markdown source must be a string');
    this.#raw = value;
    this.#crlf = null;
  }

  get bomLength() { return this.raw.startsWith('\ufeff') ? 1 : 0; }

  // BOM is encoding metadata, not the first Markdown character. Preserve it
  // in raw while excluding it from the editor's visible coordinate space.
  normalized() { return this.raw.slice(this.bomLength).replace(/\r\n|\r/g, '\n'); }

  offset(position) {
    if (this.#crlf === null) {
      this.#crlf = [];
      for (let at = this.raw.indexOf('\r\n'); at !== -1; at = this.raw.indexOf('\r\n', at + 2)) {
        this.#crlf.push(at - this.#crlf.length - this.bomLength);
      }
    }
    if (!Number.isInteger(position) || position < 0 || position > this.raw.length - this.#crlf.length - this.bomLength) {
      throw new RangeError('Invalid source position');
    }
    let from = 0, to = this.#crlf.length;
    while (from < to) {
      const middle = (from + to) >>> 1;
      if (this.#crlf[middle] < position) from = middle + 1;
      else to = middle;
    }
    return position + from + this.bomLength;
  }

  apply(changes, { preserveEndings = false } = {}) {
    const mapped = changes.map(({ from, to, insert }) => ({
      from: this.offset(from),
      to: this.offset(to),
      insert: preserveEndings ? insert : insert.replace(/\r\n|\r|\n/g, this.eol),
    }));
    const merged = [];
    for (const change of mapped) {
      const previous = merged.at(-1);
      if (previous && previous.to === change.from) {
        previous.to = change.to;
        previous.insert += change.insert;
      } else {
        merged.push({ ...change });
      }
    }
    for (const change of merged.reverse()) {
      const prefix = this.raw.slice(0, change.from);
      const suffix = this.raw.slice(change.to);
      let insert = change.insert;
      if (!insert && prefix.endsWith('\r') && suffix.startsWith('\n')) insert = '\r';
      else {
        if (prefix.endsWith('\r') && insert.startsWith('\n')) insert = '\r' + insert;
        if (insert.endsWith('\r') && suffix.startsWith('\n')) insert += '\n';
      }
      this.raw = prefix + insert + suffix;
    }
    return this.raw;
  }
}
