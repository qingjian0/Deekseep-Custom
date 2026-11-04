package ds.dsh.DeepSeek;

import java.util.Arrays;
import java.util.Collections;

public final class RoiSemanticRoleHintsTest {
    public static void main(String[] args) {
        RoiSemanticRoleHints.Result request = RoiSemanticRoleHints.classify(Arrays.asList(
                "Lokhttp3/Request$Builder;->url(Ljava/lang/String;)Lokhttp3/Request$Builder;",
                "Lokhttp3/Request$Builder;->post(Lokhttp3/RequestBody;)Lokhttp3/Request$Builder;"
        ));
        check(request.has(RoiSemanticRoleHints.Hint.REQUEST_BUILDER), "request builder hint");
        check(!request.has(RoiSemanticRoleHints.Hint.TRANSPORT), "no transport overreach");

        RoiSemanticRoleHints.Result transport = RoiSemanticRoleHints.classify(Arrays.asList(
                "Lokhttp3/OkHttpClient;->newCall(Lokhttp3/Request;)Lokhttp3/Call;",
                "Lokhttp3/Call;->execute()Lokhttp3/Response;"
        ));
        check(transport.has(RoiSemanticRoleHints.Hint.TRANSPORT), "transport hint");

        RoiSemanticRoleHints.Result response = RoiSemanticRoleHints.classify(Arrays.asList(
                "Lokhttp3/ResponseBody;->string()Ljava/lang/String;",
                "Lcom/google/gson/Gson;->fromJson(Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;"
        ));
        check(response.has(RoiSemanticRoleHints.Hint.RESPONSE_PARSER), "response parser hint");

        RoiSemanticRoleHints.Result unknown = RoiSemanticRoleHints.classify(
                Collections.singletonList("La/b;->c()V"));
        check(unknown.hints.isEmpty(), "unknown reference stays unresolved");
        check(unknown.matchedReferences.isEmpty(), "unknown references are not reported as matches");
        System.out.println("ROI semantic role hint tests passed");
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
