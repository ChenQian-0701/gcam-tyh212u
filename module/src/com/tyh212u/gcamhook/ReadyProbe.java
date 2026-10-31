package com.tyh212u.gcamhook;
import de.robv.android.xposed.*;
import java.lang.reflect.*;
final class ReadyProbe {
 static volatile Object IMPL;
 static void chain(Object o, int depth) {
  if(o==null || depth>18)return;
  try { Object v=XposedHelpers.callMethod(o,"a"); XposedBridge.log("TYH212U-HOOK: [ready-chain] "+depth+" "+o.getClass().getName()+" ready="+XposedHelpers.callMethod(v,"cM")+" obs="+v);
   for(Field f:o.getClass().getDeclaredFields()) {
    if(Modifier.isStatic(f.getModifiers()))continue;
    f.setAccessible(true); Object child=f.get(o); if(child==null)continue;
    Class<?> kuc=XposedHelpers.findClass("kuc",o.getClass().getClassLoader());
    if(kuc.isInstance(child)) chain(child,depth+1);
    else try { Object current=XposedHelpers.callMethod(child,"cM"); if(kuc.isInstance(current)) { XposedBridge.log("TYH212U-HOOK: [ready-selected] "+f.getName()+" "+current.getClass().getName()); chain(current,depth+1); } }catch(Throwable ignored){}
   }
  }catch(Throwable t){XposedBridge.log("TYH212U-HOOK: [ready-chain] "+t);}
 }
 static final java.util.concurrent.atomic.AtomicInteger TREES=new java.util.concurrent.atomic.AtomicInteger();
 static void snapshot() {
  if(TREES.incrementAndGet()>6)return;
  Object root=IMPL;
  findGate(root,java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object,Boolean>()));
 }
 static void findGate(Object o,java.util.Set<Object> seen) {
  if(o==null || seen.size()>60 || !seen.add(o))return;
  try {
   if(o.getClass().getName().equals("leg") || o.getClass().getName().equals("ldy")) {
    Object v=XposedHelpers.callMethod(o,"a");
    XposedBridge.log("TYH212U-HOOK: [gate-root] "+o.getClass().getName()+" value="+XposedHelpers.callMethod(v,"cM"));
    GateTrace.dump(v);
   }
   Class<?> kuc=XposedHelpers.findClass("kuc",o.getClass().getClassLoader());
   for(Field f:o.getClass().getDeclaredFields()) {if(Modifier.isStatic(f.getModifiers()))continue;f.setAccessible(true);Object child=f.get(o);if(child==null)continue;
    if(kuc.isInstance(child))findGate(child,seen);
    else try {Object current=XposedHelpers.callMethod(child,"cM");if(kuc.isInstance(current))findGate(current,seen);}catch(Throwable ignored){}
   }
  }catch(Throwable t){XposedBridge.log("TYH212U-HOOK: [gate-root] "+t);}
 }
 static final ThreadLocal<Object> RUN = new ThreadLocal<>();
 static void install(ClassLoader cl) {
  XposedHelpers.findAndHookMethod("jjx",cl,"H",boolean.class,new XC_MethodHook(){
   protected void afterHookedMethod(MethodHookParam p){for(int ms:new int[]{3000,10000,20000})new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->snapshot(),ms);}
  });
  XposedHelpers.findAndHookMethod("kud",cl,"a",new XC_MethodHook(){
   String last="";
   protected void afterHookedMethod(MethodHookParam p){
    try {
     Object kg=XposedHelpers.getObjectField(p.thisObject,"a");
     Object kol=XposedHelpers.getObjectField(p.thisObject,"b");
     java.util.concurrent.Future<?> future=(java.util.concurrent.Future<?>)XposedHelpers.getObjectField(kg,"b");
     Object impl=future.isDone()?future.get():null;
     IMPL=impl;
     Object ready=impl==null?null:XposedHelpers.callMethod(impl,"a");
     String s="pending="+XposedHelpers.getObjectField(kg,"g")+" busy="+XposedHelpers.callMethod(kol,"a")+" impl="+(impl==null?"null":impl.getClass().getName())+" ready="+(ready==null?null:XposedHelpers.callMethod(ready,"cM"));
     if(!s.equals(last)){last=s;XposedBridge.log("TYH212U-HOOK: [capture-gate] "+s);}
    }catch(Throwable t){XposedBridge.log("TYH212U-HOOK: [capture-gate] error "+t);}
   }
  });
  XposedHelpers.findAndHookMethod("jhr",cl,"run",new XC_MethodHook(){
   protected void beforeHookedMethod(MethodHookParam p){ RUN.set(p.thisObject); }
   protected void afterHookedMethod(MethodHookParam p){ RUN.remove(); }
  });
  XposedHelpers.findAndHookMethod("jjx",cl,"D",boolean.class,new XC_MethodHook(){
   protected void beforeHookedMethod(MethodHookParam p){
    chain(IMPL,0);
    if(Boolean.FALSE.equals(p.args[0]))new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->chain(IMPL,0),12000);
    Object r=RUN.get();
    if(r!=null) XposedBridge.log("TYH212U-HOOK: [ready-source] jhr="+XposedHelpers.getObjectField(r,"b")+" ready="+p.args[0]);
    try {
     Object gate=XposedHelpers.getObjectField(p.thisObject,"L");
     for(String n:new String[]{"c","d","a"}) {Object v=XposedHelpers.getObjectField(gate,n);XposedBridge.log("TYH212U-HOOK: [ready-gate] "+n+"="+v+" value="+(v==null?null:XposedHelpers.callMethod(v,"cM")));}
    }catch(Throwable t){XposedBridge.log("TYH212U-HOOK: [ready-gate] error "+t);}
    StringBuilder s=new StringBuilder("TYH212U-HOOK: [ready-inputs] ");
    for(Field f:p.thisObject.getClass().getDeclaredFields()) {
     if(Modifier.isStatic(f.getModifiers()))continue;
     try { f.setAccessible(true); Object v=f.get(p.thisObject);String t=f.getType().getName();
      if(v==null)continue;
      if(t.equals("rss"))v=XposedHelpers.callMethod(v,"c");
      else if(t.equals("oyd")||t.equals("oyn")||t.equals("oyj"))v=XposedHelpers.callMethod(v,"cM");
      else if(!t.equals("boolean"))continue;
      s.append(f.getName()).append('=').append(v).append(' ');
     }catch(Throwable ignored){}
    }
    XposedBridge.log(s.toString());
   }
  });
 }
}
