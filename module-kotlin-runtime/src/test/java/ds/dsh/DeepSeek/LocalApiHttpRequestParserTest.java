package ds.dsh.DeepSeek;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class LocalApiHttpRequestParserTest {
    private static ByteArrayInputStream input(String wire) {
        return new ByteArrayInputStream(wire.getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test
    void parsesContentLengthAndStripsQueryFromPath() throws Exception {
        LocalApiHttpRequestParser.Request request = LocalApiHttpRequestParser.read(input(
                "POST /v1/chat/completions?trace=1 HTTP/1.1\r\nContent-Length: 7\r\nX-Test: yes\r\n\r\npayload"));
        assertEquals("POST", request.method);
        assertEquals("/v1/chat/completions", request.path);
        assertEquals("yes", request.headers.get("x-test"));
        assertEquals("payload", new String(request.body, StandardCharsets.UTF_8));
        assertThrows(UnsupportedOperationException.class, () -> request.headers.put("x", "y"));
    }

    @Test
    void returnsNullOnlyForAnEmptyConnection() throws Exception {
        assertNull(LocalApiHttpRequestParser.read(input("")));
        assertThrows(IllegalArgumentException.class,
                () -> LocalApiHttpRequestParser.read(input("POST / HTTP/1.1\\r\\n")));
    }

    @Test
    void rejectsTruncatedContentLengthBody() {
        assertThrows(IllegalArgumentException.class, () -> LocalApiHttpRequestParser.read(input(
                "POST / HTTP/1.1\r\nContent-Length: 5\r\n\r\nabc")));
    }

    @Test
    void rejectsOversizedContentLengthBodyBeforeAllocation() {
        assertThrows(IllegalArgumentException.class, () -> LocalApiHttpRequestParser.read(input(
                "POST / HTTP/1.1\r\nContent-Length: 1048577\r\n\r\n")));
    }

    @Test
    void parsesChunkedBodyWithExtensionsAndSafeTrailers() throws Exception {
        LocalApiHttpRequestParser.Request request = LocalApiHttpRequestParser.read(input(
                "POST /v1/responses HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n"
                + "4;foo=bar\r\ntest\r\n3\r\n123\r\n0\r\nX-Trace: ok\r\n\r\n"));
        assertEquals("test123", new String(request.body, StandardCharsets.UTF_8));
    }

    @Test
    void rejectsTransferEncodingCombinedWithContentLength() {
        assertThrows(IllegalArgumentException.class, () -> LocalApiHttpRequestParser.read(input(
                "POST / HTTP/1.1\r\nContent-Length: 0\r\nTransfer-Encoding: chunked\r\n\r\n")));
    }

    @Test
    void rejectsNonHttp11ChunkedRequests() {
        assertThrows(IllegalArgumentException.class, () -> LocalApiHttpRequestParser.read(input(
                "POST / HTTP/1.0\r\nTransfer-Encoding: chunked\r\n\r\n0\r\n\r\n")));
    }

    @Test
    void rejectsInvalidChunkSizeAndTerminator() {
        assertThrows(IllegalArgumentException.class, () -> LocalApiHttpRequestParser.read(input(
                "POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\nZ\r\n")));
        assertThrows(IllegalArgumentException.class, () -> LocalApiHttpRequestParser.read(input(
                "POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n1\r\naXX0\r\n\r\n")));
    }

    @Test
    void rejectsDuplicateHeadersAndMalformedRequestLines() {
        assertThrows(IllegalArgumentException.class, () -> LocalApiHttpRequestParser.read(input(
                "POST / HTTP/1.1\r\nX-Test: a\r\nx-test: b\r\n\r\n")));
        assertThrows(IllegalArgumentException.class, () -> LocalApiHttpRequestParser.read(input(
                "POST  / HTTP/1.1\r\n\r\n")));
    }

    @Test
    void rejectsForbiddenFramingAndAuthenticationTrailers() {
        assertThrows(IllegalArgumentException.class, () -> LocalApiHttpRequestParser.read(input(
                "POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n0\r\nAuthorization: Bearer x\r\n\r\n")));
        assertThrows(IllegalArgumentException.class, () -> LocalApiHttpRequestParser.read(input(
                "POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n0\r\nContent-Length: 0\r\n\r\n")));
    }

    @Test
    void rejectsInvalidChunkCrlfAndTruncatedChunkPayload() {
        assertThrows(IllegalArgumentException.class, () -> LocalApiHttpRequestParser.read(input(
                "POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n1\na\r\n0\r\n\r\n")));
        assertThrows(IllegalArgumentException.class, () -> LocalApiHttpRequestParser.read(input(
                "POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n2\r\na")));
    }
}
