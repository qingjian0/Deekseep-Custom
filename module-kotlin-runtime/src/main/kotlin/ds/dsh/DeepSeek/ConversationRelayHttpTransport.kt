package ds.dsh.DeepSeek

import java.net.HttpURLConnection
import java.net.URL

/**
 * Owns the deterministic HttpURLConnection request-setup lifecycle used by the legacy relay.
 *
 * Response decoding and disconnect-after-response remain with the Android adapter because
 * that boundary still owns org.json and the provider-specific SSE readers.
 */
object ConversationRelayHttpTransport {
    fun interface ConnectionFactory {
        fun open(url: URL): HttpURLConnection
    }

    private val defaultFactory = ConnectionFactory { url ->
        url.openConnection() as HttpURLConnection
    }

    @JvmStatic
    fun openRequest(
        url: String,
        method: String,
        connectTimeoutMillis: Int,
        readTimeoutMillis: Int,
        doOutput: Boolean,
        headers: Map<String, String>,
        body: ByteArray?,
    ): HttpURLConnection = openRequest(
        url, method, connectTimeoutMillis, readTimeoutMillis, doOutput, headers, body, defaultFactory
    )

    /** Injectable overload for JVM tests; production callers use the default overload. */
    @JvmStatic
    fun openRequest(
        url: String,
        method: String,
        connectTimeoutMillis: Int,
        readTimeoutMillis: Int,
        doOutput: Boolean,
        headers: Map<String, String>,
        body: ByteArray?,
        connectionFactory: ConnectionFactory,
    ): HttpURLConnection {
        val connection = connectionFactory.open(URL(url))
        try {
            connection.requestMethod = method
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.doOutput = doOutput
            for ((name, value) in headers) {
                connection.setRequestProperty(name, value)
            }
            if (doOutput && body != null) {
                connection.outputStream.use { output ->
                    output.write(body)
                    output.flush()
                }
            }
            return connection
        } catch (failure: Throwable) {
            try {
                connection.disconnect()
            } catch (cleanupFailure: Throwable) {
                failure.addSuppressed(cleanupFailure)
            }
            throw failure
        }
    }
}
