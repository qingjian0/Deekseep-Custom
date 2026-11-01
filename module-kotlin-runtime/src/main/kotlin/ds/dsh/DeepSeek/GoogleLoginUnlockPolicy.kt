package ds.dsh.DeepSeek

import java.util.ArrayList

/**
 * Pure transformations for adding native login methods without mutating host-owned lists.
 */
object GoogleLoginUnlockPolicy {
    @JvmStatic
    fun ensureGoogleFirst(raw: Any?, googleOption: Any?, optionType: Class<*>?): List<*>? {
        val source = raw as? List<*> ?: return null
        if (googleOption == null || optionType == null) return source
        if (source.isEmpty()) return source

        for (item in source) {
            if (item === googleOption || googleOption.equals(item)) return source
            if (item != null && !optionType.isInstance(item)) return source
        }

        val unlocked = ArrayList<Any?>(source.size + 1)
        unlocked.add(googleOption)
        unlocked.addAll(source)
        return unlocked
    }

    @JvmStatic
    fun ensurePasswordAfterGoogle(
        raw: Any?,
        googleOption: Any?,
        passwordOption: Any?,
        optionType: Class<*>?
    ): List<*>? {
        val source = raw as? List<*> ?: return null
        if (passwordOption == null || optionType == null) return source
        if (source.isEmpty()) return source

        var googleIndex = -1
        var passwordIndex = -1
        for (index in source.indices) {
            val item = source[index]
            if (item != null && !optionType.isInstance(item)) return source
            if (same(item, googleOption)) googleIndex = index
            if (same(item, passwordOption)) passwordIndex = index
        }
        if (passwordIndex >= 0) return source

        val unlocked = ArrayList<Any?>(source)
        unlocked.add(if (googleIndex >= 0) googleIndex + 1 else 0, passwordOption)
        return unlocked
    }

    @JvmStatic
    fun ensureWechatAndMobile(
        raw: Any?,
        googleOption: Any?,
        wechatOption: Any?,
        mobileOption: Any?,
        optionType: Class<*>?
    ): List<*>? {
        val source = raw as? List<*> ?: return null
        if (wechatOption == null || mobileOption == null || optionType == null) return source
        if (source.isEmpty()) return source

        var googleIndex = -1
        var wechatIndex = -1
        var hasMobile = false
        for (index in source.indices) {
            val item = source[index]
            if (item != null && !optionType.isInstance(item)) return source
            if (same(item, googleOption)) googleIndex = index
            if (same(item, wechatOption)) wechatIndex = index
            if (same(item, mobileOption)) hasMobile = true
        }
        if (wechatIndex >= 0 && hasMobile) return source

        val unlocked = ArrayList<Any?>(source)
        val insertAt = if (googleIndex >= 0) googleIndex + 1 else 0
        if (wechatIndex < 0) {
            unlocked.add(insertAt, wechatOption)
            wechatIndex = insertAt
        }
        if (!hasMobile) unlocked.add(wechatIndex + 1, mobileOption)
        return unlocked
    }

    private fun same(left: Any?, right: Any?): Boolean =
        left === right || (left != null && left.equals(right))
}
