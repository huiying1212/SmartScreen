package com.datacollector.android.ui.permissions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.datacollector.android.ui.components.AppCard

@Composable
fun DataPermissionsScreen(
    onBack: () -> Unit,
    vm: DataPermissionsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TopBar(title = "采集权限", onBack = onBack)

        SubsectionLabel("总开关")
        AppCard {
            ToggleRow(
                title = "采集服务",
                body = "关闭后停止所有后台采集",
                checked = state.ri4suEnabled,
                onChange = vm::setRi4suEnabled,
            )
        }

        SubsectionLabel("传感器与数据源")
        AppCard {
            Column {
                ToggleRow(
                    title = "位置",
                    body = "读取当前位置用于情境分析",
                    checked = state.locationEnabled,
                    onChange = vm::setLocationEnabled,
                )
                HorizontalDivider()
                ToggleRow(
                    title = "活动识别",
                    body = "检测步行、骑行等身体活动",
                    checked = state.activityEnabled,
                    onChange = vm::setActivityEnabled,
                )
                HorizontalDivider()
                ToggleRow(
                    title = "屏幕使用统计",
                    body = "统计各 App 使用时长",
                    checked = state.screenUsageEnabled,
                    onChange = vm::setScreenUsageEnabled,
                )
                HorizontalDivider()
                ToggleRow(
                    title = "日历",
                    body = "读取日程事件用于时间情境",
                    checked = state.calendarEnabled,
                    onChange = vm::setCalendarEnabled,
                )
                HorizontalDivider()
                ToggleRow(
                    title = "Wi-Fi",
                    body = "读取当前网络名称",
                    checked = state.wifiEnabled,
                    onChange = vm::setWifiEnabled,
                )
                HorizontalDivider()
                ToggleRow(
                    title = "蓝牙",
                    body = "扫描附近设备辅助情境识别",
                    checked = state.bluetoothEnabled,
                    onChange = vm::setBluetoothEnabled,
                )
                HorizontalDivider()
                ToggleRow(
                    title = "天气",
                    body = "获取当前天气数据",
                    checked = state.weatherEnabled,
                    onChange = vm::setWeatherEnabled,
                )
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun SubsectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
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
