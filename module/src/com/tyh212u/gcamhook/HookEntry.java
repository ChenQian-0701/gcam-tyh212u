package com.tyh212u.gcamhook;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.util.Pair;
import android.util.Range;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Runtime repair for BSG GCam 9.6 on TYH212U (UMS9620 / Android 11).
 *
 * The APK is pairip-protected: re-signing it kills native registration, which
 * removes the per-frame YUV->RGB converter and leaves the viewfinder black.
 * So we never touch the APK -- we keep the original BSG signature and patch the
 * three Java-level defects at runtime instead.
 *
 *   1. com.CameraLens.equals      - real infinite loop during lens scan
 *   2. pnt.o(Key)                 - missing CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE
 *   3. com.a.loadConfinesExposureISO - NPE on that missing key (fallback guard)
 */
public class HookEntry implements IXposedHookLoadPackage {

    private static final String TAG = "TYH212U-HOOK: ";
    private static final String TARGET_PKG = "com.android.MGC_9_6_080";
    private static final String KEY_BOOST_RANGE = "postRawSensitivityBoostRange";
    private static final String KEY_NOISE_PROFILE = "sensor.noiseProfile";

    /**
     * 20:04:51 shutter experiment (see evidence/2026-10-29-hdrplus-burst-keylines.txt)
     * proved the burst stream itself is healthy: seven payload frames arrived
     * 0.44s apart. What kills the HDR+ output is the metadata GCam normalises
     * them with:
     *
     *   CheckLscMap: SpatialGainMap for payload frame 0..6 likely incorrect
     *                (values out of range)  -> per-pixel gain map is garbage
     *   viewfinder_short_tet (0.000ms)      -> a frame claims zero exposure
     *   highlight_scale ... 581.23          -> normalisation blew up, then clamped
     *
     * Feeding a garbage gain map and a zero-exposure frame into the merge yields
     * an all-zero result, which is exactly the black JPEG we get. So:
     *
     *   a) tell GCam lens shading is OFF, so it stops multiplying by the junk map
     *   b) never let an exposure time of 0 reach the pipeline
     */
    private static final String KEY_LSC_MODE = "statistics.lensShadingMapMode";
    private static final String KEY_LSC_MAP = "statistics.lensShadingCorrectionMap";
    private static final String KEY_COLOR_GAINS = "colorCorrection.gains";
    private static final String KEY_COLOR_TRANSFORM = "colorCorrection.transform";
    private static final String KEY_NEUTRAL_POINT = "sensor.neutralColorPoint";
    private static final String KEY_SENSITIVITY = "sensor.sensitivity";
    private static final String KEY_EXPOSURE_TIME = "sensor.exposureTime";
    private static final String KEY_FRAME_DURATION = "sensor.frameDuration";
    private static final String KEY_AE_STATE = "control.aeState";

    private static final long DEFAULT_FRAME_NS = 33_333_333L;

    /** Optional metadata experiments. Stage 22 traced the observed purple cast
     * to Google AWB on ISP-processed YUV; disabling that app preference restores
     * neutral reference whites without injecting a replacement colour matrix.
     */
    private static final String PROP_GAINS = "debug.gcam.gains";
    private static final String PROP_TRANSFORM = "debug.gcam.transform";

    /**
     * The HAL advertises a 33.33 ms frame duration while libgcam derives a
     * 50 ms sensor readout from the pixel array and logs
     * "CheckFrameDuration: got 33.33ms expected >= readout time (50.00ms)".
     * GCam sizes its HDR+ burst window from the advertised value, so it stops
     * asking for frames before the third one lands -- and a two-frame burst is
     * exactly the case that renders black (highlight_scale = inf).
     * Reporting the honest 50 ms gives the burst the time it actually needs.
     */
    private static final String PROP_FRAME_DUR = "debug.gcam.frameduration";
    private static final long FRAME_DUR_TARGET_NS = 50_000_000L;
    private static final AtomicBoolean FRAME_DUR_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean FRAME_DUR_FIX = new AtomicBoolean(false);

    /** Optional AE diagnostic override. No demonstrated image benefit; default keep. */
    private static final String PROP_AE_STATE = "debug.gcam.aestate";
    private static final AtomicBoolean AE_FIX = new AtomicBoolean(false);
    private static final Object NO_OVERRIDE = new Object();

    private static final AtomicBoolean LSC_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean LSC_MAP_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean GAINS_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean GAINS_FIX_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean TRANSFORM_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean TRANSFORM_FIX_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean NEUTRAL_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean SENS_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean EXPOSURE_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean AE_LOGGED = new AtomicBoolean(false);
    private static final Set<String> EXPOSURE_SEEN =
            Collections.synchronizedSet(new HashSet<String>());

