package ds.dsh.DeepSeek

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Small, platform-neutral structured logger shared by the Kotlin runtime.
 *
 * Do not pass prompts, message bodies, credentials, tokens, account data, or raw hook arguments.
 * Exception messages are deliberately omitted because host exceptions may embed private data.
 */
object DshLog {
    enum class Level(val priority: Int) { DEBUG(10), INFO(20), WARN(30), ERROR(40) }

    data class Event(
        val timestampMillis: Long,
        val level: Level,
        val tag: String,
        val message: String,
        val threadName: String,
        val errorType: String?,
    )

    fun interface Sink { fun write(event: Event) }

    private val sinks = CopyOnWriteArrayList<Sink>()
    @Volatile private var minimumLevel: Level = Level.INFO
    @Volatile private var consoleSinkEnabled: Boolean = true

    init {
        sinks += Sink { event ->
            if (consoleSinkEnabled) System.err.println(
                "DSH/${event.tag} ${event.level.name} t=${event.timestampMillis} " +
                    "thread=${event.threadName} message=${event.message}" +
                    (event.errorType?.let { " error=$it" } ?: "")
            )
        }
    }

    @JvmStatic fun setMinimumLevel(level: Level) { minimumLevel = level }

    /** Disable the JVM console mirror when a platform-native sink owns Android logging. */
    @JvmStatic fun setConsoleSinkEnabled(enabled: Boolean) { consoleSinkEnabled = enabled }

    /** Applies the same credential and private-path redaction before records leave the runtime. */
    @JvmStatic fun sanitizeForStorage(value: String): String = sanitize(value)

    /** Adds a sink and returns a handle that removes it. */
    @JvmStatic fun addSink(sink: Sink): AutoCloseable {
        sinks += sink
        return AutoCloseable { sinks -= sink }
    }

    @JvmStatic fun debug(tag: String, message: String) = log(Level.DEBUG, tag, message, null)
    @JvmStatic fun info(tag: String, message: String) = log(Level.INFO, tag, message, null)
    @JvmStatic fun warn(tag: String, message: String) = log(Level.WARN, tag, message, null)
    @JvmStatic fun error(tag: String, message: String, failure: Throwable? = null) =
        log(Level.ERROR, tag, message, failure)

    private fun log(level: Level, tag: String, message: String, failure: Throwable?) {
        if (level.priority < minimumLevel.priority) return
        val event = Event(
            timestampMillis = System.currentTimeMillis(),
            level = level,
            tag = safeTag(tag),
            message = sanitize(message),
            threadName = Thread.currentThread().name.take(MAX_THREAD_CHARS),
            errorType = failure?.javaClass?.name?.take(MAX_ERROR_TYPE_CHARS),
        )
        sinks.forEach { sink -> runCatching { sink.write(event) } }
    }

    private fun safeTag(value: String): String {
        val normalized = value.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        return normalized.take(MAX_TAG_CHARS).ifEmpty { "General" }
    }

    private fun sanitize(value: String): String {
        var result = value.replace('\n', ' ').replace('\r', ' ')
        // Redact the complete Authorization credential, including the common
        // "Bearer <token>" form. A generic key/value pass alone would mask only
        // the word "Bearer" and leave the credential exposed.
        result = AUTHORIZATION_PATTERN.replace(result) { match ->
            match.groupValues[1] + "[redacted]"
        }
        result = BEARER_PATTERN.replace(result) { match ->
            match.groupValues[1] + "[redacted]"
        }
        // Cookie headers may contain multiple semicolon-separated name/value pairs.
        // Redact the complete header tail rather than only its first pair.
        result = COOKIE_PATTERN.replace(result) { match ->
            match.groupValues[1] + "[redacted]"
        }
        result = SECRET_PATTERN.replace(result) { match ->
            match.groupValues[1] + "[redacted]"
        }
        result = PRIVATE_PATH_PATTERN.replace(result, "[private-path]")
        return result.take(MAX_MESSAGE_CHARS)
    }

    private const val MAX_TAG_CHARS = 32
    private const val MAX_THREAD_CHARS = 48
    private const val MAX_ERROR_TYPE_CHARS = 120
    private const val MAX_MESSAGE_CHARS = 512
    private val AUTHORIZATION_PATTERN = Regex(
        "(?i)(\\bauthorization\\b\\s*[:=]\\s*)(?:[A-Za-z][A-Za-z0-9_-]*\\s+)?[^\\s,;]+"
    )
    private val BEARER_PATTERN = Regex(
        "(?i)(\\bbearer\\s+)[^\\s,;]+"
    )
    private val COOKIE_PATTERN = Regex(
        "(?i)(\\bcookie\\b\\s*[:=]\\s*).*$"
    )
    private val SECRET_PATTERN = Regex(
        "(?i)(\\b(?:x-api-key|api[_-]?key|access[_-]?token|refresh[_-]?token|client[_-]?secret|token|secret|password)\\b\\s*[\\\"']?\\s*[:=]\\s*[\\\"']?)[^\\s\\\"',;}]+"
    )
    private val PRIVATE_PATH_PATTERN = Regex(
        "/(?:data|storage)/[^\\s,;]+",
        RegexOption.IGNORE_CASE,
    )
}
