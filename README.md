# GCam compatibility module for TYH212U

[简体中文](README.zh-CN.md) · [Build](docs/build.md) · [Install](docs/install.md) · [Known issues](docs/known-issues.md)

Experimental LSPosed runtime adapter for the China Telecom Tianyi One 2022, **TYH212U / ARES, UMS9620, Android 11 (API 30)**, using **BSG MGC 9.6.080 V51 MGC**. This repository contains the device implementation; general methods and reusable diagnosis/measurement tools live in [gcam-unisoc-research](https://github.com/ChenQian-0701/gcam-unisoc-research).

**Not a universal Unisoc port. Full exposure bracketing, standard HDR+ repeatability and post-shot AE recovery remain incomplete.** The adapter retains the original GCam APK/signature/native libraries and repairs compatibility at runtime. No proprietary GCam APK or native library is included.

## Exact target and input

- Package: `com.android.MGC_9_6_080`; version code `68013614`.
- Original APK SHA-256: `4ad2f119c3d3c70d05a31bf6fb70ce1d2ba22eed4b52097b37a5f2cea96c9c6d`.
- [Original download page](https://www.celsoazevedo.com/files/android/google-camera/dev-bsg/f/dl397/2/) (download separately).
- Entry guard requires model TYH212U, device ARES, API 30, the main app process and the exact target APK digest. Unknown profiles are skipped. Hash verification reads the APK once per target process and adds startup I/O.
- Supported configuration uses public cameras **0/1 only**. The observed input route is ISP YUV → existing BSG synthetic RAW10 → native GCam processing. It is not sensor RAW. The profile guard is not a general per-camera RAW detector; do not enable hidden or hardware-RAW interfaces with this profile.

## Included baseline

Google AWB=0; HDR+ enhanced `on`; 6 same-exposure frames; grouped exposure off. The module does not silently rewrite user preferences or clear runtime properties. Follow [the baseline](config/baseline.json) and [install instructions](docs/install.md).

Original device experiments observed 6/6 + 2/2 same-exposure enhanced saves/recovery in two sessions, a single-camera availability workaround, and a static-scene flat-region fluctuation reduction of roughly 42–56%. These are limited historical observations, not universal reliability or sensor-noise claims. See [evidence](docs/evidence-index.md), [quality results](docs/quality-results.md) and [publication validation](docs/validation.md).

## Build and contribute

```sh
python3 tools/setup_sdk.py
python3 tools/build_module.py --mode debug
python3 tools/check_release.py
```

Requires Python 3, JDK 17+ and the documented Android SDK tools. Debug signing is local; no keystore is in the repository. Release signing is explicit and uses a separately protected key. CI artifacts are not stable-key end-user releases.

For another Unisoc phone, start in the [general research repository](https://github.com/ChenQian-0701/gcam-unisoc-research) and establish a separate profile. Do not remove the guard and apply these numeric overrides blindly.

Original project code/docs: Apache-2.0. See [third-party notices and provenance](THIRD_PARTY_NOTICES.md). Legacy obfuscated API names are interoperability references; GCam's processing implementation is not open-sourced here.
