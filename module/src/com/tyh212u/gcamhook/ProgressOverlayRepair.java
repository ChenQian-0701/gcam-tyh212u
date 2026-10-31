package com.tyh212u.gcamhook;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Keeps GCam's centre viewfinder spinner from latching on.
 *
 * With DistributorRepair active the progress overlay is shown at startup and
 * never hidden, and the shutter stays disabled for the whole session. The
 * overlay itself is only a spinner, so suppressing its "show" is harmless even
 * if the underlying readiness signal is still missing.
 *
 * adb shell setprop debug.gcam.fix.nooverlay 1
 */
final class ProgressOverlayRepair {

    private static final String PROP = "debug.gcam.fix.nooverlay";
    private static volatile int logged = 0;

    private ProgressOverlayRepair() {
    }

    static boolean enabled() {
        return "1".equals(HookEntry.prop(PROP, "0"));
    }

    static void install(ClassLoader cl) {
        try {
            Class<?> overlay = XposedHelpers.findClass(
                    "com.google.android.apps.camera.progressoverlay.ProgressOverlay", cl);
            // a() is the "show" side (declared first, right after the constructor);
            // b() is its counterpart. Only the show side is suppressed -- blocking
            // b() as well would make the overlay impossible to clear.
            try {
                XposedHelpers.findAndHookMethod(overlay, "a", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (logged < 8) {
                            logged++;
                            XposedBridge.log("TYH212U-HOOK: [overlay] a() (show) suppressed");
                        }
                        param.setResult(null);
                    }
                });
                XposedBridge.log("TYH212U-HOOK: [overlay] hooked a()");
            } catch (Throwable t) {
                XposedBridge.log("TYH212U-HOOK: [overlay] a hook failed: " + t);
            }
            try {
                XposedHelpers.findAndHookMethod(overlay, "b", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (logged < 16) {
                            logged++;
                            XposedBridge.log("TYH212U-HOOK: [overlay] b() (hide) observed");
                        }
                    }
                });
                XposedBridge.log("TYH212U-HOOK: [overlay] hooked b() (observe only)");
            } catch (Throwable t) {
                XposedBridge.log("TYH212U-HOOK: [overlay] b hook failed: " + t);
            }
        } catch (Throwable t) {
            XposedBridge.log("TYH212U-HOOK: [overlay] install failed: " + t);
        }
    }
}
