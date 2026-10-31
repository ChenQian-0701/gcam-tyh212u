package com.tyh212u.gcamhook;
import de.robv.android.xposed.*;
/** Retain the tested Java/native interception boundary. It only observes inputs
 * and native acceptance; it never manufactures a successful frame result.
 * This workaround's effect may include deoptimization or execution timing. */
final class PayloadBoundaryRepair {
 static void install(ClassLoader cl) {
  XposedHelpers.findAndHookMethod("ila",cl,"n",XposedHelpers.findClass("inb",cl),XposedHelpers.findClass("pnx",cl),int.class,XposedHelpers.findClass("pro",cl),XposedHelpers.findClass("sxd",cl),XposedHelpers.findClass("prw",cl),XposedHelpers.findClass("prw",cl),XposedHelpers.findClass("rss",cl),XposedHelpers.findClass("pro",cl),new XC_MethodHook(){
   protected void beforeHookedMethod(MethodHookParam p){
    try {Object image=p.args[5],meta=p.args[3];XposedBridge.log("TYH212U-HOOK: [payload-input] shot="+XposedHelpers.callMethod(p.args[0],"a")+" index="+p.args[2]+" image="+(image==null?null:image.getClass().getName())+" it="+(image==null?null:XposedHelpers.callMethod(image,"d"))+" mt="+(meta==null?null:XposedHelpers.callMethod(meta,"e")));}catch(Throwable t){XposedBridge.log("TYH212U-HOOK: [payload-input] "+t);}
   }
  });
  Class<?> own=XposedHelpers.findClass("com.google.googlex.gcam.base.OwningNativePointer",cl);
  XposedHelpers.findAndHookMethod("com.google.googlex.gcam.hdrplus.NativeHdrPlusInterface",cl,"nativeAddPayloadFrame",long.class,int.class,long.class,long.class,long.class,Runnable.class,long.class,Runnable.class,own,Runnable.class,new XC_MethodHook(){
   protected void afterHookedMethod(MethodHookParam p){XposedBridge.log("TYH212U-HOOK: [payload-native] shot="+p.args[1]+" raw="+p.args[2]+" meta="+p.args[3]+" lsc="+p.args[4]+" accepted="+p.getResult());}
  });
 }
}
