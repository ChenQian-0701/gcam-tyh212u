package de.robv.android.xposed;

import java.lang.reflect.Member;

/** Compile-time stub. Real implementation is provided by LSPosed at runtime. */
public final class XposedBridge {

    public static void log(String text) {
    }

    public static void log(Throwable t) {
    }

    public static java.util.Set<XC_MethodHook.Unhook> hookAllConstructors(Class<?> hookClass,
                                                                        XC_MethodHook callback) {
        return java.util.Collections.emptySet();
    }

    public static java.util.Set<XC_MethodHook.Unhook> hookAllMethods(Class<?> hookClass, String methodName,
                                                      XC_MethodHook callback) {
        return java.util.Collections.emptySet();
    }

    public static XC_MethodHook.Unhook hookMethod(Member hookMethod, XC_MethodHook callback) {
        return null;
    }

    private XposedBridge() {
    }
}
