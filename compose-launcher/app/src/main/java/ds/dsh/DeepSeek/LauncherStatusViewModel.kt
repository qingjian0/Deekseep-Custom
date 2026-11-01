package ds.dsh.DeepSeek

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

internal data class LauncherHostInfo(
    val label: String,
    val supported: Boolean,
    val installed: Boolean,
)

internal data class LauncherStatusUiState(
    val host: LauncherHostInfo = LauncherHostInfo("正在检测…", false, false),
    val isActive: Boolean = false,
    val frameworkConnected: Boolean = false,
)

internal class LauncherStatusRepository(context: Context) {
    private val appContext = context.applicationContext

    @Suppress("DEPRECATION")
    fun load(): LauncherStatusUiState {
        val host = runCatching {
            val info = appContext.packageManager.getPackageInfo(TARGET_PACKAGE, 0)
            val code = if (Build.VERSION.SDK_INT >= 28) {
                info.longVersionCode
            } else {
                info.versionCode.toLong()
            }
            when (code) {
                287L -> LauncherHostInfo("国内版 2.7.1 (287)", true, true)
                289L -> LauncherHostInfo("国内版 2.7.2 (289)", true, true)
                290L -> LauncherHostInfo("国际版 2.7.2 (290)", true, true)
                else -> LauncherHostInfo("不支持的版本 ${info.versionName} ($code)", false, true)
            }
        }.getOrDefault(LauncherHostInfo("未安装 DeepSeek", false, false))

        val frameworkConnected = runCatching {
            XposedActivationProvider.isFrameworkConnected()
        }.getOrDefault(false)
        val targetRecentlyActive = runCatching {
            XposedActivationProvider.isTargetRecentlyActive(appContext)
        }.getOrDefault(false)

        return LauncherStatusUiState(
            host = host,
            // A framework binder connection proves only framework availability, not that
            // the DeepSeek target process has loaded this module.
            isActive = targetRecentlyActive,
            frameworkConnected = frameworkConnected,
        )
    }

    private companion object {
        const val TARGET_PACKAGE = "com.deepseek.chat"
    }
}

/** Owns the launcher activation/host status and refreshes it off the main thread. */
internal class LauncherStatusViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LauncherStatusRepository(application)
    private val refreshGeneration = AtomicLong(0L)
    private val mutableState = MutableStateFlow(LauncherStatusUiState())
    val state: StateFlow<LauncherStatusUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val generation = refreshGeneration.incrementAndGet()
        viewModelScope.launch {
            val latest = withContext(Dispatchers.IO) { repository.load() }
            if (refreshGeneration.get() == generation) {
                mutableState.value = latest
            }
        }
    }
}
