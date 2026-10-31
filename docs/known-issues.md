# Current issues and claim boundaries

- Full short/long exposure bracketing is not reliably repaired. Grouped 8-frame experiments produced real multiple exposures and outputs, but also later regressed to Base frame -1. The `half` split is an experiment, not a validated release solution.
- Standard HDR+ may save a JPEG and fail to restore the next-capture resource gate. Do not call this path stable based on historical successful saves.
- After AE-OFF captures, the observed HAL can keep actual exposure/ISO frozen even after results report AE ON/unlocked. Rebuilding the capture session through a mode switch restored it; the AE-trigger experiment did not. A seamless general repair remains open.
- The single-camera `[false]` availability override is a device/build-specific workaround. It does not prove every single-camera unavailable state is safe to ignore.
- `PayloadBoundaryRepair` observes inputs/native acceptance; its beneficial effect may involve timing/deoptimization. The mechanism is not established. Removing it requires a controlled regression.
- Fixed noise and flat shading fallbacks are compatibility inputs, not calibrated sensor models. Same-exposure bursts sacrifice the tested original bracketing plan.
- Converter levels apply only to the exact synthetic-YUV profile. Hidden camera interfaces and real RAW are not supported here. The profile guard checks device/Android/APK, not per-pointer metadata ownership or every camera format.
- Native acceptance does not guarantee later consistency/merge use. EXIF does not recover per-frame merge weights.
- Historical comments in the research source include hypotheses superseded by later experiments; this file and the curated evidence index define the current claims.
