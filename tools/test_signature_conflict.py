#!/usr/bin/env python3
"""Disposable emulator test: a different certificate must NOT update the same package.
Never use against a user's installed app. Creates only an ephemeral test key.
"""
import argparse,json,os,pathlib,secrets,shutil,subprocess,sys,tempfile

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--sdk',required=True);p.add_argument('--apk',required=True);p.add_argument('--report',required=True);a=p.parse_args()
    root=pathlib.Path(__file__).resolve().parents[1];sys.path.insert(0,str(root/'LAC-tools'));from inspect_xapk import tool
    adb=pathlib.Path(a.sdk)/'platform-tools'/('adb.exe' if os.name=='nt' else 'adb')
    if subprocess.check_output([str(adb),'shell','getprop','ro.kernel.qemu'],text=True).strip()!='1':raise SystemExit('Only disposable Android emulators are permitted for this test')
    badging=subprocess.check_output([tool(a.sdk,'aapt2'),'dump','badging',a.apk],text=True)
    if "package: name='com.hacer13.core'" not in badging:raise SystemExit('Only the HACER companion test package is permitted')
    subprocess.run([str(adb),'install','-r',a.apk],check=True,capture_output=True,text=True)
    with tempfile.TemporaryDirectory(prefix='h13-certificate-test-') as d:
        keystore=pathlib.Path(d)/'test.jks';other=pathlib.Path(d)/'other.apk';env=os.environ.copy();env['H13_EPHEMERAL_PASS']=secrets.token_urlsafe(24)
        subprocess.run(['keytool','-genkeypair','-alias','other','-keyalg','RSA','-keysize','2048','-validity','2','-dname','CN=H13 Ephemeral Test','-keystore',str(keystore),'-storepass:env','H13_EPHEMERAL_PASS','-keypass:env','H13_EPHEMERAL_PASS'],env=env,check=True,capture_output=True)
        subprocess.run([tool(a.sdk,'apksigner'),'sign','--ks',str(keystore),'--ks-key-alias','other','--ks-pass','env:H13_EPHEMERAL_PASS','--out',str(other),a.apk],env=env,check=True,capture_output=True)
        result=subprocess.run([str(adb),'install','-r',str(other)],capture_output=True,text=True)
        combined=result.stdout+result.stderr
        if result.returncode==0 or 'INSTALL_FAILED_UPDATE_INCOMPATIBLE' not in combined:raise RuntimeError('Expected signature conflict was not observed: '+combined)
        installed=subprocess.check_output([str(adb),'shell','pm','list','packages','com.hacer13.core'],text=True)
        if 'package:com.hacer13.core' not in installed:raise RuntimeError('Original test app unexpectedly missing')
    out=pathlib.Path(a.report);out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps({'result':'passed','expected_error':'INSTALL_FAILED_UPDATE_INCOMPATIBLE','original_install_retained':True,'device':'disposable-emulator','game_install_touched':False},indent=2)+'\n')
    print('Passed: Android rejected a same-package update with a different certificate; original test install retained.')
if __name__=='__main__':main()
