package com.datacollector.android.ui.reflect

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.datacollector.android.domain.model.Achievement
import com.datacollector.android.domain.model.Goal
import com.datacollector.android.domain.model.GoalTargetType
import com.datacollector.android.domain.model.JournalEntry
import com.datacollector.android.ui.components.AppCard
import com.datacollector.android.ui.components.EmptyState
import com.datacollector.android.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReflectScreen(
    vm: ReflectViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("日记", "目标", "成就")

    var journalEditor by remember { mutableStateOf<JournalEntry?>(null) }
    var showAddGoal by remember { mutableStateOf(false) }
    var showJournalEditor by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            when (tab) {
                0 -> ExtendedFloatingActionButton(
                    text = { Text("写日记") },
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = {
                        journalEditor = null
                        showJournalEditor = true
                    },
                    modifier = Modifier.padding(bottom = 84.dp),
                )
                1 -> ExtendedFloatingActionButton(
                    text = { Text("新建目标") },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = { showAddGoal = true },
                    modifier = Modifier.padding(bottom = 84.dp),
                )
                else -> {}
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                text = "反思",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            )
            SegmentedTabs(
                tabs = tabs,
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(4.dp))
            when (tab) {
                0 -> JournalList(
                    entries = state.journals,
                    onEdit = { entry ->
                        journalEditor = entry
                        showJournalEditor = true
                    },
                    onDelete = vm::deleteJournal,
                )
                1 -> GoalsList(
                    goals = state.goals,
                    todayChecked = state.todayCheckins.mapValues { it.value.achieved },
                    onToggle = vm::toggleCheckin,
                    onArchive = vm::archiveGoal,
                )
                2 -> AchievementsGrid(state.achievements)
            }
        }
    }

    if (showJournalEditor) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { showJournalEditor = false },
        ) {
            JournalEditor(
                initial = journalEditor,
                onSave = { text, mood ->
                    vm.saveJournalEntry(text, mood, journalEditor?.id)
                    showJournalEditor = false
                },
                onCancel = { showJournalEditor = false },
            )
        }
    }

    if (showAddGoal) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { showAddGoal = false },
        ) {
            AddGoalSheet(
                onCreate = { title, desc, type, value ->
                    vm.addGoal(title, desc, type, value)
                    showAddGoal = false
                },
                onCancel = { showAddGoal = false },
            )
        }
    }
}

// ── Segmented tab control ───────────────────────────────────────────────

@Composable
private fun SegmentedTabs(
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEachIndexed { i, label ->
            val isSelected = i == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.Transparent
                    )
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Journals ─────────────────────────────────────────────────────────────

@Composable
private fun JournalList(
    entries: List<JournalEntry>,
    onEdit: (JournalEntry) -> Unit,
    onDelete: (JournalEntry) -> Unit,
) {
    if (entries.isEmpty()) {
        EmptyState(title = "还没有日记", body = "点击右下角写下第一条反思。")
        return
    }
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(entries, key = { it.id }) { entry ->
            JournalCard(entry, onEdit = onEdit, onDelete = onDelete)
        }
    }
}

