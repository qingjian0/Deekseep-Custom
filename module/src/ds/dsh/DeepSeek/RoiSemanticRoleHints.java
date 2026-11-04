package ds.dsh.DeepSeek;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Conservative hints based only on directly referenced, recognizable networking APIs.
 * These hints are diagnostics, not semantic proof and never authorize Hook activation.
 */
final class RoiSemanticRoleHints {
    enum Hint { REQUEST_BUILDER, TRANSPORT, RESPONSE_PARSER }

    static final class Result {
        final List<Hint> hints;
        final List<String> matchedReferences;

        Result(List<Hint> hints, List<String> matchedReferences) {
            this.hints = Collections.unmodifiableList(new ArrayList<Hint>(hints));
            this.matchedReferences = Collections.unmodifiableList(
                    new ArrayList<String>(matchedReferences));
        }

        boolean has(Hint hint) { return hints.contains(hint); }
    }

    private RoiSemanticRoleHints() {}

    static Result classify(List<String> references) {
        boolean request = false;
        boolean transport = false;
        boolean response = false;
        List<String> matched = new ArrayList<String>();
        if (references != null) {
            for (String raw : references) {
                if (raw == null) continue;
                String value = raw.trim();
                if (value.length() == 0) continue;
                String lower = value.toLowerCase(java.util.Locale.ROOT);
                if (isRequestBuilder(lower)) {
                    request = true;
                    matched.add(value);
                }
                if (isTransport(lower)) {
                    transport = true;
                    matched.add(value);
                }
                if (isResponseParser(lower)) {
                    response = true;
                    matched.add(value);
                }
            }
        }
        List<Hint> hints = new ArrayList<Hint>();
        if (request) hints.add(Hint.REQUEST_BUILDER);
        if (transport) hints.add(Hint.TRANSPORT);
        if (response) hints.add(Hint.RESPONSE_PARSER);
        return new Result(hints, matched);
    }

    private static boolean isRequestBuilder(String value) {
        return value.contains("lokhttp3/request$builder;->url(")
                || value.contains("lokhttp3/request$builder;->header(")
                || value.contains("lokhttp3/request$builder;->post(")
                || value.contains("lokhttp3/request$builder;->method(")
                || value.contains("lokhttp3/requestbody;->create(")
                || value.contains("lretrofit2/requestfactory");
    }

    private static boolean isTransport(String value) {
        return value.contains("lokhttp3/okhttpclient;->newcall(")
                || value.contains("lokhttp3/call;->execute(")
                || value.contains("lokhttp3/call;->enqueue(")
                || value.contains("lretrofit2/call;->execute(")
                || value.contains("lretrofit2/call;->enqueue(")
                || value.contains("ljava/net/url;->openconnection(")
                || value.contains("ljava/net/httpurlconnection;->connect(")
                || value.contains("ljava/net/httpurlconnection;->getoutputstream(");
    }

    private static boolean isResponseParser(String value) {
        return value.contains("lokhttp3/response;->body(")
                || value.contains("lokhttp3/responsebody;->string(")
                || value.contains("lokhttp3/responsebody;->bytes(")
                || value.contains("lretrofit2/response;->body(")
                || value.contains("lcom/google/gson/gson;->fromjson(")
                || value.contains("lcom/squareup/moshi/jsonadapter;->fromjson(")
                || value.contains("lcom/fasterxml/jackson/databind/objectmapper;->readvalue(")
                || value.contains("lorg/json/jsonobject;-><init>(");
    }
}
