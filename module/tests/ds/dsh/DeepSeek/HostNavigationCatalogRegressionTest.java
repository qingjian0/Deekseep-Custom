package ds.dsh.DeepSeek;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class HostNavigationCatalogRegressionTest {
    public static void main(String[] args) throws Exception {
        extractsUniqueSortedRoutes();
        handlesNullInput();
        classifiesRoutesFailClosed();
        preservesDisplayTitles();
        rejectsOversizedDexStreams();
        System.out.println("Host navigation catalog regression tests passed.");
    }

    private static void extractsUniqueSortedRoutes() throws Exception {
        String chat = "com.deepseek.chat.ui.pages.chat.ChatRoute";
        String settings = "com.deepseek.chat.ui.pages.settings.SettingsRoute";
        String graph = "com.deepseek.chat.ui.pages.auth.AuthNestedGraph";
        String source = "prefix " + settings + " xx " + chat + " yy " + settings
                + " zz " + graph + " invalid com.deepseek.chat.ui.pages.bad.Other";
        List<String> routes = HostNavigationCatalog.extractRoutes(
                new ByteArrayInputStream(source.getBytes(StandardCharsets.ISO_8859_1)));
        check(routes.equals(Arrays.asList(graph, chat, settings)),
                "routes must be unique and sorted");
    }

    private static void handlesNullInput() throws Exception {
        check(HostNavigationCatalog.extractRoutes(null).equals(Collections.emptyList()),
                "null DEX input must return an empty list");
    }

    private static void classifiesRoutesFailClosed() {
        check("blocked".equals(HostNavigationCatalog.policy(null)), "null route blocked");
        check("confirm".equals(HostNavigationCatalog.policy("x.AccountDeletionRoute")),
                "account deletion requires confirmation");
        check("internal".equals(HostNavigationCatalog.policy("x.AuthNestedGraph")),
                "auth graph remains internal");
        check("internal".equals(HostNavigationCatalog.policy("x.WelcomeRoute")),
                "welcome route remains internal");
        check("parameters".equals(HostNavigationCatalog.policy("x.AgeScreenRoute")),
                "age screen requires parameters");
        check("parameters".equals(HostNavigationCatalog.policy("x.WebViewRoute")),
                "web view requires parameters");
        check("direct".equals(HostNavigationCatalog.policy("x.ChatRoute")),
                "chat route is directly launchable");
        check("direct".equals(HostNavigationCatalog.policy("x.SettingsRoute")),
                "settings route is directly launchable");
        check("internal".equals(HostNavigationCatalog.policy("x.UnknownRoute")),
                "unknown route fails closed to internal");
    }

    private static void preservesDisplayTitles() {
        check("未知页面".equals(HostNavigationCatalog.title(null)), "null title");
        check("聊天主页".equals(HostNavigationCatalog.title("x.ChatRoute")), "chat title");
        check("删除账号".equals(HostNavigationCatalog.title("x.AccountDeletionRoute")),
                "account deletion title");
        check("生日/未成年人模式".equals(
                HostNavigationCatalog.title("x.MinorModeBirthdayModifyRoute")),
                "minor-mode title");
        check("CustomRoute".equals(HostNavigationCatalog.title("x.CustomRoute")),
                "unknown route simple-name fallback");
    }

    private static void rejectsOversizedDexStreams() throws Exception {
        boolean rejected = false;
        try {
            HostNavigationCatalog.extractRoutes(new InputStreamOfSize(48 * 1024 * 1024 + 1));
        } catch (IOException expected) {
            rejected = "DEX exceeds navigation scan limit".equals(expected.getMessage());
        }
        check(rejected, "oversized DEX must be rejected with the existing message");
    }

    private static final class InputStreamOfSize extends java.io.InputStream {
        private long remaining;
        InputStreamOfSize(long size) { remaining = size; }
        @Override public int read() {
            if (remaining <= 0) return -1;
            remaining--;
            return 0;
        }
        @Override public int read(byte[] buffer, int offset, int length) {
            if (remaining <= 0) return -1;
            int count = (int) Math.min(remaining, length);
            Arrays.fill(buffer, offset, offset + count, (byte) 0);
            remaining -= count;
            return count;
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
