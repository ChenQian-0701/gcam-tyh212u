package com.tyh212u.gcamhook;

import android.view.View;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Read-only probe: who disables the shutter button?
 *
 * The shutter stays grey and ignores taps while GCam is otherwise healthy, and
 * neither setReadyState(true) nor forcing aeState recovers it. So something
 * else calls setEnabled(false) on the button. This logs the caller stack every
 * time the shutter's enabled flag changes, which names the gate directly.
 *
 * Off by default: adb shell setprop debug.gcam.probe.shutter 1
 */
final class ShutterEnableProbe {

    private static final String PROP = "debug.gcam.probe.shutter";
    private static volatile int shutterId = -1;
    private static volatile int logged = 0;

    private ShutterEnableProbe() {
    }

    static boolean enabled() {
        return "1".equals(HookEntry.prop(PROP, "0"));
    }

    static void install(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(View.class, "setEnabled", boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                View view = (View) param.thisObject;
                                if (view == null) {
                                    return;
                                }
                                if (shutterId == -1) {
                                    try {
                                        String name = view.getResources()
                                                .getResourceEntryName(view.getId());
                                        if ("shutter_button".equals(name)) {
                                            shutterId = view.getId();
                                            XposedBridge.log("TYH212U-HOOK: [shutter-probe] id="
                                                    + Integer.toHexString(shutterId));
                                        }
                                    } catch (Throwable ignored) {
                                        return;
                                    }
                                }
                                if (view.getId() != shutterId) {
                                    return;
                                }
                                boolean on = Boolean.TRUE.equals(param.args[0]);
                                if (logged > 40) {
                                    return;
                                }
                                logged++;
                                XposedBridge.log("TYH212U-HOOK: [shutter-probe] setEnabled(" + on
                                        + ")\n" + stack());
                            } catch (Throwable ignored) {
                                // read-only probe
                            }
                        }
                    });
            XposedBridge.log("TYH212U-HOOK: [shutter-probe] View.setEnabled hooked");
        } catch (Throwable t) {
            XposedBridge.log("TYH212U-HOOK: [shutter-probe] install failed: " + t);
        }
    }

    private static String stack() {
        StringBuilder sb = new StringBuilder();
        StackTraceElement[] st = new Throwable().getStackTrace();
        int n = 0;
        for (StackTraceElement e : st) {
            String s = e.toString();
            if (s.contains("ShutterEnableProbe") || s.contains("XposedBridge")
                    || s.contains("MethodHook")) {
                continue;
            }
            sb.append("    at ").append(s).append('\n');
            if (++n >= 10) {
                break;
            }
        }
        return sb.toString();
    }
}
