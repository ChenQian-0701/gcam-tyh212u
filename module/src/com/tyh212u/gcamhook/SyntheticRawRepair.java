package com.tyh212u.gcamhook;
import de.robv.android.xposed.*;
import java.nio.ByteBuffer;
/** TYH212U YUV fallback: match synthetic packed RAW10 pixels and metadata levels. */
final class SyntheticRawRepair {
 static void install(ClassLoader cl) {
  XposedHelpers.findAndHookMethod("com.android.app.Application",cl,"o",ByteBuffer.class,ByteBuffer.class,int.class,int.class,int.class,int.class,boolean.class,float[].class,int.class,float[].class,int.class,int.class,float[].class,int.class,float[].class,new XC_MethodHook(){
   boolean logged;
   protected void beforeHookedMethod(MethodHookParam p){
    if(!Boolean.TRUE.equals(p.args[6]))return;
    p.args[9]=new float[]{0,0,0,0};
    if(!logged){logged=true;XposedBridge.log("TYH212U-HOOK: [synthetic-raw] YUV->RAW10 black offset 0");}
   }
  });
  String jni="com.google.googlex.gcam.GcamModuleJNI";
  Class<?> meta=XposedHelpers.findClass("com.google.googlex.gcam.StaticMetadata",cl);
  XposedHelpers.findAndHookMethod(jni,cl,"StaticMetadata_white_level_set",long.class,meta,int.class,new XC_MethodHook(){
   protected void beforeHookedMethod(MethodHookParam p){XposedBridge.log("TYH212U-HOOK: [synthetic-raw] white="+p.args[2]+" -> 1023");p.args[2]=1023;}
  });
  XposedHelpers.findAndHookMethod(jni,cl,"FrameMetadata_black_levels_bayer_set",long.class,XposedHelpers.findClass("com.google.googlex.gcam.FrameMetadata",cl),long.class,XposedHelpers.findClass("com.google.googlex.gcam.FloatArray4",cl),new XC_MethodHook(){
   int count;
   protected void beforeHookedMethod(MethodHookParam p){
    Object levels=p.args[3];StringBuilder before=new StringBuilder();
    for(int i=0;i<4;i++){if(count<3)before.append(XposedHelpers.callMethod(levels,"a",i)).append(',');XposedHelpers.callMethod(levels,"d",i,0f);}
    if(++count<=3)XposedBridge.log("TYH212U-HOOK: [synthetic-raw] black="+before+" -> 0");
   }
  });
 }
}
