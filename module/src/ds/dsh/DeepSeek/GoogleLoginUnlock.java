package ds.dsh.DeepSeek;

import java.util.List;

/** Java compatibility facade for pure native login-option list transformations. */
final class GoogleLoginUnlock {
    private GoogleLoginUnlock() {}

    static List<?> ensureGoogleFirst(Object raw, Object googleOption, Class<?> optionType) {
        return GoogleLoginUnlockPolicy.ensureGoogleFirst(raw, googleOption, optionType);
    }

    static List<?> ensurePasswordAfterGoogle(Object raw, Object googleOption,
                                             Object passwordOption, Class<?> optionType) {
        return GoogleLoginUnlockPolicy.ensurePasswordAfterGoogle(
                raw, googleOption, passwordOption, optionType);
    }

    static List<?> ensureWechatAndMobile(Object raw, Object googleOption,
                                         Object wechatOption, Object mobileOption,
                                         Class<?> optionType) {
        return GoogleLoginUnlockPolicy.ensureWechatAndMobile(
                raw, googleOption, wechatOption, mobileOption, optionType);
    }
}
