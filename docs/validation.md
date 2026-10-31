# Validation of the public-source export

Date: 2026-10-31. These results are separate from historical research results.

## Host checks

- Built from source with official Android platform 30 and Build Tools 35.0.0; no copied SDK/JAR/key in the repository.
- Negative device/Android matches and a known SHA-256 vector passed.
- Inspected APK DEX class definitions: no packaged Xposed API implementations; only module classes. No private keys, JARs or native libraries in the APK.
- Corrected Xposed `hookMethod`/`hookAllMethods` return types, removed duplicate hook registration, and changed experimental overlay suppression to default off.

## Reversible on-device smoke test

The freshly built public module code was temporarily signed with the existing private research key so it could replace the existing same-package module without uninstalling it. The original BSG APK was unchanged and its full hash verified. Logs confirmed the new exact-device/APK guard succeeded on each start.

- Three cold process launches stayed alive at the six-second observation point: **3/3**.
- Two successive captures in one session produced new JPEGs: **2/2**, detected after approximately **7.02 / 8.05 seconds** (polling/command overhead included).
- Logs confirmed six-frame same-exposure plans for both captures, planned 80ms and analog gain16. This is the native plan, not a per-frame actual-exposure measurement.
- Visual inspection confirmed non-black saved images of a very close/obscured static surface. This is a pipeline smoke test, not detail/focus/color validation.
- The original module was reinstalled and its exact pre-test hash verified; changed runtime-property values and the handset's initial asleep state were restored. A quoting error while clearing empty properties was corrected and the final values rechecked.

The publication APK is re-signed with a new release key stored outside the repositories. Its ZIP payload (DEX, manifest and assets) is compared byte-for-byte with the on-device-tested APK; the signer differs. Build/signature/payload hashes are attached as release assets. No claim is made that the new signing identity itself was installed on the handset.

## Limits

No fresh front-camera/video/motion/bright-to-dark-AE/long-duration regression was performed. Full bracketing and the known AE problem remain open. Historical 6/6 + 2/2 and image-quality measurements remain historical, not additional successes of this release build.

Aggregate smoke data: [public-source-smoke.json](../evidence/public-source-smoke.json). Private raw logs, photos, rollback APK and signing keys are not redistributed.