    /**
     * Diagnostic: GCam's HDR+ pipeline lives in native libgcam, so it may read
     * metadata straight off the camera_metadata_t buffer instead of going
     * through CaptureResult.get(). Logging which keys actually pass through the
     * Java accessor tells us whether a Java-level patch can reach the pipeline
     * at all -- lensShadingMapMode never showing up here is the answer for that
     * particular key.
     */
    private static final Set<String> SEEN_RESULT_KEYS =
            Collections.synchronizedSet(new HashSet<String>());

    /**
     * The Unisoc HAL advertises no SENSOR_NOISE_PROFILE at all. GCam's
     * NoiseModels.flattenPairs() then dereferences a null array while converting
     * a CaptureResult into Gcam FrameMetadata, so every shutter press dies before
     * a JPEG is ever encoded. Hand back a plausible Bayer noise model.
     *
     * noise variance = S * signal + O, one entry per CFA channel (R, Gr, Gb, B).
     */
    private static final double NOISE_SHOT = 1.0e-4;
    private static final double NOISE_READ = 1.0e-5;

    /**
     * pnt.o is a very hot, generic metadata accessor that ART almost certainly
     * inlines into its callers. Hooking it is the prime suspect for the SIGSEGV
     * we see in an unrelated place (StringBuilder.append on the image thread),
     * which is the signature of deoptimisation/inlining inconsistency.
     * CameraCharacteristics.get covers the same ground less invasively.
     */
    private static final boolean ENABLE_PNT_HOOK = false;

    private static final Set<String> LOGGED_NULL_KEYS =
            Collections.synchronizedSet(new HashSet<String>());

    private static final java.util.concurrent.atomic.AtomicBoolean DUMPED_NPE =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private static final java.util.concurrent.atomic.AtomicBoolean NOISE_FIX_LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /**
     * Stability bisection. The viewfinder image thread dies with SIGSEGV inside
     * java.lang.StringBuilder.append (frame: pmg.a <- pnh.onImageAvailable) a
     * few seconds after startup, intermittently. Both hooks below sit on that
     * exact per-frame path, so each gets an independent runtime kill switch:
     *
     *   adb shell setprop debug.gcam.hook.result 0     # disable CaptureResult.get sanitiser
     *   adb shell setprop debug.gcam.hook.noise  0     # disable NoiseModels.flattenPairs
     *
     * Defaults keep both enabled so behaviour is unchanged unless asked.
     */
    private static final String PROP_HOOK_RESULT = "debug.gcam.hook.result";
    private static final String PROP_HOOK_NOISE = "debug.gcam.hook.noise";

    /**
     * Sub-switches inside the CaptureResult sanitiser. 22:13 run proved the
     * whole sanitiser is required for a non-black image but is also what breaks
     * GCam's inflight-shot bookkeeping ("Couldn't find inflight shot" ->
     * shutter never re-enables). So each lever gets its own switch and we bisect
     * down to the minimal set:
     *
     *   debug.gcam.hook.lsc     substitute a flat lens-shading map   (default 0)
     *   debug.gcam.hook.color   substitute gains / colour transform  (default 0)
     *   debug.gcam.hook.exp     substitute a zero exposure time      (default 1)
     *
     * debug.gcam.aestate keeps driving control.aeState independently
     * (keep / 0..5), because that is the lever that fixed the black frames.
     */
    private static final String PROP_HOOK_LSC = "debug.gcam.hook.lsc";
    private static final String PROP_HOOK_COLOR = "debug.gcam.hook.color";
    private static final String PROP_HOOK_EXP = "debug.gcam.hook.exp";

    /** Experimental workaround for the one-shot-per-session deadlock. */
    private static final String PROP_FIX_INFLIGHT = "debug.gcam.fix.inflight";

    @SuppressWarnings("unchecked")
    private static java.util.Map<Object, Object> currentMap(Object lhyInstance) {
        Object m = XposedHelpers.getObjectField(lhyInstance, "j");
        return m instanceof java.util.Map ? (java.util.Map<Object, Object>) m : null;
    }

    private static boolean enabled(String prop) {
        return !"0".equals(prop(prop, PROP_HOOK_COLOR.equals(prop) ? "0" : "1"));
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam == null || !TARGET_PKG.equals(lpparam.packageName)) {
            return;
        }
        if (!CompatibilityGuard.allow(lpparam)) return;
        final ClassLoader cl = lpparam.classLoader;
        log("module attached to " + lpparam.packageName + " (process=" + lpparam.processName + ")");

