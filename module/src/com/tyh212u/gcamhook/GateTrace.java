package com.tyh212u.gcamhook;
import java.lang.reflect.*;
import java.util.*;
import de.robv.android.xposed.*;
final class GateTrace {
 static void walk(Object o,int depth,Set<Object> seen,Class<?> obs) {
  if(o==null || depth>45 || seen.size()>350 || !seen.add(o))return;
  if(o instanceof Iterable){for(Object v:(Iterable<?>)o)walk(v,depth+1,seen,obs);return;}
  if(o instanceof Object[]){for(Object v:(Object[])o)walk(v,depth+1,seen,obs);return;}
  if(!obs.isInstance(o))return;
  String value="?";
  try {Object v=XposedHelpers.callMethod(o,"cM"); value=String.valueOf(v);if(value.length()>250)value=value.substring(0,250);}catch(Throwable ignored){}
  XposedBridge.log("TYH212U-HOOK: [gate-value] "+depth+" "+o.getClass().getName()+"@"+Integer.toHexString(System.identityHashCode(o))+"="+value);
  for(Class<?> c=o.getClass();c!=null && c!=Object.class;c=c.getSuperclass())for(Field f:c.getDeclaredFields()) {
   if(Modifier.isStatic(f.getModifiers()))continue;
   try {f.setAccessible(true);Object v=f.get(o);if(obs.isInstance(v) || (o.getClass().getName().equals("oyc") && v instanceof Iterable))walk(v,depth+1,seen,obs);}catch(Throwable ignored){}
  }
 }
 static void dump(Object o) {
  try {walk(o,0,Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>()),XposedHelpers.findClass("oyd",o.getClass().getClassLoader()));}catch(Throwable ignored){}
 }
}
