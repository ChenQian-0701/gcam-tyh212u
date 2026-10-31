package com.tyh212u.gcamhook;

import de.robv.android.xposed.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Equivalent pmg.a frame dispatch, without its crashing stale-frame log block. */
final class DistributorRepair {
    private static final Map<String, Field> FIELDS = new ConcurrentHashMap<>();
    private static final Map<String, Constructor<?>> CTORS = new ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicBoolean FIRST = new java.util.concurrent.atomic.AtomicBoolean();
    private static final java.util.concurrent.atomic.AtomicBoolean FAILED = new java.util.concurrent.atomic.AtomicBoolean();
    static void install(final ClassLoader cl) {
        XposedHelpers.findAndHookMethod("pmg", cl, "a", String.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                // Replacement owns this invocation even if reflection fails; never run
                // the original on state that the replacement has already modified.
                p.setResult(null);
                try {
                    dispatch(p.thisObject, (String)p.args[0], cl);
                    if (FIRST.compareAndSet(false, true)) XposedBridge.log("TYH212U-HOOK: [distributor] first dispatch completed");
                } catch (Throwable t) {
                    // Never rethrow into GCam's dispatch loop: an exception escaping
                    // pmg.a aborts the frame pipeline and leaves the UI loading.
                    if (FAILED.compareAndSet(false, true))
                        XposedBridge.log("TYH212U-HOOK: [distributor] failure: " + android.util.Log.getStackTraceString(t));
                }
            }
        });
        XposedBridge.log("TYH212U-HOOK: [distributor] pmg.a replacement installed");
    }
    private static Field field(Object o, String name) throws Exception {
        String key=o.getClass().getName()+"#"+name;
        Field f=FIELDS.get(key);
        if(f==null) { f=o.getClass().getDeclaredField(name); f.setAccessible(true); FIELDS.put(key,f); }
        return f;
    }
    private static Object get(Object o,String n) throws Exception { return field(o,n).get(o); }
    private static void set(Object o,String n,Object v) throws Exception { field(o,n).set(o,v); }
    private static long num(Object o,String n) throws Exception { return ((Number)get(o,n)).longValue(); }
    private static Object call(Object o,String n,Object... a) { return XposedHelpers.callMethod(o,n,a); }
    private static Object construct(ClassLoader cl,String n,String[] types,Object... args) throws Exception {
        Constructor<?> c=CTORS.get(n);
        if(c==null) {
            Class<?>[] ts=new Class<?>[types.length];
            for(int i=0;i<ts.length;i++) ts[i]=Class.forName(types[i],false,cl);
            c=Class.forName(n,false,cl).getDeclaredConstructor(ts); c.setAccessible(true); CTORS.put(n,c);
        }
        return c.newInstance(args);
    }
    private static Object meta(Object r) { return call(r,"c"); }
    @SuppressWarnings("unchecked")
    private static void dispatch(Object self,String reason,ClassLoader cl) throws Exception {
        Object trace=get(self,"o");
        call(trace,"f",reason);
        try {
            while(true) {
                Object image, selected, wrapped;
                List<Object> ready;
                synchronized(self) {
                    if(Boolean.TRUE.equals(get(self,"i")) || !Boolean.TRUE.equals(get(self,"t"))) return;
                    image=get(self,"v");
                    if(image==null) {
                        call(trace,"f","acquire");
                        try {
                            image=call(get(self,"a"),"g");
                            set(self,"v",image);
                            if(image!=null) ((AtomicLong)get(self,"w")).set(((Number)call(image,"d")).longValue());
                        } finally { call(trace,"g"); }
                    }
                    if(image==null) return;
                    long stamp=((Number)call(image,"d")).longValue();
                    Deque<Object> queue=(Deque<Object>)get(self,"g");
                    Object policy=get(self,"p");
                    ready=(List<Object>)get(self,"u");
                    if(ready==null) {
                        Object latest=null;
                        for(Object r:queue) {
                            Object m=meta(r);
                            if(m==null) throw new NullPointerException("frame metadata");
                            if(latest==null || num(m,"d")>num(meta(latest),"d")) latest=r;
                        }
                        if(latest!=null && stamp-num(meta(latest),"b")-num(policy,"b")-num(policy,"a")>0) return;
                        Object match=null;
                        for(Object r:queue) {
                            Object m=meta(r);
                            if(m!=null && Boolean.TRUE.equals(call(policy,"a",num(m,"b"),stamp))) {match=m;break;}
                        }
                        Iterator<Object> it=queue.iterator();
                        while(it.hasNext()) {
                            Object r=it.next(), m=meta(r);
                            if(m==null) continue;
                            boolean take=match==null
                                ? !Boolean.TRUE.equals(call(policy,"b",num(m,"b"),stamp))
                                : num(m,"d")<=num(match,"d") || num(m,"b")==num(match,"b");
                            if(take) {
                                if(ready==null) ready=new ArrayList<>(2);
                                ready.add(r); it.remove();
                            }
                        }
                        set(self,"u",ready);
                    }
                    if(ready==null || ready.isEmpty()) {
                        Object earliest=null;
                        for(Object r:queue) if(earliest==null || num(meta(r),"d")<num(meta(earliest),"d")) earliest=r;
                        if(earliest!=null && Boolean.TRUE.equals(call(policy,"b",num(meta(earliest),"b"),stamp))) {
                            set(self,"v",null); call(image,"close"); call(self,"b");
                        }
                        return;
                    }
                    selected=Class.forName("pgi",false,cl).getField("a").get(null);
                    for(Object r:ready) {
                        Object m=meta(r);
                        if(Boolean.TRUE.equals(call(policy,"a",num(m,"b"),stamp))) selected=m;
                    }
                    wrapped=construct(cl,"pme",new String[]{"pmg","prw","pgi"},self,image,selected);
                    set(self,"t",false); set(self,"v",null); set(self,"u",null);
                }
                // Everything below runs with t=false. If any step throws, t must
                // still be restored: a stuck false makes the next a() call return
                // at once, which kills frame dispatch permanently -- GCam then
                // never reports the viewfinder ready, the loading overlay stays up
                // and the shutter is disabled forever.
                try {
                    call(trace,"f",get(self,"r"));
                    Object owner=construct(cl,"pol",new String[]{"prw"},wrapped);
                    try {
                        for(Object r:ready) {
                            Object m=meta(r), frame=null;
                            if(num(m,"b")==num(selected,"b") && num(m,"c")==num(selected,"c"))
                                frame=construct(cl,"phk",new String[]{"pnx","prw"},get(self,"s"),call(owner,"k"));
                            // Original's stale-result log crashes in StringBuilder.append;
                            // delivering null is the original resource-release protocol.
                            call(r,"i",frame);
                        }
                    } finally {
                        try { call(owner,"l"); } catch(Throwable ignored) { }
                        try { call(trace,"g"); } catch(Throwable ignored) { }
                    }
                } catch(Throwable failure) {
                    if(FAILED.compareAndSet(false,true))
                        XposedBridge.log("TYH212U-HOOK: [distributor] dispatch failed, t restored: "
                                + android.util.Log.getStackTraceString(failure));
                } finally {
                    synchronized(self) { try { set(self,"t",true); } catch(Throwable ignored) { } }
                }
            }
        } finally {call(trace,"g");}
    }
}
