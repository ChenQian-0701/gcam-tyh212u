package com.tyh212u.gcamhook;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Stage 41 probe: preview AE diagnostics.
 *
 * Symptom being investigated: after a shot in a bright scene the viewfinder is
 * not re-exposed when the phone is pointed at a dark scene (preview looks black).
 * This probe logs AE mode / AE lock / AE state / exposure time / sensitivity as
 * they change (result side) and the AE fields GCam puts into its requests.
 *
 * adb shell setprop debug.gcam.probe.ae 1
 */
final class AeProbe {

    private static final String PROP = "debug.gcam.probe.ae";
    private static final AtomicInteger LOGS = new AtomicInteger();

    private static final Map<String, String> LAST = new java.util.concurrent.ConcurrentHashMap<String, String>();

    private AeProbe() {
    }

    static boolean enabled() {
        return "1".equals(HookEntry.prop(PROP, "0"));
    }

    static void install(ClassLoader cl) {
        try {
            Class<?> key = XposedHelpers.findClass("android.hardware.camera2.CaptureResult$Key", cl);
            XposedHelpers.findAndHookMethod("android.hardware.camera2.CaptureResult", cl, "get", key,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            try {
                                String n = nameOf(p.args[0]);
                                if (n == null) {
                                    return;
                                }
                                String v = String.valueOf(p.getResult());
                                String key = "res:" + n;
                                if (v.equals(LAST.get(key))) {
                                    return;
                                }
                                LAST.put(key, v);
                                if (LOGS.incrementAndGet() > 300) {
                                    return;
                                }
                                XposedBridge.log("TYH212U-HOOK: [ae] t=" + System.currentTimeMillis()
                                        + " result " + n + "=" + v);
                            } catch (Throwable ignored) {
                            }
                        }
                    });
            XposedBridge.log("TYH212U-HOOK: [ae] CaptureResult.get hooked");
        } catch (Throwable t) {
            XposedBridge.log("TYH212U-HOOK: [ae] result hook failed: " + t);
        }
        try {
            Class<?> builder = XposedHelpers.findClass("android.hardware.camera2.CaptureRequest$Builder", cl);
            Class<?> key = XposedHelpers.findClass("android.hardware.camera2.CaptureRequest$Key", cl);
            XposedHelpers.findAndHookMethod(builder, "set", key, Object.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        String n = nameOf(p.args[0]);
                        if (n == null) {
                            return;
                        }
                        String v = String.valueOf(p.args[1]);
                        String key = "req:" + n;
                        if (v.equals(LAST.get(key))) {
                            return;
                        }
                        LAST.put(key, v);
                        if (LOGS.incrementAndGet() > 400) {
                            return;
                        }
                        XposedBridge.log("TYH212U-HOOK: [ae] t=" + System.currentTimeMillis()
                                + " request " + n + "=" + v);
                    } catch (Throwable ignored) {
                    }
                }
            });
            XposedBridge.log("TYH212U-HOOK: [ae] builder.set hooked");
        } catch (Throwable t) {
            XposedBridge.log("TYH212U-HOOK: [ae] request hook failed: " + t);
        }
        XposedBridge.log("TYH212U-HOOK: [ae] installed");
    }

    private static String nameOf(Object k) {
        try {
            if (k == CaptureResult.CONTROL_AE_MODE) {
                return "aeMode";
            }
            if (k == CaptureResult.CONTROL_AE_LOCK) {
                return "aeLock";
            }
            if (k == CaptureResult.CONTROL_AE_STATE) {
                return "aeState";
            }
            if (k == CaptureResult.SENSOR_EXPOSURE_TIME) {
                return "exposureNs";
            }
            if (k == CaptureResult.SENSOR_SENSITIVITY) {
                return "iso";
            }
            if (k == CaptureRequest.CONTROL_AE_MODE) {
                return "aeMode(req)";
            }
            if (k == CaptureRequest.CONTROL_AE_LOCK) {
                return "aeLock(req)";
            }
            if (k == CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER) {
                return "aePrecapture(req)";
            }
            if (k == CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION) {
                return "aeComp(req)";
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
