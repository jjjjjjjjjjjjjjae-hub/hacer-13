# HACER 13 · HACER Core 1.0.1

Android 13+ companion with a movable H13 menu, an explicit source integration AAR and signed **data-only** GitHub updates. God Mode, High Jump and Speed are **DEMO controls**; they never change LAC health, movement, memory or networking.

## What is implemented

- Native dark menu: tap H13, drag H13 or the panel header, collapse, close, animated opening. The companion overlay asks for Android notification and overlay permissions and runs as a visible foreground service with a Stop action.
- `HacerHook.attach(Activity)` / `detach(Activity)` for a host you are authorized to modify. The included PreviewActivity uses this exact adapter. The AAR also needs `updater.jar` (or the Gradle project dependency).
- Ed25519-pinned inventory, SHA-256 and exact size for every resource; downloads only changed blobs. Strict JSON/PNG allowlist, bounded downloads, no redirects, no executable updates.
- Immutable staged bundles, one atomic active/backup/highest-revision pointer, startup verification and recovery. User rollback preserves the replay floor. Clearing app data/uninstalling resets that local trust state; this is not hardware-backed anti-rollback.
- Read-only APK/OBB audit, opt-in local packaging tools and a 90 MiB repository guard. No third-party APK, OBB or XAPK is committed or uploaded to CI.

## Build and test

JDK 17, Gradle 8.11.1, Android SDK/platform 35 and build-tools 35.0.0:

```sh
gradle -p core :updater:test :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :hook:assembleRelease
python -m pip install cryptography==44.0.3
python -m unittest discover -s tests -v
gradle -p core :app:connectedDebugAndroidTest
```

GitHub Actions builds an APK and AAR and runs instrumented tests on an Android 15 emulator. Actions artifacts are labelled **debug**; their temporary CI certificate is not a production signing identity. For a stable signed release set `HACER_KEYSTORE`, `HACER_KEYSTORE_PASS`, `HACER_KEY_ALIAS` and optionally `HACER_KEY_PASS`, then run `gradle -p core :app:assembleRelease`. Keep keys outside this repository. Android code updates always use the normal APK signing/update process.

## Publish data updates

Edit only `updates/lac/data/menu.json`, `theme.json` or bounded `resources/*.png` (optional `badge.png` is displayed). Use the separately retained schema-2 Ed25519 private key:

```sh
python tools/publish_data.py /private/HACER13_Ed25519_Private.pem updates/lac/data updates/lac --revision 2
```

Commit the changed blobs and `release-v2.json` together. Always increase revision, including after a rollback. Private signing keys are never needed in GitHub Actions. Schema 2 uses a newly provisioned public key baked into Core 1.0.1; the old schema-1 `release.json` and its key remain a separate legacy channel. Existing schema-1 clients need a normally signed APK update to migrate; no remote trust-key replacement is accepted.

## LAC status

The supplied `com.ma.lac_60(2).xapk` passes APK signature/ZIP CRC checks. APK package/version `com.MA.LAC`/60 matches the untouched OBB; game version is 1.9.7. See [audit](reports/xapk-audit.json) and [test report](reports/TESTING.md).

The LAC APK has **not** been patched, re-signed, redistributed or run in this development session. No permission to embed into this proprietary build has been established. Use the companion, or integrate the AAR in an authorized host. The legacy local patcher requires an explicit authorization flag and reviewed APK hash and remains unverified. A new certificate with the same package cannot replace an official installation; the tools never uninstall it or promise access to its saves.
