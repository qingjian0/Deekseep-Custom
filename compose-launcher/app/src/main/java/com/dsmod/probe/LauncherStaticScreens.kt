package com.dsmod.probe

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
