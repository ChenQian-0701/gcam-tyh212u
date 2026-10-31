package com.tyh212u.gcamhook;
import de.robv.android.xposed.*;
import java.util.concurrent.atomic.AtomicInteger;
/** Observe native viewfinder lease release, invoking every original callback. */
final class ViewfinderLeaseProbe {
 static final AtomicInteger SENT=new AtomicInteger(),DONE=new AtomicInteger();
 static void install(ClassLoader cl){
  XposedHelpers.findAndHookMethod("com.google.googlex.gcam.hdrplus.NativeHdrPlusInterface",cl,"nativeAddViewfinderFrame",long.class,int.class,long.class,long.class,long.class,long.class,Runnable.class,new XC_MethodHook(){
   protected void beforeHookedMethod(MethodHookParam p){
    int n=SENT.incrementAndGet();Runnable callback=(Runnable)p.args[6];long start=android.os.SystemClock.elapsedRealtime();
    if(n<=5 || n%10==0)XposedBridge.log("TYH212U-HOOK: [vf-lease-add] n="+n+" outstanding="+(n-DONE.get())+" stream="+p.args[1]);
    if(callback!=null)p.args[6]=(Runnable)()->{try{callback.run();}finally{int d=DONE.incrementAndGet();if(n<=5 || n%10==0)XposedBridge.log("TYH212U-HOOK: [vf-lease-release] n="+n+" age_ms="+(android.os.SystemClock.elapsedRealtime()-start)+" outstanding="+(SENT.get()-d));}};
   }
  });
 }
}
