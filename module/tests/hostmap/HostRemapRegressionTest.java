package com.dsmod.probe;

public final class HostRemapRegressionTest {
    public static void main(String[] args) {
        int code = Integer.parseInt(args[0]);
        check(code == 287 || code == 289 || code == 290, "unexpected host code");
        check(HostRemap.activate(code), "mapping table did not activate for " + code);
        check(HostRemap.active(), "mapping must be active for " + code);
        check(HostRemap.hostCode() == code, "selected host code changed");
        check(HostRemap.classCount() > 0, "mapping table contains no class mappings");
        check("java.lang.String".equals(HostRemap.cls("java.lang.String")),
                "non-obfuscated platform class must remain unchanged");
        check("java.lang.String".equals(HostRemap.className(String.class)),
                "non-obfuscated runtime class name must remain unchanged");
        if (code == 287) {
            check("gt0".equals(HostRemap.raw287("gt0")),
                    "287 raw class names must retain identity mapping");
        }
        System.out.println("HostRemap " + code + " mapping regression OK; classes="
                + HostRemap.classCount());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
