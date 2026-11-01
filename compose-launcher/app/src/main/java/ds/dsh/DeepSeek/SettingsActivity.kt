package ds.dsh.DeepSeek

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ds.dsh.DeepSeek.ui.CategoryCard
import ds.dsh.DeepSeek.ui.NoteCard
import ds.dsh.DeepSeek.ui.SectionHeader
import ds.dsh.DeepSeek.ui.StatusHeroCard
import ds.dsh.DeepSeek.ui.YunSeekTheme
import ds.dsh.DeepSeek.ui.pressScale
import ds.dsh.DeepSeek.ui.staggeredEntrance
import androidx.compose.foundation.interaction.MutableInteractionSource
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipOutputStream

/**
 * YunSeek standalone launcher UI (Material 3 + Monet).
 *
 * The launcher process cannot read DeepSeek's private files. Every module setting shown here is
 * read and written through [ModuleConfigBridge] — the authenticated export/import broadcast that
 * the injected DeepSeek process answers — so DeepSeek must have been opened (with the module
 * active) at least once. Written values take effect after DeepSeek is restarted.
 */
class SettingsActivity : ComponentActivity() {
    companion object {
        @JvmField val VERSION: String = BuildInfo.MODULE_VERSION
        private const val TAG = "YunSeekLauncher"
        private const val APP_NAME = "YunSeek"
        private const val TARGET_PACKAGE = "com.deepseek.chat"
        private const val REPOSITORY = "https://github.com/lllucccian/Deekseep"
        private const val ROUTE_ABOUT = "about"
    }

    private val main = Handler(Looper.getMainLooper())
    private val backupRepository = LauncherBackupRepository()

    // ---- Compose-observed state owned by the activity -----------------------------------------
    private lateinit var statusViewModel: LauncherStatusViewModel
    private lateinit var configViewModel: LauncherConfigViewModel
    private var busyMessage by mutableStateOf<String?>(null)

