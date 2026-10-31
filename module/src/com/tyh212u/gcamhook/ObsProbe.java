package com.tyh212u.gcamhook;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Stage 39 probe: find which observable flips CaptureModule's ready gate.
 *
 * jjx.D(false) (CaptureModule#setReadyState) is what greys the shutter and lets
 * the photo module show its centre spinner, but its stack only shows a generic
 * observer lambda. jjx.L is a kbg ready gate whose d() recomputes the value from
 * two observable inputs (fields c and d). This probe follows those inputs down
 * to their ConcurrentObs leaves and logs every value write with a stack, so the
 * original writer becomes visible.
 *
 * Enable with: adb shell setprop debug.gcam.probe.obs 1
 */
final class ObsProbe {

    private static final String PROP = "debug.gcam.probe.obs";

    private static final java.util.Map<Object, String> KBGS =
            java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<Object, String>());
    private static final java.util.concurrent.atomic.AtomicInteger GATE_SEQ =
            new java.util.concurrent.atomic.AtomicInteger();
    private static final List<Object> TRACKED = new java.util.concurrent.CopyOnWriteArrayList<Object>();
    private static final List<String> LABELS = new java.util.concurrent.CopyOnWriteArrayList<String>();
    private static final AtomicInteger FALSE_LOGS = new AtomicInteger();
    private static final AtomicInteger CMD_LOGS = new AtomicInteger();
    private static volatile String lastKbg = "";

    private ObsProbe() {
    }

    static boolean enabled() {
        return "1".equals(HookEntry.prop(PROP, "0"));
    }

    static void install(ClassLoader cl) {
        hookKbg(cl);
        hookConcurrentSet(cl);
        hookCommandClasses(cl);
        hookCommandExecute(cl);
        hookObservableGetters(cl);
        hookAvailabilityTransform(cl);
        hookMemoizer(cl);
        hookCommandTransform(cl);
        hookGateWiring(cl);
        hookCaptureGateInputs(cl);
        XposedBridge.log("TYH212U-HOOK: [obs] installed (v4)");
    }

    /** Track jjx.L (kbg) and, on each recompute, the ConcurrentObs leaves feeding it. */
    private static void hookKbg(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod("jjx", cl, "D", boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        if (!Boolean.TRUE.equals(p.args[0])) {
                            long now = nowMs();
                            XposedBridge.log("TYH212U-HOOK: [attr] ready=false"
                                    + " lastLnm(age=" + (now - LAST_LNM_T) + "ms)=" + trunc(LAST_LNM, 160)
                                    + " lastFxq(age=" + (now - LAST_FXQ_T) + "ms)=" + trunc(LAST_FXQ, 160));
                        }
                        Object gate = XposedHelpers.getObjectField(p.thisObject, "L");
                        if (gate != null && !KBGS.containsKey(gate)) {
                            String id = "G" + GATE_SEQ.incrementAndGet();
                            KBGS.put(gate, id);
                            XposedBridge.log("TYH212U-HOOK: [obs] gate " + id + " tracked (total " + KBGS.size() + ")");
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
            XposedHelpers.findAndHookMethod("kbg", cl, "d", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        if (!KBGS.containsKey(p.thisObject)) {
                            return;
                        }
                        String sig = callerSignature();
                        if (sig == null || !KG_D_SIGS.add(sig) || KG_D_SIGS.size() > 40) {
                            return;
                        }
                        XposedBridge.log("TYH212U-HOOK: [obs] kbg.d caller=" + sig + "\n" + stack(20));
                    } catch (Throwable ignored) {
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        if (!KBGS.containsKey(p.thisObject)) {
                            return;
                        }
                        String gateId = KBGS.get(p.thisObject);
                        Object a = XposedHelpers.getObjectField(p.thisObject, "a");
                        Object val = XposedHelpers.getObjectField(a, "d");
                        Object c = XposedHelpers.getObjectField(p.thisObject, "c");
                        Object d = XposedHelpers.getObjectField(p.thisObject, "d");
                        Object g = XposedHelpers.getObjectField(p.thisObject, "g");
                        retrack("c", c);
                        retrack("d", d);
                        if (Boolean.FALSE.equals(val)) {
                            String cmd = dumpCommandSet(c);
                            if (cmd != null && !cmd.equals(lastCmds)) {
                                lastCmds = cmd;
                                XposedBridge.log("TYH212U-HOOK: [obs] command-set " + cmd);
                            }
                        }
                        String s = "value=" + val + " c=" + valueOf(c) + " d=" + valueOf(d)
                                + " g=" + g + " chainC=" + chainOf(c, 0) + " chainD=" + chainOf(d, 0);
                        if (!s.equals(lastKbg)) {
                            lastKbg = s;
                            XposedBridge.log("TYH212U-HOOK: [obs] kbg.d t=" + nowMs() + " gate=" + gateId + " " + s);
                        }
                    } catch (Throwable t) {
                        XposedBridge.log("TYH212U-HOOK: [obs] kbg logging failed: " + t);
                    }
                }
            });
            XposedBridge.log("TYH212U-HOOK: [obs] jjx.D + kbg.d hooked");
        } catch (Throwable t) {
            XposedBridge.log("TYH212U-HOOK: [obs] kbg hook failed: " + t);
        }
    }

    /** oxv.a(Object) / oxv.d(Object) are the two value setters of ConcurrentObs. */
    private static void hookConcurrentSet(ClassLoader cl) {
        XC_MethodHook h = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam p) {
                try {
                    String label = labelOf(p.thisObject);
                    if (label == null) {
                        return;
                    }
                    Object v = p.args != null && p.args.length > 0 ? p.args[0] : null;
                    String s = String.valueOf(v);
                    XposedBridge.log("TYH212U-HOOK: [obs] " + label + " " + p.method.getName() + "(" + s + ")");
                    if ("false".equals(s) && FALSE_LOGS.incrementAndGet() <= 12) {
                        XposedBridge.log("TYH212U-HOOK: [obs] " + label + " -> false stack:\n" + stack(20));
                    }
                } catch (Throwable ignored) {
                }
            }
        };
        try {
            XposedHelpers.findAndHookMethod("oxv", cl, "a", Object.class, h);
            XposedHelpers.findAndHookMethod("oxv", cl, "d", Object.class, h);
            XposedBridge.log("TYH212U-HOOK: [obs] oxv setters hooked");
        } catch (Throwable t) {
            XposedBridge.log("TYH212U-HOOK: [obs] oxv hook failed: " + t);
        }
    }

    /** Log creation of capture-command objects: per-shot commands are built on demand. */
    private static void hookCommandClasses(ClassLoader cl) {
        for (final String cn : new String[]{"ldy", "leg", "ldr", "ktu", "kty"}) {
            try {
                Class<?> c = XposedHelpers.findClass(cn, cl);
                XposedBridge.hookAllConstructors(c, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam p) {
                        try {
                            if (CMD_LOGS.incrementAndGet() > 24) {
                                return;
                            }
                            XposedBridge.log("TYH212U-HOOK: [obs] new " + cn + " command instance\n" + stack(14));
                        } catch (Throwable ignored) {
                        }
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log("TYH212U-HOOK: [obs] ctor hook " + cn + " failed: " + t);
            }
        }
        XposedBridge.log("TYH212U-HOOK: [obs] command constructors hooked");
    }

    /** Command execution entry: c(Lkub;Llko;)V on every capture command class. */
    private static void hookCommandExecute(ClassLoader cl) {
        try {
            final Class<?> kub = XposedHelpers.findClass("kub", cl);
            final Class<?> lko = XposedHelpers.findClass("lko", cl);
            for (final String cn : new String[]{"ldr", "ktu", "ldy", "leg"}) {
                try {
                    XposedHelpers.findAndHookMethod(cn, cl, "c", kub, lko, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam p) {
                            try {
                                if (CMD_LOGS.incrementAndGet() > 40) {
                                    return;
                                }
                                XposedBridge.log("TYH212U-HOOK: [obs] execute " + cn + "\n" + stack(16));
                            } catch (Throwable ignored) {
                            }
                        }
                    });
                } catch (Throwable t) {
                    XposedBridge.log("TYH212U-HOOK: [obs] execute hook " + cn + " failed: " + t);
                }
            }
            XposedBridge.log("TYH212U-HOOK: [obs] command execution hooked");
        } catch (Throwable t) {
            XposedBridge.log("TYH212U-HOOK: [obs] execute hooks failed: " + t);
        }
    }

    /** Which level of the ready chain actually returns false while kbg.d recomputes. */
    private static void hookObservableGetters(ClassLoader cl) {
        for (final String cn : new String[]{"oxz", "oxx", "oyj", "oxv"}) {
            try {
                XposedHelpers.findAndHookMethod(cn, cl, "cM", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam p) {
                        try {
                            Object r = p.getResult();
                            if (!Boolean.FALSE.equals(r)) {
                                return;
                            }
                            if (!callerIsKbg()) {
                                return;
                            }
                            String key = cn + "@" + Integer.toHexString(System.identityHashCode(p.thisObject));
                            if (FALSE_KEYS.add(key) && FALSE_KEYS.size() <= 12) {
                                XposedBridge.log("TYH212U-HOOK: [obs] false from " + key
                                        + " toString=" + trunc(String.valueOf(p.thisObject), 240)
                                        + "\n" + stack(14));
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log("TYH212U-HOOK: [obs] cM hook " + cn + " failed: " + t);
            }
        }
        XposedBridge.log("TYH212U-HOOK: [obs] observable getters hooked");
    }

    private static final java.util.Set<String> FALSE_KEYS =
            java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<String>());

    private static boolean callerIsKbg() {
        for (StackTraceElement e : new Throwable().getStackTrace()) {
            if ("kbg".equals(e.getClassName())) {
                return true;
            }
        }
        return false;
    }

    /** kud = capture gate supplier: result = f(pending, busy, flag). Log all inputs. */
    private static void hookCaptureGateInputs(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod("kud", cl, "a", new XC_MethodHook() {
                private volatile String last = "";
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        Object res = p.getResult();
                        Object val = null;
                        try {
                            val = res == null ? null : XposedHelpers.callMethod(res, "cM");
                        } catch (Throwable ignored) {
                        }
                        Object kug = XposedHelpers.getObjectField(p.thisObject, "a");
                        Object kol = XposedHelpers.getObjectField(p.thisObject, "b");
                        Object flag = XposedHelpers.getObjectField(p.thisObject, "c");
                        String pending = String.valueOf(XposedHelpers.getObjectField(kug, "g"));
                        boolean busy = false;
                        try {
                            busy = Boolean.TRUE.equals(XposedHelpers.callMethod(kol, "a"));
                        } catch (Throwable ignored) {
                        }
                        String s = "result=" + val + " pending=" + pending + " busy=" + busy + " flag=" + flag;
                        if (!s.equals(last)) {
                            last = s;
                            XposedBridge.log("TYH212U-HOOK: [gate] kud.a t=" + nowMs() + " " + s);
                            if (Boolean.FALSE.equals(val)) {
                                XposedBridge.log("TYH212U-HOOK: [gate] false stack:\n" + stack(18));
                            }
                        }
                    } catch (Throwable t) {
                        XposedBridge.log("TYH212U-HOOK: [gate] log failed: " + t);
                    }
                }
            });
            XposedBridge.log("TYH212U-HOOK: [obs] kud.a inputs hooked");
        } catch (Throwable t) {
            XposedBridge.log("TYH212U-HOOK: [obs] kud inputs hook failed: " + t);
        }
    }

    /** What observable is wired into the ready gate (kbg.a) and by which lambda. */
    private static void hookGateWiring(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod("kbg", cl, "a", XposedHelpers.findClass("oyd", cl), new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        Object arg = p.args != null && p.args.length > 0 ? p.args[0] : null;
                        XposedBridge.log("TYH212U-HOOK: [wire] kbg.a arg=" + (arg == null ? "null"
                                : arg.getClass().getName() + " :: " + trunc(String.valueOf(arg), 300)));
                    } catch (Throwable ignored) {
                    }
                }
            });
            XposedBridge.log("TYH212U-HOOK: [obs] kbg.a hooked");
        } catch (Throwable t) {
            XposedBridge.log("TYH212U-HOOK: [obs] kbg.a hook failed: " + t);
        }
    }

    static long nowMs() {
        return System.currentTimeMillis();
    }

    /** Keep only the "two cameras" style samples, drop the per-frame ones. */
    private static boolean interestingLnmInput(Object in) {
        try {
            if (in instanceof java.util.List) {
                java.util.List<?> l = (java.util.List<?>) in;
                if (l.size() == 2) {
                    return true;
                }
                return !l.isEmpty() && l.get(0) instanceof Boolean;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static String trunc(String s, int n) {
        return s == null || s.length() <= n ? s : s.substring(0, n) + "...";
    }

    /** lnm = "waitForCamerasAllAvailable" transform: log the input list when it says false. */
    private static void hookAvailabilityTransform(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod("lnm", cl, "apply", Object.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        Object r = p.getResult();
                        if (!Boolean.FALSE.equals(r)) {
                            return;
                        }
                        Object in = p.args != null && p.args.length > 0 ? p.args[0] : null;
                        LAST_LNM = "input(" + (in == null ? "null" : in.getClass().getName()) + ")="
                                + trunc(String.valueOf(in), 120) + " caller=" + callerSignature();
                        LAST_LNM_T = nowMs();
                        if (!interestingLnmInput(in)) {
                            return;
                        }
                        XposedBridge.log("TYH212U-HOOK: [lnm] false t=" + nowMs() + " caller=" + callerSignature()
                                + " input(" + (in == null ? "null" : in.getClass().getName()) + ")="
                                + trunc(String.valueOf(in), 200));
                    } catch (Throwable ignored) {
                    }
                }
            });
            XposedBridge.log("TYH212U-HOOK: [obs] lnm.apply hooked");
        } catch (Throwable t) {
            XposedBridge.log("TYH212U-HOOK: [obs] lnm hook failed: " + t);
        }
    }

    private static final java.util.Set<String> AVAIL_SIGS =
            java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<String>());

    private static boolean isObserverFrame(String c) {
        return c.equals("oyf") || c.equals("oyu") || c.equals("oyt") || c.equals("oxz")
                || c.equals("oxx") || c.equals("oyj") || c.equals("oyc") || c.equals("oxv")
                || c.startsWith("epWSX") || c.startsWith("com.tyh212u") || c.equals("t")
                || c.equals("m") || c.startsWith("android.util.Brerkr") || c.startsWith("android.os.Cundailt");
    }

    private static String callerSignature() {
        for (StackTraceElement e : new Throwable().getStackTrace()) {
            String c = e.getClassName();
            if (isObserverFrame(c)) {
                continue;
            }
            return c + "#" + e.getMethodName();
        }
        return null;
    }

    /** Memoizer observation: same input identity + changed content = stale cache. */
    private static void hookMemoizer(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod("oyu", cl, "m", Object.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        String label = labelOf(p.thisObject);
                        if (label == null) {
                            return;
                        }
                        Object in = p.args != null && p.args.length > 0 ? p.args[0] : null;
                        Object out = p.getResult();
                        String content = trunc(String.valueOf(in), 200);
                        String id = Integer.toHexString(System.identityHashCode(in));
                        Object prev;
                        synchronized (LAST_INPUT) {
                            prev = LAST_INPUT.put(p.thisObject, in);
                        }
                        boolean sameIdentity = prev == in;
                        if (MEMO_LOGS.incrementAndGet() > 200) {
                            return;
                        }
                        if (sameIdentity && !content.equals(LAST_CONTENT.get(p.thisObject))) {
                            XposedBridge.log("TYH212U-HOOK: [memo] STALE? " + label + " input@" + id
                                    + " content changed while identity same -> out=" + out + "\n    in=" + content);
                        }
                        LAST_CONTENT.put(p.thisObject, content);
                    } catch (Throwable ignored) {
                    }
                }
            });
            XposedBridge.log("TYH212U-HOOK: [obs] oyu.m hooked");
        } catch (Throwable t) {
            XposedBridge.log("TYH212U-HOOK: [obs] oyu hook failed: " + t);
        }
    }

    /** fxq.apply transforms the capture-command set (ktt) into a boolean availability. */
    private static void hookCommandTransform(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod("fxq", cl, "apply", Object.class, new XC_MethodHook() {
                private volatile String last5 = "";

                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    // Experimental: the selector-5 predicate demands CONTINUOUS_PICTURE AF;
                    // treat a transient AF-OFF metadata sample as available.
                    if (!"1".equals(HookEntry.prop("debug.gcam.fix.afgate", "0"))) {
                        return;
                    }
                    try {
                        int sel = (Integer) XposedHelpers.getObjectField(p.thisObject, "d");
                        if (sel != 5) {
                            return;
                        }
                        Object in = p.args != null && p.args.length > 0 ? p.args[0] : null;
                        if (in == null || !"mhz".equals(in.getClass().getName())) {
                            return;
                        }
                        Object a = XposedHelpers.getObjectField(in, "a");
                        String s = String.valueOf(a);
                        if (s.contains("controlAfMode=OFF")) {
                            p.setResult(Boolean.TRUE);
                        }
                    } catch (Throwable ignored) {
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        int sel = -1;
                        try {
                            sel = (Integer) XposedHelpers.getObjectField(p.thisObject, "d");
                        } catch (Throwable ignored) {
                        }
                        Object in = p.args != null && p.args.length > 0 ? p.args[0] : null;
                        if (Boolean.FALSE.equals(p.getResult())) {
                            String sum = summarizeInput(in);
                            LAST_FXQ = "sel=" + sel + " input=" + trunc(sum, 120) + " caller=" + callerSignature();
                            LAST_FXQ_T = nowMs();
                            XposedBridge.log("TYH212U-HOOK: [fxq] false t=" + nowMs() + " sel=" + sel
                                    + " input=" + trunc(sum, 200) + " caller=" + callerSignature());
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
            XposedBridge.log("TYH212U-HOOK: [obs] fxq.apply hooked");
        } catch (Throwable t) {
            XposedBridge.log("TYH212U-HOOK: [obs] fxq hook failed: " + t);
        }
    }

    static volatile String LAST_LNM = "";
    static volatile long LAST_LNM_T = 0L;
    static volatile String LAST_FXQ = "";
    static volatile long LAST_FXQ_T = 0L;

    private static final java.util.Set<String> FXQ_KEYS =
            java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<String>());

    private static String summarizeInput(Object in) {
        if (in == null) {
            return "null";
        }
        String cn = in.getClass().getName();
        try {
            if ("mhz".equals(cn)) {
                Object a = XposedHelpers.getObjectField(in, "a");
                Object b = XposedHelpers.getObjectField(in, "b");
                return "mhz{a=" + trunc(String.valueOf(a), 140) + ", b=" + trunc(String.valueOf(b), 140) + "}";
            }
            if (in instanceof java.util.List) {
                StringBuilder sb = new StringBuilder("[");
                int i = 0;
                for (Object e : (java.util.List<?>) in) {
                    if (i++ >= 6) {
                        break;
                    }
                    sb.append(trunc(String.valueOf(e), 60)).append(',');
                }
                return sb.append(']').toString();
            }
        } catch (Throwable ignored) {
        }
        return cn + ":" + trunc(String.valueOf(in), 160);
    }

    private static volatile String lastCmds = "";

    /** Walk the ktt command holder and report each command's observable value. */
    private static String dumpCommandSet(Object root) {
        Object ktt = findKtt(root, 0);
        if (ktt == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String f : new String[]{"normal", "normalFlash", "hdrPlus", "hdrPlusTorch", "hdrPlusZsl"}) {
            try {
                Object cmd = XposedHelpers.getObjectField(ktt, f);
                sb.append(f).append('=').append(cmdValue(cmd)).append(' ');
            } catch (Throwable ignored) {
            }
        }
        return sb.toString();
    }

    private static Object findKtt(Object o, int depth) {
        if (o == null || depth > 8) {
            return null;
        }
        String cn = o.getClass().getName();
        if ("ktt".equals(cn)) {
            return o;
        }
        try {
            if ("oxz".equals(cn) || "oxx".equals(cn)) {
                return findKtt(XposedHelpers.getObjectField(o, "a"), depth + 1);
            }
            if ("oyj".equals(cn)) {
                return findKtt(XposedHelpers.getObjectField(o, "c"), depth + 1);
            }
            if ("oxv".equals(cn)) {
                return findKtt(XposedHelpers.getObjectField(o, "d"), depth + 1);
            }
            if ("oyf".equals(cn)) {
                return findKtt(XposedHelpers.getObjectField(o, "b"), depth + 1);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String cmdValue(Object cmd) {
        if (cmd == null) {
            return "null";
        }
        try {
            Object obs = XposedHelpers.callMethod(cmd, "b");
            Object v = obs == null ? null : XposedHelpers.callMethod(obs, "cM");
            return cmd.getClass().getName() + ":" + v;
        } catch (Throwable t) {
            return cmd.getClass().getName() + ":?";
        }
    }

    private static final java.util.Set<String> KG_D_SIGS =
            java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<String>());

    private static final java.util.Set<String> FXQ_SIGS =
            java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<String>());

    private static final java.util.Map<Object, Object> LAST_INPUT =
            java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<Object, Object>());
    private static final java.util.Map<Object, String> LAST_CONTENT =
            java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<Object, String>());
    private static final AtomicInteger MEMO_LOGS = new AtomicInteger();

    private static final AtomicInteger AVAIL_LOGS = new AtomicInteger();

    private static String labelOf(Object o) {
        if (o == null) {
            return null;
        }
        List<Object> ts = TRACKED;
        for (int i = 0; i < ts.size(); i++) {
            if (ts.get(i) == o) {
                return LABELS.get(i);
            }
        }
        return null;
    }

    /** Register every ConcurrentObs reachable from an input observable. */
    private static void retrack(String root, Object o) {
        List<Object> found = new ArrayList<Object>();
        List<String> names = new ArrayList<String>();
        collect(root, o, found, names, 0);
        for (int i = 0; i < found.size(); i++) {
            Object inst = found.get(i);
            if (!TRACKED.contains(inst)) {
                TRACKED.add(inst);
                LABELS.add(names.get(i));
                XposedBridge.log("TYH212U-HOOK: [obs] tracking " + names.get(i));
            }
        }
    }

    private static void collect(String path, Object o, List<Object> out, List<String> names, int depth) {
        if (o == null || depth > 6) {
            return;
        }
        String cn = o.getClass().getName();
        try {
            if ("oxv".equals(cn)) {
                out.add(o);
                names.add(path);
                Object val = XposedHelpers.getObjectField(o, "d");
                if (val != null && !(val instanceof Boolean)) {
                    collect(path + ".oxv.d", val, out, names, depth + 1);
                }
            } else if ("oyu".equals(cn)) {
                out.add(o);
                names.add(path + ".oyu");
                collect(path + ".oyu.a", XposedHelpers.getObjectField(o, "a"), out, names, depth + 1);
            } else if ("oyf".equals(cn)) {
                collect(path + ".oyf.b", XposedHelpers.getObjectField(o, "b"), out, names, depth + 1);
            } else if ("oyc".equals(cn)) {
                Object list = XposedHelpers.getObjectField(o, "a");
                if (list instanceof java.util.List) {
                    int i = 0;
                    for (Object e : (java.util.List<?>) list) {
                        if (i++ >= 6) break;
                        collect(path + ".oyc[" + i + "]", e, out, names, depth + 1);
                    }
                }
            } else if ("oxz".equals(cn) || "oxx".equals(cn)) {
                collect(path + "." + cn, XposedHelpers.getObjectField(o, "a"), out, names, depth + 1);
            } else if ("oyj".equals(cn)) {
                Object b = XposedHelpers.getObjectField(o, "b");
                if (b != null) {
                    out.add(b);
                    names.add(path + ".oyj.b");
                }
                collect(path + ".oyj.c", XposedHelpers.getObjectField(o, "c"), out, names, depth + 1);
            } else if ("oyt".equals(cn)) {
                Object memo = XposedHelpers.getObjectField(o, "b");
                if (memo != null) {
                    out.add(memo);
                    names.add(path + ".oyt.b");
                }
                collect(path + ".oyt.a", XposedHelpers.getObjectField(o, "a"), out, names, depth + 1);
            }
        } catch (Throwable ignored) {
        }
    }

    private static String chainOf(Object o, int depth) {
        if (o == null || depth > 6) {
            return "null";
        }
        String cn = o.getClass().getName();
        StringBuilder sb = new StringBuilder(cn);
        try {
            if ("oxz".equals(cn) || "oxx".equals(cn)) {
                sb.append(" -> ").append(chainOf(XposedHelpers.getObjectField(o, "a"), depth + 1));
            } else if ("oyj".equals(cn)) {
                sb.append(" -> ").append(chainOf(XposedHelpers.getObjectField(o, "c"), depth + 1));
            } else if ("oxv".equals(cn)) {
                sb.append("=").append(XposedHelpers.getObjectField(o, "d"));
            }
        } catch (Throwable ignored) {
        }
        return sb.toString();
    }

    private static Object valueOf(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return XposedHelpers.callMethod(o, "cM");
        } catch (Throwable ignored) {
            return String.valueOf(o);
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
}
