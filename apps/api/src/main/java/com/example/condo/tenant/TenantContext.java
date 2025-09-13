package com.example.condo.tenant;

public final class TenantContext {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
    private TenantContext() {}
    public static void set(String tenant) { CURRENT.set(tenant); }
    public static String get() { return CURRENT.get(); }
    public static void clear() { CURRENT.remove(); }
}