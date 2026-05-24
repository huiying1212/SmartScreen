package com.datacollector.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.datacollector.android.ui.components.MoodFace
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** font size (sp), line-height multiplier, letter-spacing (sp) */
private fun headlineSpec(length: Int): Triple<Int, Float, Float> = when {
    length <= 6   -> Triple(44, 1.18f, -0.4f)
    length <= 12  -> Triple(38, 1.20f, -0.3f)
    length <= 20  -> Triple(30, 1.22f, -0.2f)
    length <= 30  -> Triple(24, 1.25f, -0.1f)
    length <= 50  -> Triple(20, 1.30f, 0f)
    length <= 80  -> Triple(17, 1.35f, 0f)
    length <= 140 -> Triple(15, 1.40f, 0f)
    length <= 220 -> Triple(13, 1.45f, 0f)
    else          -> Triple(12, 1.50f, 0f)
}

private fun scorePhrase(score: Int) = when {
    score < 33 -> "状态不错\n继续保持"
    score < 66 -> "保持节奏\n稳步前行"
    else       -> "注意休息\n照顾好自己"
}

@Composable
fun HomeScreen(vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val dateStr = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("M 月 d 日"))
    }
    val headline = remember(state.latestReminder, state.score) {
        state.latestReminder
            ?.takeIf { it.isNotBlank() }
            ?.trim()
            ?: scorePhrase(state.score)
    }
    val (sizeSp, lineFactor, letterSp) = headlineSpec(headline.length)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "RI4SU",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "今日 ${state.score}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Box(
            modifier = Modifier.size(260.dp),
            contentAlignment = Alignment.Center,
        ) {
            MoodFace(
                score = state.score,
                style = state.faceStyle,
                sizeDp = 260,
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = dateStr,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = headline,
            fontSize = sizeSp.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            lineHeight = (sizeSp * lineFactor).sp,
            letterSpacing = letterSp.sp,
        )

        Spacer(Modifier.height(100.dp))
    }
}
