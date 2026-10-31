package com.tyh212u.gcamhook;
import de.robv.android.xposed.*;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicInteger;
/** Diagnose BSG's post-conversion corrector independently of Camera2 metadata. */
final class ExposureProbe {
 static final String[] N={"actual_exposure_time_ms","actual_analog_gain","applied_digital_gain","post_raw_digital_gain"};
 static final AtomicInteger COUNT=new AtomicInteger();
 static float[] read(Object frame,Class<?> jni,Class<?> meta)throws Exception {
  long ptr=((Number)XposedHelpers.getObjectField(frame,"a")).longValue();float[] a=new float[N.length];
  for(int i=0;i<a.length;i++)a[i]=((Number)jni.getDeclaredMethod("FrameMetadata_"+N[i]+"_get",long.class,meta).invoke(null,ptr,frame)).floatValue();return a;
 }
 static void install(ClassLoader cl)throws Exception {
  Class<?> meta=Class.forName("com.google.googlex.gcam.FrameMetadata",false,cl);
  Class<?> jni=Class.forName("com.google.googlex.gcam.GcamModuleJNI",false,cl);
  XposedHelpers.findAndHookMethod("com.a",cl,"ExpAnalogISODigitISOCorrector",meta,XposedHelpers.findClass("pro",cl),new XC_MethodHook(){
   protected void beforeHookedMethod(MethodHookParam p) {
    try {float[] a=read(p.args[0],jni,meta);int n=COUNT.incrementAndGet();if(n<=12 || n%120==0)XposedBridge.log("TYH212U-HOOK: [exposure-before] "+java.util.Arrays.toString(a));
    }catch(Throwable t){if(COUNT.incrementAndGet()<5)XposedBridge.log("TYH212U-HOOK: [exposure-error] "+t);}
   }
   protected void afterHookedMethod(MethodHookParam p){try {int n=COUNT.get();if(n<=12 || n%120==0)XposedBridge.log("TYH212U-HOOK: [exposure-after] "+java.util.Arrays.toString(read(p.args[0],jni,meta)));}catch(Throwable ignored){}}
  });
 }
}
