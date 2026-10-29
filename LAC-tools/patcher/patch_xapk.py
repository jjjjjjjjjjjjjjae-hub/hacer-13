#!/usr/bin/env python3
"""Locally embed a visible data-only HACER 13 menu into a user-supplied LAC XAPK.

This build script requires: apktool 2.11+, Android build-tools (d8, zipalign,
apksigner), Java 17, Gradle and Android SDK. It never uploads the game archive.
An output APK must be signed with the same *custom* key for subsequent installs.
The original LAC signature is irreversibly replaced on a patched APK.
"""
import argparse
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
import hashlib
from pathlib import Path

EXPECTED_PKG = 'com.MA.LAC'

def run(*args, cwd=None):
    print('+', *map(str, args), flush=True)
    subprocess.run([str(a) for a in args], cwd=cwd, check=True)

def patch_smali(folder: Path):
    candidates = list(folder.glob('smali*/com/unity3d/player/UnityPlayerActivity.smali'))
    if len(candidates) != 1:
        raise RuntimeError(f'Expected 1 UnityPlayerActivity.smali, got {len(candidates)}; cannot safely patch this build')
    target = candidates[0]
    src = target.read_text(encoding='utf-8')
    methods = list(re.finditer(r'(?ms)^\.method[^\n]* onCreate\(Landroid/os/Bundle;\)V\n.*?^\.end method', src))
    if len(methods) != 1:
        raise RuntimeError(f'onCreate found {len(methods)} times: manual review needed')
    method = methods[0].group()
    if 'Lcom/hacer13/embedded/HacerHook;' in method:
        raise RuntimeError('HACER hook already present (avoid double patching)')
    if not re.search(r'^\s*return-void\s*$', method, re.M):
        raise RuntimeError('No return-void: cannot patch automatically')
    # Install UI after original onCreate has completed; attach is idempotent.
    hook = '    invoke-static {p0}, Lcom/hacer13/embedded/HacerHook;->attach(Landroid/app/Activity;)V\n'
    method_new = re.sub(r'(?m)^(\s*)return-void\s*$', lambda m: hook + m.group(1) + 'return-void', method)
    target.write_text(src[:methods[0].start()] + method_new + src[methods[0].end():], encoding='utf-8')
    print('Patched onCreate:', target.relative_to(folder))

def extract_aar(path: Path, target: Path):
    with zipfile.ZipFile(path) as z:
        with z.open('classes.jar') as src, target.open('wb') as dest:
            shutil.copyfileobj(src, dest)

