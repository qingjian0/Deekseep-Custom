package ds.dsh.DeepSeek;

/**
 * Java ABI compatibility facade. Translation data and exact-match policy live in Kotlin.
 */
final class UiLanguageCatalog {
    private UiLanguageCatalog() {}

    static boolean mightTranslate(String value) {
        return UiLanguageCatalogData.mightTranslate(value);
    }

    static String toEnglish(String value) {
        return UiLanguageCatalogData.toEnglish(value);
    }
}
