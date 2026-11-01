package ds.dsh.DeepSeek;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Pure parser and launch policy for the independently verified code249/code257 catalogs. */
final class HostNavigationCatalog {
    static final String DIRECT = "direct";
    static final String HIGH_RISK = "confirm";
    static final String PARAMETERS = "parameters";
    static final String INTERNAL = "internal";
    static final String BLOCKED = "blocked";

    private HostNavigationCatalog() {}

    static List<String> extractRoutes(InputStream input) throws IOException {
        return HostNavigationPolicy.extractRoutes(input);
    }

    static String policy(String route) {
        return HostNavigationPolicy.policy(route);
    }

    static String title(String route) {
        return HostNavigationPolicy.title(route);
    }

    static String obfuscatedRouteClass(String route) {
        if (HostCompat.isV236()) return obfuscatedRouteClass249(route);
        if (!HostCompat.isV241()) return null;
        return obfuscatedRouteClass257(route);
    }

    static String obfuscatedRouteClass249(String route) {
        return HostNavigationPolicy.obfuscatedRouteClass249(route);
    }

    static String obfuscatedRouteClass257(String route) {
        return HostNavigationPolicy.obfuscatedRouteClass257(route);
    }
}
