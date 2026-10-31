package com.tyh212u.gcamhook;

import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Stage 41 probe: who reconfigures the capture session (mode switch path).
 *
 * A mode switch keeps the CameraDevice open but replaces the capture session
 * (stream list changes). To reuse that reconfigure path after a still capture
 * (to unstick the HAL AE), we first need the GCam method that triggers it.
 *
 * adb shell setprop debug.gcam.probe.sessioncfg 1
 */
final class SessionProbe {

    private static final String PROP = "debug.gcam.probe.sessioncfg";
    private static final AtomicInteger LOGS = new AtomicInteger();
    private static final java.util.Set<String> SIGS =
            java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<String>());

    private SessionProbe() {
    }

    static boolean enabled() {
        return "1".equals(HookEntry.prop(PROP, "0"));
    }

    static void install(ClassLoader cl) {
        try {
            Class<?> cb = XposedHelpers.findClass(
                    "android.hardware.camera2.CameraCaptureSession$StateCallback", cl);
            XposedHelpers.findAndHookMethod("android.hardware.camera2.impl.CameraDeviceImpl", cl,
                    "createCaptureSession", java.util.List.class, cb, android.os.Handler.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            try {
                                String sig = sigOf();
                                if (sig == null || !SIGS.add(sig) || LOGS.incrementAndGet() > 40) {
                                    return;
                                }
                                XposedBridge.log("TYH212U-HOOK: [sessioncfg] createCaptureSession from "
                                        + sig + "\n" + stack(20));
                            } catch (Throwable ignored) {
                            }
                        }
                    });
            XposedBridge.log("TYH212U-HOOK: [sessioncfg] createCaptureSession(List) hooked");
            try {
                Class<?> cfg = XposedHelpers.findClass(
                        "android.hardware.camera2.params.SessionConfiguration", cl);
                XposedHelpers.findAndHookMethod("android.hardware.camera2.impl.CameraDeviceImpl", cl,
                        "createCaptureSession", cfg, logHook());
                XposedBridge.log("TYH212U-HOOK: [sessioncfg] createCaptureSession(SessionConfiguration) hooked");
            } catch (Throwable t) {
                XposedBridge.log("TYH212U-HOOK: [sessioncfg] cfg hook failed: " + t);
            }
            try {
                XposedHelpers.findAndHookMethod("android.hardware.camera2.impl.CameraDeviceImpl", cl,
                        "createConstrainedHighSpeedCaptureSession", java.util.List.class, cb,
                        android.os.Handler.class, logHook());
                XposedBridge.log("TYH212U-HOOK: [sessioncfg] highSpeed hooked");
            } catch (Throwable t) {
                XposedBridge.log("TYH212U-HOOK: [sessioncfg] highSpeed hook failed: " + t);
            }
        } catch (Throwable t) {
            XposedBridge.log("TYH212U-HOOK: [sessioncfg] hook failed: " + t);
        }
    }

    private static XC_MethodHook logHook() {
        return new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam p) {
                try {
                    String sig = sigOf();
                    if (sig == null || !SIGS.add(sig) || LOGS.incrementAndGet() > 40) {
                        return;
                    }
                    XposedBridge.log("TYH212U-HOOK: [sessioncfg] config from " + sig + "\n" + stack(20));
                } catch (Throwable ignored) {
                }
            }
        };
    }

    private static String sigOf() {
        for (StackTraceElement e : new Throwable().getStackTrace()) {
            String c = e.getClassName();
            if (c.startsWith("com.tyh212u") || c.startsWith("epWSX") || c.equals("t") || c.equals("m")
                    || c.startsWith("java.lang.Thread")) {
                continue;
            }
            return c + "#" + e.getMethodName();
        }
        return null;
    }

    private static String stack(int max) {
        StackTraceElement[] st = new Throwable().getStackTrace();
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (StackTraceElement e : st) {
            String c = e.getClassName();
            if (c.startsWith("com.tyh212u.gcamhook") || c.startsWith("java.lang.Thread")) {
                continue;
            }
            sb.append("\n    at ").append(e);
            if (++n >= max) {
                break;
            }
        }
        return sb.toString();
    }
}
