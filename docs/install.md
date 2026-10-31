# Install, baseline and rollback

This alpha requires a rooted Android 11 TYH212U/ARES with a working LSPosed environment. Installing or configuring LSPosed itself is outside this repository. Only use the exact original BSG APK identified in the README. Obtain any required compatibility service from its original source; a Google-services provider was needed in the original setup ([Gcam Services Provider](https://github.com/lukaspieper/Gcam-Services-Provider)).

1. Preserve your current module, preferences and runtime-property snapshot locally. Do not publish full snapshots.
2. Build the module or download this repository's explicitly signed prerelease. Install the module, enable its LSPosed scope **only for `com.android.MGC_9_6_080`**, then restart GCam. Do not enable two independent modules providing these same hooks simultaneously.
3. In BSG settings use Interface/Model Blueline, Google AWB=0, HDR+ enhanced, and camera whitelist 0/1. Menu labels are build-specific. The JSON baseline is documentation, not an XML configuration automatically imported by the app.
4. Remove old experiment settings by explicitly setting baseline runtime properties, then restart the target process:

```sh
adb shell setprop debug.gcam.burst.frames 6
adb shell setprop debug.gcam.fix.uniformburst 1
adb shell setprop debug.gcam.fix.groupedexposure 0
adb shell am force-stop com.android.MGC_9_6_080
adb shell am start -n com.android.MGC_9_6_080/com.android.camera.CameraLauncher
```

Disable additional probes/experiments you previously enabled; the full baseline is in [config/baseline.json](../config/baseline.json). Properties are read at hook installation in many cases and usually disappear on reboot; application preferences persist. Do not write preference XML while the app is running.

A module signed with a new key cannot update a same-package installation signed by your old private research key. Android may require uninstalling the old module first; preserve it and its settings before doing so. This does not require uninstalling or re-signing GCam. Read the release notes for the signer used by that release.

Rollback: disable this LSPosed module and restart GCam, or reinstall your saved compatible module. For shot-related AE freeze, the observed manual workaround is switching Night Sight → Photo; a successful UI AE-mode change alone did not restore exposure. A newly saved JPEG is not proof that the next capture and AE are healthy.

No root-system camera library or HAL modification is required by this module. Do not flash unrelated firmware or libraries as part of these instructions.
