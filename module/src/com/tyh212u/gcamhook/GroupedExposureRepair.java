package com.tyh212u.gcamhook;
import de.robv.android.xposed.*;
/** Group requests into a stable short section and a longer exposure section.
 * Never rewrites actual frame metadata or bypasses native consistency checks.
 */
final class GroupedExposureRepair {
 static void install(ClassLoader cl)throws Exception {
  String name="com.google.googlex.gcam.GcamModuleJNI";
  Class<?> jni=XposedHelpers.findClass(name,cl),fr=XposedHelpers.findClass("com.google.googlex.gcam.FrameRequest",cl);
  Class<?> spec=XposedHelpers.findClass("com.google.googlex.gcam.BurstSpec",cl);
  java.lang.reflect.Field owner=spec.getDeclaredField("b");owner.setAccessible(true);
  XC_MethodHook patch=new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p)throws Throwable {
   if(p.hasThrowable() || p.getResult()==null)return;long result=((Number)p.getResult()).longValue();if(result==0)return;
   Object borrowed=spec.getDeclaredConstructor(long.class).newInstance(result);owner.setBoolean(borrowed,false);
   Object vector=XposedHelpers.callMethod(borrowed,"c");int n=((Number)XposedHelpers.callMethod(vector,"a")).intValue();if(n!=8 && n!=9)return;
   Object first=XposedHelpers.callMethod(vector,"b",0);long fp=((Number)XposedHelpers.getObjectField(first,"a")).longValue();
   float exp=((Number)jni.getMethod("FrameRequest_desired_exposure_time_ms_get",long.class,fr).invoke(null,fp,first)).floatValue();
   float gain=((Number)jni.getMethod("FrameRequest_desired_analog_gain_get",long.class,fr).invoke(null,fp,first)).floatValue();
   float planExp=exp;float previewExp=PreviewExposureTracker.shortBaseMs();
   if(previewExp>0f)exp=previewExp;
   float maxMs=50f;
   try{maxMs=Float.parseFloat(HookEntry.prop("debug.gcam.grouped.maxms","50"));}catch(Throwable ignored){}
   if(!(exp>0 && exp<=maxMs && gain>0)){XposedBridge.log("TYH212U-HOOK: [grouped-exposure] skip exp="+exp+"ms max="+maxMs+" plan="+planExp+" preview="+previewExp+" gain="+gain);return;}
   // Split direction matters more than the absolute numbers.
   //   double : short=exp, long=2*exp  -> the next plan inherits 2*exp and the
   //            base ratchets 40 -> 80 -> 160 across consecutive shots.
   //   half   : short=exp/2, long=exp  -> the AE-correct value stays the long
   //            frame, so the next plan inherits the same exp and the base is
   //            stable shot to shot.
   boolean half="half".equals(HookEntry.prop("debug.gcam.grouped.split","half"));
   float shortMs=half?exp*0.5f:exp;
   float longMs=half?exp:exp*2f;
   float longer=longMs;
   for(int i=0;i<n;i++){Object request=XposedHelpers.callMethod(vector,"b",i);long ptr=((Number)XposedHelpers.getObjectField(request,"a")).longValue();
    jni.getMethod("FrameRequest_desired_exposure_time_ms_set",long.class,fr,float.class).invoke(null,ptr,request,i<3?shortMs:longer);
    jni.getMethod("FrameRequest_desired_analog_gain_set",long.class,fr,float.class).invoke(null,ptr,request,gain);
    jni.getMethod("FrameRequest_desired_digital_gain_set",long.class,fr,float.class).invoke(null,ptr,request,1f);
    jni.getMethod("FrameRequest_type_set",long.class,fr,int.class).invoke(null,ptr,request,i<3?1:3);
   }
   XposedBridge.log("TYH212U-HOOK: [grouped-exposure] "+(half?"half":"double")+" 3 short="+shortMs+"ms then remaining long="+longer+"ms gain="+gain+" (plan="+planExp+" preview="+previewExp+")");
  }};
  Class<?> gcam=XposedHelpers.findClass("com.google.googlex.gcam.Gcam",cl),meta=XposedHelpers.findClass("com.google.googlex.gcam.FrameMetadata",cl),opts=XposedHelpers.findClass("com.google.googlex.gcam.BuildPayloadBurstSpecOptions",cl);
  XposedHelpers.findAndHookMethod(name,cl,"Gcam_BuildPayloadBurstSpec__SWIG_0",long.class,gcam,int.class,long.class,XposedHelpers.findClass("com.google.googlex.gcam.RawReadView",cl),long.class,meta,long.class,XposedHelpers.findClass("com.google.googlex.gcam.SpatialGainMap",cl),long.class,opts,patch);
  XposedHelpers.findAndHookMethod(name,cl,"Gcam_BuildPayloadBurstSpec__SWIG_1",long.class,gcam,int.class,long.class,meta,long.class,opts,patch);
 }
}
