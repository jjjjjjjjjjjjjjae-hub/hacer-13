# HACER Core 1.0.1 — final verification

Date: 2026-10-29. Tested implementation commit: 01eaf84d62f7d2d201c3510d0f0cdb3180a7877b.

[Successful Android build and emulator run](https://github.com/jjjjjjjjjjjjjjae-hub/hacer-13/actions/runs/40934969183).
Exact artifact IDs, sizes, SHA-256 hashes and signing identities are recorded in [build-artifacts.json](build-artifacts.json).

| Check | Result |
|---|---|
| Java updater tests | 30 passed locally and in GitHub Actions |
| Python signing/archive tests | 12 passed locally and in Actions |
| Android SDK 35 APK + integration AAR compilation | Passed |
| Android lint | Passed; warnings are available in the build artifact |
| Android 15 x86_64 instrumentation | 4 passed, 0 failures, 0 errors, 0 skipped |
| Different-certificate update on the same package | Rejected by Android with INSTALL_FAILED_UPDATE_INCOMPATIBLE; original test installation retained |
| Repository file-size guard | Passed; no proprietary game archives or private signing keys committed |
| Original XAPK ZIP CRC / APK signature / APK-OBB match | Passed; see xapk-audit.json |
| Actual LAC process and embedded menu in the proprietary game | Not tested or modified |

## What the Android tests actually exercised

1. The exact HacerHook source adapter in PreviewActivity: H13 opens, the three DEMO switches render, the menu stays inside the viewport, its header moves it, collapse/reopen/close work, and reattaching is idempotent. The captured image is a real Android preview host, not LAC.
2. A real HTTPS fetch from the repository's release-v2.json, bundled Bouncy Castle Ed25519 verification, SHA-256/size verification of the data blobs, installation, and a repeated no-update check.
3. The companion overlay starts as a foreground service after test-device permission grants and stops through its Stop intent. Shipping code asks the user for notification and overlay permissions; the test grants these only on a disposable emulator.
4. The user opt-in schedules the periodic network job, Android's test shell forces that real JobService to execute, it installs signed data without pressing the manual update button, and user opt-out cancels the job. This verifies job execution, not an elapsed six-hour wall-clock interval. Android controls normal scheduling time.

After instrumentation, a separate disposable-emulator test installs the companion APK, tries a same-package APK signed with an ephemeral different key, verifies Android rejects it and confirms the original companion remains installed. It never touches a game installation. See [signature-conflict.json](signature-conflict.json).

## Updater guarantees covered by tests

The JVM tests cover valid signatures, wrong keys, tampered signatures, an RFC 8032 verification vector, malformed SPKI, hashes/sizes, changed-file reuse, interrupted downloads, failures before pointer commit, rollback with a retained replay floor across restart, recovery from corrupted cached data, missing/malformed state, concurrent installation, menu reads during slow downloads, URL restrictions, forbidden/duplicate paths, incompatible schemas, non-demo feature rejection, bounded downloads and PNG dimensions.

The client accepts an Ed25519-authenticated schema-2 inventory and only inert JSON/PNG presentation data. A file must have its signed size and SHA-256 before activation. A single atomic state pointer commits active bundle, backup and highest accepted revision together. Rendering does not wait for network I/O. No executable code loader or silent APK installer is present.

## APK artifacts and signing

- HACER-Core-1.0.1-CI-debug.apk is the exact APK used by the final Android instrumentation job. It has a temporary CI Android debug certificate.
- HACER-Core-1.0.1-signed.apk was separately built from the same implementation using javac, D8, aapt2, zipalign and apksigner, with the retained HACER keystore. Its Android v3 signature, package, minimum/target SDK and bundled license notice were checked locally. This separately signed binary itself was not installed on a device in this session.
- Both use package com.hacer13.core, version code 2, Android minimum 33 and target 35. Their certificates differ. They are alternatives; one cannot update the other in place. Future stable APK releases must use the retained HACER keystore.
- The integration artifact includes hook-release.aar, updater.jar and its pinned BC runtime dependency. Use Gradle project dependencies or include all exported components.

The signing material is retained separately and is not part of this public repository or its Actions artifacts. Ed25519 data signing and Android APK signing are separate identities. The first schema-2 presentation bundle contains 465 bytes across menu.json and theme.json. Updating these does not require downloading the game's XAPK.

## LAC / OBB audit and remaining limits

The supplied com.ma.lac_60(2).xapk is 426126131 bytes. Its original APK is 95484109 bytes, package com.MA.LAC, version code 60, version 1.9.7. The OBB is 406496175 bytes at Android/obb/com.MA.LAC/main.60.com.MA.LAC.obb. APK and OBB package/version match. APK v2 signing and all ZIP CRCs verified; full SHA-256 values are in [xapk-audit.json](xapk-audit.json). The original archive and resources remain unchanged and were not uploaded to GitHub or Actions.

No permission to embed into this proprietary LAC build has been established in the supplied materials. The tested integration is the source adapter in an authorized preview host and a companion overlay. The legacy local patcher remains opt-in and unverified against this game; it has not generated a claimed working patched LAC APK. God Mode, High Jump and Speed only toggle demo preferences.

No physical phone, original LAC runtime, gameplay bridge, frame-rate benchmark or hardware power-loss scenario was tested. The menu was visually inspected on the portrait emulator; production game orientation and device compatibility remain unverified.

Atomic rename and file fsync protect commits; directory fsync is best-effort on filesystems that allow it. App data clearing/uninstall resets the local replay floor; it is not hardware-backed. LAC saves are never accessed or modified, and the companion cannot back up another app's private data. Use the game's supported export facility before any independently authorized installation experiment.

Platform reference: [Android 14 JobScheduler permissions](https://developer.android.com/about/versions/14/behavior-changes-14).
Crypto reference and license: [Bouncy Castle Java](https://www.bouncycastle.org/download/bouncy-castle-java/); the 1.86 JAR SHA-256 is pinned and its notice is included in APK/AAR assets.
