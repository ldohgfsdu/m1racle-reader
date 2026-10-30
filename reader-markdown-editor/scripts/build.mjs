import { createHash } from 'node:crypto';
import { cp, mkdir, readFile, readdir, rm, stat, writeFile } from 'node:fs/promises';
import { join, relative, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { readerAtomicPolicy } from './atomic-policy.mjs';
import { readerTablePolicy } from './table-policy.mjs';

const editorRoot = fileURLToPath(new URL('..', import.meta.url));
const readerRoot = resolve(editorRoot, '..');
const localModules = join(editorRoot, 'node_modules');
const noteRoot = process.env.M1RACLE_NOTE_ROOT
  ? resolve(process.env.M1RACLE_NOTE_ROOT)
  : 'C:/Users/42899/Desktop/M1racle Note';
const noteModules = join(noteRoot, 'node_modules');
const dist = join(editorRoot, 'dist');
const target = join(readerRoot, 'app', 'src', 'main', 'assets', 'markdown-editor');
const radixSource = 'app/src/main/java/com/m1racle/reader/design/RadixColors.kt';
const radixPalette = await readFile(join(readerRoot, radixSource), 'utf8');
const radixDeclarations = [...radixPalette.matchAll(/val (\w+) = Color\(0xFF([A-Fa-f0-9]{6})\)/g)]
  .map(([, name, rgb]) => `--radix-${name.replace(/([a-z])([A-Z])/g, '$1-$2').replace(/([a-zA-Z])(\d)/g, '$1-$2').toLowerCase()}:#${rgb};`);
if (radixDeclarations.length < 100) throw new Error('Radix source palette is incomplete');
const readerRadixPalette = {
  name: 'reader-radix-palette',
  enforce: 'pre',
  transform(code, id) {
    if (resolve(id) !== join(editorRoot, 'src', 'style.css')) return null;
    return { code: `:root{--radix-white:#FFFFFF;--radix-black:#000000;${radixDeclarations.join('')}}\n${code}`, map: null };
  },
};
if (relative(readerRoot, target).startsWith('..')) throw new Error('Bundle target escaped the Reader worktree');
if (relative(readerRoot, dist).startsWith('..')) throw new Error('Build output escaped the Reader worktree');

async function exists(path) {
  try {
    await stat(path);
    return true;
  } catch {
    return false;
  }
}

const dependencyRoot = await exists(join(localModules, 'vite', 'dist', 'node', 'index.js'))
  ? localModules
  : noteModules;
if (!(await exists(join(dependencyRoot, 'vite', 'dist', 'node', 'index.js')))) {
  throw new Error(
    `Dependencies not found. Run npm ci in ${editorRoot}, or set M1RACLE_NOTE_ROOT ` +
    `to an optional checkout with node_modules. Checked ${localModules} then ${noteModules}.`,
  );
}

const { build } = await import(pathToFileURL(join(dependencyRoot, 'vite', 'dist', 'node', 'index.js')).href);
const withoutModulePreload = {
  name: 'reader-without-module-preload',
  enforce: 'post',
  transform(code, id) {
    if (!id.replaceAll('\\', '/').includes('vite/modulepreload-polyfill.js')) return null;
    return { code: '// modulepreload is not emitted by this single-entry bundle.\n', map: null };
  },
};
const packageNames = [
  '@atomic-editor/editor',
  '@codemirror/commands',
  '@codemirror/lang-markdown',
  '@codemirror/language',
  '@codemirror/search',
  '@codemirror/state',
  '@codemirror/view',
  '@lezer/common',
  '@lezer/highlight',
  '@lezer/markdown',
];
const alias = Object.fromEntries(packageNames.map(name => [name, join(dependencyRoot, ...name.split('/'))]));
alias['@atomic-editor/editor/styles.css'] = join(dependencyRoot, '@atomic-editor', 'editor', 'dist', 'styles', 'inline-preview.css');

await rm(dist, { recursive: true, force: true });
await mkdir(dist, { recursive: true });
await build({
  root: editorRoot,
  configFile: false,
  logLevel: 'warn',
  plugins: [readerAtomicPolicy(), readerTablePolicy(), withoutModulePreload, readerRadixPalette],
  resolve: { alias },
  optimizeDeps: {
    exclude: ['@atomic-editor/editor'],
    include: packageNames.filter(name => name !== '@atomic-editor/editor'),
  },
  base: './',
  build: {
    outDir: dist,
    emptyOutDir: true,
    target: 'es2020',
    assetsInlineLimit: 0,
    sourcemap: false,
    rollupOptions: {
      output: {
        entryFileNames: 'assets/app-[hash].js',
        chunkFileNames: 'assets/chunk-[hash].js',
        assetFileNames: 'assets/[name]-[hash][extname]',
      },
    },
  },
  // Note's existing esbuild helper cannot unlink its Windows temp file on
  // this host. The bundle is built unminified and still targets ES2020.
  esbuild: false,
});

const htmlPath = join(dist, 'index.html');
const html = await readFile(htmlPath, 'utf8');
if (/https?:\/\//i.test(html.replaceAll('https://reader-markdown.local/', ''))) {
  throw new Error('Built HTML contains an absolute HTTP resource');
}
if (!html.includes("default-src 'none'") || !html.includes('connect-src \'none\'')) {
  throw new Error('Built HTML lost its restrictive content security policy');
}

async function licenseFile(packageRoot) {
  for (const name of await readdir(packageRoot)) {
    if (/^licen[cs]e(?:[-.]|$)/i.test(name)) return join(packageRoot, name);
  }
  return null;
}

const runtimePackages = new Set(packageNames);
const queue = [...runtimePackages];
while (queue.length) {
  const name = queue.shift();
  const packageRoot = join(dependencyRoot, ...name.split('/'));
  const metadata = JSON.parse(await readFile(join(packageRoot, 'package.json'), 'utf8'));
  for (const dependency of Object.keys(metadata.dependencies || {})) {
    if (runtimePackages.has(dependency) || !(await exists(join(dependencyRoot, ...dependency.split('/'))))) continue;
    runtimePackages.add(dependency);
    queue.push(dependency);
  }
}

const licenses = join(dist, 'licenses');
await mkdir(licenses, { recursive: true });
for (const name of [...runtimePackages].sort()) {
  const packageRoot = join(dependencyRoot, ...name.split('/'));
  const license = await licenseFile(packageRoot);
  if (!license) throw new Error(`License missing for runtime dependency ${name}`);
  await cp(license, join(licenses, `${name.replaceAll('/', '-')}.txt`));
}
await cp(join(editorRoot, 'THIRD_PARTY_NOTICES.md'), join(dist, 'THIRD_PARTY_NOTICES.md'));

async function walk(directory) {
  const files = [];
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) files.push(...await walk(path));
    else files.push(path);
  }
  return files;
}

