package com.tyh212u.gcamhook;

import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.os.SystemClock;

import de.robv.android.xposed.XposedBridge;

/**
 * Tracks the exposure the viewfinder is actually running at.
 *
 * Why: the grouped-exposure experiment derives its short section from the first
 * frame of the burst the native planner handed back. That value is itself the
 * previous shot's long exposure, so the short base climbs 20 -> 40 -> 80 ms
 * across consecutive shots and the grouping degrades into "everything long".
 *
 * The honest reference is the live preview: GCam keeps a repeating PREVIEW
 * request running between shots, and its CaptureResult carries the exposure the
 * sensor is genuinely settled on right now. Reading that gives every shot the
 * same starting point instead of a value inherited from the last capture.
 *
 * Fed from HookEntry's existing CaptureResult.get sanitiser (the exposureTime
 * branch), which already sees every result GCam reads. Hooking
 * TotalCaptureResult's constructors was tried first and never fired on this
 * build, so the accessor is the reliable entry point.
 *
 * Read-only: nothing here rewrites metadata, requests or results.
 */
final class PreviewExposureTracker {

    private static final String PROP = "debug.gcam.fix.previewbase";
    /** A preview result older than this is not representative of "now". */
    private static final long FRESH_MS = 2000L;

    private static volatile long exposureNs = -1L;
    private static volatile long wallClock = 0L;
    private static volatile long sensorTs = -1L;
    private static volatile int seen = 0;

    private PreviewExposureTracker() {
    }

    static boolean enabled() {
        return "1".equals(HookEntry.prop(PROP, "1"));
    }

    /**
     * Called from the CaptureResult.get hook for android.sensor.exposureTime.
     *
     * @param captureResult the CaptureResult the value came from
     * @param expNs         the exposure the HAL reported, in nanoseconds
     */
    static void observe(Object captureResult, long expNs) {
        if (!enabled() || expNs <= 0L || !(captureResult instanceof CaptureResult)) {
            return;
        }
        try {
            CaptureResult result = (CaptureResult) captureResult;
            CaptureRequest request = result.getRequest();
            if (request == null) {
                return;
            }
            Integer intent = request.get(CaptureRequest.CONTROL_CAPTURE_INTENT);
            if (intent == null || intent != CaptureRequest.CONTROL_CAPTURE_INTENT_PREVIEW) {
                return;
            }
            Boolean lock = request.get(CaptureRequest.CONTROL_AE_LOCK);
            if (lock != null && lock) {
                return;
            }
            exposureNs = expNs;
            wallClock = SystemClock.elapsedRealtime();
            if (seen++ == 0) {
                XposedBridge.log("TYH212U-HOOK: [preview-base] first preview sample " + describe());
            }
        } catch (Throwable ignored) {
            // read-only probe: never disturb the frame path
        }
    }

    /**
     * @return the current preview exposure in milliseconds, or -1 when there is
     *         no fresh PREVIEW result to trust.
     */
    static float shortBaseMs() {
        if (!enabled()) {
            return -1f;
        }
        long age = SystemClock.elapsedRealtime() - wallClock;
        if (wallClock == 0L || age > FRESH_MS || exposureNs <= 0L) {
            return -1f;
        }
        return exposureNs / 1_000_000f;
    }

    static String describe() {
        long age = wallClock == 0L ? -1L : SystemClock.elapsedRealtime() - wallClock;
        return "preview exp=" + (exposureNs / 1_000_000f) + "ms age=" + age
                + "ms samples=" + seen + " ts=" + sensorTs;
    }
}
