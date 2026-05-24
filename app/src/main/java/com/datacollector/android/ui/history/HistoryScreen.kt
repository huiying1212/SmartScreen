package com.datacollector.android.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.datacollector.android.ui.components.AppCard
import com.datacollector.android.ui.components.MiniLineChart
import com.datacollector.android.ui.components.MoodFace
import com.datacollector.android.ui.components.SectionHeader
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(vm: HistoryViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val monthDay = remember { DateTimeFormatter.ofPattern("M/d") }
    val trendValues = remember(state.todayPoints) {
        state.todayPoints.sortedBy { it.ts }.map { it.score }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader("历史记录")

        AppCard {
            Column {
                Text(
                    text = "今日趋势",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                MiniLineChart(values = trendValues, heightDp = 90)
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(state.days, key = { it.date.toEpochDay() }) { day ->
                DayCell(
                    label = if (day.date == today) "今天" else monthDay.format(day.date),
                    score = day.score,
                    faceStyle = state.faceStyle,
                )
            }
        }

        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun DayCell(label: String, score: Int?, faceStyle: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (score != null) {
            MoodFace(score = score, style = faceStyle, sizeDp = 64)
        } else {
            Spacer(Modifier.height(64.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
