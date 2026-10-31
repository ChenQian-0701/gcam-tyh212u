package com.tyh212u.gcamhook;
import de.robv.android.xposed.*;
import android.hardware.camera2.*;
import android.os.Handler;
import java.util.List;
import java.util.concurrent.Executor;
/** Use STILL_CAPTURE for explicitly manual requests of a new HDR payload burst. */
final class StillIntentRepair {
 static final ThreadLocal<Boolean> ACTIVE=new ThreadLocal<>();
 static void install(ClassLoader cl){
  XposedHelpers.findAndHookMethod("com.google.googlex.gcam.GcamModuleJNI",cl,"Gcam_BeginPayloadFrames",long.class,XposedHelpers.findClass("com.google.googlex.gcam.Gcam",cl),int.class,long.class,XposedHelpers.findClass("com.google.googlex.gcam.BurstSpec",cl),new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){Object v=XposedHelpers.callMethod(p.args[4],"c");ACTIVE.set(((Number)XposedHelpers.callMethod(v,"a")).intValue()>0);}});
  XposedHelpers.findAndHookMethod(CaptureRequest.Builder.class,"build",new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){
   if(!Boolean.TRUE.equals(ACTIVE.get()))return;
   CaptureRequest.Builder b=(CaptureRequest.Builder)p.thisObject;Integer intent=b.get(CaptureRequest.CONTROL_CAPTURE_INTENT),ae=b.get(CaptureRequest.CONTROL_AE_MODE);Long exp=b.get(CaptureRequest.SENSOR_EXPOSURE_TIME);
   if(intent!=null && intent==0 && ae!=null && ae==0 && exp!=null && exp>0){b.set(CaptureRequest.CONTROL_CAPTURE_INTENT,CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);XposedBridge.log("TYH212U-HOOK: [still-intent] CUSTOM -> STILL_CAPTURE exp_ns="+exp);}
  }});
  Class<?> session=XposedHelpers.findClass("android.hardware.camera2.impl.CameraCaptureSessionImpl",cl);
  XC_MethodHook clear=new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){ACTIVE.remove();}};
  XposedHelpers.findAndHookMethod(session,"captureBurst",List.class,CameraCaptureSession.CaptureCallback.class,Handler.class,clear);
  XposedHelpers.findAndHookMethod(session,"captureBurstRequests",List.class,Executor.class,CameraCaptureSession.CaptureCallback.class,clear);
 }
}
