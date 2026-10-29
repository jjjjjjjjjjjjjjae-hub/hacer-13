#!/usr/bin/env python3
"""Build the small HACER companion with JDK17 + Android SDK35, without Gradle downloads.

Does not read, patch, bundle or publish the LAC game. Signing passwords come from
HACER_KEYSTORE_PASS (and optionally HACER_KEY_PASS), never command-line literals.
"""
import argparse,os,pathlib,shutil,subprocess,sys,tempfile,xml.etree.ElementTree as E,zipfile,hashlib

def build(sdk,jdk,keystore,output,bc_jar,alias='hacer13'):
    root=pathlib.Path(__file__).resolve().parents[1];sdk=pathlib.Path(sdk).resolve();jdk=pathlib.Path(jdk).resolve();output=pathlib.Path(output).resolve();keystore=pathlib.Path(keystore).resolve()
    if output.is_relative_to(root) or keystore.is_relative_to(root):raise ValueError('Keep APK output and private keystore outside the public source repository')
    if not os.environ.get('HACER_KEYSTORE_PASS'):raise ValueError('Set HACER_KEYSTORE_PASS in your environment')
    sys.path.insert(0,str(root/'LAC-tools'));from inspect_xapk import tool
    suffix='.exe' if os.name=='nt' else ''
    def j(name):return str(jdk/'bin'/(name+suffix))
    def run(*args):subprocess.run([str(a) for a in args],check=True)
    android=sdk/'platforms/android-35/android.jar';env=os.environ.copy();env['JAVA_HOME']=str(jdk);env['PATH']=str(jdk/'bin')+os.pathsep+env['PATH']
    bc_jar=pathlib.Path(bc_jar).resolve()
    if not bc_jar.is_file():raise ValueError('Provide the pinned bcprov-jdk15to18-1.86.jar build dependency')
    if hashlib.sha256(bc_jar.read_bytes()).hexdigest()!='fc50334d4d87b4272e72fa95ca748ead05bdef02ef760a5b13f64ffee317cd81':raise ValueError('Crypto build dependency checksum mismatch')
    with tempfile.TemporaryDirectory(prefix='h13-build-') as d:
        tmp=pathlib.Path(d);classes=tmp/'classes';classes.mkdir();dex=tmp/'dex';dex.mkdir()
        sources=[]
        for source in ['core/app/src/main/java','core/ui/src/main/java','core/updater/src/main/java','LAC-tools/hook/src/main/java']:sources.extend(sorted((root/source).rglob('*.java')))
        run(j('javac'),'-encoding','UTF-8','--release','17','-cp',str(android)+os.pathsep+str(bc_jar),'-d',classes,*sources)
        jar=tmp/'classes.jar';run(j('jar'),'--create','--file',jar,'-C',classes,'.')
        subprocess.run([tool(sdk,'d8'),'--release','--min-api','33','--lib',str(android),'--output',str(dex),str(jar),str(bc_jar)],env=env,check=True)
        E.register_namespace('android','http://schemas.android.com/apk/res/android');manifest=E.parse(root/'core/app/src/main/AndroidManifest.xml');manifest.getroot().set('package','com.hacer13.core')
        manifest.find('application').set('{http://schemas.android.com/apk/res/android}debuggable','false')
        xml=tmp/'AndroidManifest.xml';manifest.write(xml,encoding='utf-8',xml_declaration=True)
        unsigned=tmp/'unsigned.apk';run(tool(sdk,'aapt2'),'link','-o',unsigned,'--manifest',xml,'-I',android,'--min-sdk-version','33','--target-sdk-version','35','--version-code','2','--version-name','1.0.1')
        with zipfile.ZipFile(unsigned,'a',compression=zipfile.ZIP_DEFLATED) as z:
            for f in dex.glob('classes*.dex'):z.write(f,f.name)
            with zipfile.ZipFile(bc_jar) as dependency:
                for name in dependency.namelist():
                    if 'LICENSE' in name.upper() and not name.endswith('/'):z.writestr('assets/licenses/bouncycastle-'+pathlib.Path(name).name,dependency.read(name))
        aligned=tmp/'aligned.apk';run(tool(sdk,'zipalign'),'-f','4',unsigned,aligned)
        output.parent.mkdir(parents=True,exist_ok=True)
        cmd=[tool(sdk,'apksigner'),'sign','--ks',str(keystore),'--ks-key-alias',alias,'--ks-pass','env:HACER_KEYSTORE_PASS','--out',str(output),str(aligned)]
        if os.environ.get('HACER_KEY_PASS'):cmd[2:2]=['--key-pass','env:HACER_KEY_PASS']
        subprocess.run(cmd,env=env,check=True)
        subprocess.run([tool(sdk,'apksigner'),'verify','--verbose','--print-certs',str(output)],env=env,check=True)
        run(tool(sdk,'zipalign'),'-c','4',output)
    print('Built signed HACER companion:',output)

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--sdk',required=True);p.add_argument('--jdk',default=os.environ.get('JAVA_HOME'),required=not bool(os.environ.get('JAVA_HOME')));p.add_argument('--keystore',required=True);p.add_argument('--output',required=True);p.add_argument('--bc-jar',required=True);p.add_argument('--alias',default='hacer13');a=p.parse_args();build(a.sdk,a.jdk,a.keystore,a.output,a.bc_jar,a.alias)
