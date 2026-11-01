package ds.dsh.DeepSeek

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

internal enum class ConfigBridgeUiStatus { IDLE, LOADING, READY, UNAVAILABLE }

internal data class LauncherConfigUiState(
    val bridgeStatus: ConfigBridgeUiStatus = ConfigBridgeUiStatus.IDLE,
    val files: Map<String, ByteArray> = emptyMap(),
    val errorMessage: String? = null,
)

/** Owns config bridge state and serializes target-process IPC away from the Activity. */
internal class LauncherConfigViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LauncherConfigRepository(application)
    private val operationMutex = Mutex()
    private val mutableState = MutableStateFlow(LauncherConfigUiState())
    val state: StateFlow<LauncherConfigUiState> = mutableState.asStateFlow()

    suspend fun loadConfig(): String? = serialized {
        val previous = mutableState.value
        mutableState.value = previous.copy(
            bridgeStatus = ConfigBridgeUiStatus.LOADING,
            errorMessage = null,
        )
        try {
            val reply = withContext(Dispatchers.IO) {
                repository.request(ModuleConfigBridge.MODE_EXPORT)
            }
            val json = reply.json
            if (reply.code == ModuleConfigBridge.RESULT_OK && !json.isNullOrEmpty()) {
                val files = withContext(Dispatchers.Default) {
                    runCatching { repository.parseConfigFiles(json) }.getOrDefault(emptyMap())
                }
                mutableState.value = LauncherConfigUiState(
                    bridgeStatus = ConfigBridgeUiStatus.READY,
                    files = files,
                )
                json
            } else {
                mutableState.value = previous.copy(
                    bridgeStatus = ConfigBridgeUiStatus.UNAVAILABLE,
                    errorMessage = repository.failureText(reply),
                )
                null
            }
        } catch (cancelled: CancellationException) {
            mutableState.value = previous
            throw cancelled
        }
    }

    suspend fun writeConfigValue(path: String, value: String): Boolean = serialized {
        val previous = mutableState.value
        mutableState.value = previous.copy(errorMessage = null)
        try {
            val bytes = value.toByteArray(Charsets.UTF_8)
            val payload = withContext(Dispatchers.Default) {
                repository.buildConfigJson(mapOf(path to bytes))
            }
            val reply = withContext(Dispatchers.IO) {
                repository.request(ModuleConfigBridge.MODE_IMPORT, payload)
            }
            if (reply.code == ModuleConfigBridge.RESULT_OK) {
                mutableState.value = mutableState.value.copy(
                    bridgeStatus = ConfigBridgeUiStatus.READY,
                    files = mutableState.value.files + (path to bytes),
                    errorMessage = null,
                )
                true
            } else {
                mutableState.value = mutableState.value.copy(
                    errorMessage = repository.failureText(reply),
                )
                false
            }
        } catch (cancelled: CancellationException) {
            mutableState.value = previous
            throw cancelled
        }
    }

    suspend fun importConfig(json: String): Boolean = serialized {
        val previous = mutableState.value
        mutableState.value = previous.copy(errorMessage = null)
        try {
            val reply = withContext(Dispatchers.IO) {
                repository.request(ModuleConfigBridge.MODE_IMPORT, json)
            }
            if (reply.code == ModuleConfigBridge.RESULT_OK) {
                val files = withContext(Dispatchers.Default) {
                    runCatching { repository.parseConfigFiles(json) }.getOrDefault(emptyMap())
                }
                mutableState.value = LauncherConfigUiState(
                    bridgeStatus = ConfigBridgeUiStatus.READY,
                    files = files,
                )
                true
            } else {
                mutableState.value = mutableState.value.copy(
                    errorMessage = repository.failureText(reply),
                )
                false
            }
        } catch (cancelled: CancellationException) {
            mutableState.value = previous
            throw cancelled
        }
    }

    private suspend fun <T> serialized(block: suspend () -> T): T {
        operationMutex.lock()
        try {
            return block()
        } finally {
            operationMutex.unlock()
        }
    }
}
