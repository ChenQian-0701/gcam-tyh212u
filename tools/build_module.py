#!/usr/bin/env python3
"""Build only the open-source module with standard Android SDK tools."""
import argparse, hashlib, json, os, shutil, subprocess, zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
VERSION='0.1.0-alpha.1'

def run(args, **kwargs):
    subprocess.run([str(a) for a in args], check=True, **kwargs)

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--sdk',type=Path,default=Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or Path.home()/'.cache/gcam-unisoc-research/android-sdk'))
    p.add_argument('--build-tools',default='35.0.0')
    p.add_argument('--mode',choices=['unsigned','debug','release'],default='debug')
    p.add_argument('--keystore',type=Path)
    p.add_argument('--alias',default='release')
    p.add_argument('--out',type=Path,default=ROOT/'dist')
    a=p.parse_args(); bt=a.sdk/'build-tools'/a.build_tools; jar=a.sdk/'platforms/android-30/android.jar'
    for f in [jar,bt/'aapt2',bt/'d8',bt/'apksigner',bt/'zipalign']:
        if not f.exists():p.error(f'Missing SDK component {f}; run tools/setup_sdk.py or pass --sdk')
    if a.mode=='release' and (not a.keystore or not os.environ.get('GCAM_STORE_PASSWORD') or not os.environ.get('GCAM_KEY_PASSWORD')):
        p.error('Release requires --keystore and GCAM_STORE_PASSWORD / GCAM_KEY_PASSWORD environment variables')
    # Prevent accidental deletion of an arbitrary user-supplied directory.
    build=ROOT/'.build'; shutil.rmtree(build,ignore_errors=True)
    for n in ['stubs','classes','dex']:(build/n).mkdir(parents=True,exist_ok=True)
    a.out.mkdir(parents=True,exist_ok=True)
    run(['javac','--release','8','-encoding','UTF-8','-cp',jar,'-d',build/'stubs',*sorted((ROOT/'module/stubs').rglob('*.java'))])
    run(['javac','--release','8','-encoding','UTF-8','-cp',str(jar)+os.pathsep+str(build/'stubs'),'-d',build/'classes',*sorted((ROOT/'module/src').rglob('*.java'))])
    # Stub classes are deliberately absent from DEX and the final APK.
    run([bt/'d8','--min-api','30','--release','--lib',jar,'--classpath',build/'stubs','--output',build/'dex',*sorted((build/'classes').rglob('*.class'))])
    run([bt/'aapt2','link','-o',build/'resources.apk','-I',jar,'--manifest',ROOT/'module/AndroidManifest.xml','--version-code','2','--version-name',VERSION])
    unsigned=build/'unsigned.apk';shutil.copyfile(build/'resources.apk',unsigned)
    with zipfile.ZipFile(unsigned,'a',zipfile.ZIP_DEFLATED) as z:
        z.write(build/'dex/classes.dex','classes.dex')
        z.write(ROOT/'module/assets/xposed_init','assets/xposed_init')
    aligned=build/'aligned.apk';run([bt/'zipalign','-f','4',unsigned,aligned])
    dest=a.out/f'gcam-tyh212u-{VERSION}-{a.mode}.apk'
    if a.mode=='unsigned':shutil.copyfile(aligned,dest)
    else:
        env=os.environ.copy()
        if a.mode=='debug':
            key=Path.home()/'.cache/gcam-unisoc-research/signing/debug.p12';key.parent.mkdir(parents=True,exist_ok=True)
            env['GCAM_DEBUG_PASSWORD']='android'
            if not key.exists():
                run(['keytool','-genkeypair','-keystore',key,'-storetype','PKCS12','-alias','androiddebugkey','-storepass:env','GCAM_DEBUG_PASSWORD','-keypass:env','GCAM_DEBUG_PASSWORD','-dname','CN=Android Debug,O=GCam Unisoc Research','-keyalg','RSA','-keysize','2048','-validity','10000'],env=env)
                key.chmod(0o600)
            alias='androiddebugkey'; store='env:GCAM_DEBUG_PASSWORD'; password=store
        else:
            key=a.keystore;alias=a.alias;store='env:GCAM_STORE_PASSWORD';password='env:GCAM_KEY_PASSWORD'
        run([bt/'apksigner','sign','--ks',key,'--ks-key-alias',alias,'--ks-pass',store,'--key-pass',password,'--v1-signing-enabled','false','--v2-signing-enabled','true','--v3-signing-enabled','true','--v4-signing-enabled','false','--out',dest,aligned],env=env)
        run([bt/'apksigner','verify','--verbose',dest])
    sha=hashlib.sha256(dest.read_bytes()).hexdigest()
    (dest.with_suffix('.apk.sha256')).write_text(sha+'  '+dest.name+'\n')
    print('Built',dest.name,'SHA-256',sha)

if __name__=='__main__':main()
