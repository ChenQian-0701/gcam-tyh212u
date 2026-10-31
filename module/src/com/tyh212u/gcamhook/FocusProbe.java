package com.tyh212u.gcamhook;

import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Stage 40 probe: focus-info publishing chain.
 *
 * The ready-gate predicate saw kna{controlAfMode=OFF, INACTIVE, 0.0} which is the
 * default kna.a() instance, while the request/result stream carries AF-C. knb holds
 * the current focus snapshot and fup.a(metadata) is the only writer of knb.b, so we
 * log knb construction and every fup update with the value it publishes.
 *
 * adb shell setprop debug.gcam.probe.focus 1
 */
final class FocusProbe {

    private static final String PROP = "debug.gcam.probe.focus";
    private static final AtomicInteger LOGS = new AtomicInteger();
    private static final AtomicInteger CTORS = new AtomicInteger();
    private static volatile String last = "";

    private FocusProbe() {
    }

    static boolean enabled() {
        return "1".equals(HookEntry.prop(PROP, "0"));
    }

    static void install(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod("knb", cl, "<init>", boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        if (CTORS.incrementAndGet() > 20) {
                            return;
                        }
                        XposedBridge.log("TYH212U-HOOK: [focus] new knb@"
                                + Integer.toHexString(System.identityHashCode(p.thisObject)) + "\n" + stack(12));
                    } catch (Throwable ignored) {
                    }
                }
            });
            XposedBridge.log("TYH212U-HOOK: [focus] knb.<init> hooked");
        } catch (Throwable t) {
            XposedBridge.log("TYH212U-HOOK: [focus] knb hook failed: " + t);
        }
        try {
            XposedHelpers.findAndHookMethod("fup", cl, "a", XposedHelpers.findClass("pro", cl),
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            try {
                                Object knb = XposedHelpers.getObjectField(p.thisObject, "d");
                                Object kna = XposedHelpers.getObjectField(knb, "b");
                                String s = "knb@" + Integer.toHexString(System.identityHashCode(knb))
                                        + " kna=" + String.valueOf(kna);
                                if (s.equals(last) || LOGS.incrementAndGet() > 80) {
                                    return;
                                }
                                last = s;
                                XposedBridge.log("TYH212U-HOOK: [focus] t=" + System.currentTimeMillis()
                                        + " fup.a -> " + s);
                            } catch (Throwable ignored) {
                            }
                        }
                    });
            XposedBridge.log("TYH212U-HOOK: [focus] fup.a hooked");
        } catch (Throwable t) {
            XposedBridge.log("TYH212U-HOOK: [focus] fup hook failed: " + t);
        }
        XposedBridge.log("TYH212U-HOOK: [focus] installed");
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
