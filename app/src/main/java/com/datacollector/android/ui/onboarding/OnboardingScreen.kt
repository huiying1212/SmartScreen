package com.datacollector.android.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.datacollector.android.ui.components.AppCard
import com.datacollector.android.ui.components.MoodFace
import com.datacollector.android.ui.components.SectionHeader

private val FACE_STYLES = listOf(
    "CLASSIC" to "经典",
    "WARM" to "暖阳",
    "COOL" to "清凉",
    "MONO" to "水墨",
    "MATCHA" to "抹茶",
    "SUNSET" to "落日",
)

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    vm: OnboardingViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var step by remember { mutableIntStateOf(0) }
    val total = 5

    Scaffold(
        modifier = Modifier
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            OnboardingNav(
                step = step,
                total = total,
                canAdvance = canAdvance(step, state),
                onBack = { if (step > 0) step-- },
                onNext = {
                    if (step == total - 1) vm.finish(onFinished) else step++
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            StepIndicator(step = step, total = total)
            Spacer(Modifier.height(24.dp))
            when (step) {
                0 -> WelcomeStep()
                1 -> PrivacyStep(
                    accepted = state.privacyAccepted,
                    onAcceptedChange = vm::setPrivacyAccepted,
                )
                2 -> PermissionStep()
                3 -> GoalAndStyleStep(
                    goal = state.personalGoal,
                    onGoalChange = vm::setPersonalGoal,
                    faceStyle = state.faceStyle,
                    onFaceStyleChange = vm::setFaceStyle,
                )
                4 -> FinishStep()
            }
        }
    }
}

private fun canAdvance(step: Int, s: OnboardingState): Boolean = when (step) {
    1 -> s.privacyAccepted
    else -> true
}

@Composable
private fun StepIndicator(step: Int, total: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        repeat(total) { i ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (i <= step) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
            )
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(20.dp))
        MoodFace(score = 35, sizeDp = 200)
        Spacer(Modifier.height(28.dp))
        Text(
            text = "欢迎来到 RI4SU",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "理解你的屏幕使用，而不是限制",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "RI4SU 通过对你的设备使用上下文进行温和的觉察，" +
                "用一个会变化的小表情和每日反思，帮你建立可持续的手机习惯。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PrivacyStep(accepted: Boolean, onAcceptedChange: (Boolean) -> Unit) {
    SectionHeader("隐私与数据", "请阅读后再继续")
    AppCard {
        Column {
            Text(
                "RI4SU 在本机收集你的设备使用上下文（位置、活动、屏幕使用、" +
                    "日历、Wi-Fi、蓝牙、天气）以驱动反思。所有原始数据保存在设备上的应用私有目录。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "为生成评分与反思文案，匿名化的上下文摘要会发送至 LLM 服务（DeepSeek / 通义千问）。" +
                    "你可以随时在设置中关闭联网调用。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = accepted, onCheckedChange = onAcceptedChange)
                Text(
                    "我理解并接受上述数据使用方式",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun PermissionStep() {
    SectionHeader("权限", "可以稍后再开启，但相关功能需要对应权限")
    val items = listOf(
        "通知" to "悬浮表情和反思提醒需要发送通知。",
        "位置" to "用于推断你正在通勤 / 居家 / 户外。",
        "使用情况访问" to "在系统设置 > 特殊应用访问中开启，用于记录屏幕时间。",
        "悬浮窗" to "把表情显示在桌面上方，让觉察更轻量。",
        "日历" to "结合日程给出更贴合你节奏的反思。",
        "蓝牙" to "判断是否在专注/外出场景，可选。",
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { (title, desc) ->
            AppCard(contentPadding = PaddingValues(14.dp)) {
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            "进入主界面后，会按需弹出系统权限申请。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GoalAndStyleStep(
    goal: String,
    onGoalChange: (String) -> Unit,
    faceStyle: String,
    onFaceStyleChange: (String) -> Unit,
) {
    SectionHeader("个人目标", "一句话写下你希望的状态，作为反思的方向")
    OutlinedTextField(
        value = goal,
        onValueChange = onGoalChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("例：晚上 21 点后不再刷短视频") },
        shape = RoundedCornerShape(12.dp),
    )
    Spacer(Modifier.height(20.dp))
    SectionHeader("表情风格", "影响主界面与悬浮窗的视觉色调")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(FACE_STYLES) { (code, label) ->
            FaceStyleOption(
                code = code,
                label = label,
                selected = faceStyle == code,
                onClick = { onFaceStyleChange(code) },
            )
        }
    }
}

@Composable
private fun FaceStyleOption(
    code: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val border = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(border.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            MoodFace(score = 30, style = code, sizeDp = 86)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun FinishStep() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Spacer(Modifier.height(40.dp))
        MoodFace(score = 20, sizeDp = 200)
        Spacer(Modifier.height(28.dp))
        Text(
            "准备就绪",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "点击「开始」即可启动数据采集与悬浮表情。\n你可以随时在「设置」中调整或重新引导。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OnboardingNav(
    step: Int,
    total: Int,
    canAdvance: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        OutlinedButton(onClick = onBack, enabled = step > 0) { Text("上一步") }
        Button(onClick = onNext, enabled = canAdvance) {
            Text(if (step == total - 1) "开始" else "下一步")
        }
    }
}

