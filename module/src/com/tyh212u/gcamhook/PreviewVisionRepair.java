package com.tyh212u.gcamhook;
import de.robv.android.xposed.*;
/** Optional isolation of CameraVisionKit's preview-image consumer.
 * Stop before it acquires a frame reference; leave the owning ticket untouched.
 */
final class PreviewVisionRepair {
 static void install(ClassLoader cl){
  XposedHelpers.findAndHookMethod("mcc",cl,"h",XposedHelpers.findClass("pjo",cl),XposedHelpers.findClass("phh",cl),new XC_MethodHook(){
   boolean logged;
   protected void beforeHookedMethod(MethodHookParam p){p.setResult(null);if(!logged){logged=true;XposedBridge.log("TYH212U-HOOK: [preview-vision] optional preview consumer dispatch disabled");}}
  });
 }
}