def build(args):
    if not args.authorized_local_modification:
        raise SystemExit('Requires explicit --authorized-local-modification for a host you have permission to modify. Use the companion APK otherwise.')
    source = Path(args.xapk).resolve()
    output = Path(args.output).resolve()
    repo = Path(__file__).resolve().parents[2]
    if source == output or output.is_relative_to(repo):
        raise SystemExit('Output must be a new path outside the source repository; never overwrite your original XAPK')
    if not source.is_file(): raise SystemExit('Input XAPK not found')
    if not shutil.which('apktool'): raise SystemExit('Please install apktool in PATH')
    sdk = Path(args.sdk).expanduser().resolve()
    build_tools = sdk / 'build-tools' / args.build_tools
    sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
    from inspect_xapk import tool
    d8, align, signer = [Path(tool(sdk,x)) for x in ('d8','zipalign','apksigner')]
    for tool in (d8, align, signer):
        if not tool.exists(): raise SystemExit(f'Missing Android build tool {tool}')
    project = Path(__file__).resolve().parents[1]
    sys.path.insert(0,str(project))
    from inspect_xapk import audit
    inspected = audit(source, sdk)
    actual = next(e['sha256'] for e in inspected['entries'] if e['path'].endswith('.apk'))
    if actual != args.expected_apk_sha256:
        raise SystemExit('APK SHA-256 does not match the explicitly reviewed input')
    if not shutil.which('gradle'): raise SystemExit('Install Gradle 8.11.1 or compatible version')
    # Local build outputs only. NEVER run this inside the public repository on a copyrighted game archive.
    run('gradle', ':hook:assembleRelease', ':updater:jar', ':updater:copyRuntimeLibs', '--no-daemon', cwd=project)
    aar = project / 'hook/build/outputs/aar/hook-release.aar'
    if not aar.is_file(): raise RuntimeError('No hook AAR produced')
    output.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='hacer13-') as tmpstr:
        wd = Path(tmpstr)
        original = wd / 'original.apk'
        obb_path = None
        with zipfile.ZipFile(source) as source_zip:
            apps = [i for i in source_zip.infolist() if i.filename.endswith('.apk')]
            obbs = [i for i in source_zip.infolist() if i.filename.endswith('.obb')]
            if len(apps) != 1 or len(obbs) != 1: raise RuntimeError('Expected exactly one APK and one OBB in LAC XAPK')
            obb_path = obbs[0].filename
            if obb_path != 'Android/obb/com.MA.LAC/main.60.com.MA.LAC.obb':
                raise RuntimeError(f'Unexpected OBB path: {obb_path}')
            with source_zip.open(apps[0]) as f, original.open('wb') as o: shutil.copyfileobj(f,o)
        decoded = wd / 'decoded'
        run('apktool','d','-r','-f',str(original),'-o',str(decoded))
        # The shared verifier intentionally requires Android 13+. Apktool owns SDK metadata.
        apktool_config = decoded/'apktool.yml'
        metadata = apktool_config.read_text(encoding='utf-8')
        metadata, count = re.subn(r"(?m)^(\s*minSdkVersion:)\s*.*$",r"\1 '33'",metadata)
        if count != 1:
            raise RuntimeError('Cannot safely set Android 13 minimum in apktool metadata')
        apktool_config.write_text(metadata,encoding='utf-8')
        patch_smali(decoded)
        rebuilt = wd/'rebuilt.apk'
        run('apktool','b',str(decoded),'-o',str(rebuilt))
        jar = wd/'classes.jar'
        extract_aar(aar,jar)
        dex_dir = wd/'hook_dex';dex_dir.mkdir()
        updater_jar = repo/'core/updater/build/libs/updater.jar'
        runtime_jars = sorted((repo/'core/updater/build/runtime-libs').glob('*.jar'))
        if not runtime_jars: raise RuntimeError('Missing pinned crypto runtime dependency')
        run(d8,'--min-api','33','--lib',sdk/'platforms/android-35/android.jar','--output',dex_dir,jar,updater_jar,*runtime_jars)
        dex_files = [dex for dex in dex_dir.iterdir() if dex.name.endswith('.dex')]
        if not dex_files: raise RuntimeError('No hook DEX generated')
        # Appending a separate DEX avoids merging/replacing any of the game's existing DEX classes.
        apk_with_dex = wd/'hooked.apk'
        shutil.copyfile(rebuilt,apk_with_dex)
        with zipfile.ZipFile(apk_with_dex,'a',compression=zipfile.ZIP_DEFLATED) as zipout:
            existing = set(zipout.namelist())
            nums = [int(m.group(1) or '1') for name in existing if (m:=re.fullmatch(r'classes(\d*)\.dex',name))]
            for index, dex in enumerate(sorted(dex_files),start=max(nums)+1):
                zipout.write(dex,f'classes{index}.dex')
        aligned = wd/'aligned.apk'
        run(align,'-f','-p','4',apk_with_dex,aligned)
        signing_keystore = Path(args.keystore).expanduser().resolve()
        if not signing_keystore.is_file():
            raise RuntimeError('Missing user-owned signing keystore; create one with keytool outside the repository')
        signed = wd/'signed.apk'
        run(signer,'sign','--ks',signing_keystore,'--ks-key-alias',args.alias,'--ks-pass','env:HACER_KEYSTORE_PASS',
            '--out',signed,aligned)
        run(signer,'verify','--verbose',signed)
        # Stream OBB; never load 406 MB into RAM. Store (not recompress) to avoid CPU-heavy second compression.
        with zipfile.ZipFile(source) as zin, zipfile.ZipFile(output,'w',allowZip64=True) as zout:
            zout.write(signed,'com.MA.LAC-60.apk',compress_type=zipfile.ZIP_STORED)
            with zin.open(obb_path) as stream, zout.open(obb_path,'w',force_zip64=True) as target:
                shutil.copyfileobj(stream,target,4*1024*1024)
        with zipfile.ZipFile(output) as z:
            for entry in z.infolist():
                if z.getinfo(entry.filename).file_size < 0: raise RuntimeError('Invalid entry')
            assert len(z.namelist()) == 2
        print('Wrote local patched XAPK:',output,'size',output.stat().st_size)
        with zipfile.ZipFile(source) as before, zipfile.ZipFile(output) as after:
            from inspect_xapk import digest
            with before.open(obb_path) as a, after.open(obb_path) as b:
                if digest(a) != digest(b):
                    output.unlink(missing_ok=True)
                    raise RuntimeError('OBB changed; rejected output')
        print('New certificate, SAME package: it cannot update an official differently-signed LAC install. No automatic uninstall is performed. Export saves with the game-supported method first.')
        print('CAUTION: actual launch and game-side compatibility are NOT proven by packaging checks.')

if __name__ == '__main__':
    p=argparse.ArgumentParser()
    p.add_argument('xapk')
    p.add_argument('output')
    p.add_argument('--sdk',required=True)
    p.add_argument('--build-tools',default='35.0.0')
    p.add_argument('--keystore',required=True)
    p.add_argument('--alias',default='hacer13')
    p.add_argument('--authorized-local-modification',action='store_true')
    p.add_argument('--expected-apk-sha256',required=True)
    build(p.parse_args())
