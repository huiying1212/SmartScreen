package com.datacollector.android.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.datacollector.android.ui.components.AppCard
import com.datacollector.android.ui.components.SectionHeader

private val FACE_STYLES = listOf(
    "CLASSIC" to "经典", "WARM" to "暖阳", "COOL" to "清凉",
    "MONO" to "水墨", "MATCHA" to "抹茶", "SUNSET" to "落日",
)

private val WALLPAPER_STYLES = listOf(
    "唯美艺术", "水墨国风", "印象派", "极简主义", "自然风光", "赛博朋克",
)

@Composable
fun SettingsScreen(
    onNavigateToPermissions: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionHeader("功能开关")
        AppCard {
            Column {
                ToggleRow(
                    title = "反思表情",
                    body = "在桌面之上展示拟人表情",
                    checked = state.overlayEnabled,
                    onChange = vm::setOverlayEnabled,
                )
                HorizontalDivider()
                ToggleRow(
                    title = "反思壁纸",
                    body = "定时根据使用情境生成壁纸",
                    checked = state.wallpaperGenEnabled,
                    onChange = vm::setWallpaperGenEnabled,
                )
            }
        }
        AppCard(contentPadding = PaddingValues(0.dp)) {
            PermissionNavRow(
                title = "采集权限管理",
                body = "位置、活动、屏幕等数据采集权限",
                onClick = onNavigateToPermissions,
            )
        }

        SectionHeader("个性化")
        AppCard {
            Column {
                Text("表情风格", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                StyleChooser(
                    options = FACE_STYLES,
                    selected = state.faceStyle,
                    onSelected = vm::setFaceStyle,
                )
            }
        }
        AppCard {
            Column {
                Text("壁纸风格", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                StyleChooser(
                    options = WALLPAPER_STYLES.map { it to it },
                    selected = state.wallpaperStyle,
                    onSelected = vm::setWallpaperStyle,
                )
            }
        }

        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun ToggleRow(
    title: String,
    body: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PermissionNavRow(title: String, body: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StyleChooser(
    options: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (code, label) ->
            val isSelected = code == selected
            OutlinedButton(
                onClick = { onSelected(code) },
                colors = if (isSelected) {
                    androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else androidx.compose.material3.ButtonDefaults.outlinedButtonColors(),
            ) { Text(label) }
        }
    }
}
