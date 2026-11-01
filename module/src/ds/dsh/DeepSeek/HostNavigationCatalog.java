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
        if (route == null) return null;
        if (route.endsWith("ChatRoute")) return "oa1";
        if (route.endsWith("SettingsNestedGraph")) return "kn7";
        if (route.endsWith("SettingsNestedGraph.SettingsRoute")) return "hn7";
        if (route.endsWith("AccountManagementRoute")) return "en7";
        if (route.endsWith("AccountDeletionRoute")) return "dn7";
        if (route.endsWith("DataControlsSettingsRoute")) return "fn7";
        if (route.endsWith("FontSizeRoute")) return "gn7";
        if (route.endsWith("ShareLinksManagementRoute")) return "in7";
        if (route.endsWith("TermsOfServiceRoute")) return "jn7";
        return null;
    }

    static String obfuscatedRouteClass257(String route) {
        if (route == null) return null;
        if (route.endsWith("ChatRoute")) return "id1";
        if (route.endsWith("SettingsNestedGraph")) return "kv7";
        if (route.endsWith("SettingsNestedGraph.SettingsRoute")) return "hv7";
        if (route.endsWith("AccountManagementRoute")) return "dv7";
        if (route.endsWith("AccountDeletionRoute")) return "cv7";
        if (route.endsWith("DataControlsSettingsRoute")) return "ev7";
        if (route.endsWith("FontSizeRoute")) return "fv7";
        if (route.endsWith("MinorModeBirthdayModifyRoute")) return "gv7";
        if (route.endsWith("ShareLinksManagementRoute")) return "iv7";
        if (route.endsWith("TermsOfServiceRoute")) return "jv7";
        return null;
    }
}
