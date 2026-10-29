#!/usr/bin/env python3
"""Read-only, streaming audit. Never extracts an archive tree or uploads game bytes."""
import argparse, hashlib, json, os, pathlib, re, subprocess, tempfile, zipfile

def digest(stream):
    h=hashlib.sha256()
    while chunk:=stream.read(4*1024*1024): h.update(chunk)
    return h.hexdigest()

def safe_entries(z):
    seen=set();total=0
    for i in z.infolist():
        p=pathlib.PurePosixPath(i.filename)
        if i.filename in seen or p.is_absolute() or '..' in p.parts or '\\' in i.filename or ':' in i.filename:
            raise ValueError('Unsafe/duplicate archive entry')
        if (i.external_attr>>16)&0o170000==0o120000: raise ValueError('Symlinks not allowed')
        seen.add(i.filename);total+=i.file_size
        if total>8*1024**3 or i.file_size>4*1024**3: raise ValueError('Archive size limit exceeded')
    return z.infolist()

def tool(sdk,name):
    directory=pathlib.Path(sdk)/'build-tools'/'35.0.0'
    for suffix in ['', '.bat', '.exe']:
        p=directory/(name+suffix)
        if p.is_file(): return str(p)
    raise ValueError('Missing SDK tool: '+name)

def audit(path,sdk=None):
    report={'archive_bytes':pathlib.Path(path).stat().st_size,'status':'structure-only','runtime_tested':False}
    with zipfile.ZipFile(path) as z:
        entries=safe_entries(z);apks=[i for i in entries if i.filename.lower().endswith('.apk')];obbs=[i for i in entries if i.filename.lower().endswith('.obb')]
        if len(apks)!=1 or len(obbs)!=1: raise ValueError('Expected exactly one base APK and one OBB; split APK sets require separate handling')
        report['entries']=[]
        for i in entries:
            with z.open(i) as f: h=digest(f) # ZIP CRC is checked as every stream reaches EOF.
            report['entries'].append({'path':i.filename,'bytes':i.file_size,'sha256':h,'crc32':f'{i.CRC:08x}'})
        report['crc_verified']=True
        match=re.fullmatch(r'Android/obb/([^/]+)/main\.(\d+)\.([^/]+)\.obb',obbs[0].filename)
        if not match or match[1]!=match[3]: raise ValueError('Invalid OBB path/package')
        report['obb_package']=match[1];report['obb_version_code']=int(match[2])
        if sdk:
            with tempfile.TemporaryDirectory(prefix='h13-audit-') as d:
                apk=pathlib.Path(d)/'base.apk'
                with z.open(apks[0]) as src,apk.open('wb') as dst:
                    import shutil;shutil.copyfileobj(src,dst,4*1024*1024)
                badging=subprocess.check_output([tool(sdk,'aapt2'),'dump','badging',str(apk)],text=True)
                package=re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'",badging)
                if not package: raise ValueError('Cannot read APK manifest')
                report.update(package=package[1],version_code=int(package[2]),version_name=package[3])
                if package[1]!=match[1] or int(package[2])!=int(match[2]): raise ValueError('APK/OBB package or version mismatch')
                if package[1]!='com.MA.LAC': raise ValueError('Not the expected LAC package')
                cert=subprocess.run([tool(sdk,'apksigner'),'verify','--verbose','--print-certs',str(apk)],capture_output=True,text=True)
                report['signature_verified']=cert.returncode==0
                report['certificate_report']=cert.stdout
                if cert.returncode: raise ValueError('APK signature verification failed: '+cert.stderr[-800:])
                with zipfile.ZipFile(apk) as inner:
                    report['native_abis']=sorted({p.split('/')[1] for p in inner.namelist() if p.startswith('lib/') and p.endswith('.so')})
                report['status']='apk-obb-verified'
    return report

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('xapk');p.add_argument('--sdk',default=os.environ.get('ANDROID_HOME'));p.add_argument('--report',required=True);a=p.parse_args()
    r=audit(a.xapk,a.sdk);target=pathlib.Path(a.report);target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(r,indent=2)+'\n');print('Audit:',r['status'],'->',target)
if __name__=='__main__': main()
