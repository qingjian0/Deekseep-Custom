package ds.dsh.DeepSeek;

public final class AccountCredentialTextPolicyRegressionTest {
    public static void main(String[] args) {
        sanitizesFilenameCharactersAndControls();
        trimsTrailingDotsAndSpacesAndBoundsLength();
        formatsShortAccountIdsConsistently();
        System.out.println("AccountCredentialTextPolicyRegressionTest OK");
    }

    private static void sanitizesFilenameCharactersAndControls() {
        String input = "a/b\\c:d*e?f\"g<h>i|j" + ((char) 1);
        check("a_b_c_d_e_f_g_h_i_j_".equals(
                AccountCredentialTextPolicy.safeFileNamePart(input)), "unsafe filename characters");
        check("".equals(AccountCredentialTextPolicy.safeFileNamePart(null)), "null filename");
    }

    private static void trimsTrailingDotsAndSpacesAndBoundsLength() {
        check("account".equals(AccountCredentialTextPolicy.safeFileNamePart(" account.  ")),
                "trailing dot and spaces");
        String longName = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMN";
        check(AccountCredentialTextPolicy.safeFileNamePart(longName).length() == 40,
                "filename segment maximum length");
    }

    private static void formatsShortAccountIdsConsistently() {
        check("abcdefgh".equals(AccountCredentialTextPolicy.shortId("ABCDEFGH123")),
                "lowercase first eight characters");
        check("short".equals(AccountCredentialTextPolicy.shortId(" Short ")),
                "trim short ID");
        check("未知账号".equals(AccountCredentialTextPolicy.shortId(null)), "null ID fallback");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
