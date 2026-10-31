package com.tyh212u.gcamhook;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import android.hardware.camera2.CaptureRequest;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Stage 41 experiment: restart AE after a still capture.
 *
 * Symptom: shoot in a bright scene, then point at a dark scene -> the preview is
 * not re-exposed and stays black. Result metadata shows AE_MODE_ON / AE_LOCK=false
 * but the exposure time no longer changes (HAL AE stays converged on the bright
 * sample) until the camera session is rebuilt (mode switch) or the app restarts.
 *
 * GCam returns to AE_MODE_ON after the capture, but never sends an
 * AE_PRECAPTURE_TRIGGER, which some HALs need to re-run AE. This repair watches
 * for the AE OFF -> ON transition of the capture request builder and injects one
 * AE_PRECAPTURE_TRIGGER=START into the first TEMPLATE_PREVIEW request built after
 * that.
 *
 * adb shell setprop debug.gcam.fix.aerestart 1
 */
final class AeRestartRepair {

    private static final String PROP = "debug.gcam.fix.aerestart";
    private static final int TEMPLATE_PREVIEW = 1;

    private static final Map<Object, Integer> TEMPLATES =
            java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<Object, Integer>());
    private static volatile boolean PENDING = false;
    private static volatile int LAST_AE_MODE = -1;
    private static final AtomicInteger INJECTED = new AtomicInteger();

    private AeRestartRepair() {
    }

    static boolean enabled() {
        // Experiment only: request-level nudges did NOT unstick the HAL AE.
        return "1".equals(HookEntry.prop(PROP, "0"));
    }

    static void install(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod("android.hardware.camera2.impl.CameraDeviceImpl", cl,
                    "createCaptureRequest", int.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            try {
                                Object builder = p.getResult();
                                if (builder != null) {
                                    TEMPLATES.put(builder, (Integer) p.args[0]);
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log("TYH212U-HOOK: [aerestart] device hook failed: " + t);
        }
        try {
            Class<?> key = XposedHelpers.findClass("android.hardware.camera2.CaptureRequest$Key", cl);
            XposedHelpers.findAndHookMethod("android.hardware.camera2.CaptureRequest$Builder", cl,
                    "set", key, Object.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam p) {
                            try {
                                if (p.args[0] != CaptureRequest.CONTROL_AE_MODE || p.args[1] == null) {
                                    return;
                                }
                                int v = ((Number) p.args[1]).intValue();
                                if (LAST_AE_MODE == 0 && v == 1) {
                                    PENDING = true;
                                    XposedBridge.log("TYH212U-HOOK: [aerestart] AE OFF->ON, will inject precapture");
                                }
                                LAST_AE_MODE = v;
                            } catch (Throwable ignored) {
                            }
                        }
                    });
            XposedBridge.log("TYH212U-HOOK: [aerestart] builder.set hooked");
        } catch (Throwable t) {
            XposedBridge.log("TYH212U-HOOK: [aerestart] set hook failed: " + t);
        }
        try {
            XposedHelpers.findAndHookMethod("android.hardware.camera2.CaptureRequest$Builder", cl,
                    "build", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam p) {
                            try {
                                if (!PENDING) {
                                    return;
                                }
                                Integer tpl = TEMPLATES.get(p.thisObject);
                                if (tpl == null || tpl != TEMPLATE_PREVIEW) {
                                    return;
                                }
                                XposedHelpers.callMethod(p.thisObject, "set",
                                        CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                                        CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
                                XposedHelpers.callMethod(p.thisObject, "set",
                                        CaptureRequest.CONTROL_MODE, 1); // AUTO
                                XposedHelpers.callMethod(p.thisObject, "set",
                                        CaptureRequest.CONTROL_AE_LOCK, false);
                                XposedHelpers.callMethod(p.thisObject, "set",
                                        CaptureRequest.CONTROL_AE_MODE, 1); // ON
                                PENDING = false;
                                if (INJECTED.incrementAndGet() <= 10) {
                                    XposedBridge.log("TYH212U-HOOK: [aerestart] precapture injected into preview request ("
                                            + INJECTED.get() + ")");
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    });
            XposedBridge.log("TYH212U-HOOK: [aerestart] build hooked");
        } catch (Throwable t) {
            XposedBridge.log("TYH212U-HOOK: [aerestart] build hook failed: " + t);
        }
        XposedBridge.log("TYH212U-HOOK: [aerestart] installed");
    }
}
