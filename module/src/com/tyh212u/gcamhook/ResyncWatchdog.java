package com.tyh212u.gcamhook;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Stage 39 resync watchdog.
 *
 * Observed failure shape B: CaptureModule has already gone back to ready=true
 * (jjx.D(true)) while the photo module's centre spinner (ProgressOverlay) is
 * still VISIBLE and the shutter button is still disabled. The capture pipeline
 * is fine in that state, only the UI never caught up, so hiding the overlay and
 * re-enabling the shutter restores shooting without touching the camera.
 *
 * Only armed on a ready=true transition, and only acts when the overlay is
 * actually visible and the shutter is disabled -- i.e. the inconsistent state.
 *
 * adb shell setprop debug.gcam.fix.resync 1
 */
final class ResyncWatchdog {

    private static final String PROP = "debug.gcam.fix.resync";
    private static final long DELAY_MS = 800L;

    private static final List<Object> OVERLAYS = new java.util.concurrent.CopyOnWriteArrayList<Object>();
    private static final List<Object> SHUTTERS = new java.util.concurrent.CopyOnWriteArrayList<Object>();
    private static final AtomicInteger ACTIONS = new AtomicInteger();
    private static final AtomicInteger VIZ_LOGS = new AtomicInteger();
    private static final java.util.Map<Object, Long> VISIBLE_SINCE =
            java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<Object, Long>());
    private static volatile boolean LAST_READY = true;

    private ResyncWatchdog() {
    }

    static boolean enabled() {
        return "1".equals(HookEntry.prop(PROP, "0"));
    }

    static void install(ClassLoader cl) {
        try {
            Class<?> overlay = XposedHelpers.findClass(
                    "com.google.android.apps.camera.progressoverlay.ProgressOverlay", cl);
            XposedBridge.hookAllConstructors(overlay, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    OVERLAYS.add(p.thisObject);
                }
            });
            XposedBridge.log("TYH212U-HOOK: [resync] overlay constructors hooked");
        } catch (Throwable t) {
            XposedBridge.log("TYH212U-HOOK: [resync] overlay hook failed: " + t);
        }
        try {
            Class<?> shutter = XposedHelpers.findClass(
                    "com.google.android.apps.camera.ui.shutterbutton.ShutterButton", cl);
            XposedBridge.hookAllConstructors(shutter, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    SHUTTERS.add(p.thisObject);
                }
            });
            XposedBridge.log("TYH212U-HOOK: [resync] shutter constructors hooked");
        } catch (Throwable t) {
            XposedBridge.log("TYH212U-HOOK: [resync] shutter hook failed: " + t);
        }
        try {
            XposedHelpers.findAndHookMethod("jjx", cl, "D", boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    LAST_READY = Boolean.TRUE.equals(p.args[0]);
                    if (!Boolean.TRUE.equals(p.args[0])) {
                        return;
                    }
                    XposedBridge.log("TYH212U-HOOK: [resync] armed (ready=true)");
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                            new Runnable() {
                                @Override
                                public void run() {
                                    resync();
                                }
                            }, DELAY_MS);
                }
            });
            XposedBridge.log("TYH212U-HOOK: [resync] watchdog armed on setReadyState(true)");
        } catch (Throwable t) {
            XposedBridge.log("TYH212U-HOOK: [resync] jjx.D hook failed: " + t);
        }
        hookVisibility();
        startPeriodic();
    }

    /** Trace who makes the tracked overlay visible. */
    private static void hookVisibility() {
        try {
            XposedHelpers.findAndHookMethod("android.view.View", ResyncWatchdog.class.getClassLoader(),
                    "setVisibility", int.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            try {
                                if (!OVERLAYS.contains(p.thisObject)) {
                                    return;
                                }
                                if (VIZ_LOGS.incrementAndGet() > 30) {
                                    return;
                                }
                                XposedBridge.log("TYH212U-HOOK: [viz] overlay setVisibility(" + p.args[0]
                                        + ") lastReady=" + LAST_READY + "\n" + stack(12));
                            } catch (Throwable ignored) {
                            }
                        }
                    });
            XposedBridge.log("TYH212U-HOOK: [resync] View.setVisibility hook installed");
        } catch (Throwable t) {
            XposedBridge.log("TYH212U-HOOK: [resync] visibility hook failed: " + t);
        }
    }

    /** Periodic guard: overlay visible for >5s while no shot is running -> hide + enable shutter. */
    private static void startPeriodic() {
        final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    periodic();
                } catch (Throwable t) {
                    XposedBridge.log("TYH212U-HOOK: [resync] periodic failed: " + t);
                }
                h.postDelayed(this, 2000L);
            }
        }, 2000L);
        XposedBridge.log("TYH212U-HOOK: [resync] periodic guard started");
    }

    private static void periodic() {
        long now = System.currentTimeMillis();
        boolean stuck = false;
        for (Object o : OVERLAYS) {
            try {
                int vis = (Integer) XposedHelpers.callMethod(o, "getVisibility");
                if (vis == 0) {
                    Long since = VISIBLE_SINCE.get(o);
                    if (since == null) {
                        VISIBLE_SINCE.put(o, now);
                    } else if (now - since > 5000L) {
                        stuck = true;
                    }
                } else {
                    VISIBLE_SINCE.remove(o);
                }
            } catch (Throwable ignored) {
            }
        }
        if (!stuck) {
            return;
        }
        XposedBridge.log("TYH212U-HOOK: [resync] overlay visible >5s, lastReady=" + LAST_READY + " -> force hide");
        for (Object o : OVERLAYS) {
            try {
                if (0 == (Integer) XposedHelpers.callMethod(o, "getVisibility")) {
                    XposedHelpers.callMethod(o, "setVisibility", 8);
                    VISIBLE_SINCE.remove(o);
                }
            } catch (Throwable ignored) {
            }
        }
        for (Object o : SHUTTERS) {
            try {
                if (!(Boolean) XposedHelpers.callMethod(o, "isEnabled")) {
                    XposedHelpers.callMethod(o, "setEnabled", true);
                }
            } catch (Throwable ignored) {
            }
        }
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

    private static void resync() {
        try {
            StringBuilder dbg = new StringBuilder("check");
            for (Object o : OVERLAYS) {
                try {
                    dbg.append(" ov=").append(XposedHelpers.callMethod(o, "getVisibility"));
                } catch (Throwable ignored) {
                }
            }
            for (Object o : SHUTTERS) {
                try {
                    dbg.append(" sh=").append(XposedHelpers.callMethod(o, "isEnabled"));
                } catch (Throwable ignored) {
                }
            }
            if (ACTIONS.incrementAndGet() <= 30 || true) {
                XposedBridge.log("TYH212U-HOOK: [resync] " + dbg);
            }
            boolean acted = false;
            for (Object o : OVERLAYS) {
                try {
                    int vis = (Integer) XposedHelpers.callMethod(o, "getVisibility");
                    if (vis == 0) { // View.VISIBLE
                        XposedHelpers.callMethod(o, "setVisibility", 8); // GONE
                        XposedBridge.log("TYH212U-HOOK: [resync] overlay VISIBLE -> GONE while ready");
                        acted = true;
                    }
                } catch (Throwable ignored) {
                }
            }
            for (Object o : SHUTTERS) {
                try {
                    boolean en = (Boolean) XposedHelpers.callMethod(o, "isEnabled");
                    if (!en) {
                        XposedHelpers.callMethod(o, "setEnabled", true);
                        XposedBridge.log("TYH212U-HOOK: [resync] shutter disabled -> enabled while ready");
                        acted = true;
                    }
                } catch (Throwable ignored) {
                }
            }
            if (acted && ACTIONS.incrementAndGet() <= 20) {
                XposedBridge.log("TYH212U-HOOK: [resync] applied (overlays=" + OVERLAYS.size()
                        + " shutters=" + SHUTTERS.size() + ")");
            }
        } catch (Throwable t) {
            XposedBridge.log("TYH212U-HOOK: [resync] failed: " + t);
        }
    }
}
