package com.tyh212u.gcamhook;
import de.robv.android.xposed.*;
import android.hardware.camera2.*;
import java.util.*;
import java.lang.reflect.*;
final class CaptureAudit {
 static final Set<String> HOOKED=Collections.synchronizedSet(new HashSet<String>());
 static void install(ClassLoader cl)throws Exception {
  String jn="com.google.googlex.gcam.GcamModuleJNI";Class<?> jni=XposedHelpers.findClass(jn,cl);
  Class<?> gcam=XposedHelpers.findClass("com.google.googlex.gcam.Gcam",cl),spec=XposedHelpers.findClass("com.google.googlex.gcam.BurstSpec",cl),frClass=XposedHelpers.findClass("com.google.googlex.gcam.FrameRequest",cl);
  Class<?> shotClass=XposedHelpers.findClass("com.google.googlex.gcam.ShotParams",cl);
  XposedHelpers.findAndHookMethod(jn,cl,"Gcam_StartShotCapture",long.class,gcam,int.class,long.class,shotClass,long.class,XposedHelpers.findClass("com.google.googlex.gcam.ShotCallbacks",cl),long.class,XposedHelpers.findClass("com.google.googlex.gcam.DebugParams",cl),new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p)throws Throwable {
   long ptr=((Number)p.args[3]).longValue();Object shot=p.args[4];
   Object merge=jni.getMethod("ShotParams_merge_max_frame_count_override_get",long.class,shotClass).invoke(null,ptr,shot);
   Object awb=jni.getMethod("ShotParams_GcamAwbDesired",long.class,shotClass).invoke(null,ptr,shot);
   XposedBridge.log("TYH212U-HOOK: [audit-shot] merge_max_override="+merge+" google_awb="+awb);
  }});
  XposedHelpers.findAndHookMethod(jn,cl,"Gcam_BeginPayloadFrames",long.class,gcam,int.class,long.class,spec,new XC_MethodHook(){
   protected void beforeHookedMethod(MethodHookParam p)throws Throwable {
    Object vec=XposedHelpers.callMethod(p.args[4],"c");int n=((Number)XposedHelpers.callMethod(vec,"a")).intValue();
    for(int i=0;i<n;i++){Object fr=XposedHelpers.callMethod(vec,"b",i);long ptr=((Number)XposedHelpers.getObjectField(fr,"a")).longValue();StringBuilder b=new StringBuilder();
     for(String k:new String[]{"desired_exposure_time_ms","desired_analog_gain","desired_digital_gain"})b.append(k).append('=').append(jni.getMethod("FrameRequest_"+k+"_get",long.class,frClass).invoke(null,ptr,fr)).append(' ');
     XposedBridge.log("TYH212U-HOOK: [audit-plan] shot="+p.args[2]+" i="+i+" "+b);
    }
   }
  });
  XposedHelpers.findAndHookMethod("ila",cl,"n",XposedHelpers.findClass("inb",cl),XposedHelpers.findClass("pnx",cl),int.class,XposedHelpers.findClass("pro",cl),XposedHelpers.findClass("sxd",cl),XposedHelpers.findClass("prw",cl),XposedHelpers.findClass("prw",cl),XposedHelpers.findClass("rss",cl),XposedHelpers.findClass("pro",cl),new XC_MethodHook(){
   protected void beforeHookedMethod(MethodHookParam p){try{Object m=p.args[3];XposedBridge.log("TYH212U-HOOK: [audit-actual] shot="+XposedHelpers.callMethod(p.args[0],"a")+" i="+p.args[2]+" exp_ns="+XposedHelpers.callMethod(m,"a",CaptureResult.SENSOR_EXPOSURE_TIME)+" iso="+XposedHelpers.callMethod(m,"a",CaptureResult.SENSOR_SENSITIVITY)+" sensor_ts="+XposedHelpers.callMethod(m,"a",CaptureResult.SENSOR_TIMESTAMP));}catch(Throwable t){XposedBridge.log("TYH212U-HOOK: [audit-actual] "+t);}}
  });
  XposedHelpers.findAndHookMethod(CaptureRequest.Builder.class,"build",new XC_MethodHook(){
   protected void afterHookedMethod(MethodHookParam p){try{CaptureRequest r=(CaptureRequest)p.getResult();Integer intent=r.get(CaptureRequest.CONTROL_CAPTURE_INTENT);if(intent!=null && intent==2)XposedBridge.log("TYH212U-HOOK: [audit-request] exp_ns="+r.get(CaptureRequest.SENSOR_EXPOSURE_TIME)+" iso="+r.get(CaptureRequest.SENSOR_SENSITIVITY)+" ae="+r.get(CaptureRequest.CONTROL_AE_MODE));}catch(Throwable ignored){}}
  });
  String base="com.google.googlex.gcam.hdrplus.";
  for(String role:new String[]{"MergedRaw","MergedLumaDenoised","MergedChromaDenoised","FinalImage"}) {
   String iface=role.equals("FinalImage")?"ManagedImageCallback":role+"Callback";
   String setter=role.equals("MergedRaw")?"nativeSetMergedRawImageCallback":"nativeSet"+role+"Callback";
   Class<?> cb=XposedHelpers.findClass(base+iface,cl);
   XposedHelpers.findAndHookMethod(base+"NativeHdrPlusInterface",cl,setter,long.class,cb,new XC_MethodHook(){
    protected void beforeHookedMethod(MethodHookParam p){Object target=p.args[1];if(target==null)return;Class<?> c=target.getClass();String key=c.getName()+"#"+role;if(!HOOKED.add(key))return;
     try {XC_MethodHook hook=new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam v){XposedBridge.log("TYH212U-HOOK: [audit-callback] "+role+" "+Arrays.toString(v.args));}};
      if(role.equals("MergedRaw") || role.equals("FinalImage"))XposedHelpers.findAndHookMethod(c,"accept",int.class,long.class,long.class,int.class,hook);
      else XposedHelpers.findAndHookMethod(c,"accept",int.class,long.class,long.class,hook);
      XposedBridge.log("TYH212U-HOOK: [audit-hook] "+role+" "+c.getName());
     }catch(Throwable t){XposedBridge.log("TYH212U-HOOK: [audit-hook] "+role+" "+t);}
    }
   });
  }
 }
}
