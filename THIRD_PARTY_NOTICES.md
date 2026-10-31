# Third-party notices and source provenance

Original adapter implementations, tooling, documentation and report composition: Copyright 2026 ChenQian-0701 and contributors, Apache-2.0. Research was developed through device experiments, logs, API inspection and analysis of the exact proprietary BSG build identified in the README. No claim of clean-room reimplementation is made.

## Xposed runtime API

`module/stubs/de/robv/android/xposed/` contains declaration-only compile-time shims for the external runtime API, not an embedded Xposed implementation. The declarations were written for compilation and public export corrects `hookMethod` / `hookAllMethods` return types against the upstream API. They are **excluded from the APK DEX**. At runtime LSPosed provides these classes.

Upstream API attribution: XposedBridge, Copyright 2013 rovo89, Tungstwenty, Apache License 2.0. Source: https://github.com/rovo89/XposedBridge (art branch); upstream notice: https://github.com/rovo89/XposedBridge/blob/art/NOTICE.txt . The framework and its bundled libraries are not redistributed.

## Proprietary camera software

Google Camera, BSG modifications, encrypted payloads, images/resources and native processing libraries remain under their respective rights. They are not included or relicensed. Obfuscated class/method names and JNI signatures in the adapter identify interoperability boundaries. Original APKs must be obtained separately from their original distribution source.

## External build dependencies

JDK, Android SDK/platform/build tools, D8/R8 and apksigner retain their upstream licenses. SDK packages are downloaded from Google into an external user cache; no JAR or SDK binary is committed or put in the module APK. No historical patch/signature-check bypass scripts or original-app re-signing key are included.

## Evidence

The supplied crops and aggregate scene results are from the project owner's submitted test scene. Full source photographs, GPS metadata, faces, raw device identifiers and unrelated assets are not redistributed. Published historical prose is project documentation; linked outside publications retain their rights.
