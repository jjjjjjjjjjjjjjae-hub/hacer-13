# HACER Core 1.0 — Android (LAC demo)

**Status:** Source-code prototype; build validation pending. This app does not patch, inject into, or change the LAC game. Floating MOD MENU items are visual previews only.

## What works
- Standalone Android app (Android 13/API 33 or newer).
- Overlay menu with user-granted *Display over other apps* permission; close button.
- On app launch, checks `updates/lac/release.json` on this GitHub repository (at most once every 6 h, or on demand).
- Authenticates downloaded data bytes using the hard-coded Ed25519 public key, with the private signing key held outside this repo.
- Only accepts structured **data-only menu configuration** with a higher `revision`; refuses unsigned/modified/oversized/unexpected data.
- Stores config in app-private storage using Android AtomicFile, retains the previous version for manual rollback.
- No root, no Accessibility Service, no executable code downloads, no silent installs, no self-spreading behavior.

## Build via GitHub Actions
Open **Actions → HACER Core Android → Run workflow**, then download the **HACER-Core-1.0-debug** artifact. The artifact contains a debug/test APK. GitHub Actions also runs after pushes to `core/**` or its workflow file.

## Build locally
Open this `core/` directory as an Android Studio Gradle project. Use JDK 17, Android SDK 35. Gradle 8.11.1 and Android Gradle Plugin 8.9.2. Run `gradle assembleDebug`. Output: `core/app/build/outputs/apk/debug/app-debug.apk`.

## Signed menu updates without reinstalling game
1. Keep the Ed25519 **private key OFF GitHub**, ideally securely stored offline.
2. Copy the desired JSON menu schema as `payload.json` with `schema=1`, `min_core=1`, and `revision` greater than the installed revision. Allowed feature fields: `id`, `title`, `description`.
3. Install `cryptography` on your computer; run:
   `python tools/sign_release.py path/to/ed25519_private_key.pem payload.json updates/lac/release.json`
4. Commit only the new `updates/lac/release.json`; existing installations will check and use that signed **data** when launched/checked. No 400 MB game archive re-download.

**Important:** APK application-code upgrades are different and require Android's APK signing/install process. An Ed25519 menu signature does not authorize modifying LAC and cannot replace the game's APK signature. Overlay lifetime may be limited by Android background-service rules; start it manually after granting permission.
