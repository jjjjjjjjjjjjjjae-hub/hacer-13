# HACER 13 — authorized source integration and local LAC audit

The repository contains no LAC APK, OBB or XAPK. The original supplied archive is read only. Its APK/OBB package/version and original Android certificate were checked; see ../reports/xapk-audit.json. No working patched LAC build is claimed.

## Preferred source integration

Use the hook Gradle project and its updater project dependency in a host you are permitted to change. After the host Activity creates its content:

    HacerHook.attach(this);

In its onDestroy:

    HacerHook.detach(this);

Requires Android 13+ and INTERNET for user-initiated signed data checks. The menu stays inside the host window; it needs no system overlay permission. attach is idempotent per Activity and has no static Activity reference. Core's PreviewActivity exercises this exact adapter.

Build the AAR plus its dependency:

    gradle -p LAC-tools :hook:assembleRelease :updater:jar :updater:copyRuntimeLibs

An AAR alone does not embed transitive JAR dependencies. Include updater.jar and the pinned BC JAR from core/updater/build/runtime-libs alongside hook-release.aar (or use the Gradle project dependencies). All switches are DEMO; there is no Unity memory manipulation, gameplay bridge, anti-cheat or license bypass.

## Read-only XAPK validation

    python LAC-tools/inspect_xapk.py /private/input.xapk --sdk /path/to/Android/Sdk --report /private/audit.json

The auditor streams CRC/SHA-256 checks, rejects traversal/symlinks/duplicate entries and split-APK sets, reads APK metadata using aapt2, verifies Android signatures using apksigner, and requires the OBB package/version to match. Without SDK it reports structure-only and never claims signature or runtime validation.

## Legacy local patcher: unverified compatibility

The local patcher is retained for an independently authorized build. It is not run on the proprietary game in this development task. It requires Apktool, SDK 35 / build-tools 35.0.0, JDK 17, Gradle 8.11.1, a private user keystore outside the repository and two explicit review inputs:

    python LAC-tools/patcher/patch_xapk.py /private/input.xapk /private/output.xapk --sdk /path/to/sdk --keystore /private/hacer13.jks --authorized-local-modification --expected-apk-sha256 REVIEWED_APK_HASH

It validates the original APK/OBB before patching, changes only the declared SDK minimum and visible Activity UI entry point, adds the shared compiled menu/updater DEX, rebuilds, aligns, signs, verifies, and streams the original OBB unchanged. The post-packaging OBB hash must match. It does not prove Unity/game-side compatibility or licensing permission.

A new certificate on the SAME package cannot update an official installation. It is not a separately installable package merely because its certificate changed. Do not erase an installed game or assume its private saves were backed up. The tools never uninstall it, bypass Android's signature checks or access private game saves. Use the game's supported export/backup feature before any independently authorized manual installation experiment.

Only companion behavior is exercised by disposable-emulator CI, including a deliberate certificate-conflict test on com.hacer13.core. No copyrighted game is uploaded to Actions.
