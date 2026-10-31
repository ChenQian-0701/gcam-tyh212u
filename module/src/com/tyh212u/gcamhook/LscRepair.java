package com.tyh212u.gcamhook;
import de.robv.android.xposed.*;
import java.lang.reflect.*;
/** Supply a valid native flat grid to both ZSL and enhanced HDR+ paths. */
final class LscRepair {
 static void install(final ClassLoader cl) throws Exception {
  final Class<?> map=Class.forName("com.google.googlex.gcam.SpatialGainMap",false,cl);
  Class<?> jni=Class.forName("com.google.googlex.gcam.GcamModuleJNI",false,cl);
  final Method create=jni.getDeclaredMethod("new_SpatialGainMap__SWIG_2",int.class,int.class);
  final Method write=jni.getDeclaredMethod("SpatialGainMap_WriteRggb",long.class,map,int.class,int.class,int.class,float.class);
  final Method delete=jni.getDeclaredMethod("delete_SpatialGainMap",long.class);
  final Constructor<?> wrap=map.getDeclaredConstructor(long.class);
  create.setAccessible(true); write.setAccessible(true); delete.setAccessible(true);wrap.setAccessible(true);
  XposedHelpers.findAndHookMethod("tbt",cl,"q",XposedHelpers.findClass("pro",cl),new XC_MethodHook(){
   boolean logged;
   protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
    long ptr=((Number)create.invoke(null,2,2)).longValue();
    if(ptr==0)throw new IllegalStateException("SpatialGainMap allocation failed");
    Object m=null;
    try {
     m=wrap.newInstance(ptr);
     for(int y=0;y<2;y++)for(int x=0;x<2;x++)for(int c=0;c<4;c++)write.invoke(null,ptr,m,x,y,c,1.0f);
     p.setResult(m);
     if(!logged){logged=true;XposedBridge.log("TYH212U-HOOK: [native-lsc] tbt.q -> 2x2 RGGB flat grid");}
    }catch(Throwable t){if(m!=null)XposedHelpers.callMethod(m,"a");else delete.invoke(null,ptr);throw t;}
   }
  });
 }
}
