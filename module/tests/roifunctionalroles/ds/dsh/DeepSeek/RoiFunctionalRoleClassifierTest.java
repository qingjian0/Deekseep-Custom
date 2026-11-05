package ds.dsh.DeepSeek;

import java.util.Arrays;
import java.util.Collections;

public final class RoiFunctionalRoleClassifierTest {
    public static void main(String[] args) {
        RoiFunctionalRoleClassifier.Result request =
                RoiFunctionalRoleClassifier.classify(Collections.singletonList(
                        "Lokhttp3/Request$Builder;->url(Ljava/lang/String;)Lokhttp3/Request$Builder;"));
        check(request.has(RoiFunctionalRoleClassifier.Role.REQUEST_CONSTRUCTION),
                "request construction");
        check(!request.has(RoiFunctionalRoleClassifier.Role.HTTP_TRANSPORT),
                "request API must not imply transport");
        equal(0.45, request.staticConfidence, "static confidence ceiling");

        RoiFunctionalRoleClassifier.Result transport =
                RoiFunctionalRoleClassifier.classify(Arrays.asList(
                        "Lokhttp3/OkHttpClient;->newCall(Lokhttp3/Request;)Lokhttp3/Call;",
                        "Lokhttp3/Call;->execute()Lokhttp3/Response;"));
        check(transport.has(RoiFunctionalRoleClassifier.Role.HTTP_TRANSPORT),
                "transport role");

        RoiFunctionalRoleClassifier.Result response =
                RoiFunctionalRoleClassifier.classify(Collections.singletonList(
                        "Lokhttp3/ResponseBody;->string()Ljava/lang/String;"));
        check(response.has(RoiFunctionalRoleClassifier.Role.RESPONSE_DECODING),
                "response decoding");

        RoiFunctionalRoleClassifier.Result unknown =
                RoiFunctionalRoleClassifier.classify(Collections.singletonList("La/b;->c()V"));
        check(unknown.has(RoiFunctionalRoleClassifier.Role.UNRESOLVED),
                "obfuscated names remain unresolved");
        equal(0.0, unknown.staticConfidence, "unknown confidence");

        RoiFunctionalRoleClassifier.Result nulls =
                RoiFunctionalRoleClassifier.classify(null);
        check(nulls.has(RoiFunctionalRoleClassifier.Role.UNRESOLVED),
                "null evidence remains unresolved");
        System.out.println("ROI functional role classifier tests passed");
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }

    private static void equal(double expected, double actual, String label) {
        if (Math.abs(expected - actual) > 0.000001) {
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
        }
    }
}
