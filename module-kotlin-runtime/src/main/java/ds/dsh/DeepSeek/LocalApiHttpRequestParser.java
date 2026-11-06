package ds.dsh.DeepSeek;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Bounded HTTP/1.x request parser shared by the Android gateway and JVM tests. */
public final class LocalApiHttpRequestParser {
    static final int MAX_HEADER_BYTES = 16 * 1024;
    static final int MAX_BODY_BYTES = 1024 * 1024;

    private LocalApiHttpRequestParser() {}

    public static Request read(InputStream in) throws Exception {
        if (in == null) throw new IllegalArgumentException("Input stream is required");
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        int matched = 0;
        while (header.size() < MAX_HEADER_BYTES) {
            int value = in.read();
            if (value < 0) {
                if (header.size() == 0) return null;
                throw new IllegalArgumentException("Invalid HTTP headers");
            }
            header.write(value);
            if ((matched == 0 && value == '\r') || (matched == 2 && value == '\r')) matched++;
            else if ((matched == 1 && value == '\n') || (matched == 3 && value == '\n')) {
                matched++;
                if (matched == 4) break;
            } else matched = value == '\r' ? 1 : 0;
        }
        if (matched != 4) throw new IllegalArgumentException("Invalid HTTP headers");
        String[] lines = new String(header.toByteArray(), StandardCharsets.ISO_8859_1).split("\\r\\n");
        String[] first = lines[0].split(" ", -1);
        if (first.length != 3 || !isHttpToken(first[0]) || !isRequestTarget(first[1])
                || (!"HTTP/1.0".equals(first[2]) && !"HTTP/1.1".equals(first[2]))) {
            throw new IllegalArgumentException("Invalid request line");
        }
        Map<String, String> headers = new HashMap<String, String>();
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].length() == 0) continue;
            int colon = lines[i].indexOf(':');
            if (colon <= 0) throw new IllegalArgumentException("Invalid HTTP header");
            String name = lines[i].substring(0, colon).toLowerCase(Locale.ROOT);
            String rawValue = lines[i].substring(colon + 1);
            if (!isHttpToken(name) || !isHttpFieldValue(rawValue) || headers.containsKey(name)) {
                throw new IllegalArgumentException("Duplicate or empty HTTP header");
            }
            headers.put(name, rawValue.trim());
        }
        String transferEncoding = headers.get("transfer-encoding");
        String contentLength = headers.get("content-length");
        if (transferEncoding != null && contentLength != null) {
            throw new IllegalArgumentException("Ambiguous request framing");
        }
        byte[] body;
        if (transferEncoding != null) {
            if (!"HTTP/1.1".equals(first[2])) {
                throw new IllegalArgumentException("Transfer encoding requires HTTP/1.1");
            }
            if (!"chunked".equalsIgnoreCase(transferEncoding.trim())) {
                throw new IllegalArgumentException("Unsupported transfer encoding");
            }
            body = readChunkedBody(in);
        } else {
            int length;
            if (contentLength != null && !isDecimalDigits(contentLength)) {
                throw new IllegalArgumentException("Invalid content length");
            }
            try {
                length = Integer.parseInt(contentLength == null ? "0" : contentLength);
            } catch (NumberFormatException invalidLength) {
                throw new IllegalArgumentException("Invalid content length");
            }
            if (length < 0 || length > MAX_BODY_BYTES) {
                throw new IllegalArgumentException("Request too large");
            }
            body = new byte[length];
            int offset = 0;
            while (offset < length) {
                int count = in.read(body, offset, length - offset);
                if (count < 0) throw new IllegalArgumentException("Truncated request body");
                if (count == 0) continue;
                offset += count;
            }
        }
        String path = first[1];
        int queryStart = path.indexOf('?');
        if (queryStart >= 0) path = path.substring(0, queryStart);
        if (!path.startsWith("/")) throw new IllegalArgumentException("Invalid request target");
        return new Request(first[0], path, headers, body);
    }

    private static byte[] readChunkedBody(InputStream in) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int trailerBytes = 0;
        while (true) {
            String sizeLine = readCrlfLine(in, 1024);
            int extension = sizeLine.indexOf(';');
            String sizeText = extension >= 0 ? sizeLine.substring(0, extension) : sizeLine;
            if (sizeText.length() > 16 || !isHexDigits(sizeText)) {
                throw new IllegalArgumentException("Invalid chunk size");
            }
            long chunkSize;
            try {
                chunkSize = Long.parseLong(sizeText, 16);
            } catch (NumberFormatException invalidSize) {
                throw new IllegalArgumentException("Invalid chunk size");
            }
            if (chunkSize < 0 || chunkSize > MAX_BODY_BYTES - body.size()) {
                throw new IllegalArgumentException("Request too large");
            }
            if (chunkSize == 0) {
                while (true) {
                    String trailer = readCrlfLine(in, 4096);
                    trailerBytes += trailer.length() + 2;
                    if (trailerBytes > MAX_HEADER_BYTES) {
                        throw new IllegalArgumentException("Request trailers too large");
                    }
                    if (trailer.length() == 0) break;
                    int colon = trailer.indexOf(':');
                    if (colon <= 0) throw new IllegalArgumentException("Invalid request trailer");
                    String name = trailer.substring(0, colon).toLowerCase(Locale.ROOT);
                    String trailerValue = trailer.substring(colon + 1);
                    if (!isHttpToken(name) || !isHttpFieldValue(trailerValue)) {
                        throw new IllegalArgumentException("Invalid request trailer");
                    }
                    if ("content-length".equals(name) || "transfer-encoding".equals(name)
                            || "host".equals(name) || "authorization".equals(name)) {
                        throw new IllegalArgumentException("Forbidden request trailer");
                    }
                }
                return body.toByteArray();
            }
            byte[] chunk = new byte[(int) chunkSize];
            int offset = 0;
            while (offset < chunk.length) {
                int count = in.read(chunk, offset, chunk.length - offset);
                if (count < 0) throw new IllegalArgumentException("Truncated chunk data");
                if (count == 0) continue;
                offset += count;
            }
            body.write(chunk);
            if (in.read() != '\r' || in.read() != '\n') {
                throw new IllegalArgumentException("Invalid chunk terminator");
            }
        }
    }

    private static String readCrlfLine(InputStream in, int maxBytes) throws Exception {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (line.size() <= maxBytes) {
            int value = in.read();
            if (value < 0) throw new IllegalArgumentException("Truncated chunk framing");
            if (value == '\r') {
                if (in.read() != '\n') throw new IllegalArgumentException("Invalid chunk line ending");
                return new String(line.toByteArray(), StandardCharsets.US_ASCII);
            }
            if (value == '\n' || value < 0x20 && value != '\t') {
                throw new IllegalArgumentException("Invalid chunk line");
            }
            line.write(value);
        }
        throw new IllegalArgumentException("Chunk line too large");
    }

    private static boolean isHttpToken(String value) {
        return value != null && value.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+");
    }
    private static boolean isHttpFieldValue(String value) {
        if (value == null) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c < 0x20 && c != '\t') || c == 0x7f) return false;
        }
        return true;
    }
    private static boolean isRequestTarget(String value) {
        if (value == null || value.length() == 0) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c <= 0x20 || c >= 0x7f) return false;
        }
        return value.startsWith("/");
    }
    private static boolean isDecimalDigits(String value) {
        if (value == null || value.length() == 0) return false;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < '0' || value.charAt(i) > '9') return false;
        }
        return true;
    }
    private static boolean isHexDigits(String value) {
        if (value == null || value.length() == 0) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F'))) return false;
        }
        return true;
    }

    public static final class Request {
        public final String method;
        public final String path;
        public final Map<String, String> headers;
        public final byte[] body;

        private Request(String method, String path, Map<String, String> headers, byte[] body) {
            this.method = method;
            this.path = path;
            this.headers = Collections.unmodifiableMap(new HashMap<String, String>(headers));
            this.body = body.clone();
        }
    }
}