        if (enabled("debug.gcam.fix.distributor")) {
            try { DistributorRepair.install(cl); } catch (Throwable t) { log("[distributor] install failed: " + t); }
        }
        if ("1".equals(prop("debug.gcam.fix.nativelsc", "0"))) {
            try { LscRepair.install(cl); } catch (Throwable t) { log("[native-lsc] install failed: " + t); }
        }
        if ("1".equals(prop("debug.gcam.probe.exposure", "0"))) ExposureProbe.install(cl);
        if ("1".equals(prop("debug.gcam.probe.raw", "0"))) RawProbe.install(cl);
        if (enabled("debug.gcam.fix.syntheticraw")) SyntheticRawRepair.install(cl);
        if (enabled("debug.gcam.fix.burstlimit")) BurstLimitRepair.install(cl);
        if (enabled("debug.gcam.fix.payloadboundary")) PayloadBoundaryRepair.install(cl);
        if ("1".equals(prop("debug.gcam.fix.gyrosnapshot", "0"))) GyroSnapshotRepair.install(cl);
        if ("1".equals(prop("debug.gcam.fix.framecache", "0"))) FrameCacheRepair.install(cl);
        if ("1".equals(prop("debug.gcam.fix.gyrofallback", "0"))) GyroFallbackRepair.install(cl);
        if ("1".equals(prop("debug.gcam.fix.previewvision", "0"))) PreviewVisionRepair.install(cl);
        if ("1".equals(prop("debug.gcam.fix.microvideo", "0"))) MicrovideoRepair.install(cl);
        if ("1".equals(prop("debug.gcam.fix.stillintent", "0"))) StillIntentRepair.install(cl);
        if ("1".equals(prop("debug.gcam.fix.hardwarezsl", "0"))) HardwareZslRepair.install(cl);
        if ("1".equals(prop("debug.gcam.fix.groupedexposure", "0"))) GroupedExposureRepair.install(cl);
        if ("1".equals(prop("debug.gcam.probe.capture", "0"))) CaptureAudit.install(cl);
        if ("1".equals(prop("debug.gcam.probe.session", "0"))) SessionRequestProbe.install(cl);
        if (ShutterEnableProbe.enabled()) ShutterEnableProbe.install(cl);
        if (ProgressOverlayRepair.enabled()) ProgressOverlayRepair.install(cl);
        if ("1".equals(prop("debug.gcam.probe.gateorigin", "0"))) GateOriginProbe.install(cl);
        if ("1".equals(prop("debug.gcam.probe.vflease", "0"))) ViewfinderLeaseProbe.install(cl);
        if ("1".equals(prop("debug.gcam.probe.input", "0"))) InputSnapshot.install(cl);
        hookLensEquals(cl);
        hookCharacteristicsGet(cl);
        if (ENABLE_PNT_HOOK) {
            hookCharacteristicsFallback(cl);
        }
        hookConfinesExposureGuard(cl);
        if (enabled(PROP_HOOK_NOISE)) {
            hookNoiseModel(cl);
        } else {
            log("[4/4] SKIPPED by " + PROP_HOOK_NOISE + "=0");
        }
        if (enabled(PROP_HOOK_RESULT)) {
            hookCaptureResultSanitize(cl);
        } else {
            log("[5/6] SKIPPED by " + PROP_HOOK_RESULT + "=0");
        }
        hookInflightShotProbe(cl);
        hookReadyState(cl);
        if ("1".equals(prop("debug.gcam.probe.ready", "0"))) ReadyProbe.install(cl);
        if (AvailabilityRepair.enabled()) AvailabilityRepair.install(cl);
        if (ObsProbe.enabled()) ObsProbe.install(cl);
        if (StateMachineProbe.enabled()) StateMachineProbe.install(cl);
        if (RequestProbe.enabled()) RequestProbe.install(cl);
        if (FocusProbe.enabled()) FocusProbe.install(cl);
        if (AeProbe.enabled()) AeProbe.install(cl);
        if (AeRestartRepair.enabled()) AeRestartRepair.install(cl);
        if (SessionProbe.enabled()) SessionProbe.install(cl);
        if (ResyncWatchdog.enabled()) ResyncWatchdog.install(cl);

