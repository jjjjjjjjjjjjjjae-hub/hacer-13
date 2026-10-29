# HACER 13 — LAC XAPK local integration prototype

**Goal:** Make an *in-game*, user-visible MOD MENU with a signed **data-only** GitHub updater, without storing a ~426 MB third-party game in GitHub. This kit is **source code only**. It has not yet generated a verified working game APK. The menu is demonstrative and does not modify health, speed, weapons, etc.

## Why not upload the XAPK to the repository?
- GitHub ordinary Git rejects an individual file over 100 MiB.
- LAC XAPK is ~426 MB, OBB uncompressed 406.5 MB, APK 95.5 MB.
- Do not redistribute the proprietary game without rights. Keep the original XAPK on your computer; commit patch tools and signed menu data only.

## What the patcher intends to do
1. Read the local `com.ma.lac_60(2).xapk` without uploading it.
2. Decode the APK with Apktool, hook `UnityPlayerActivity.onCreate` to show an `H13` button inside the game window.
3. Build a small Java hook DEX; embed it in the APK.
4. Rebuild, align, and **re-sign the APK with your own stable keystore**.
5. Repack **the original OBB unchanged** alongside the signed APK into local XAPK.
6. The in-game menu checks `updates/lac/release.json` at app launch (max once per 6 hours) and upon user action, verifies Ed25519 signed JSON and downloads only its small menu data; **never** downloads executable code.

## Requirements
- Android SDK 35, Android build-tools 35.0.0, Java 17, Gradle 8.11.1, Apktool 2.11+.
- Android 13+ for Ed25519 verification (menu UI supports Android 7+, but the signed updater reports unavailability on older Android versions).
- Original LAC game XAPK that you legally obtained.
- Keystore and `HACER_KEYSTORE_PASS` set in the environment. Keep keystore private, not in GitHub.

## Example command (Windows PowerShell)
```powershell
$env:HACER_KEYSTORE_PASS = 'YOUR_STRONG_PASSWORD'
python patcher/patch_xapk.py 'C:\path\com.ma.lac_60(2).xapk' 'C:\path\HACER13-LAC-patched.xapk' --sdk 'C:\Users\you\AppData\Local\Android\Sdk' --keystore 'C:\private\hacer13.jks' --alias hacer13
```

**Do not overwrite your official installed LAC package:** the modified APK's certificate differs from the original; first back up any saves. A re-signed APK may also fail in-game license/integrity checks. An XAPK packer/check is NOT a runtime smoke test.

## Current test status
- ZIP/XAPK structure and APK/OBB sizes observed from supplied sample.
- Source code is not compiled in this environment (Android SDK + Apktool not installed).
- **No modified APK or XAPK is being claimed as working.** Run a test on an authorized Android device before use.

## Future
- Build a compatible old-Android Ed25519 verifier.
- Add the actual Unity game integrations **only after legal permission and compatibility testing**.
- Add CI compilation test that uses only hook source code, no copyrighted binaries.
