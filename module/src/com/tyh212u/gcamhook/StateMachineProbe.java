package com.tyh212u.gcamhook;

import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Stage 40 probe: trace the spinner/state machine that shows ProgressOverlay.
 *
 * The stack that makes the centre spinner visible is:
 *   View.setVisibility <- lrv.a <- mjc.a <- mja.g <- lrn.c <- lrw.c <- jeo.a
 * mja is a small state machine whose g(mjc) sets the current state (and asserts
 * "Setting new state without first exiting current state"), mjc is the state base
 * class, lrw/lrn wrap the spinner.
 *
 * This probe only reads: it logs every state transition (new state class, current
 * state, caller stack) and every state callback, so we can see which state the
 * camera enters when the phone is moved.
 *
 * adb shell setprop debug.gcam.probe.sm 1
 */
final class StateMachineProbe {

    private static final String PROP = "debug.gcam.probe.sm";
    private static final AtomicInteger G_LOGS = new AtomicInteger();
    private static final java.util.List<Object> OVERLAYS = new java.util.concurrent.CopyOnWriteArrayList<Object>();
    private static final AtomicInteger CB_LOGS = new AtomicInteger();
    private static final java.util.Set<String> G_SIGS =
            java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<String>());

    private StateMachineProbe() {
    }

    static boolean enabled() {
        return "1".equals(HookEntry.prop(PROP, "0"));
    }

    static void install(ClassLoader cl) {
        try {
            Class<?> po = XposedHelpers.findClass(
                    "com.google.android.apps.camera.progressoverlay.ProgressOverlay", cl);
            XposedBridge.hookAllConstructors(po, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    OVERLAYS.add(p.thisObject);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("TYH212U-HOOK: [sm] overlay ctor hook failed: " + t);
        }
        try {
            XposedHelpers.findAndHookMethod("mja", cl, "g", XposedHelpers.findClass("mjc", cl),
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            try {
                                Object next = p.args != null && p.args.length > 0 ? p.args[0] : null;
                                Object cur = XposedHelpers.getObjectField(p.thisObject, "c");
                                String sig = next == null ? "null" : String.valueOf(stateId(next));
                                String key = sig + "|" + stackSig();
                                if (!G_SIGS.add(key) || G_SIGS.size() > 40) {
                                    return;
                                }
                                XposedBridge.log("TYH212U-HOOK: [sm] t=" + System.currentTimeMillis() + " transition -> " + sig
                                        + " current=" + (cur == null ? "null" : String.valueOf(stateId(cur)))
                                        + " caller=" + stackSig() + "\n" + stack(16));
                            } catch (Throwable ignored) {
                            }
                        }
                    });
            XposedBridge.log("TYH212U-HOOK: [sm] mja.g hooked");
        } catch (Throwable t) {
            XposedBridge.log("TYH212U-HOOK: [sm] mja.g hook failed: " + t);
        }
        for (final String m : new String[]{"a", "b", "g", "i"}) {
            try {
                XposedHelpers.findAndHookMethod("mjc", cl, m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam p) {
                        try {
                            if (CB_LOGS.incrementAndGet() > 120) {
                                return;
                            }
                            XposedBridge.log("TYH212U-HOOK: [sm] t=" + System.currentTimeMillis() + " callback state="
                                    + String.valueOf(stateId(p.thisObject)) + "." + m + "() ov=" + overlayVis());
                        } catch (Throwable ignored) {
                        }
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log("TYH212U-HOOK: [sm] mjc." + m + " hook failed: " + t);
            }
        }
        XposedBridge.log("TYH212U-HOOK: [sm] installed");
    }

    private static String overlayVis() {
        StringBuilder sb = new StringBuilder();
        for (Object o : OVERLAYS) {
            try {
                sb.append(XposedHelpers.callMethod(o, "getVisibility"));
            } catch (Throwable ignored) {
            }
        }
        return sb.length() == 0 ? "none" : sb.toString();
    }

    private static Object stateId(Object state) {
        try {
            Object id = XposedHelpers.getObjectField(state, "a");
            if (id instanceof Enum) {
                return ((Enum<?>) id).name();
            }
            return String.valueOf(id);
        } catch (Throwable t) {
            return "?";
        }
    }

    private static String stackSig() {
        StackTraceElement[] st = new Throwable().getStackTrace();
        int n = 0;
        for (StackTraceElement e : st) {
            String c = e.getClassName();
            if (c.startsWith("com.tyh212u") || c.startsWith("epWSX") || c.equals("t") || c.equals("m")
                    || c.startsWith("java.lang.Thread")) {
                continue;
            }
            if (++n >= 2) {
                return c + "#" + e.getMethodName();
            }
        }
        return "?";
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
