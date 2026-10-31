package com.tyh212u.gcamhook;

import de.robv.android.xposed.*;

/** Bound the synthetic-YUV burst and avoid unsupported exposure alternation.
 * Actual metadata stays intact: alter the requested plan before native builds it.
 */
final class BurstLimitRepair {
    private static String property(String key, String fallback) {
        try {
            return (String) Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class, String.class).invoke(null, key, fallback);
        } catch (Throwable t) { return fallback; }
    }

    private static int frames() {
        try { return Math.max(2, Math.min(12,
                Integer.parseInt(property("debug.gcam.burst.frames", "6")))); }
        catch (NumberFormatException e) { return 6; }
    }

    static void install(ClassLoader cl) throws Exception {
        String jni = "com.google.googlex.gcam.GcamModuleJNI";
        final Class<?> options = XposedHelpers.findClass(
                "com.google.googlex.gcam.BuildPayloadBurstSpecOptions", cl);
        final Class<?> nativeApi = XposedHelpers.findClass(jni, cl);
        final Class<?> gcam = XposedHelpers.findClass("com.google.googlex.gcam.Gcam", cl);
        final Class<?> metadata = XposedHelpers.findClass("com.google.googlex.gcam.FrameMetadata", cl);
        final String[] names = {"max_frame_count", "default_min_short_frames",
                "default_max_short_frames", "default_min_bracketing_frames",
                "default_max_bracketing_frames"};
        final int limit = frames();
        final boolean uniform = "1".equals(property("debug.gcam.fix.uniformburst", "1"));
        XC_MethodHook builder = new XC_MethodHook() {
            protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                Object option = p.args[p.args.length - 1];
                long ptr = ((Number) p.args[p.args.length - 2]).longValue();
                if (option == null || ptr == 0) return;
                int old = ((Number) nativeApi.getMethod("BuildPayloadBurstSpecOptions_max_frame_count_get",
                        long.class, options).invoke(null, ptr, option)).intValue();
                int[] caps = {limit, 2, Math.max(2, limit - 2), 0, Math.min(2, limit - 2)};
                if (uniform) {
                    Object frame = null;
                    for (Object value : p.args) if (metadata.isInstance(value)) { frame = value; break; }
                    if (frame != null) {
                        long fp = ((Number) XposedHelpers.getObjectField(frame, "a")).longValue();
                        float exp = ((Number) nativeApi.getMethod("FrameMetadata_actual_exposure_time_ms_get",
                                long.class, metadata).invoke(null, fp, frame)).floatValue();
                        float gain = ((Number) nativeApi.getMethod("FrameMetadata_actual_analog_gain_get",
                                long.class, metadata).invoke(null, fp, frame)).floatValue();
                        if (exp > 0 && Float.isFinite(exp) && gain > 0 && Float.isFinite(gain)) {
                            nativeApi.getMethod("BuildPayloadBurstSpecOptions_exposure_time_override_ms_set",
                                    long.class, options, float.class).invoke(null, ptr, option, exp);
                            nativeApi.getMethod("BuildPayloadBurstSpecOptions_gain_override_set",
                                    long.class, options, float.class).invoke(null, ptr, option, gain);
                            caps = new int[]{limit, limit, limit, 0, 0};
                            XposedBridge.log("TYH212U-HOOK: [uniform-burst] frames=" + limit
                                    + " exp_ms=" + exp + " gain=" + gain);
                        }
                    }
                }
                for (int i = 0; i < names.length; i++) nativeApi.getMethod(
                        "BuildPayloadBurstSpecOptions_" + names[i] + "_set",
                        long.class, options, int.class).invoke(null, ptr, option, caps[i]);
                XposedBridge.log("TYH212U-HOOK: [burst-limit] before builder max=" + old + " -> " + limit);
            }
        };
        XposedHelpers.findAndHookMethod(jni, cl, "Gcam_BuildPayloadBurstSpec__SWIG_0",
                long.class, gcam, int.class, long.class,
                XposedHelpers.findClass("com.google.googlex.gcam.RawReadView", cl),
                long.class, metadata, long.class,
                XposedHelpers.findClass("com.google.googlex.gcam.SpatialGainMap", cl),
                long.class, options, builder);
        XposedHelpers.findAndHookMethod(jni, cl, "Gcam_BuildPayloadBurstSpec__SWIG_1",
                long.class, gcam, int.class, long.class, metadata, long.class, options, builder);
        XposedHelpers.findAndHookMethod(jni, cl, "Gcam_BeginPayloadFrames",
                long.class, gcam, int.class, long.class,
                XposedHelpers.findClass("com.google.googlex.gcam.BurstSpec", cl),
                new XC_MethodHook() {
                    protected void beforeHookedMethod(MethodHookParam p) {
                        Object vector = XposedHelpers.callMethod(p.args[4], "c");
                        XposedBridge.log("TYH212U-HOOK: [burst-limit] begin shot=" + p.args[2]
                                + " frames=" + XposedHelpers.callMethod(vector, "a"));
                    }
                });
    }
}
