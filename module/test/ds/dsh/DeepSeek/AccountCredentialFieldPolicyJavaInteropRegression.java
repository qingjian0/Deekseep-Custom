package ds.dsh.DeepSeek;

/** Java ABI and boundary regression for the Kotlin-owned credential field policy. */
public final class AccountCredentialFieldPolicyJavaInteropRegression {
    public static void main(String[] args) {
        require(AccountCredentialFieldPolicy.isValidIdLength(8), "minimum id length");
        require(!AccountCredentialFieldPolicy.isValidIdLength(7), "short id rejection");
        require(AccountCredentialFieldPolicy.isValidIdLength(128), "maximum id length");
        require(!AccountCredentialFieldPolicy.isValidIdLength(129), "long id rejection");
        require(AccountCredentialFieldPolicy.isValidTokenLength(16), "minimum token length");
        require(!AccountCredentialFieldPolicy.isValidTokenLength(15), "short token rejection");
        require(AccountCredentialFieldPolicy.isValidTokenLength(16_384), "maximum token length");
        require(!AccountCredentialFieldPolicy.isValidTokenLength(16_385), "long token rejection");
        require("value".equals(AccountCredentialFieldPolicy.trim("  value  ")),
                "Java trim-compatible normalization");
        require("\u2003value\u2003".equals(AccountCredentialFieldPolicy.trim("\u2003value\u2003")),
                "non-ASCII whitespace must remain unchanged");
        require(AccountCredentialFieldPolicy.isIntegralNumber(2.0d), "integral number");
        require(!AccountCredentialFieldPolicy.isIntegralNumber(2.5d), "fractional number");
        require(!AccountCredentialFieldPolicy.isIntegralNumber(Double.NaN), "NaN rejection");
        require(!AccountCredentialFieldPolicy.isIntegralNumber(Double.POSITIVE_INFINITY),
                "infinite number rejection");
        require("abcdef12".equals(AccountCredentialFieldPolicy.shortId(" ABCDEF123456 ")),
                "short id normalization");
        require("未知账号".equals(AccountCredentialFieldPolicy.shortId(null)),
                "null id fallback");
        require("a_b_name".equals(AccountCredentialFieldPolicy.safeFileName(" a/b:name. ")),
                "Java-compatible filename sanitization");
        require("Alice_a_b_trailing_等5个账号_DeepSeek账号.txt".equals(
                AccountCredentialFieldPolicy.suggestFileName(
                        java.util.Arrays.asList(" Alice ", "a/b", null, " trailing. ", "ignored"), 5)),
                "filename ordering, filtering, and total count");
        require("DeepSeek账号_DeepSeek账号.txt".equals(AccountCredentialFieldPolicy.suggestFileName(null, 0)),
                "empty filename fallback");
        System.out.println("AccountCredentialFieldPolicyJavaInteropRegression OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
