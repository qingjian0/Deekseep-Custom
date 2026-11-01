package ds.dsh.DeepSeek

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

internal data class ConfigBridgeReply(
    val code: Int,
    val data: Bundle?,
) {
    val json: String?
        get() = data?.getString(ModuleConfigBridge.EXTRA_JSON)
}

/** Owns the launcher-to-target config IPC protocol and its wire-format conversion. */
internal class LauncherConfigRepository(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun request(mode: String, json: String? = null): ConfigBridgeReply =
        suspendCancellableCoroutine { continuation ->
            val finished = AtomicBoolean(false)
            val timeout = Runnable {
                finish(
                    ConfigBridgeReply(RESULT_TIMEOUT, null),
                    finished,
                    continuation,
                    null,
                )
            }
            val reply = object : ResultReceiver(mainHandler) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    finish(
                        ConfigBridgeReply(resultCode, resultData),
                        finished,
                        continuation,
                        timeout,
                    )
                }
            }

            mainHandler.postDelayed(timeout, BRIDGE_TIMEOUT_MS)
            try {
                appContext.sendBroadcast(ModuleConfigBridge.request(mode, json, reply))
            } catch (_: Exception) {
                finish(
                    ConfigBridgeReply(ModuleConfigBridge.RESULT_ERROR, null),
                    finished,
                    continuation,
                    timeout,
                )
            }

            continuation.invokeOnCancellation {
                finished.set(true)
                mainHandler.removeCallbacks(timeout)
            }
        }

    fun parseConfigFiles(json: String): Map<String, ByteArray> {
        val root = JSONObject(json)
        val files = root.optJSONArray("files") ?: return emptyMap()
        val result = LinkedHashMap<String, ByteArray>()
        for (index in 0 until files.length()) {
            val entry = files.optJSONObject(index) ?: continue
            val path = entry.optString("path", "")
            if (path.isEmpty()) continue
            result[path] = runCatching {
                Base64.decode(entry.optString("data", ""), Base64.DEFAULT)
            }.getOrDefault(ByteArray(0))
        }
        return result
    }

    fun buildConfigJson(files: Map<String, ByteArray>): String {
        val root = JSONObject()
        root.put("format", "deekseep-config")
        root.put("version", 1)
        root.put("module", BuildConfig.VERSION_NAME)
        root.put("exported_at", System.currentTimeMillis())
        val entries = JSONArray()
        files.forEach { (path, bytes) ->
            entries.put(
                JSONObject()
                    .put("path", path)
                    .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP)),
            )
        }
        root.put("files", entries)
        return root.toString(2)
    }

    fun failureText(reply: ConfigBridgeReply): String = when {
        reply.code == RESULT_TIMEOUT ->
            "DeepSeek 未响应：请先打开一次 DeepSeek（确认模块已在 LSPosed 中启用），再回来重试"
        reply.data?.getString("error") != null ->
            "DeepSeek 返回错误：${reply.data.getString("error")}"
        else -> "请先打开一次 DeepSeek，再重试"
    }

    private fun finish(
        reply: ConfigBridgeReply,
        finished: AtomicBoolean,
        continuation: kotlinx.coroutines.CancellableContinuation<ConfigBridgeReply>,
        timeout: Runnable?,
    ) {
        if (!finished.compareAndSet(false, true)) return
        if (timeout != null) mainHandler.removeCallbacks(timeout)
        if (continuation.isActive) continuation.resume(reply)
    }

    private companion object {
        const val BRIDGE_TIMEOUT_MS = 4000L
        const val RESULT_TIMEOUT = -2
    }
}
