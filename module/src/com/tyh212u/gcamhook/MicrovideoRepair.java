package com.tyh212u.gcamhook;
import de.robv.android.xposed.*;
import java.lang.reflect.Field;
/** Turn off Microvideo through its original feature flags; ordinary video and
 * still HDR merging keep their own paths. Avoid a background frame-ring owner. */
final class MicrovideoRepair {
 static void install(ClassLoader cl)throws Exception {
  Class<?> flags=XposedHelpers.findClass("hmz",cl);
  Field enable=flags.getField("j"),supported=flags.getField("k");
  Class<?> key=XposedHelpers.findClass("hmn",cl);
  Class<?> activeRing=XposedHelpers.findClass("juj",cl);
  java.lang.reflect.Constructor<?> emptyRing=XposedHelpers.findClass("jvo",cl).getDeclaredConstructor();emptyRing.setAccessible(true);
  XposedHelpers.findAndHookMethod("jjn",cl,"a",new XC_MethodHook(){
   protected void afterHookedMethod(MethodHookParam p)throws Throwable{
    if(!p.hasThrowable() && activeRing.isInstance(p.getResult())){
     p.setResult(emptyRing.newInstance());XposedBridge.log("TYH212U-HOOK: [microvideo] using original empty frame-ring provider");
    }
   }
  });

  XposedHelpers.findAndHookMethod("hoh",cl,"p",key,new XC_MethodHook(){
   int n;
   protected void afterHookedMethod(MethodHookParam p)throws Throwable{
    if(p.hasThrowable() || (p.args[0]!=enable.get(null) && p.args[0]!=supported.get(null)))return;
    if(n++<5)XposedBridge.log("TYH212U-HOOK: [microvideo] read="+p.getResult()+" -> false");p.setResult(false);
   }
  });
  XposedHelpers.findAndHookMethod("hoh",cl,"l",key,boolean.class,new XC_MethodHook(){
   int n;
   protected void beforeHookedMethod(MethodHookParam p)throws Throwable{
    if(p.args[0]!=enable.get(null) && p.args[0]!=supported.get(null))return;
    if(n++<5)XposedBridge.log("TYH212U-HOOK: [microvideo] configure="+p.args[1]+" -> false");p.args[1]=false;
   }
  });
 }
}
