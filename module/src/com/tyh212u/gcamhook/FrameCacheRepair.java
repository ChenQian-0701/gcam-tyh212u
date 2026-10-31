package com.tyh212u.gcamhook;
import de.robv.android.xposed.*;
/** Limit retained Java ZSL frames so the shared image pool keeps capture headroom.
 * Native trimming and reference release remain with the original FrameBuffer.
 */
final class FrameCacheRepair {
 static void install(ClassLoader cl){
  XposedHelpers.findAndHookMethod("phw",cl,"v",XposedHelpers.findClass("pgo",cl),int.class,new XC_MethodHook(){
   protected void beforeHookedMethod(MethodHookParam p){int old=((Number)p.args[1]).intValue();if(old>8){p.args[1]=8;XposedBridge.log("TYH212U-HOOK: [frame-cache] capacity="+old+" -> 8 source="+p.args[0]);}}
  });
 }
}
