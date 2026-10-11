import json, pathlib, struct, zipfile

apk = pathlib.Path(r'C:\Users\21234\Desktop\M1racle Reader\apk\m1racle-reader-release-latest.apk')
with zipfile.ZipFile(apk) as z:
    data = z.read('AndroidManifest.xml')
strings = []
result = {'manifest': {}, 'permissions': [], 'application': {}, 'activities': []}
def length_at(at, utf8):
    if utf8:
        n = data[at]
        return (((n & 127) << 8) | data[at + 1], at + 2) if n & 128 else (n, at + 1)
    n = struct.unpack_from('<H', data, at)[0]
    return (((n & 32767) << 16) | struct.unpack_from('<H', data, at + 2)[0], at + 4) if n & 32768 else (n, at + 2)
offset = struct.unpack_from('<H', data, 2)[0]
while offset < len(data):
    kind, header, size = struct.unpack_from('<HHI', data, offset)
    if size < header or size == 0:
        raise ValueError('invalid AXML chunk')
    if kind == 1:
        count, styles, flags, start, style_start = struct.unpack_from('<IIIII', data, offset + 8)
        utf8 = bool(flags & 256)
        for i in range(count):
            relative = struct.unpack_from('<I', data, offset + header + 4 * i)[0]
            at = offset + start + relative
            n, at = length_at(at, utf8)
            if utf8:
                n, at = length_at(at, True)
                strings.append(data[at:at+n].decode('utf-8'))
            else:
                strings.append(data[at:at+n*2].decode('utf-16le'))
    elif kind == 0x102:
        extension = offset + 16
        ns, name, attr_start, attr_size, attr_count = struct.unpack_from('<IIHHH', data, extension)
        tag = strings[name]
        attrs = {}
        for i in range(attr_count):
            at = extension + attr_start + i * attr_size
            ans, aname, raw, typed_size, unused, value_type, value = struct.unpack_from('<IIIHBBI', data, at)
            attrs[strings[aname]] = strings[raw] if raw != 0xffffffff else strings[value] if value_type == 3 else bool(value) if value_type == 18 else value
        if tag == 'manifest': result['manifest'] = attrs
        if tag == 'uses-sdk': result['sdk'] = attrs
        if tag.startswith('uses-permission'): result['permissions'].append(attrs)
        if tag == 'application': result['application'] = attrs
        if tag == 'activity': result['activities'].append(attrs)
    offset += size
result['has_internet_permission'] = any(p.get('name') == 'android.permission.INTERNET' for p in result['permissions'])
output = json.dumps(result, ensure_ascii=False, indent=2)
print(output)
pathlib.Path(r'C:\Users\21234\AppData\Local\Temp\m1racle-audit-20261002\apk-manifest-evidence.json').write_text(output, encoding='utf-8')
