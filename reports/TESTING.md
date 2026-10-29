# HACER Core 1.0.1 — verification record

Date: 2026-10-29. This file will be updated with the exact build/run results.

| Check | Result |
|---|---|
| Java updater tests | 30 passed locally and in Actions run 40931540366, including non-blocking reads, RFC 8032 verification vector and SPKI validation |
| Python signing/archive tests | 12 passed locally |
| Original XAPK ZIP CRC | Passed |
| Original APK Android v2 signature | Verified with Android build-tools 35.0.0 |
| APK/OBB package + version | `com.MA.LAC`, version code 60, matched |
| Original OBB | Read-only; SHA-256 recorded in xapk-audit.json |
| Android APK/AAR build and lint | Passed at d6fc963 in Actions run 40931540366 |
| Android emulator UI / foreground overlay / network updater | All 3 passed on Android 15 at d6fc963. That run failed only during screenshot collection after Gradle removed the app-owned evidence. Shell-owned evidence collection is being rerun. |
| Actual LAC runtime and embedded menu | Not performed; no claim of compatibility |
| God Mode / High Jump / Speed | Demo UI only; no gameplay implementation |

The JVM tests cover pinned keys, tampered signatures, incorrect content hashes, interrupted downloads, rollback and retained replay floor, corrupted cache recovery, missing/malformed state, concurrent installations, URL restrictions, executable/unknown paths, invalid schemas, size limits and PNG dimensions.

The instrumented tests exercise the actual source adapter in PreviewActivity, menu open/drag/collapse/close/reattach, Android Ed25519 and live signed GitHub data, and foreground overlay start/stop. A preview screenshot is captured by the test; it is not a screenshot of LAC. Pending tests must not be described as passed.

Process-level fault injection is not a hardware power-loss test. State files use atomic rename and file fsync; directory fsync is best-effort where Android filesystems support it. App data clearing resets the local replay floor. LAC saves are never read or changed; a companion cannot back up another app's private saves without that app's supported export facility.
