package com.dsmod.probe

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.util.Base64
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
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
import com.dsmod.probe.ui.CategoryCard
import com.dsmod.probe.ui.NoteCard
import com.dsmod.probe.ui.SectionHeader
import com.dsmod.probe.ui.StatusHeroCard
import com.dsmod.probe.ui.YunSeekTheme
import com.dsmod.probe.ui.pressScale
import com.dsmod.probe.ui.staggeredEntrance
import androidx.compose.foundation.interaction.MutableInteractionSource
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
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
        private const val BRIDGE_TIMEOUT_MS = 4000L
        private const val RESULT_TIMEOUT = -2
        private const val MAX_IMPORT_BYTES = 64 * 1024 * 1024
        private const val MAX_LAUNCHER_FILE = 4 * 1024 * 1024
        private const val ZIP_META = "yunseek-backup.json"
        private const val ZIP_SETTINGS = "settings/deekseep-config.json"
        private const val ZIP_LAUNCHER_FILES = "launcher/files/"
        private const val ZIP_LAUNCHER_PREFS = "launcher/shared_prefs/"
        private const val ROUTE_ABOUT = "about"
    }

    private enum class BridgeStatus { IDLE, LOADING, READY, UNAVAILABLE }

    private val main = Handler(Looper.getMainLooper())

    // ---- Compose-observed state owned by the activity -----------------------------------------
    private lateinit var statusViewModel: LauncherStatusViewModel
    private var bridgeStatus by mutableStateOf(BridgeStatus.IDLE)
    private var configFiles by mutableStateOf<Map<String, ByteArray>>(emptyMap())
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

    /** Sends one bridge request with a timeout; [done] is called exactly once on the main thread. */
    private fun bridgeRequest(mode: String, json: String?, done: (Int, Bundle?) -> Unit) {
        val finished = AtomicBoolean(false)
        val reply = object : ResultReceiver(main) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                if (finished.compareAndSet(false, true)) done(resultCode, resultData)
            }
        }
        main.postDelayed({
            if (finished.compareAndSet(false, true)) done(RESULT_TIMEOUT, null)
        }, BRIDGE_TIMEOUT_MS)
        runCatching { sendBroadcast(ModuleConfigBridge.request(mode, json, reply)) }
            .onFailure {
                if (finished.compareAndSet(false, true)) done(ModuleConfigBridge.RESULT_ERROR, null)
            }
    }

    private fun bridgeFailureText(code: Int, data: Bundle?): String = when {
        code == RESULT_TIMEOUT -> "DeepSeek 未响应：请先打开一次 DeepSeek（确认模块已在 LSPosed 中启用），再回来重试"
        data?.getString("error") != null -> "DeepSeek 返回错误：${data.getString("error")}"
        else -> "请先打开一次 DeepSeek，再重试"
    }

    private fun parseConfigFiles(json: String): Map<String, ByteArray> {
        val root = JSONObject(json)
        val files = root.optJSONArray("files") ?: return emptyMap()
        val out = LinkedHashMap<String, ByteArray>()
        for (i in 0 until files.length()) {
            val entry = files.optJSONObject(i) ?: continue
            val path = entry.optString("path", "")
            if (path.isEmpty()) continue
            out[path] = runCatching { Base64.decode(entry.optString("data", ""), Base64.DEFAULT) }
                .getOrDefault(ByteArray(0))
        }
        return out
    }

    private fun loadConfig(onDone: ((String?) -> Unit)? = null) {
        bridgeStatus = BridgeStatus.LOADING
        bridgeRequest(ModuleConfigBridge.MODE_EXPORT, null) { code, data ->
            val json = data?.getString(ModuleConfigBridge.EXTRA_JSON)
            if (code == ModuleConfigBridge.RESULT_OK && !json.isNullOrEmpty()) {
                configFiles = runCatching { parseConfigFiles(json) }.getOrDefault(emptyMap())
                bridgeStatus = BridgeStatus.READY
                onDone?.invoke(json)
            } else {
                bridgeStatus = BridgeStatus.UNAVAILABLE
                onDone?.invoke(null)
            }
        }
    }

    private fun buildConfigJson(files: Map<String, ByteArray>): String {
        val root = JSONObject()
        root.put("format", "deekseep-config")
        root.put("version", 1)
        root.put("module", BuildConfig.VERSION_NAME)
        root.put("exported_at", System.currentTimeMillis())
        val array = JSONArray()
        files.forEach { (path, bytes) ->
            array.put(JSONObject().put("path", path).put("data", Base64.encodeToString(bytes, Base64.NO_WRAP)))
        }
        root.put("files", array)
        return root.toString(2)
    }

    /** Writes a single settings file back into DeepSeek through the existing import path. */
    private fun writeConfigValue(path: String, value: String, onDone: (Boolean) -> Unit) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        bridgeRequest(ModuleConfigBridge.MODE_IMPORT, buildConfigJson(mapOf(path to bytes))) { code, data ->
            if (code == ModuleConfigBridge.RESULT_OK) {
                configFiles = configFiles + (path to bytes)
                toast("已写入。请强制停止并重新打开 DeepSeek 后生效")
                onDone(true)
            } else {
                toast(bridgeFailureText(code, data))
                onDone(false)
            }
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
        loadConfig { json ->
            if (!zip) {
                if (json == null) {
                    busyMessage = null
                    toast(bridgeFailureText(RESULT_TIMEOUT, null))
                    return@loadConfig
                }
                writeInBackground(uri, json.toByteArray(StandardCharsets.UTF_8), "设置已导出（JSON）")
                return@loadConfig
            }
            busyMessage = "正在打包 ZIP…"
            Thread {
                val result = runCatching {
                    contentResolver.openOutputStream(uri, "wt")!!.use { out ->
                        ZipOutputStream(out.buffered()).use { zos -> writeZip(zos, json) }
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

    /** Returns the number of payload entries written. Runs off the main thread. */
    private fun writeZip(zos: ZipOutputStream, settingsJson: String?): Int {
        var count = 0
        val contents = JSONArray()
        fun put(name: String, bytes: ByteArray) {
            zos.putNextEntry(ZipEntry(name))
            zos.write(bytes)
            zos.closeEntry()
            contents.put(name)
            count++
        }
        if (settingsJson != null) put(ZIP_SETTINGS, settingsJson.toByteArray(StandardCharsets.UTF_8))
        listOf(
            File(filesDir.path) to ZIP_LAUNCHER_FILES,
            File(applicationInfo.dataDir, "shared_prefs") to ZIP_LAUNCHER_PREFS,
        ).forEach { (root, prefix) ->
            root.walkTopDown().filter { it.isFile && it.length() <= MAX_LAUNCHER_FILE }.forEach { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                runCatching { put(prefix + relative, file.readBytes()) }
            }
        }
        val meta = JSONObject()
            .put("format", "yunseek-backup")
            .put("version", 1)
            .put("app", APP_NAME)
            .put("app_version", BuildConfig.VERSION_NAME)
            .put("app_version_code", BuildConfig.VERSION_CODE)
            .put("deepseek", statusViewModel.state.value.host.label)
            .put("created_at", System.currentTimeMillis())
            .put("contains_module_settings", settingsJson != null)
            .put("contents", contents)
        zos.putNextEntry(ZipEntry(ZIP_META))
        zos.write(meta.toString(2).toByteArray(StandardCharsets.UTF_8))
        zos.closeEntry()
        zos.putNextEntry(ZipEntry("README.txt"))
        zos.write(
            ("YunSeek 模块数据备份\n\n" +
                "settings/deekseep-config.json：通过配置桥导出的模块设置（与旧版\u201c导出配置\u201d格式相同）。\n" +
                "launcher/：YunSeek 启动器自身的数据文件。\n\n" +
                "在新安装的 YunSeek 中选择\u201c导入恢复\u201d并选择此 ZIP 即可恢复。\n" +
                "提示词库、置顶与会话记录保存在 DeepSeek 私有目录，不在此备份中。\n")
                .toByteArray(StandardCharsets.UTF_8),
        )
        zos.closeEntry()
        return count
    }

    private fun readLimited(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > MAX_IMPORT_BYTES) throw IllegalArgumentException("文件过大")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private class ImportPlan(val settingsJson: String?, val launcherFiles: Map<String, ByteArray>)

    private fun importData(uri: Uri) {
        busyMessage = "正在读取备份…"
        Thread {
            val plan = runCatching {
                val bytes = contentResolver.openInputStream(uri)!!.use { readLimited(it) }
                val isZip = bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
                if (isZip) parseZip(bytes) else {
                    val text = String(bytes, StandardCharsets.UTF_8).trim().removePrefix("\uFEFF")
                    val root = JSONObject(JSONTokener(text))
                    require(root.optString("format") == "deekseep-config") { "不是有效的 YunSeek / Deekseep 配置文件" }
                    ImportPlan(text, emptyMap())
                }
            }
            main.post {
                plan.onSuccess { applyImport(it) }.onFailure {
                    busyMessage = null
                    toast("导入失败：${it.message}")
                }
            }
        }.start()
    }

    private fun parseZip(bytes: ByteArray): ImportPlan {
        var settings: String? = null
        val launcher = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name
                when {
                    name == ZIP_SETTINGS -> settings = String(readLimited(zis), StandardCharsets.UTF_8)
                    name.startsWith(ZIP_LAUNCHER_FILES) -> {
                        val data = readLimited(zis)
                        if (data.size <= MAX_LAUNCHER_FILE) launcher[name.removePrefix(ZIP_LAUNCHER_FILES)] = data
                    }
                }
            }
        }
        require(settings != null || launcher.isNotEmpty()) { "ZIP 中没有可恢复的 YunSeek 数据" }
        return ImportPlan(settings, launcher)
    }

    private fun applyImport(plan: ImportPlan) {
        var launcherRestored = 0
        val root = runCatching { filesDir.canonicalFile }.getOrNull()
        if (root != null) {
            plan.launcherFiles.forEach { (relative, data) ->
                runCatching {
                    val target = File(root, relative).canonicalFile
                    require(target.path.startsWith(root.path + File.separator))
                    target.parentFile?.mkdirs()
                    target.writeBytes(data)
                    launcherRestored++
                }
            }
        }
        val settings = plan.settingsJson
        if (settings == null) {
            busyMessage = null
            toast("已恢复启动器数据 $launcherRestored 项（备份中不含模块设置）")
            return
        }
        busyMessage = "正在写入 DeepSeek…"
        bridgeRequest(ModuleConfigBridge.MODE_IMPORT, settings) { code, data ->
            busyMessage = null
            if (code == ModuleConfigBridge.RESULT_OK) {
                runCatching { configFiles = parseConfigFiles(settings); bridgeStatus = BridgeStatus.READY }
                toast("导入完成。请强制停止并重新打开 DeepSeek 后生效")
            } else {
                toast(bridgeFailureText(code, data))
            }
        }
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
                        else -> LauncherCatalog.find(s)?.let { CategoryScreen(it) }
                            ?: HomeScreen(onOpen = { route = it })
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

    @Composable
    private fun ConfigKeyRow(key: ConfigKey, current: ByteArray?) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                if (key.kind == ConfigKind.FLAG) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(key.label, style = MaterialTheme.typography.titleSmall)
                            Text(key.path, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text(if (current != null) "已开启" else "未开启") },
                        )
                    }
                } else {
                    EditableValue(key, current)
                }
            }
        }
    }

    @Composable
    private fun EditableValue(key: ConfigKey, current: ByteArray?) {
        val original = remember(current) { current?.let { String(it, StandardCharsets.UTF_8) } ?: "" }
        var text by remember(original) { mutableStateOf(original) }
        var saving by remember { mutableStateOf(false) }
        var expanded by remember { mutableStateOf(key.kind == ConfigKind.VALUE) }
        val multiline = key.kind != ConfigKind.VALUE
        val error = remember(text, key.kind) { validate(key, text) }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(key.label, style = MaterialTheme.typography.titleSmall)
                Text(
                    if (current == null) "${key.path} · 未设置" else key.path,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (multiline) {
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起" else "编辑") }
            }
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = !multiline,
                    minLines = if (multiline) 4 else 1,
                    maxLines = if (multiline) 14 else 1,
                    textStyle = if (key.kind == ConfigKind.JSON) {
                        MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    } else MaterialTheme.typography.bodyMedium,
                    placeholder = { if (key.hint.isNotEmpty()) Text(key.hint) },
                    supportingText = { Text(error ?: key.hint) },
                    isError = error != null,
                    keyboardOptions = if (key.hint == "正整数") {
                        KeyboardOptions(keyboardType = KeyboardType.Number)
                    } else KeyboardOptions.Default,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        enabled = !saving && error == null && text != original,
                        onClick = {
                            saving = true
                            writeConfigValue(key.path, text) { saving = false }
                        },
                    ) { Text(if (saving) "写入中…" else "写入 DeepSeek") }
                    OutlinedButton(enabled = text != original, onClick = { text = original }) { Text("还原") }
                }
            }
        }
    }

    private fun validate(key: ConfigKey, text: String): String? = when {
        key.kind == ConfigKind.JSON && text.isNotBlank() -> runCatching {
            val value = JSONTokener(text).nextValue()
            require(value is JSONObject || value is JSONArray)
            null
        }.getOrElse { "JSON 格式错误" }
        key.hint == "正整数" && text.isNotBlank() && (text.trim().toIntOrNull()?.let { it <= 0 } != false) -> "请输入正整数"
        else -> null
    }



    @Suppress("DEPRECATION")
    private fun isFullBackupHostSupported(): Boolean = runCatching {
        val info = packageManager.getPackageInfo(TARGET_PACKAGE, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        code == 249L || code == 257L || code == 268L
    }.getOrDefault(false)

        }