async function sha256(path) {
  return createHash('sha256').update(await readFile(path)).digest('hex');
}

const builtFiles = await walk(dist);
const files = [];
for (const path of builtFiles.sort()) {
  const data = await readFile(path);
  files.push({
    path: relative(dist, path).replaceAll('\\', '/'),
    bytes: data.byteLength,
    sha256: createHash('sha256').update(data).digest('hex'),
  });
}

const sourcePaths = [
  'src/source.mjs',
  'src/source-history.mjs',
  'src/phrases.mjs',
  'src/preview-update.mjs',
  'scripts/build.mjs',
  'scripts/atomic-policy.mjs',
  'scripts/table-policy.mjs',
  'src/main.mjs',
  'src/style.css',
  'index.html',
  'package.json',
  'package-lock.json',
  'THIRD_PARTY_NOTICES.md',
];
const sources = [];
sources.push({ path: radixSource, sha256: await sha256(join(readerRoot, radixSource)) });
for (const path of sourcePaths) {
  const absolute = join(editorRoot, path);
  sources.push({ path: `reader-markdown-editor/${path}`, sha256: await sha256(absolute) });
}
sources.push({
  path: 'upstream/@atomic-editor/editor/dist/inline-preview.js',
  sha256: await sha256(join(dependencyRoot, '@atomic-editor', 'editor', 'dist', 'inline-preview.js')),
});
sources.push({
  path: 'upstream/@atomic-editor/editor/dist/table-widget.js',
  sha256: await sha256(join(dependencyRoot, '@atomic-editor', 'editor', 'dist', 'table-widget.js')),
});
const manifest = {
  generatedAt: new Date().toISOString(),
  origin: 'https://reader-markdown.local/',
  entry: 'index.html',
  node: process.version,
  vitePath: join(dependencyRoot, 'vite'),
  dependencyRoot,
  files,
  sources,
};
const manifestText = JSON.stringify(manifest, null, 2) + '\n';
await writeFile(join(dist, 'build-manifest.json'), manifestText, 'utf8');

await rm(target, { recursive: true, force: true });
await mkdir(target, { recursive: true });
await cp(dist, target, { recursive: true });
console.log(`Built ${files.length} editor files -> ${target}`);
console.log(`Manifest: ${join(target, 'build-manifest.json')}`);
