package com.tyh212u.gcamhook;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Stage 40 fix: ignore a single-camera "unavailable" sample in GCam's
 * waitForCamerasAllAvailable transform.
 *
 * Attribution (stage 39, one-to-one): every time CaptureModule went to
 * ready=false (grey spinner + disabled shutter) the lnm transform had just
 * returned false, 4 ms earlier, for input [false] -- a one-element availability
 * list. That single camera is the one GCam itself is streaming from, so the
 * "all cameras available" check can never succeed while preview is running;
 * GCam then waits and the photo module stays not-ready (12-58 s, sometimes
 * until the app is restarted).
 *
 * The override is deliberately narrow: only a List of size 1 whose only element
 * is Boolean.FALSE is rewritten to TRUE. Multi-camera lists (e.g. [true,false])
 * are left untouched, so real availability handling keeps working.
 *
 * adb shell setprop debug.gcam.fix.camavailability 0   # to disable
 */
final class AvailabilityRepair {

    private static final String PROP = "debug.gcam.fix.camavailability";
    private static final AtomicInteger HITS = new AtomicInteger();

    private AvailabilityRepair() {
    }

    static boolean enabled() {
        return !"0".equals(HookEntry.prop(PROP, "1"));
    }

    static void install(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod("lnm", cl, "apply", Object.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        Object in = p.args != null && p.args.length > 0 ? p.args[0] : null;
                        if (!(in instanceof List)) {
                            return;
                        }
                        List<?> l = (List<?>) in;
                        if (l.size() != 1 || !Boolean.FALSE.equals(l.get(0))) {
                            return;
                        }
                        p.setResult(Boolean.TRUE);
                        if (HITS.incrementAndGet() <= 10) {
                            XposedBridge.log("TYH212U-HOOK: [availability] single-camera unavailable -> available ("
                                    + HITS.get() + ")");
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
            XposedBridge.log("TYH212U-HOOK: [availability] lnm.apply hooked");
        } catch (Throwable t) {
            XposedBridge.log("TYH212U-HOOK: [availability] hook failed: " + t);
        }
    }
}
