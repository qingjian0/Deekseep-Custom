package ds.dsh.DeepSeek;

/**
 * Bounded parser for a DexKit DEX method descriptor.
 *
 * This validates descriptor structure only. It does not load classes or infer method semantics.
 */
final class RoiMethodShape {
    final String ownerDescriptor;
    final String methodName;
    final int parameterCount;
    final String returnDescriptor;

    private RoiMethodShape(String ownerDescriptor, String methodName,
                           int parameterCount, String returnDescriptor) {
        this.ownerDescriptor = ownerDescriptor;
        this.methodName = methodName;
        this.parameterCount = parameterCount;
        this.returnDescriptor = returnDescriptor;
    }

    static RoiMethodShape parse(String descriptor) {
        if (descriptor == null || descriptor.length() == 0 || descriptor.length() > 4096) return null;
        int arrow = descriptor.indexOf("->");
        if (arrow <= 1 || arrow + 2 >= descriptor.length()) return null;
        String owner = descriptor.substring(0, arrow);
        if (!isObjectDescriptor(owner)) return null;

        int open = descriptor.indexOf('(', arrow + 2);
        if (open <= arrow + 2) return null;
        String method = descriptor.substring(arrow + 2, open);
        if (!isValidMethodName(method)) return null;

        int close = descriptor.indexOf(')', open + 1);
        if (close < 0) return null;
        int count = 0;
        int cursor = open + 1;
        while (cursor < close) {
            int next = consumeType(descriptor, cursor, false);
            if (next <= cursor || next > close) return null;
            cursor = next;
            count++;
            if (count > 255) return null;
        }
        if (cursor != close) return null;

        int returnStart = close + 1;
        int returnEnd = consumeType(descriptor, returnStart, true);
        if (returnEnd != descriptor.length()) return null;
        String returnType = descriptor.substring(returnStart, returnEnd);
        return new RoiMethodShape(owner, method, count, returnType);
    }

    private static int consumeType(String value, int start, boolean allowVoid) {
        int length = value.length();
        if (start >= length) return -1;
        int cursor = start;
        int dimensions = 0;
        while (cursor < length && value.charAt(cursor) == '[') {
            dimensions++;
            cursor++;
            if (dimensions > 255) return -1;
        }
        if (cursor >= length) return -1;
        char type = value.charAt(cursor);
        if (type == 'V') return allowVoid && dimensions == 0 ? cursor + 1 : -1;
        if ("ZBCSIJFD".indexOf(type) >= 0) return cursor + 1;
        if (type != 'L') return -1;
        int semicolon = value.indexOf(';', cursor + 1);
        if (semicolon <= cursor + 1) return -1;
        for (int i = cursor + 1; i < semicolon; i++) {
            char ch = value.charAt(i);
            if (ch == '.' || ch == '[' || ch == '(' || ch == ')' || ch == ';') return -1;
        }
        return semicolon + 1;
    }

    private static boolean isObjectDescriptor(String value) {
        return value.length() > 2 && value.charAt(0) == 'L'
                && value.charAt(value.length() - 1) == ';'
                && value.indexOf('.') < 0 && value.indexOf('[') < 0;
    }

    private static boolean isValidMethodName(String value) {
        if (value.length() == 0) return false;
        if ("<init>".equals(value) || "<clinit>".equals(value)) return true;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '/' || ch == ';' || ch == '[' || ch == ']' || ch == '(' || ch == ')') return false;
        }
        return true;
    }
}
