package com.dsmod.probe
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.nio.charset.StandardCharsets

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.dsmod.probe.ui.pressScale

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dsmod.probe.ui.CategoryCard
import com.dsmod.probe.ui.NoteCard
import com.dsmod.probe.ui.SectionHeader
import com.dsmod.probe.ui.staggeredEntrance

@Composable
internal fun HomeScreen(
    onOpen: (String) -> Unit,
    statusContent: @Composable () -> Unit,
) {
    PageColumn {
        statusContent()
        SectionHeader("功能", Modifier.staggeredEntrance(1))
        LauncherCatalog.categories.forEachIndexed { i, category ->
            CategoryCard(
                icon = category.icon,
                title = category.title,
                description = category.description,
                modifier = Modifier.staggeredEntrance(i + 2),
                onClick = { onOpen(category.id) },
            )
        }
        SectionHeader("数据", Modifier.staggeredEntrance(11))
        Row(
            modifier = Modifier.fillMaxWidth().staggeredEntrance(12),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionTile(Icons.Outlined.ArrowUpward, "导出模块数据", Modifier.weight(1f)) {
                onOpen(LauncherCatalog.DATA_ID)
            }
            ActionTile(Icons.Outlined.ArrowDownward, "导入恢复", Modifier.weight(1f)) {
                onOpen(LauncherCatalog.DATA_ID)
            }
        }
    }
}

@Composable
internal fun FeaturesScreen(
    onOpen: (String) -> Unit,
    onOpenDeepSeekSettings: () -> Unit,
) {
    PageColumn {
        NoteCard(
            "点按分类查看并修改对应设置。启动器通过配置桥读写 DeepSeek 内的模块设置，" +
                "修改后需强制停止并重新打开 DeepSeek 才会生效。",
            Modifier.staggeredEntrance(0),
            icon = Icons.Filled.Info,
        )
        LauncherCatalog.categories.forEachIndexed { i, category ->
            val detail = when {
                category.keys.isNotEmpty() -> "${category.description} · ${category.keys.size} 项设置"
                else -> category.description
            }
            CategoryCard(
                icon = category.icon,
                title = category.title,
                description = detail,
                modifier = Modifier.staggeredEntrance(i + 1),
                onClick = { onOpen(category.id) },
            )
        }
        OpenInDeepSeekButton(
            Modifier.staggeredEntrance(10),
            "打开 DeepSeek 模块设置",
            onOpenDeepSeekSettings,
        )
    }
}

@Composable
internal fun SettingsScreen(
    onOpen: (String) -> Unit,
    onOpenAbout: () -> Unit,
    onOpenDeepSeekSettings: () -> Unit,
    onRefreshStatus: () -> Unit,
    appVersionLine: String,
    repositoryUrl: String,
    onOpenUrl: (String) -> Unit,
) {
    PageColumn {
        SectionHeader("模块", Modifier.staggeredEntrance(0))
        CategoryCard(
            Icons.Outlined.OpenInNew,
            "打开 DeepSeek 模块设置",
            "启动 DeepSeek 并直接进入模块设置页",
            Modifier.staggeredEntrance(1),
            onOpenDeepSeekSettings,
        )
        CategoryCard(
            Icons.Filled.Refresh,
            "重新检测激活状态",
            "刷新激活状态与 DeepSeek 版本识别",
            Modifier.staggeredEntrance(2),
            onRefreshStatus,
        )
        SectionHeader("数据", Modifier.staggeredEntrance(3))
        CategoryCard(
            Icons.Outlined.ArrowUpward,
            "导出与导入恢复",
            "ZIP 全量 / JSON 仅设置，跨大版本重装前使用",
            Modifier.staggeredEntrance(4),
        ) { onOpen(LauncherCatalog.DATA_ID) }
        SectionHeader("关于", Modifier.staggeredEntrance(5))
        CategoryCard(
            Icons.Outlined.Gavel,
            "开源声明与关于",
            "GPL-3.0 · 原作者与维护者",
            Modifier.staggeredEntrance(6),
            onOpenAbout,
        )
        CategoryCard(
            Icons.Outlined.Code,
            "源代码",
            repositoryUrl,
            Modifier.staggeredEntrance(7),
        ) { onOpenUrl(repositoryUrl) }
        Text(
            appVersionLine + " · " + BuildInfo.BUILD_EDITION,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).staggeredEntrance(8),
        )
    }
}

