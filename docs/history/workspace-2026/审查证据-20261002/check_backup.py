import hashlib, io, json, pathlib, subprocess, zipfile

root = pathlib.Path(r'C:\Users\21234\Desktop\M1racle Reader')
repo = pathlib.Path(r'C:\Users\21234\AppData\Local\Temp\m1racle-audit-20261002\repository')
report = {}
for relative in ['source/m1racle-reader-source-1.0.0-20261002.zip', 'git/m1racle-reader-git-bundle-1.0.0-20261002.bundle', 'apk/m1racle-reader-release-latest.apk']:
    path = root / relative
    expected = pathlib.Path(str(path) + '.sha256').read_text().split()[0]
    actual = hashlib.file_digest(path.open('rb'), 'sha256').hexdigest()
    report[relative] = {'size': path.stat().st_size, 'sha256': actual, 'sidecar_matches': actual == expected}
with zipfile.ZipFile(root / 'apk/m1racle-reader-release-latest.apk') as z:
    report['apk_integrity'] = {'bad_entry': z.testzip(), 'entries': len(z.infolist()), 'core_entries': {n: z.getinfo(n).file_size for n in ['AndroidManifest.xml', 'resources.arsc', 'classes.dex']}}
with zipfile.ZipFile(root / 'source/m1racle-reader-source-1.0.0-20261002.zip') as z:
    report['source_integrity'] = {'bad_entry': z.testzip()}
    snapshot = {n.split('/', 1)[1]: z.read(n) for n in z.namelist() if not n.endswith('/')}
archive = subprocess.check_output(['git', '-C', str(repo), 'archive', '--format=zip', 'HEAD'])
with zipfile.ZipFile(io.BytesIO(archive)) as z:
    tracked = {n: z.read(n) for n in z.namelist() if not n.endswith('/')}
different = sorted(n for n in snapshot.keys() & tracked.keys() if snapshot[n] != tracked[n])
substantive = [n for n in different if snapshot[n].replace(b'\r\n', b'\n') != tracked[n].replace(b'\r\n', b'\n')]
report['source_vs_bundle_head'] = {'snapshot_files': len(snapshot), 'tracked_files': len(tracked), 'byte_different_count': len(different), 'newline_only_count': len(different) - len(substantive), 'substantive_differences': substantive, 'snapshot_only': sorted(snapshot.keys() - tracked.keys()), 'head_only': sorted(tracked.keys() - snapshot.keys())}
report['head'] = subprocess.check_output(['git', '-C', str(repo), 'rev-parse', 'HEAD'], text=True).strip()
print(json.dumps(report, ensure_ascii=False, indent=2))
pathlib.Path(r'C:\Users\21234\AppData\Local\Temp\m1racle-audit-20261002\backup-evidence.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
