package com.tyh212u.gcamhook;

import java.util.concurrent.atomic.AtomicInteger;

import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Stage 40 probe: audit CONTROL_AF_MODE on the request side.
 *
 * The ready-gate predicate rejects a metadata sample whose AF mode is OFF, and
 * that sample is persistent after the phone is moved. This probe logs every
 * builder.set(CONTROL_AF_MODE, ...) and every repeating request's AF/AE mode,
 * so we can tell whether GCam itself asks for AF OFF (port bug) or the HAL
 * reports OFF while AF-C was requested.
 *
 * adb shell setprop debug.gcam.probe.req 1
 */
final class RequestProbe {

    private static final String PROP = "debug.gcam.probe.req";
    private static final AtomicInteger LOGS = new AtomicInteger();
    private static volatile String lastSet = "";
    private static volatile String lastRepeat = "";
    private static volatile String lastResult = "";

    private RequestProbe() {
    }

    static boolean enabled() {
        return "1".equals(HookEntry.prop(PROP, "0"));
    }

    static void install(ClassLoader cl) {
        try {
            Class<?> builder = XposedHelpers.findClass("android.hardware.camera2.CaptureRequest$Builder", cl);
            Class<?> key = XposedHelpers.findClass("android.hardware.camera2.CaptureRequest$Key", cl);
            XposedHelpers.findAndHookMethod(builder, "set", key, Object.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        Object k = p.args[0];
                        if (k != CaptureRequest.CONTROL_AF_MODE) {
                            return;
                        }
                        String s = "builder.set(AF_MODE=" + p.args[1] + ")";
                        if (s.equals(lastSet) || LOGS.incrementAndGet() > 60) {
                            return;
                        }
                        lastSet = s;
                        XposedBridge.log("TYH212U-HOOK: [req] t=" + System.currentTimeMillis() + " " + s
                                + "\n" + stack(14));
                    } catch (Throwable ignored) {
                    }
                }
            });
            XposedBridge.log("TYH212U-HOOK: [req] builder.set hooked");
        } catch (Throwable t) {
            XposedBridge.log("TYH212U-HOOK: [req] builder hook failed: " + t);
        }
        try {
            XposedHelpers.findAndHookMethod("android.hardware.camera2.impl.CameraCaptureSessionImpl", cl,
                    "setRepeatingRequest", CaptureRequest.class,
                    XposedHelpers.findClass("android.hardware.camera2.CameraCaptureSession$CaptureCallback", cl),
                    android.os.Handler.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam p) {
                            try {
                                CaptureRequest r = (CaptureRequest) p.args[0];
                                Integer af = r.get(CaptureRequest.CONTROL_AF_MODE);
                                Integer ae = r.get(CaptureRequest.CONTROL_AE_MODE);
                                String s = "repeating af=" + af + " ae=" + ae;
                                if (s.equals(lastRepeat) || LOGS.incrementAndGet() > 120) {
                                    return;
                                }
                                lastRepeat = s;
                                XposedBridge.log("TYH212U-HOOK: [req] t=" + System.currentTimeMillis() + " " + s);
                            } catch (Throwable ignored) {
                            }
                        }
                    });
            XposedBridge.log("TYH212U-HOOK: [req] setRepeatingRequest hooked");
        } catch (Throwable t) {
            XposedBridge.log("TYH212U-HOOK: [req] repeating hook failed: " + t);
        }
        try {
            Class<?> key2 = XposedHelpers.findClass("android.hardware.camera2.CaptureResult$Key", cl);
            XposedHelpers.findAndHookMethod("android.hardware.camera2.CaptureResult", cl, "get", key2,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            try {
                                if (p.args[0] != CaptureResult.CONTROL_AF_MODE) {
                                    return;
                                }
                                Object v = p.getResult();
                                String s = "result af=" + v;
                                if (s.equals(lastResult) || LOGS.incrementAndGet() > 150) {
                                    return;
                                }
                                lastResult = s;
                                XposedBridge.log("TYH212U-HOOK: [req] t=" + System.currentTimeMillis() + " " + s);
                            } catch (Throwable ignored) {
                            }
                        }
                    });
            XposedBridge.log("TYH212U-HOOK: [req] CaptureResult.get hooked");
        } catch (Throwable t) {
            XposedBridge.log("TYH212U-HOOK: [req] result hook failed: " + t);
        }
        XposedBridge.log("TYH212U-HOOK: [req] installed");
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