    private var pendingExportZip = true
    private val exportZipLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> if (uri != null) exportData(uri, zip = true) }
    private val exportJsonLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) exportData(uri, zip = false) }
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) importData(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Every pre-UI step is optional: a failure here must never keep the launcher from opening.
        runCatching { enableEdgeToEdge() }.onFailure { Log.w(TAG, "edge-to-edge", it) }
        runCatching { UiLanguage.refreshSystem(this) }.onFailure { Log.w(TAG, "language", it) }
        statusViewModel = ViewModelProvider(this)[LauncherStatusViewModel::class.java]
        configViewModel = ViewModelProvider(this)[LauncherConfigViewModel::class.java]
        try {
            setContent {
                YunSeekTheme {
                    LauncherApp()
                }
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Compose UI failed to start", error)
            setContentView(TextView(this).apply {
                setPadding(48, 96, 48, 48)
                text = "$APP_NAME ${BuildConfig.VERSION_NAME}\n\n界面加载失败：${error.javaClass.simpleName}"
            })
        }
    }

    override fun onResume() {
        super.onResume()
        statusViewModel.refresh()
    }

    private fun tr(zh: String, en: String): String =
        runCatching { UiLanguage.text(this, zh, en) }.getOrDefault(zh)

    // ---- Environment ----------------------------------------------------------------------------

    private val appVersionLine: String
        get() = "$APP_NAME ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    private fun openDeepSeekSettings() {
        val launch = runCatching { packageManager.getLaunchIntentForPackage(TARGET_PACKAGE) }.getOrNull()
        if (launch == null) {
            toast("未安装 DeepSeek")
            return
        }
        runCatching { startActivity(launch) }.onFailure {
            toast("无法打开 DeepSeek")
            return
        }
        main.postDelayed({
            runCatching { sendBroadcast(ModuleConfigBridge.requestOpenSettings()) }
        }, 900L)
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { toast("无法打开链接") }
    }

    private fun toast(value: String) = runCatching {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show()
    }

    // ---- Config bridge --------------------------------------------------------------------------

    private fun loadConfig() {
        lifecycleScope.launch {
            try {
                configViewModel.loadConfig()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "config refresh failed", error)
            }
        }
    }

    /** Writes one setting through the ViewModel-owned authenticated IPC repository. */
    private fun writeConfigValue(path: String, value: String, onDone: (Boolean) -> Unit) {
        lifecycleScope.launch {
            val success = try {
                configViewModel.writeConfigValue(path, value)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "config write failed", error)
                false
            }
            if (success) {
                toast("已写入。请强制停止并重新打开 DeepSeek 后生效")
            } else {
                toast(configViewModel.state.value.errorMessage ?: "DeepSeek 未响应，请重试")
            }
            onDone(success)
        }
    }

    // ---- Export / import -----------------------------------------------------------------------

    private fun timestamp(): String = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())

    private fun launchExport(zip: Boolean) {
        pendingExportZip = zip
        val name = "yunseek-${BuildConfig.VERSION_NAME}-${timestamp()}" + if (zip) ".zip" else ".json"
        runCatching {
            if (zip) exportZipLauncher.launch(name) else exportJsonLauncher.launch(name)
        }.onFailure { toast("无法打开文件选择器：${it.message}") }
    }

    private fun launchImport() {
        runCatching {
            importLauncher.launch(
                arrayOf(
                    "application/zip", "application/x-zip-compressed", "application/json",
                    "text/json", "text/plain", "application/octet-stream",
                ),
            )
        }.onFailure { toast("无法打开文件选择器：${it.message}") }
    }

    private fun exportData(uri: Uri, zip: Boolean) {
        busyMessage = "正在读取模块设置…"
        lifecycleScope.launch {
            val json = try {
                configViewModel.loadConfig()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "config export read failed", error)
                null
            }
            if (!zip) {
                if (json == null) {
                    busyMessage = null
                    toast(configViewModel.state.value.errorMessage ?: "DeepSeek 未响应，请重试")
                    return@launch
                }
                writeInBackground(uri, json.toByteArray(StandardCharsets.UTF_8), "设置已导出（JSON）")
                return@launch
            }
            busyMessage = "正在打包 ZIP…"
            Thread {
                val result = runCatching {
                    contentResolver.openOutputStream(uri, "wt")!!.use { out ->
                        ZipOutputStream(out.buffered()).use { zos ->
                            backupRepository.writeZip(
                                output = zos,
                                settingsJson = json,
                                filesDirectory = filesDir,
                                sharedPreferencesDirectory = File(applicationInfo.dataDir, "shared_prefs"),
                                appName = APP_NAME,
                                appVersion = BuildConfig.VERSION_NAME,
                                appVersionCode = BuildConfig.VERSION_CODE,
                                hostLabel = statusViewModel.state.value.host.label,
                            )
                        }
                    }
                }
                main.post {
                    busyMessage = null
                    result.onSuccess { count ->
                        toast(
                            if (json == null) "已导出启动器数据（$count 项）。DeepSeek 未响应，模块设置未包含"
                            else "模块数据已导出（ZIP，$count 项）",
                        )
                    }.onFailure { toast("导出失败：${it.message}") }
                }
            }.start()
        }
    }

    private fun writeInBackground(uri: Uri, bytes: ByteArray, success: String) {
        Thread {
            val result = runCatching {
                contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
            }
            main.post {
                busyMessage = null
                result.onSuccess { toast(success) }.onFailure { toast("导出失败：${it.message}") }
            }
        }.start()
    }

    private fun importData(uri: Uri) {
        busyMessage = "正在读取备份…"
        Thread {
            val plan = runCatching {
                contentResolver.openInputStream(uri)!!.use { backupRepository.parse(it) }
            }
            main.post {
                plan.onSuccess { applyImport(it) }.onFailure {
                    busyMessage = null
                    toast("导入失败：${it.message}")
                }
            }
        }.start()
    }

    private fun applyImport(plan: LauncherBackupPlan) {
        Thread {
            val restored = runCatching {
                backupRepository.restore(
                    plan = plan,
                    filesDirectory = filesDir,
                    sharedPreferencesDirectory = File(applicationInfo.dataDir, "shared_prefs"),
                )
            }
            main.post {
                restored.onFailure {
                    busyMessage = null
                    toast("恢复启动器数据失败：${it.message}")
                    return@post
                }
                val launcherRestored = restored.getOrDefault(0)
                val settings = plan.settingsJson
                if (settings == null) {
                    busyMessage = null
                    toast("已恢复启动器数据 $launcherRestored 项（备份中不含模块设置）")
                    return@post
                }
                busyMessage = "正在写入 DeepSeek…"
                lifecycleScope.launch {
                    val success = try {
                        configViewModel.importConfig(settings)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Log.w(TAG, "config import failed", error)
                        false
                    }
                    busyMessage = null
                    if (success) {
                        toast("导入完成。请强制停止并重新打开 DeepSeek 后生效")
                    } else {
                        toast(configViewModel.state.value.errorMessage ?: "DeepSeek 未响应，请重试")
                    }
                }
            }
        }.start()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        runCatching {
            if (requestCode == Main.FULL_DATA_BACKUP_REQUEST) {
                ClosedFullDataBackupBridge.handleExportResult(this, resultCode, data)
            } else if (requestCode == Main.FULL_DATA_RESTORE_REQUEST) {
                ClosedFullDataBackupBridge.handleRestoreResult(this, resultCode, data)
            }
        }.onFailure { toast("操作失败：${it.message}") }
    }

    private fun licenseText(): String = runCatching {
        val id = resources.getIdentifier("gpl_3_0", "raw", packageName)
        require(id != 0)
        resources.openRawResource(id).bufferedReader().use { it.readText() }
    }.getOrDefault(
        "YunSeek 以 GNU General Public License v3.0 (GPL-3.0-only) 授权发布。\n\n" +
            "你可以在遵守 GPL-3.0 的前提下自由使用、研究、修改和再分发本软件；" +
            "分发修改版本时必须以相同许可证公开对应源代码。本软件不提供任何担保。\n\n" +
            "完整许可证文本：https://www.gnu.org/licenses/gpl-3.0.html",
    )

    // ---- UI -------------------------------------------------------------------------------------

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun LauncherApp() {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var route by rememberSaveable { mutableStateOf<String?>(null) }
        var licenseOpen by remember { mutableStateOf(false) }
        val statusState by statusViewModel.state.collectAsStateWithLifecycle()
        val configState by configViewModel.state.collectAsStateWithLifecycle()

        BackHandler(enabled = route != null || tab != 0) {
            if (route != null) route = null else tab = 0
        }

        val title = route?.let { r ->
            if (r == ROUTE_ABOUT) "开源声明与关于" else LauncherCatalog.find(r)?.title
        } ?: APP_NAME

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Crossfade(targetState = title, label = "title") { t ->
                            Text(t, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        }
                    },
                    navigationIcon = {
                        AnimatedVisibility(visible = route != null, enter = fadeIn(), exit = fadeOut()) {
                            IconButton(onClick = { route = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
            bottomBar = {
                NavigationBar {
                    val items = listOf(
                        Triple("首页", Icons.Filled.Home, 0),
                        Triple("功能", Icons.AutoMirrored.Filled.List, 1),
                        Triple("设置", Icons.Filled.Settings, 2),
                    )
                    items.forEach { (label, icon, index) ->
                        NavigationBarItem(
                            selected = route == null && tab == index,
                            onClick = { route = null; tab = index },
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(label) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                val screen = route ?: "tab:$tab"
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        val enteringDetail = !targetState.startsWith("tab:") && initialState.startsWith("tab:")
                        val leavingDetail = targetState.startsWith("tab:") && !initialState.startsWith("tab:")
                        when {
                            enteringDetail ->
                                (slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(260))) togetherWith
                                    (slideOutHorizontally(tween(320)) { -it / 6 } + fadeOut(tween(200)))
                            leavingDetail ->
                                (slideInHorizontally(tween(320)) { -it / 6 } + fadeIn(tween(260))) togetherWith
                                    (slideOutHorizontally(tween(320)) { it / 3 } + fadeOut(tween(200)))
                            else ->
                                (fadeIn(tween(240)) + slideInVertically(tween(280)) { it / 24 }) togetherWith
                                    (fadeOut(tween(160)) + slideOutVertically(tween(200)) { -it / 32 })
                        }
                    },
                    label = "screen",
                ) { s ->
                    when {
                        s == "tab:0" -> HomeScreen(onOpen = { route = it }, statusContent = { StatusCard(0, statusState) })
                        s == "tab:1" -> FeaturesScreen(onOpen = { route = it }, onOpenDeepSeekSettings = { openDeepSeekSettings() })
                        s == "tab:2" -> SettingsScreen(onOpen = { route = it }, onOpenAbout = { route = ROUTE_ABOUT }, onOpenDeepSeekSettings = { openDeepSeekSettings() }, onRefreshStatus = { statusViewModel.refresh(); toast("已刷新") }, appVersionLine = appVersionLine, repositoryUrl = REPOSITORY, onOpenUrl = { openUrl(it) })
                        s == ROUTE_ABOUT -> AboutScreen(onLicense = { licenseOpen = true }, appName = APP_NAME, appVersionLine = appVersionLine, repositoryUrl = REPOSITORY, onOpenUrl = { openUrl(it) })
                        s == LauncherCatalog.DATA_ID -> DataScreen(
                            showFullBackup = BuildInfo.PROTECTED_BUILD && isFullBackupHostSupported(),
                            onExport = { launchExport(it) },
                            onImport = { launchImport() },
                            onFullBackup = {
                                runCatching { ClosedFullDataBackupBridge.beginBackup(this@SettingsActivity) }
                                    .onFailure { toast("备份失败：${it.message}") }
                            },
                            onFullRestore = {
                                runCatching { ClosedFullDataBackupBridge.beginRestore(this@SettingsActivity) }
                                    .onFailure { toast("恢复失败：${it.message}") }
                            },
                        )
                        s == LauncherCatalog.HELP_ID -> HelpScreen(onAbout = { route = ROUTE_ABOUT }, onOpenDeepSeekSettings = { openDeepSeekSettings() })
                        else -> LauncherCatalog.find(s)?.let { category ->
                            CategoryScreen(
                                category = category,
                                bridgeStatus = configState.bridgeStatus,
                                configFiles = configState.files,
                                onRefresh = { loadConfig() },
                                onWrite = { path, value, done ->
                                    writeConfigValue(path, value, done)
                                },
                                onOpenDeepSeekSettings = { openDeepSeekSettings() },
                            )
                        } ?: HomeScreen(onOpen = { route = it }, statusContent = { StatusCard(0, statusState) })
                    }
                }
                val busy = busyMessage
                if (busy != null) BusyDialog(busy)
            }
        }

        if (licenseOpen) {
            AlertDialog(
                onDismissRequest = { licenseOpen = false },
                title = { Text("GNU GPL-3.0") },
                text = {
                    Text(
                        licenseText(),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                },
                confirmButton = { TextButton(onClick = { licenseOpen = false }) { Text("关闭") } },
            )
        }
    }

            @Composable
    private fun StatusCard(index: Int, status: LauncherStatusUiState) {
        val host = status.host
        val active = status.isActive
        val lines = buildList {
            add(appVersionLine)
            add("DeepSeek：${host.label}")
            if (status.frameworkConnected && !active) {
                add("LSPosed 框架服务已连接，但尚未验证 DeepSeek 目标进程注入")
            }
            if (!active) add("请在 LSPosed 中启用 YunSeek 并勾选 DeepSeek，然后重启 DeepSeek")
            else if (host.installed && !host.supported) add("当前 DeepSeek 版本未经适配，部分功能可能失效")
        }
        StatusHeroCard(
            active = active,
            title = if (active) "模块已激活" else "模块未激活",
            lines = lines,
            modifier = Modifier.staggeredEntrance(index),
            onClick = { statusViewModel.refresh() },
        )
    }

                @Suppress("DEPRECATION")
    private fun isFullBackupHostSupported(): Boolean = runCatching {
        val info = packageManager.getPackageInfo(TARGET_PACKAGE, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        code == 249L || code == 257L || code == 268L
    }.getOrDefault(false)

        }
