package com.tyh212u.gcamhook;
import de.robv.android.xposed.*;
import java.lang.reflect.Field;
/** Restore the original direct-channel capability check on this non-Pixel HAL.
 * The BSG force flag bypasses that check and starts a software-filled ring.
 */
final class GyroFallbackRepair {
 static void install(ClassLoader cl)throws Exception {
  Field flag=XposedHelpers.findClass("com.a",cl).getField("camera_force_gyro_sample");
  XposedHelpers.findAndHookMethod("hoh",cl,"p",XposedHelpers.findClass("hmn",cl),new XC_MethodHook(){
   int count;
   protected void afterHookedMethod(MethodHookParam p)throws Throwable{
    if(p.args[0]!=flag.get(null) || p.hasThrowable())return;
    if(count++<3)XposedBridge.log("TYH212U-HOOK: [gyro-fallback] force_sample="+p.getResult()+" -> false; use capability check");
    p.setResult(false);
   }
  });
 }
}
