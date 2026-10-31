package com.tyh212u.gcamhook;
import de.robv.android.xposed.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
/** Optional read-only producer traces for the integer capture budget. */
final class GateOriginProbe {
 static final Set<Object> TARGETS=Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>()));
 static final AtomicInteger LOGS=new AtomicInteger();
 static void collect(Object o,int depth,Set<Object> seen,Class<?> obs){
  if(o==null || depth>35 || seen.size()>250 || !seen.add(o))return;
  if(o instanceof Iterable){for(Object x:(Iterable<?>)o)collect(x,depth+1,seen,obs);return;}
  if(!obs.isInstance(o))return;
  if(o.getClass().getName().equals("oxv")){
   Object v=XposedHelpers.callMethod(o,"cM");if(v instanceof Number) {TARGETS.add(o);XposedBridge.log("TYH212U-HOOK: [gate-origin-target] "+Integer.toHexString(System.identityHashCode(o))+"="+v);}
  }
  for(Class<?> c=o.getClass();c!=null && c!=Object.class;c=c.getSuperclass())for(Field f:c.getDeclaredFields()){
   if(Modifier.isStatic(f.getModifiers()))continue;
   try{f.setAccessible(true);Object v=f.get(o);if(obs.isInstance(v) || (o.getClass().getName().equals("oyc") && v instanceof Iterable))collect(v,depth+1,seen,obs);}catch(Throwable ignored){}
  }
 }
 static void install(ClassLoader cl)throws Exception {
  Class<?> obs=XposedHelpers.findClass("oyd",cl),gir=XposedHelpers.findClass("gir",cl);
  XC_MethodHook ctor=new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){
   try{XposedBridge.log("TYH212U-HOOK: [gate-origin-root] "+Arrays.toString(p.args));collect(p.args[0],0,Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>()),obs);}catch(Throwable t){XposedBridge.log(t);}
  }};
  XposedHelpers.class.getMethod("findAndHookConstructor",Class.class,Object[].class).invoke(null,new Object[]{gir,new Object[]{obs,obs,obs,ctor}});
  XposedHelpers.findAndHookMethod("oxv",cl,"d",Object.class,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){
   if(!TARGETS.contains(p.thisObject))return;
   Object old=XposedHelpers.getObjectField(p.thisObject,"d"),next=p.args[0];
   if(!(old instanceof Number) || !(next instanceof Number) || ((Number)next).longValue()!=0 || ((Number)old).longValue()<=0 || LOGS.incrementAndGet()>12)return;
   StringBuilder b=new StringBuilder("TYH212U-HOOK: [gate-origin-update] ").append(Integer.toHexString(System.identityHashCode(p.thisObject))).append(' ').append(old).append(" -> ").append(next);
   for(StackTraceElement e:Thread.currentThread().getStackTrace()){String n=e.getClassName();if(n.startsWith("android.")||n.startsWith("java.")||n.contains("gcamhook")||n.contains("xposed"))continue;b.append("\n  ").append(e);if(b.length()>2200)break;}
   XposedBridge.log(b.toString());
  }});
 }
}
