package com.tyh212u.gcamhook;

import android.os.Build;
import android.os.StrictMode;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Checked before installing any repair or probe. No unknown-device override. */
final class CompatibilityGuard {
    private static Boolean verified;

    static synchronized boolean allow(XC_LoadPackage.LoadPackageParam p) {
        if (!CompatibilityProfile.matchesDevice(Build.MODEL, Build.DEVICE, Build.VERSION.SDK_INT)) {
            XposedBridge.log("TYH212U-HOOK: [profile] skipped unsupported model/device/Android version");
            return false;
        }
        if (!p.packageName.equals(p.processName)) return false;
        if (verified != null) return verified;
        verified = false;
        StrictMode.ThreadPolicy before = StrictMode.allowThreadDiskReads();
        try {
            if (p.appInfo != null && p.appInfo.sourceDir != null) {
                verified = CompatibilityProfile.APK_SHA256.equals(
                        CompatibilityProfile.sha256(p.appInfo.sourceDir));
            }
        } catch (Exception e) {
            XposedBridge.log("TYH212U-HOOK: [profile] cannot verify target APK; skipping hooks");
        } finally {
            StrictMode.setThreadPolicy(before);
        }
        XposedBridge.log("TYH212U-HOOK: [profile] exact-device and APK check=" + verified);
        return verified;
    }
}
