package com.tyh212u.gcamhook;
import de.robv.android.xposed.*;
import android.hardware.HardwareBuffer;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
/** Snapshot BSG's software gyro ring once per scan, then read records in Java.
 * Keeps real bytes intact and avoids a native buffer lock/copy for every record.
 */
final class GyroSnapshotRepair {
 static final class Scan {int depth=1;Object reader;byte[] bytes;}
 static final ThreadLocal<Scan> SCAN=new ThreadLocal<>();
 static final AtomicInteger LOGS=new AtomicInteger();
 static void install(ClassLoader cl)throws Exception {
  Class<?> app=XposedHelpers.findClass("com.android.app.Application",cl);
  Class<?> jni=XposedHelpers.findClass("com.google.android.libraries.camera.gyro.hardwarebuffer.ReadHardwareBufferJniFunctions",cl);
  Method fill=app.getMethod("buffer_filling",HardwareBuffer.class);
  Method read=jni.getMethod("readHardwareBuffer",HardwareBuffer.class,byte[].class,int.class,int.class,int.class);
  XposedHelpers.findAndHookMethod("ppa",cl,"mda190e616797844b591057d0190e7728",new XC_MethodHook(){
   protected void beforeHookedMethod(MethodHookParam p){
    Scan active=SCAN.get();if(active!=null){active.depth++;return;}
    Scan s=new Scan();SCAN.set(s);
    try{
     Object reader=XposedHelpers.getObjectField(p.thisObject,"i");
     if(!Boolean.TRUE.equals(XposedHelpers.getObjectField(reader,"b")))return;
     Object hardware=XposedHelpers.getObjectField(reader,"a");if(!(hardware instanceof HardwareBuffer))return;
     byte[] original=(byte[])XposedHelpers.getObjectField(p.thisObject,"b");
     HardwareBuffer h=(HardwareBuffer)hardware;
     if(h.getFormat()!=HardwareBuffer.BLOB || h.getHeight()!=1 || original.length!=h.getWidth())return;
     byte[] snapshot=new byte[original.length];fill.invoke(null,h);
     if(!Boolean.TRUE.equals(read.invoke(null,h,snapshot,0,0,snapshot.length)))return;
     s.reader=reader;s.bytes=snapshot;
     if(LOGS.incrementAndGet()<=5)XposedBridge.log("TYH212U-HOOK: [gyro-snapshot] copied ring bytes="+snapshot.length);
    }catch(Throwable t){if(LOGS.incrementAndGet()<=5)XposedBridge.log("TYH212U-HOOK: [gyro-snapshot] fallback "+t);}
   }
   protected void afterHookedMethod(MethodHookParam p){Scan s=SCAN.get();if(s!=null && --s.depth==0)SCAN.remove();}
  });
  XposedHelpers.findAndHookMethod("pic",cl,"o",byte[].class,int.class,int.class,int.class,new XC_MethodHook(){
   protected void beforeHookedMethod(MethodHookParam p){
    Scan s=SCAN.get();if(s==null || s.reader!=p.thisObject || s.bytes==null)return;
    byte[] output=(byte[])p.args[0];int from=((Number)p.args[1]).intValue(),to=((Number)p.args[2]).intValue(),length=((Number)p.args[3]).intValue();
    if(from<0 || to<0 || length<0 || from>s.bytes.length-length || to>output.length-length)return;
    System.arraycopy(s.bytes,from,output,to,length);p.setResult(null);
   }
  });
 }
}
