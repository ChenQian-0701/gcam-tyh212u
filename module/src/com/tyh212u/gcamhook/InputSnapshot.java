package com.tyh212u.gcamhook;
import de.robv.android.xposed.*;
import java.nio.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import android.graphics.*;
/** Read-only diagnostic: save the first YUV input after the shutter. */
final class InputSnapshot {
 static final AtomicBoolean REQUEST=new AtomicBoolean();
 static void install(ClassLoader cl){
  XposedHelpers.findAndHookMethod("jjx",cl,"H",boolean.class,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){REQUEST.set(true);}});
  XposedHelpers.findAndHookMethod("com.android.app.Application",cl,"o",ByteBuffer.class,ByteBuffer.class,int.class,int.class,int.class,int.class,boolean.class,float[].class,int.class,float[].class,int.class,int.class,float[].class,int.class,float[].class,new XC_MethodHook(){
   protected void beforeHookedMethod(MethodHookParam p){
    if(!REQUEST.compareAndSet(true,false))return;
    try {
     int w=(Integer)p.args[2],h=(Integer)p.args[3],ys=(Integer)p.args[4],uvs=(Integer)p.args[5];boolean vu=(Boolean)p.args[6];
     ByteBuffer y=((ByteBuffer)p.args[0]).duplicate(),uv=((ByteBuffer)p.args[1]).duplicate();byte[] nv=new byte[w*h*3/2];
     for(int row=0;row<h;row++)for(int col=0;col<w;col++)nv[row*w+col]=y.get(row*ys+col);
     for(int row=0;row<h/2;row++)for(int col=0;col<w;col++) {int index=row*uvs+(vu?col:(col^1));nv[w*h+row*w+col]=index<uv.limit()?uv.get(index):(byte)128;}
     String path="/sdcard/Android/data/com.android.MGC_9_6_080/files/input-"+System.currentTimeMillis()+".jpg";
     new File(path).getParentFile().mkdirs();try(FileOutputStream f=new FileOutputStream(path)){new YuvImage(nv,ImageFormat.NV21,w,h,null).compressToJpeg(new Rect(0,0,w,h),95,f);}
     XposedBridge.log("TYH212U-HOOK: [input-snapshot] "+path+" NV21="+vu+" dims="+w+"x"+h);
    }catch(Throwable t){XposedBridge.log("TYH212U-HOOK: [input-snapshot] "+t);}
   }
  });
 }
}
