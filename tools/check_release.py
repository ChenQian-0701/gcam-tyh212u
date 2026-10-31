#!/usr/bin/env python3
"""Meaningful host checks: rejected profiles, digest, packaged DEX and defaults."""
import json,re,struct,subprocess,tempfile,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]

def dex_classes(data):
    if not data.startswith(b'dex\n'):raise AssertionError('Not DEX')
    def u32(pos):return struct.unpack_from('<I',data,pos)[0]
    strings=u32(0x3c);types=u32(0x44);count=u32(0x60);classes=u32(0x64)
    def string(index):
        pos=u32(strings+index*4)
        while data[pos]&128:pos+=1
        pos+=1
        return data[pos:data.index(0,pos)].decode('utf-8')
    return [string(u32(types+u32(classes+i*32)*4)) for i in range(count)]

def main():
    with tempfile.TemporaryDirectory(prefix='gcam-profile-test-') as tmp:
        subprocess.run(['javac','--release','8','-d',tmp,str(ROOT/'module/src/com/tyh212u/gcamhook/CompatibilityProfile.java'),str(ROOT/'tests/ProfileTest.java')],check=True)
        subprocess.run(['java','-cp',tmp,'com.tyh212u.gcamhook.ProfileTest'],check=True)
    baseline=json.loads((ROOT/'config/baseline.json').read_text())['runtime_properties']
    assert baseline['debug.gcam.fix.groupedexposure']=='0'
    assert baseline['debug.gcam.fix.nooverlay']=='0'
    text=(ROOT/'module/src/com/tyh212u/gcamhook/ProgressOverlayRepair.java').read_text()
    assert 'HookEntry.prop(PROP, "0")' in text
    entry=(ROOT/'module/src/com/tyh212u/gcamhook/HookEntry.java').read_text()
    assert entry.index('CompatibilityGuard.allow')<entry.index('DistributorRepair.install')
    assert entry.count('ProgressOverlayRepair.install(cl)')==1
    assert entry.count('ShutterEnableProbe.install(cl)')==1
    apks=sorted((ROOT/'dist').glob('*.apk'))
    assert apks,'Build at least one APK before checking'
    for apk in apks:
        with zipfile.ZipFile(apk) as z:
            names=z.namelist()
            assert 'assets/xposed_init' in names and 'classes.dex' in names
            assert not any(n.endswith(('.p12','.jks','.jar','.so')) for n in names)
            classes=dex_classes(z.read('classes.dex'))
            assert any('CompatibilityGuard;' in c for c in classes)
            assert not any(c.startswith('Lde/robv/android/xposed/') for c in classes), 'Compile stubs must not be shipped'
        print(apk.name,':',len(classes),'module classes; no packaged Xposed implementation, private keys or native libraries')
    print('Release host checks: PASS (not an on-device camera test)')

if __name__=='__main__':main()
