package com.tyh212u.gcamhook;
import de.robv.android.xposed.*;
import java.nio.*;
import java.util.concurrent.atomic.AtomicInteger;
final class RawProbe {
 static AtomicInteger N=new AtomicInteger();
 static String stats(ByteBuffer in,boolean words){
  if(in==null)return "null";ByteBuffer b=in.duplicate().order(ByteOrder.LITTLE_ENDIAN);int n=b.limit(),step=Math.max(words?2:1,n/4096);if(words)step=(step+1)&~1;
  int min=65535,max=0,c=0;long sum=0;for(int i=0;i<n-(words?1:0);i+=step){int x=words?(b.getShort(i)&65535):(b.get(i)&255);min=Math.min(min,x);max=Math.max(max,x);sum+=x;c++;}
  return "size="+n+" min="+min+" max="+max+" mean="+(c==0?0:sum/(double)c);
 }
 static String floats(float[] a){if(a==null)return "null";float lo=Float.POSITIVE_INFINITY,hi=Float.NEGATIVE_INFINITY;for(float v:a){lo=Math.min(lo,v);hi=Math.max(hi,v);}return "n="+a.length+" min="+lo+" max="+hi+" first="+java.util.Arrays.toString(java.util.Arrays.copyOf(a,Math.min(a.length,9)));}
 static String raw10(ByteBuffer in) {
  if(in==null)return "null";ByteBuffer b=in.duplicate();int n=b.limit(),step=Math.max(5,(n/4096/5)*5);long sum=0;int min=1023,max=0,c=0;
  for(int i=0;i+4<n;i+=step)for(int j=0;j<4;j++){int v=((b.get(i+j)&255)<<2)|((b.get(i+4)&255)>>(2*j)&3);min=Math.min(min,v);max=Math.max(max,v);sum+=v;c++;}
  return "size="+n+" min="+min+" max="+max+" mean="+(sum/(double)Math.max(c,1));
 }
 static void install(ClassLoader cl) {
  XposedHelpers.findAndHookMethod("com.android.app.Application",cl,"o",ByteBuffer.class,ByteBuffer.class,int.class,int.class,int.class,int.class,boolean.class,float[].class,int.class,float[].class,int.class,int.class,float[].class,int.class,float[].class,new XC_MethodHook(){
   protected void afterHookedMethod(MethodHookParam p){int n=N.incrementAndGet();if(n>12 && n%120!=0)return;
    StringBuilder s=new StringBuilder("TYH212U-HOOK: [raw-convert] ");for(int i=2;i<p.args.length;i++)s.append(i).append('=').append(p.args[i] instanceof float[]?floats((float[])p.args[i]):p.args[i]).append(' ');
    try{XposedBridge.log(s+" Y{"+stats((ByteBuffer)p.args[0],false)+"} raw{"+raw10((ByteBuffer)p.getResult())+"}");}catch(Throwable t){XposedBridge.log("TYH212U-HOOK: [raw-probe] "+t);}
   }
  });
 }
}
