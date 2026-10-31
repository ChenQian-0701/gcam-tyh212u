package com.tyh212u.gcamhook;
import de.robv.android.xposed.*;
import android.hardware.camera2.*;
import android.os.Handler;
import java.util.*;
import java.util.concurrent.Executor;
/** Read-only Camera2 submission audit, independent of native HDR shot plans. */
final class SessionRequestProbe {
 static void install(ClassLoader cl){
  Class<?> session=XposedHelpers.findClass("android.hardware.camera2.impl.CameraCaptureSessionImpl",cl);
  XposedHelpers.findAndHookMethod("ila",cl,"n",XposedHelpers.findClass("inb",cl),XposedHelpers.findClass("pnx",cl),int.class,XposedHelpers.findClass("pro",cl),XposedHelpers.findClass("sxd",cl),XposedHelpers.findClass("prw",cl),XposedHelpers.findClass("prw",cl),XposedHelpers.findClass("rss",cl),XposedHelpers.findClass("pro",cl),new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){
   try{Object m=p.args[3];Object value=XposedHelpers.callMethod(m,"dy",CaptureResult.class);if(value instanceof CaptureResult){CaptureResult result=(CaptureResult)value;CaptureRequest request=result.getRequest();XposedBridge.log("TYH212U-HOOK: [camera2-association] i="+p.args[2]+" frame="+result.getFrameNumber()+" tag="+request.getTag()+" request_exp_ns="+request.get(CaptureRequest.SENSOR_EXPOSURE_TIME)+" result_exp_ns="+result.get(CaptureResult.SENSOR_EXPOSURE_TIME)+" request_iso="+request.get(CaptureRequest.SENSOR_SENSITIVITY)+" result_iso="+result.get(CaptureResult.SENSOR_SENSITIVITY));}}catch(Throwable t){XposedBridge.log("TYH212U-HOOK: [camera2-association] "+t);}
  }});

  XC_MethodHook hook=new XC_MethodHook(){int n;
   protected void beforeHookedMethod(MethodHookParam p){
    if(n++>40)return;
    List<?> list=p.args[0] instanceof List?(List<?>)p.args[0]:Collections.singletonList(p.args[0]);
    int i=0;for(Object value:list){if(!(value instanceof CaptureRequest))continue;CaptureRequest r=(CaptureRequest)value;
     XposedBridge.log("TYH212U-HOOK: [camera2-submit] method="+p.method.getName()+" i="+(i++)+" intent="+r.get(CaptureRequest.CONTROL_CAPTURE_INTENT)+" ae="+r.get(CaptureRequest.CONTROL_AE_MODE)+" lock="+r.get(CaptureRequest.CONTROL_AE_LOCK)+" exp_ns="+r.get(CaptureRequest.SENSOR_EXPOSURE_TIME)+" iso="+r.get(CaptureRequest.SENSOR_SENSITIVITY)+" zsl="+r.get(CaptureRequest.CONTROL_ENABLE_ZSL)+" tag="+r.getTag());
    }
   }
  };
  XposedHelpers.findAndHookMethod(session,"captureBurst",List.class,CameraCaptureSession.CaptureCallback.class,Handler.class,hook);
  XposedHelpers.findAndHookMethod(session,"capture",CaptureRequest.class,CameraCaptureSession.CaptureCallback.class,Handler.class,hook);
  XposedHelpers.findAndHookMethod(session,"setRepeatingRequest",CaptureRequest.class,CameraCaptureSession.CaptureCallback.class,Handler.class,hook);
  XposedHelpers.findAndHookMethod(session,"captureBurstRequests",List.class,Executor.class,CameraCaptureSession.CaptureCallback.class,hook);
 }
}