@Composable
internal fun HelpScreen(
    onAbout: () -> Unit,
    onOpenDeepSeekSettings: () -> Unit,
) {
    val steps = listOf(
        "在 LSPosed 中启用 YunSeek，作用域勾选 DeepSeek（com.deepseek.chat）。",
        "强制停止并重新打开 DeepSeek，首页状态卡显示“模块已激活”即表示注入成功。",
        "完整的功能设置位于 DeepSeek 内的模块设置页，可在任意分类页点击“在 DeepSeek 中打开此设置”直接进入。",
        "在启动器中修改的设置需要重启 DeepSeek 后生效。",
        "跨大版本重装前，请先在“数据”中导出 ZIP，安装后再导入恢复。",
        "已适配：国内版 2.7.1 (287)、国内版 2.7.2 (289)、国际版 2.7.2 (290)。",
    )
    PageColumn {
        CategoryHeader(LauncherCatalog.find(LauncherCatalog.HELP_ID)!!, 0)
        SectionHeader("使用说明", Modifier.staggeredEntrance(1))
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            modifier = Modifier.fillMaxWidth().staggeredEntrance(2),
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                steps.forEachIndexed { i, step ->
                    Row {
                        Text(
                            "${i + 1}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(24.dp),
                        )
                        Text(step, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        OpenInDeepSeekButton(
            Modifier.staggeredEntrance(3),
            "打开 DeepSeek 模块设置",
            onOpenDeepSeekSettings,
        )
        CategoryCard(
            Icons.Outlined.Gavel,
            "开源声明与关于",
            "GPL-3.0 · 原作者与维护者",
            Modifier.staggeredEntrance(4),
            onAbout,
        )
    }
}

@Composable
internal fun AboutScreen(
    onLicense: () -> Unit,
    appName: String,
    appVersionLine: String,
    repositoryUrl: String,
    onOpenUrl: (String) -> Unit,
) {
    val rows = listOf(
        "应用" to appVersionLine,
        "版本类型" to BuildInfo.BUILD_EDITION,
        "构建时间" to BuildInfo.BUILD_DATE,
        "原作者" to "Lucian (lllucccian) · Deekseep",
        "YunSeek 维护者" to "张云安 (ZW-SYS)",
        "许可证" to "GNU GPL-3.0",
    )
    PageColumn {
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
            modifier = Modifier.fillMaxWidth().staggeredEntrance(0),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    appName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "适用于 DeepSeek 的 Xposed 模块，基于 Lucian 的开源项目 Deekseep 维护。" +
                        "本模块与 DeepSeek 官方无关。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                )
            }
        }
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            modifier = Modifier.fillMaxWidth().staggeredEntrance(1),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                rows.forEachIndexed { i, (key, value) ->
                    if (i > 0) HorizontalDivider(
                        Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            key,
                            modifier = Modifier.weight(0.38f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            value,
                            modifier = Modifier.weight(0.62f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        CategoryCard(
            Icons.Outlined.Gavel,
            "开源许可证",
            "查看 GNU GPL-3.0 许可证",
            Modifier.staggeredEntrance(2),
            onLicense,
        )
        CategoryCard(
            Icons.Outlined.Code,
            "源代码",
            repositoryUrl,
            Modifier.staggeredEntrance(3),
        ) { onOpenUrl(repositoryUrl) }
    }
}

    @Composable
    internal fun DataScreen(
        showFullBackup: Boolean,
        onExport: (zip: Boolean) -> Unit,
        onImport: () -> Unit,
        onFullBackup: () -> Unit,
        onFullRestore: () -> Unit,
    ) {
        var zip by rememberSaveable { mutableStateOf(true) }
        PageColumn {
            NoteCard(
                "用于跨大版本重装 YunSeek 或 DeepSeek：卸载前先导出，安装新版本并激活模块后再导入恢复。" +
                    "导出与导入都需要 DeepSeek 已打开过一次（模块已注入）。",
                Modifier.staggeredEntrance(0),
                icon = Icons.Filled.Info,
            )
            SectionHeader("导出模块数据", Modifier.staggeredEntrance(1))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().staggeredEntrance(2)) {
                SegmentedButton(
                    selected = zip,
                    onClick = { zip = true },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("ZIP 全量") }
                SegmentedButton(
                    selected = !zip,
                    onClick = { zip = false },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("JSON 仅设置") }
            }
            AnimatedContent(targetState = zip, label = "formatDesc",
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) }) { z ->
                Text(
                    if (z) "包含：配置桥可读取的全部模块设置（聊天、外观、Agent、心跳计划、远程开关覆盖、语言等）" +
                        "以及 YunSeek 启动器自身的数据文件。"
                    else "仅包含模块设置，与旧版\u201c导出配置\u201d的 JSON 格式完全兼容。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
            val exportInteraction = remember { MutableInteractionSource() }
            Button(
                onClick = { onExport(zip) },
                interactionSource = exportInteraction,
                modifier = Modifier.fillMaxWidth().height(52.dp).pressScale(exportInteraction).staggeredEntrance(3),
            ) {
                Icon(Icons.Outlined.ArrowUpward, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(if (zip) "导出 ZIP" else "导出 JSON")
            }
            SectionHeader("导入恢复", Modifier.staggeredEntrance(4))
            Text(
                "选择之前导出的 ZIP 或 JSON（也兼容旧版 Deekseep 导出的配置 JSON）。导入会覆盖同名设置。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp).staggeredEntrance(5),
            )
            val importInteraction = remember { MutableInteractionSource() }
            FilledTonalButton(
                onClick = onImport,
                interactionSource = importInteraction,
                modifier = Modifier.fillMaxWidth().height(52.dp).pressScale(importInteraction).staggeredEntrance(6),
            ) {
                Icon(Icons.Outlined.ArrowDownward, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("导入恢复")
            }
            NoteCard(
                "提示词库、置顶与会话记录保存在 DeepSeek 私有目录，启动器无法读取，不在此备份中；" +
                    "请在 DeepSeek 内的模块设置中处理。导入后需强制停止并重新打开 DeepSeek 才会生效。",
                Modifier.staggeredEntrance(7),
                icon = Icons.Filled.Lock,
            )
            if (showFullBackup) {
                SectionHeader("完整数据（需要 Root）", Modifier.staggeredEntrance(8))
                CategoryCard(Icons.Outlined.ArrowUpward, "一键备份全部数据",
                    "加密保存模块配置、DeepSeek 数据、聊天记录与日志", Modifier.staggeredEntrance(9)) {
                    onFullBackup()
                }
                CategoryCard(Icons.Outlined.ArrowDownward, "一键恢复全部数据",
                    "校验备份后覆盖当前私有数据", Modifier.staggeredEntrance(10)) {
                    onFullRestore()
                }
            }
        }
    }
internal enum class ConfigBridgeUiStatus { IDLE, LOADING, READY, UNAVAILABLE }

@Composable
    internal fun CategoryScreen(
    category: FeatureCategory,
    bridgeStatus: ConfigBridgeUiStatus,
    configFiles: Map<String, ByteArray>,
    onRefresh: () -> Unit,
    onWrite: (String, String, (Boolean) -> Unit) -> Unit,
    onOpenDeepSeekSettings: () -> Unit,
) {
        LaunchedEffect(category.id) {
            if (category.keys.isNotEmpty() && bridgeStatus != ConfigBridgeUiStatus.LOADING) onRefresh()
        }
        PageColumn {
            CategoryHeader(category, 0)
            OpenInDeepSeekButton(Modifier.staggeredEntrance(1), onClick = onOpenDeepSeekSettings)
            NoteCard(
                "在启动器中修改的设置会写入 DeepSeek 内的模块配置，需强制停止并重新打开 DeepSeek 后生效。",
                Modifier.staggeredEntrance(2),
                icon = Icons.Filled.Info,
            )
            if (category.note.isNotEmpty()) {
                NoteCard(category.note, Modifier.staggeredEntrance(3), icon = Icons.Filled.Lock)
            }
            if (category.keys.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().staggeredEntrance(3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionHeader("当前设置", Modifier.weight(1f))
                    TextButton(onClick = onRefresh, enabled = bridgeStatus != BridgeStatus.LOADING) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("刷新")
                    }
                }
                AnimatedContent(
                    targetState = bridgeStatus,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                    label = "bridge",
                ) { status ->
                    when (status) {
                        BridgeStatus.IDLE, BridgeStatus.LOADING -> Box(
                            Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                        BridgeStatus.UNAVAILABLE -> NoteCard(
                            "无法读取设置：DeepSeek 未响应。请先打开一次 DeepSeek（确认模块已在 LSPosed 中启用并勾选 DeepSeek），" +
                                "返回此页后点\u201c刷新\u201d。也可以直接点击上方按钮在 DeepSeek 中修改。",
                            icon = Icons.Filled.Info,
                        )
                        BridgeStatus.READY -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            val files = configFiles
                            category.keys.forEach { key -> ConfigKeyRow(key, files[key.path]) }
                            if (category.keys.any { it.kind == ConfigKind.FLAG }) {
                                NoteCard(
                                    "开关类设置在启动器中只读：配置桥只能写入文件、无法删除开关文件，" +
                                        "且部分开关需要在 DeepSeek 内确认风险提示。请在 DeepSeek 中切换。",
                                    icon = Icons.Filled.Lock,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
    private fun ConfigKeyRow(key: ConfigKey, current: ByteArray?, onWrite: (String, String, (Boolean) -> Unit) -> Unit) {
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
                    EditableValue(key, current, onWrite)
                }
            }
        }
    }

@Composable
    private fun EditableValue(key: ConfigKey, current: ByteArray?, onWrite: (String, String, (Boolean) -> Unit) -> Unit) {
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
                            onWrite(key.path, text) { saving = false }
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
