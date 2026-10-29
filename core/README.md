# HACER Core 1.0.1

Android 13+ native companion and signed data updater. See the repository README and reports/TESTING.md for actual results.

Build: JDK 17, Gradle 8.11.1, SDK 35 / build-tools 35.0.0.

    gradle -p core :updater:test :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :hook:assembleRelease

The same MenuView and HacerHook source adapter run in PreviewActivity and the authorized integration AAR. Companion overlay and notifications are explicit opt-ins. H13 and the header can be dragged; the menu can be opened, collapsed and closed. Gameplay controls are DEMO only.

The :updater module is pure Java with org.json supplied by Android and a bundled, SHA-256-pinned Bouncy Castle 1.86 lightweight Ed25519 verifier. It avoids AndroidKeyStore's incompatible Ed25519 public-key import. JVM tests use the pinned test-only JSON dependency. There is no embedded third-party game.

Updates use release-v2.json, the pinned schema-2 Ed25519 public key and hash-addressed JSON/PNG blobs. All files are validated before an atomic state pointer activates the bundle. Last-known-good presentation and highest accepted revision are independent; a rollback never reduces the network replay floor. Downloads use a separate writer lock so current-menu reads do not wait for network I/O.

Background checks are scheduled by the user's explicit toggle. Android chooses their actual execution time. Overlay service is user-started and non-sticky, with a visible foreground notification and Stop action. No Accessibility, device-admin, root, automatic APK installs or cross-app save access is requested.

For a stable release, keep a keystore outside the repository, supply HACER_KEYSTORE / HACER_KEYSTORE_PASS / HACER_KEY_ALIAS to Gradle, and use :app:assembleRelease. The offline tools/build_standalone.py alternative uses javac, D8, aapt2, zipalign and apksigner with that same private keystore. CI debug artifacts use a separate temporary debug certificate.
