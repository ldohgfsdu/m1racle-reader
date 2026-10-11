import json, subprocess, sys, xml.etree.ElementTree as ET
from pathlib import Path
sys.stdout.reconfigure(encoding='utf-8')
root=Path(__file__).resolve().parent
adb=str(root.parent / '.build-tools/sdk/platform-tools/adb.exe')
def run(*args):
    return subprocess.check_output([adb,'-s','emulator-5554',*args])
mode=sys.argv[1]
if mode=='snapshot':
    name=sys.argv[2]
    run('shell','uiautomator','dump','/sdcard/reader-ui.xml')
    data=run('shell','cat','/sdcard/reader-ui.xml')
    (root/(name+'.xml')).write_bytes(data)
    (root/(name+'.png')).write_bytes(run('exec-out','screencap','-p'))
    tree=ET.fromstring(data)
    nodes=[{k:n.get(k) for k in ['text','content-desc','resource-id','bounds','clickable','scrollable']} for n in tree.iter('node') if n.get('text') or n.get('content-desc') or n.get('scrollable')=='true']
    print(json.dumps(nodes,ensure_ascii=False))
elif mode=='tap':
    tree=ET.parse(root/(sys.argv[2]+'.xml'))
    matches=[n for n in tree.iter('node') if sys.argv[3] in (n.get('text'),n.get('content-desc'))]
    if len(matches)!=1: raise ValueError(f'Expected one matching UI node, found {len(matches)}')
    import re
    x1,y1,x2,y2=map(int,re.findall(r'\d+',matches[0].get('bounds')))
    run('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
elif mode=='logs':
    (root/'logcat.txt').write_bytes(run('logcat','-d'))
    print(run('logcat','-b','crash','-d').decode('utf-8',errors='replace'))
elif mode=='verify':
    def texts(name):
        return [n.get('text') for n in ET.parse(root/(name+'.xml')).iter('node') if n.get('text')]
    def descs(name):
        return [n.get('content-desc') for n in ET.parse(root/(name+'.xml')).iter('node') if n.get('content-desc')]
    checks={
        'home':'M1racle Reader' in texts('home'),
        'settings':'版本 1.0.0' in texts('settings'),
        'markdown_scroll':texts('markdown') != texts('markdown-scrolled'),
        'markdown_toc_jump':'Section 2 · 3%' in texts('markdown-jump'),
        'epub_turn_page':'第 1 / 4 页' in texts('epub') and '第 2 / 4 页' in texts('epub-next'),
        'epub_toc_jump':'第 1 / 4 页' in texts('epub-toc-jump'),
        'epub_restore':texts('epub-before-restart')==texts('epub-restored'),
        'pdf_page_render':'PDF page 1 of 3' in descs('pdf'),
        'pdf_scroll':'2 / 3 · 67%' in texts('pdf-controls-visible'),
        'pdf_reading_menu':'上下连续' in texts('pdf-reading-mode') and '左右翻页' in texts('pdf-reading-mode'),
        'saf_import':'Emulator import check' in texts('imported-markdown') and 'alpha beta gamma alpha.' in texts('imported-markdown'),
        'markdown_search':'1 / 2' in texts('imported-search-typed') and '2 / 2' in texts('imported-search-next'),
    }
    apk=root.parent/'reader/.external_outbox/m1racle-reader-release-latest.apk'
    import hashlib
    digest=hashlib.sha256(apk.read_bytes()).hexdigest()
    installed=run('shell','pm','path','com.m1racle.reader').decode().strip().removeprefix('package:')
    installed_hash=run('shell','sha256sum',installed).decode().split()[0]
    download_hash=run('shell','sha256sum','/sdcard/Download/m1racle-reader-release-latest.apk').decode().split()[0]
    checks['artifact_identity']=digest==installed_hash==download_hash
    crash=run('logcat','-b','crash','-d').decode(errors='replace')
    (root/'crash-buffer.txt').write_text(crash,encoding='utf-8')
    checks['no_crash_buffer']=not crash.strip()
    summary={'serial':'emulator-5554','api':35,'abi':'x86_64','apk_sha256':digest,'checks':checks,'scope':'basic emulator smoke; not full acceptance or physical-device verification'}
    (root/'verification.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(summary,ensure_ascii=False))
    assert all(checks.values()), 'Emulator smoke check failed'