        log("all hook attempts finished");
    }

    /**
     * Last-resort workaround for the one-shot-per-session deadlock.
     *
     * The JPEG is written, but GCam never re-arms the shutter because the shot
     * is torn down before the image is delivered. jjx.D(Z) is CaptureModule's
     * setReadyState(boolean) (timing key "CaptureModule#setReadyState"), and it
     * is what greys the shutter out during a capture. When the capture never
     * reports completion, ready never comes back.
     *
     * With debug.gcam.fix.shutter=1 we re-assert ready a few seconds after it
     * was cleared, so the next shot can be taken. This does NOT repair the
     * underlying lifecycle - it only makes the UI usable again for testing.
     */
    private static final String PROP_FIX_SHUTTER = "debug.gcam.fix.shutter";
    private static final java.util.concurrent.atomic.AtomicBoolean READY =
            new java.util.concurrent.atomic.AtomicBoolean(true);

    private void hookReadyState(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod("jjx", cl, "D", boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(final MethodHookParam param) {
                            final boolean ready = Boolean.TRUE.equals(param.args[0]);
                            READY.set(ready);
                            log("[fix] setReadyState(" + ready + ")");
                            if (!ready && "1".equals(prop("debug.gcam.probe.ready", "0"))) log("[ready-stack] " + stackOf(new Throwable("not ready")));
                            if (ready || !"1".equals(prop(PROP_FIX_SHUTTER, "0"))) {
                                return;
                            }
                            final Object module = param.thisObject;
                            try {
                                // Wait well past the capture+encode window, then
                                // only re-assert if GCam still has not recovered.
                                new android.os.Handler(android.os.Looper.getMainLooper())
                                        .postDelayed(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (READY.get()) {
                                                    return;   // already recovered on its own
                                                }
                                                try {
                                                    XposedHelpers.callMethod(module, "D", Boolean.TRUE);
                                                    READY.set(true);
                                                    log("[fix] re-asserted setReadyState(true)");
                                                } catch (Throwable t) {
                                                    log("[fix] re-assert failed: " + t);
                                                }
                                            }
                                        }, 8000L);
                            } catch (Throwable t) {
                                log("[fix] postDelayed failed: " + t);
                            }
                        }
                    });
            log("[fix] jjx.D(Z) hooked");
        } catch (Throwable t) {
            log("[fix] jjx.D hook FAILED: " + t);
        }
    }

    /**
     * Read-only probe for the shutter deadlock.
     *
     * After the first shot the JPEG is written but the shutter stays grey and
     * the log shows:
     *
     *   W CAM_lhy : Couldn't find inflight shot, already processed? ShotId-0
     *
     * i.e. the final image arrives after the shot has been dropped from
     * lhy.j (HashMap of in-flight shots), so GCam discards the result and never
     * re-arms the shutter. lhy.a(Llxm;) is the only method that removes entries,
     * so we log the map around both it and dp() to see which runs first.
     */
    private void hookInflightShotProbe(ClassLoader cl) {
        try {
            final Class<?> lhy = XposedHelpers.findClass("lhy", cl);
            XposedHelpers.findAndHookMethod(lhy, "dp",
                    XposedHelpers.findClass("inb", cl),
                    android.graphics.Bitmap.class,
                    XposedHelpers.findClass("com.google.googlex.gcam.ShotMetadata", cl),
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            log("[probe] dp() enter, inflight map = " + mapKeys(param.thisObject));
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            log("[probe] dp() exit,  inflight map = " + mapKeys(param.thisObject));
                        }
                    });
            log("[probe] lhy.dp hooked");

            XposedHelpers.findAndHookMethod(lhy, "a",
                    XposedHelpers.findClass("lxm", cl),
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            log("[probe] lhy.a(" + param.args[0].getClass().getSimpleName()
                                    + ") enter, inflight map = " + mapKeys(param.thisObject));
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            log("[probe] lhy.a() exit,  inflight map = " + mapKeys(param.thisObject));
                        }
                    });
            log("[probe] lhy.a hooked");

            // c(inb, BurstSpec, pro) is the only place that puts a shot into the
            // map. If this never fires, the shot is never registered at all.
            XposedHelpers.findAndHookMethod(lhy, "c",
                    XposedHelpers.findClass("inb", cl),
                    XposedHelpers.findClass("com.google.googlex.gcam.BurstSpec", cl),
                    XposedHelpers.findClass("pro", cl),
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            log("[probe] lhy.c(BurstSpec) enter, inflight map = " + mapKeys(param.thisObject));
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            log("[probe] lhy.c() exit,  inflight map = " + mapKeys(param.thisObject));
                        }
                    });
            log("[probe] lhy.c hooked");

            // e(lhz, inb) is the FrameServer "shot finished" callback and the
            // only other place that removes from the inflight map. Probe shows
            // it runs BEFORE dp(), so dp() finds nothing and GCam drops the
            // finished image -> shutter stays grey. Optional workaround: put the
            // entry back after e() so dp() can still resolve it.
            //   adb shell setprop debug.gcam.fix.inflight 1
            XposedHelpers.findAndHookMethod(lhy, "e",
                    XposedHelpers.findClass("lhz", cl),
                    XposedHelpers.findClass("inb", cl),
                    new XC_MethodHook() {
                        private java.util.HashMap<Object, Object> saved;

                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            log("[probe] lhy.e() enter, inflight map = " + mapKeys(param.thisObject));
                            if ("1".equals(prop(PROP_FIX_INFLIGHT, "0"))) {
                                try {
                                    java.util.Map<?, ?> m = currentMap(param.thisObject);
                                    if (m != null && !m.isEmpty()) {
                                        saved = new java.util.HashMap<Object, Object>();
                                        saved.putAll(m);
                                    }
                                } catch (Throwable ignored) {
                                }
                            }
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (saved != null && !saved.isEmpty()) {
                                try {
                                    java.util.Map<Object, Object> m = currentMap(param.thisObject);
                                    if (m != null) {
                                        m.putAll(saved);
                                        log("[probe] lhy.e() restored " + saved.size()
                                                + " inflight entry, map = " + mapKeys(param.thisObject));
                                    }
                                } catch (Throwable t) {
                                    log("[probe] restore failed: " + t);
                                }
                                saved = null;
                            }
                            log("[probe] lhy.e() exit,  inflight map = " + mapKeys(param.thisObject));
                        }
                    });
            log("[probe] lhy.e hooked");
        } catch (Throwable t) {
            log("[probe] FAILED: " + t);
        }
    }

    /** Best-effort dump of the inflight-shot map keys. */
    private static String mapKeys(Object lhyInstance) {
        try {
            Object map = XposedHelpers.getObjectField(lhyInstance, "j");
            if (map == null) {
                return "null";
            }
            java.util.Map<?, ?> m = (java.util.Map<?, ?>) map;
            return m.size() + " " + m.keySet();
        } catch (Throwable t) {
            return "err:" + t;
        }
    }

    // ---------------------------------------------------------------- hook 4
    // The HAL has no android.sensor.noiseProfile, so SENSOR_NOISE_PROFILE hands a
    // null Pair[] to flattenPairs(), which indexes it and throws. That aborts the
    // CaptureResult -> FrameMetadata conversion for every frame, so no JPEG is
    // ever encoded. Supply a flat Bayer noise model: shot[4] then read[4].
    private void hookNoiseModel(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod("com.NoiseModels", cl, "flattenPairs",
                    android.util.Pair[].class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Object arg = param.args[0];
                            if (arg == null || ((Object[]) arg).length == 0) {
                                param.setResult(defaultNoiseFlat());
                                if (NOISE_FIX_LOGGED.compareAndSet(false, true)) {
                                    log("[4/4] flattenPairs(null) -> synthetic noise model");
                                }
                            }
                        }
                    });
            log("[4/4] com.NoiseModels.flattenPairs hooked");
        } catch (Throwable t) {
            log("[4/4] flattenPairs FAILED: " + t);
        }
    }

    /** shot noise x4 followed by read noise x4, one entry per CFA channel. */
    private static double[] defaultNoiseFlat() {
        double[] d = new double[8];
        for (int i = 0; i < 4; i++) {
            d[i] = NOISE_SHOT;
            d[i + 4] = NOISE_READ;
        }
        return d;
    }

    // ---------------------------------------------------------------- hook 0
    // loadConfinesExposureISO (reached via pnt.<init> -> tbt.SetVALUES) reads the
    // boost range straight off CameraCharacteristics, bypassing pnt.o, and then
    // calls Range.getUpper() on it. Patch the platform accessor itself so the
    // value is never null anywhere in the process.
    private void hookCharacteristicsGet(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(CameraCharacteristics.class, "get",
                    CameraCharacteristics.Key.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String name = keyName(param.args);
                            if (name == null) {
                                return;
                            }
                            if (param.getResult() == null) {
                                Object synth = synthetic(name);
                                if (synth != null) {
                                    param.setResult(synth);
                                    log("[0/3] CameraCharacteristics.get -> synthetic for " + name);
                                }
                            }
                        }
                    });
            log("[0/3] CameraCharacteristics.get hooked");
        } catch (Throwable t) {
            log("[0/3] CameraCharacteristics.get FAILED: " + t);
        }
    }

    // ---------------------------------------------------------------- hook 1
    // CameraLens.equals spins forever inside LinkedHashMap.containsValue.
    // Reference equality only: same semantics as the DEX patch that made
    // "Cancelling viewfinder" disappear. Worst case is duplicate lens entries.
    private void hookLensEquals(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod("com.CameraLens", cl, "equals", Object.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult(Boolean.valueOf(param.thisObject == param.args[0]));
                        }
                    });
            log("[1/3] CameraLens.equals hooked (reference equality)");
        } catch (Throwable t) {
            log("[1/3] CameraLens.equals FAILED: " + t);
        }
    }

    // ---------------------------------------------------------------- hook 2
    // The sensor has no CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE. BSG reads it
    // unconditionally and blows up on null. Hand back a benign Range(100,100)
    // -- identical to what the DEX patch returned.
    private void hookCharacteristicsFallback(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod("pnt", cl, "o",
                    CameraCharacteristics.Key.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String name = keyName(param.args);
                            if (name == null) {
                                return;
                            }
                            Object synth = synthetic(name);
                            if (synth != null) {
                                if (param.hasThrowable() || param.getResult() == null) {
                                    param.setThrowable(null);
                                    param.setResult(synth);
                                    log("[2/3] synthesized metadata for " + name);
                                }
                                return;
                            }
                            // Diagnostics: surface every other metadata key the
                            // HAL does not provide, so we can extend coverage.
                            if (param.getResult() == null && LOGGED_NULL_KEYS.add(name)) {
                                log("[2/3] null metadata key: " + name);
                            }
                        }
                    });
            log("[2/3] pnt.o hooked");
        } catch (Throwable t) {
            log("[2/3] pnt.o FAILED: " + t);
        }
    }

    // ---------------------------------------------------------------- hook 3
    // Belt and braces: if anything still dereferences the missing key, swallow
    // the NPE so viewfinder creation is not cancelled. The DEX patch treated a
    // missing value as "1.0x exposure", which is what an unset field means.
    private void hookConfinesExposureGuard(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod("com.a", cl, "loadConfinesExposureISO",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.hasThrowable()) {
                                Throwable t = param.getThrowable();
                                if (t instanceof NullPointerException) {
                                    param.setThrowable(null);
                                    param.setResult(null);
                                    if (DUMPED_NPE.compareAndSet(false, true)) {
                                        log("[3/3] swallowed NPE in loadConfinesExposureISO, stack:");
                                        log(stackOf(t));
                                    }
                                } else {
                                    log("[3/3] loadConfinesExposureISO threw " + t);
                                }
                            }
                        }
                    });
            log("[3/3] com.a.loadConfinesExposureISO guarded");
        } catch (Throwable t) {
            log("[3/3] loadConfinesExposureISO FAILED: " + t);
        }
    }

    // ---------------------------------------------------------------- hook 5
    // Per-frame CaptureResult sanitiser. Two defects, both confirmed by the
    // libgcam diagnostics dumped during the 20:04:51 shutter press.
    //
    // This is deliberately conservative: we only rewrite values that are
    // provably unusable (a junk LSC gain map, a zero exposure). Everything the
    // HAL reports sanely is passed through untouched, and every original value
    // we override is logged once so the next session knows what it replaced.
    private void hookCaptureResultSanitize(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(CaptureResult.class, "get",
                    CaptureResult.Key.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String name = resultKeyName(param.args);
                            if (name == null) {
                                return;
                            }
                            if (SEEN_RESULT_KEYS.size() < 60 && SEEN_RESULT_KEYS.add(name)) {
                                log("[5/6] result key read: " + name);
                            }
                            Object value = param.getResult();

                            if (name.contains(KEY_LSC_MODE)) {
                                if (LSC_LOGGED.compareAndSet(false, true)) {
                                    log("[5/6] lensShadingMapMode reported as " + value
                                            + " (substitute=" + enabled(PROP_HOOK_LSC) + ")");
                                }
                                if (enabled(PROP_HOOK_LSC)) {
                                    param.setResult(Integer.valueOf(0));
                                }
                                return;
                            }

                            // The real lever. GCam reads the gain map itself, not
                            // the mode, and every payload frame was rejected by
                            // libgcam's CheckLscMap as "values out of range".
                            // Substituting a flat 1.0 map is the same thing as
                            // "no lens shading correction" -- vignetting comes
                            // back, but the per-pixel garbage multiply is gone.
                            if (name.contains(KEY_LSC_MAP)) {
                                if (!enabled(PROP_HOOK_LSC)) {
                                    return;
                                }
                                Object orig = value;
                                if (orig != null) {
                                    Object flat = identityLscMap(orig);
                                    if (flat != null) {
                                        param.setResult(flat);
                                        if (LSC_MAP_LOGGED.compareAndSet(false, true)) {
                                            log("[5/6] lensShadingCorrectionMap -> flat 1.0 identity ("
                                                    + lscDims(orig) + ")");
                                        }
                                    }
                                }
                                return;
                            }

                            if (name.contains(KEY_COLOR_GAINS)) {
                                if (GAINS_LOGGED.compareAndSet(false, true)) {
                                    log("[5/6] colorCorrection.gains = " + describe(value));
                                }
                                if (!enabled(PROP_HOOK_COLOR)) {
                                    return;
                                }
                                if (isZeroGains(value)) {
                                    Object g = neutralGains();
                                    if (g != NO_OVERRIDE) {
                                        param.setResult(g);
                                    }
                                    if (GAINS_FIX_LOGGED.compareAndSet(false, true)) {
                                        log("[5/6] zero colorCorrection.gains -> " + describe(g)
                                                + " (prop " + PROP_GAINS + ")");
                                    }
                                }
                                return;
                            }

                            if (name.contains(KEY_COLOR_TRANSFORM)) {
                                if (TRANSFORM_LOGGED.compareAndSet(false, true)) {
                                    log("[5/6] colorCorrection.transform = " + describeTransform(value));
                                }
                                if (!enabled(PROP_HOOK_COLOR)) {
                                    return;
                                }
                                if ("keep".equals(prop(PROP_TRANSFORM, "identity"))) {
                                    return;
                                }
                                if (isZeroTransform(value)) {
                                    param.setResult(identityTransform());
                                    if (TRANSFORM_FIX_LOGGED.compareAndSet(false, true)) {
                                        log("[5/6] zero colorCorrection.transform -> identity 3x3");
                                    }
                                }
                                return;
                            }

                            if (name.contains(KEY_NEUTRAL_POINT)) {
                                if (NEUTRAL_LOGGED.compareAndSet(false, true)) {
                                    log("[5/6] sensor.neutralColorPoint = " + describe(value));
                                }
                                return;
                            }

                            if (name.contains(KEY_FRAME_DURATION)) {
                                long fd = asLong(value, -1L);
                                if (FRAME_DUR_LOGGED.compareAndSet(false, true)) {
                                    log("[5/6] frameDuration = " + fd + "ns");
                                }
                                String mode = prop(PROP_FRAME_DUR, "keep");
                                if ("keep".equals(mode)) {
                                    return;
                                }
                                long target = FRAME_DUR_TARGET_NS;
                                if (!"auto".equals(mode)) {
                                    try {
                                        target = (long) (Float.parseFloat(mode) * 1_000_000L);
                                    } catch (Throwable ignored) {
                                        // keep the default
                                    }
                                }
                                if (fd > 0 && fd < target) {
                                    param.setResult(Long.valueOf(target));
                                    if (FRAME_DUR_FIX.compareAndSet(false, true)) {
                                        log("[5/6] frameDuration " + fd + "ns -> " + target + "ns");
                                    }
                                }
                                return;
                            }

                            if (name.contains(KEY_SENSITIVITY)) {
                                if (SENS_LOGGED.compareAndSet(false, true)) {
                                    log("[5/6] sensor.sensitivity = " + describe(value));
                                }
                                return;
                            }

                            if (name.contains(KEY_EXPOSURE_TIME)) {
                                long ns = asLong(value, -1L);
                                PreviewExposureTracker.observe(param.thisObject, ns);
                                if (ns <= 0 && enabled(PROP_HOOK_EXP)) {
                                    param.setResult(Long.valueOf(DEFAULT_FRAME_NS));
                                    if (EXPOSURE_LOGGED.compareAndSet(false, true)) {
                                        log("[5/6] exposureTime was " + ns
                                                + "ns -> substituted " + DEFAULT_FRAME_NS + "ns");
                                    }
                                } else if (EXPOSURE_SEEN.size() < 8) {
                                    EXPOSURE_SEEN.add(String.valueOf(ns));
                                }
                                return;
                            }

                            if (name.contains(KEY_AE_STATE)) {
                                if (AE_LOGGED.compareAndSet(false, true)) {
                                    log("[5/6] aeState observed: " + value);
                                }
                                String mode = prop(PROP_AE_STATE, "keep");
                                if (!"keep".equals(mode)) {
                                    try {
                                        int want = Integer.parseInt(mode.trim());
                                        param.setResult(Integer.valueOf(want));
                                        if (AE_FIX.compareAndSet(false, true)) {
                                            log("[5/6] aeState " + value + " -> " + want);
                                        }
                                    } catch (Throwable ignored) {
                                        // keep whatever the HAL reported
                                    }
                                }
                                return;
                            }
                        }
                    });
            log("[5/6] CaptureResult.get sanitiser hooked");
        } catch (Throwable t) {
            log("[5/6] CaptureResult.get FAILED: " + t);
        }
    }

    private static long asLong(Object o, long dflt) {
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        return dflt;
    }

    /**
     * The Unisoc HAL reports COLOR_CORRECTION_GAINS as a present but all-zero
     * RggbChannelVector. GCam multiplies the sensor signal by these gains when
     * converting to linear sRGB, so a zero vector maps every pixel to exactly
     * 0 -- which is precisely the black JPEG we get, complete with a
     * highlight_scale that blows up to 581 because the white point looks like
     * zero. Neutral 1.0 gains give a correctly-exposed image with no white
     * balance applied; real per-illuminant gains can be fitted later.
     */
    private static boolean isZeroGains(Object v) {
        if (v == null) {
            return true;
        }
        try {
            float r = ((Number) XposedHelpers.callMethod(v, "getRed")).floatValue();
            float ge = ((Number) XposedHelpers.callMethod(v, "getGreenEven")).floatValue();
            float go = ((Number) XposedHelpers.callMethod(v, "getGreenOdd")).floatValue();
            float b = ((Number) XposedHelpers.callMethod(v, "getBlue")).floatValue();
            return r <= 0f && ge <= 0f && go <= 0f && b <= 0f;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object neutralGains() {
        // Tunable at runtime so the white-balance search does not need a rebuild:
        //   adb shell setprop debug.gcam.gains 1.0,1.0,1.0
        //   adb shell setprop debug.gcam.gains null      (let GCam estimate its own)
        //   adb shell setprop debug.gcam.gains keep      (pass the HAL value through)
        String spec = prop(PROP_GAINS, "1.0,1.0,1.0");
        if ("null".equals(spec)) {
            return null;
        }
        if ("keep".equals(spec)) {
            return NO_OVERRIDE;
        }
        try {
            String[] p = spec.split(",");
            float r = Float.parseFloat(p[0].trim());
            float g = Float.parseFloat(p[1].trim());
            float b = p.length > 2 ? Float.parseFloat(p[2].trim()) : 1.0f;
            return new android.hardware.camera2.params.RggbChannelVector(r, g, g, b);
        } catch (Throwable t) {
            return new android.hardware.camera2.params.RggbChannelVector(1f, 1f, 1f, 1f);
        }
    }

    static String prop(String key, String dflt) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method m = sp.getMethod("get", String.class, String.class);
            Object v = m.invoke(null, key, dflt);
            return v == null ? dflt : String.valueOf(v).trim();
        } catch (Throwable t) {
            return dflt;
        }
    }

    /**
     * COLOR_CORRECTION_TRANSFORM is a 3x3 rational matrix. An all-zero matrix
     * collapses every pixel to zero just like zero gains do, so we treat it as
     * unusable too and fall back to the identity matrix. Colours will be wrong
     * but the image survives, which is what we need to separate "black" from
     * "badly coloured".
     */
    private static int[] transformElements(Object v) {
        if (v == null) {
            return null;
        }
        try {
            int[] dst = new int[18];
            XposedHelpers.callMethod(v, "getElements", dst, Integer.valueOf(0));
            return dst;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isZeroTransform(Object v) {
        int[] e = transformElements(v);
        if (e == null) {
            return true;
        }
        for (int i : e) {
            if (i != 0) {
                return false;
            }
        }
        return true;
    }

    private static String describeTransform(Object v) {
        int[] e = transformElements(v);
        if (e == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < e.length; i += 2) {
            sb.append(e[i]).append('/').append(e[i + 1]).append(' ');
        }
        return sb.toString().trim();
    }

    private static Object identityTransform() {
        int[] id = {1, 1, 0, 1, 0, 1, 0, 1, 1, 1, 0, 1, 0, 1, 0, 1, 1, 1};
        return new android.hardware.camera2.params.ColorSpaceTransform(id);
    }

    /**
     * Build a LensShadingMap whose every gain factor is 1.0, i.e. "no shading
     * correction". LensShadingMap has no public factory, so we copy the source
     * map's dimensions and call its hidden constructor through reflection.
     * Returns null on failure, in which case the caller leaves the original
     * value in place rather than risking a null dereference downstream.
     */
    private static Object identityLscMap(Object orig) {
        Object cached = FLAT_LSC;
        if (cached != null) {
            return cached;
        }
        try {
            int rows = ((Number) XposedHelpers.callMethod(orig, "getRowCount")).intValue();
            int cols = ((Number) XposedHelpers.callMethod(orig, "getColumnCount")).intValue();
            if (rows <= 0 || cols <= 0) {
                return null;
            }
            float[] gains = new float[rows * cols * 4];
            java.util.Arrays.fill(gains, 1.0f);
            Class<?> cls = Class.forName("android.hardware.camera2.params.LensShadingMap");
            java.lang.reflect.Constructor<?> ctor =
                    cls.getDeclaredConstructor(float[].class, int.class, int.class);
            ctor.setAccessible(true);
            // LensShadingMap is immutable, so one instance can serve every frame.
            // Building it per frame meant reflection on the capture hot path,
            // which risked stalling the burst (and a stalled burst is exactly
            // what leaves only two payload frames and a black result).
            FLAT_LSC = ctor.newInstance(gains, Integer.valueOf(rows), Integer.valueOf(cols));
            return FLAT_LSC;
        } catch (Throwable t) {
            if (LSC_MAP_LOGGED.compareAndSet(false, true)) {
                log("[5/6] identityLscMap failed: " + t);
            }
            return null;
        }
    }

    private static volatile Object FLAT_LSC = null;

    private static String lscDims(Object map) {
        try {
            return XposedHelpers.callMethod(map, "getRowCount")
                    + "x" + XposedHelpers.callMethod(map, "getColumnCount");
        } catch (Throwable ignored) {
            return "?";
        }
    }

    private static String describe(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Object[]) {
            Object[] a = (Object[]) value;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < a.length && i < 8; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(a[i]);
            }
            return sb.append(']').toString();
        }
        return String.valueOf(value);
    }

    private static String resultKeyName(Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) {
            return null;
        }
        try {
            return ((CaptureResult.Key<?>) args[0]).getName();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String stackOf(Throwable t) {
        StringBuilder sb = new StringBuilder(t.toString());
        StackTraceElement[] st = t.getStackTrace();
        int n = 0;
        for (StackTraceElement e : st) {
            sb.append("\n    at ").append(e.toString());
            if (++n >= 14) {
                break;
            }
        }
        return sb.toString();
    }

    private static String keyName(Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) {
            return null;
        }
        try {
            return ((CameraCharacteristics.Key<?>) args[0]).getName();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Synthetic stand-ins for metadata this HAL simply does not implement. */
    private static Object synthetic(String name) {
        if (name.contains(KEY_BOOST_RANGE)) {
            return Range.create(100, 100);
        }
        if (name.contains(KEY_NOISE_PROFILE)) {
            Double[] shot = {NOISE_SHOT, NOISE_SHOT, NOISE_SHOT, NOISE_SHOT};
            Double[] read = {NOISE_READ, NOISE_READ, NOISE_READ, NOISE_READ};
            return Pair.create(shot, read);
        }
        return null;
    }

    private static void log(String msg) {
        XposedBridge.log(TAG + msg);
    }
}