@Composable
private fun JournalCard(
    entry: JournalEntry,
    onEdit: (JournalEntry) -> Unit,
    onDelete: (JournalEntry) -> Unit,
) {
    AppCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.date,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!entry.mood.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "· ${entry.mood}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                var menu by remember { mutableStateOf(false) }
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        onClick = { menu = false; onEdit(entry) },
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        onClick = { menu = false; onDelete(entry) },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(text = entry.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun JournalEditor(
    initial: JournalEntry?,
    onSave: (text: String, mood: String?) -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember { mutableStateOf(initial?.text ?: "") }
    var mood by remember { mutableStateOf(initial?.mood ?: "") }
    Column(modifier = Modifier.padding(20.dp)) {
        Text(if (initial == null) "今日反思" else "编辑反思",
            style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            placeholder = { Text("今天发生了什么？此刻感受到什么？") },
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = mood,
            onValueChange = { mood = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("心情标签（可选，例：平静 / 烦躁）") },
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("取消")
            }
            Button(
                onClick = { if (text.isNotBlank()) onSave(text.trim(), mood.ifBlank { null }) },
                modifier = Modifier.weight(1f),
                enabled = text.isNotBlank(),
            ) {
                Text("保存")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ── Goals ────────────────────────────────────────────────────────────────

@Composable
private fun GoalsList(
    goals: List<Goal>,
    todayChecked: Map<Long, Boolean>,
    onToggle: (Goal) -> Unit,
    onArchive: (Goal) -> Unit,
) {
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (goals.isEmpty()) {
            item {
                EmptyState(title = "还没有目标",
                    body = "点击右下角新建一个小目标，慢慢调整使用习惯。")
            }
        }
        items(goals, key = { it.id }) { goal ->
            GoalCard(
                goal = goal,
                achieved = todayChecked[goal.id] == true,
                onToggle = { onToggle(goal) },
                onArchive = { onArchive(goal) },
            )
        }
    }
}

@Composable
private fun GoalCard(
    goal: Goal,
    achieved: Boolean,
    onToggle: () -> Unit,
    onArchive: () -> Unit,
) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (achieved) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primaryContainer
                    )
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                if (achieved) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "已完成",
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(goal.title, style = MaterialTheme.typography.titleMedium)
                if (!goal.description.isNullOrBlank()) {
                    Text(
                        goal.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onArchive) { Text("归档") }
        }
    }
}

@Composable
private fun AddGoalSheet(
    onCreate: (title: String, desc: String?, type: GoalTargetType, value: String?) -> Unit,
    onCancel: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(GoalTargetType.FREE_TEXT) }
    var typeMenu by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(20.dp)) {
        Text("新建目标", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("目标，例：21:00 后不刷短视频") },
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = desc,
            onValueChange = { desc = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("备注（可选）") },
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(8.dp))
        Box {
            OutlinedButton(onClick = { typeMenu = true }) {
                Text("类型：${typeLabel(type)}")
            }
            DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                GoalTargetType.values().forEach { t ->
                    DropdownMenuItem(
                        text = { Text(typeLabel(t)) },
                        onClick = { type = t; typeMenu = false },
                    )
                }
            }
        }
        if (type == GoalTargetType.APP_LIMIT || type == GoalTargetType.NIGHT_CURFEW) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        when (type) {
                            GoalTargetType.APP_LIMIT -> "限额（分钟）"
                            GoalTargetType.NIGHT_CURFEW -> "宵禁开始时间，例：21:00"
                            else -> ""
                        }
                    )
                },
                shape = RoundedCornerShape(12.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("取消") }
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onCreate(
                            title.trim(),
                            desc.ifBlank { null },
                            type,
                            value.ifBlank { null },
                        )
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = title.isNotBlank(),
            ) { Text("创建") }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun typeLabel(t: GoalTargetType): String = when (t) {
    GoalTargetType.FREE_TEXT -> "自由文本"
    GoalTargetType.APP_LIMIT -> "应用限额"
    GoalTargetType.NIGHT_CURFEW -> "晚间宵禁"
    GoalTargetType.DAILY_JOURNAL -> "每日日记"
}

// ── Achievements ─────────────────────────────────────────────────────────

@Composable
private fun AchievementsGrid(achievements: List<Achievement>) {
    if (achievements.isEmpty()) {
        EmptyState(title = "成就墙", body = "完成反思与目标，逐步解锁勋章。")
        return
    }
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(achievements, key = { it.code }) { a ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (a.isUnlocked)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            a.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.weight(1f))
                        if (a.isUnlocked) {
                            Text(
                                "已解锁",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        } else if (a.target > 1) {
                            Text(
                                "${a.progress}/${a.target}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        a.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (a.isUnlocked)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Suppress("unused") private fun ignored() = Color.Transparent
