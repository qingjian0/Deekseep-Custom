package ds.dsh.DeepSeek

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

internal data class LauncherConfigUiState(
    val bridgeStatus: ConfigBridgeUiStatus = ConfigBridgeUiStatus.IDLE,
    val files: Map<String, ByteArray> = emptyMap(),
    val errorMessage: String? = null,
)

/** Owns config bridge state and serializes target-process IPC away from the Activity. */
internal class LauncherConfigViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LauncherConfigRepository(application)
    private val mutableState = MutableStateFlow(LauncherConfigUiState())
    val state: StateFlow<LauncherConfigUiState> = mutableState.asStateFlow()

    suspend fun loadConfig(): String? {
        mutableState.value = mutableState.value.copy(
            bridgeStatus = ConfigBridgeUiStatus.LOADING,
            errorMessage = null,
        )
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
            return json
        }

        mutableState.value = mutableState.value.copy(
            bridgeStatus = ConfigBridgeUiStatus.UNAVAILABLE,
            errorMessage = repository.failureText(reply),
        )
        return null
    }

    suspend fun writeConfigValue(path: String, value: String): Boolean {
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
            return true
        }
        mutableState.value = mutableState.value.copy(
            errorMessage = repository.failureText(reply),
        )
        return false
    }

    suspend fun importConfig(json: String): Boolean {
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
            return true
        }
        mutableState.value = mutableState.value.copy(
            errorMessage = repository.failureText(reply),
        )
        return false
    }
}
