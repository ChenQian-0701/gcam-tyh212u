# Building the module

Requirements: Linux x86_64 for the provided SDK downloader, Python 3.10+, JDK 17+ (`java`, `javac`, `keytool`), Android platform 30 and SDK Build Tools 35.0.0. On other hosts install matching official SDK packages and pass `--sdk`; the module source is not inherently Linux-only.

```sh
python3 tools/setup_sdk.py
python3 tools/build_module.py --mode debug
python3 tools/check_release.py
```

`setup_sdk.py` downloads the official Google `platform-30_r03.zip` and `build-tools_r35_linux.zip` into a user cache, verifies checksums from Google's repository metadata and records SHA-256 locally. It does not download GCam. The SDK packages retain their own licenses; consult their included license files and [Android SDK terms](https://developer.android.com/studio/terms). You can instead use your existing licensed SDK via `--sdk`, `ANDROID_HOME` or `ANDROID_SDK_ROOT`.

The build compiles declaration-only Xposed API stubs for type checking, then compiles and dexes only module classes. It packages a small resource-free APK, aligns it before signing and verifies the signature. No external SDK jars or Xposed stub implementations are packaged.

Unsigned output: `python3 tools/build_module.py --mode unsigned`.

Debug mode creates a persistent **development-only** key in the user cache. It does not use or expose the old research key. Debug keys on different machines are different, so debug builds are not interchangeable updates.

For a release, create and back up a separate keystore outside the repository, then supply its path/alias and `GCAM_STORE_PASSWORD` / `GCAM_KEY_PASSWORD` environment variables through your local secret mechanism:

```sh
python3 tools/build_module.py --mode release --keystore /secure/location/release.p12 --alias release
```

No password value is printed or passed as a command-line literal. Do not commit keys, secret files or environment dumps. Do not re-sign the original GCam APK. Release signing here signs **only this module**. Build timestamps/signatures may differ; this project does not promise bit-identical APKs across toolchains.

Host checks exercise rejected profiles/digests and package hygiene. They do not substitute for an on-device camera regression. Read [validation](validation.md) before relying on a binary.
