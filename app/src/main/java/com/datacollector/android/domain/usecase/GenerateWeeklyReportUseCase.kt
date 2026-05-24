package com.datacollector.android.domain.usecase

import com.datacollector.android.data.repository.ContextSummaryRepository
import com.datacollector.android.data.repository.JournalRepository
import com.datacollector.android.data.repository.ReflectionRepository
import com.datacollector.android.data.repository.ScoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

/**
 * Aggregates a week's worth of scores, top apps, location bucket counts, and
 * journal dates and writes a heuristic reflective summary, persisting it as
 * a "weekly_report" reflection message. (The text is intentionally kept
 * heuristic so this can run offline; richer LLM summaries can be wired in
 * later by passing the data into DeepSeekApiClient.)
 */
class GenerateWeeklyReportUseCase @Inject constructor(
    private val scoreRepo: ScoreRepository,
    private val contextRepo: ContextSummaryRepository,
    private val journalRepo: JournalRepository,
    private val reflectionRepo: ReflectionRepository,
) {
    suspend operator fun invoke(today: LocalDate = LocalDate.now()): String? = withContext(Dispatchers.IO) {
        val start = TimeWindow.startOfWeek(today)
        val end = TimeWindow.endOfWeek(today)

        val scores = scoreRepo.rangeBetween(start, end)
        if (scores.isEmpty()) return@withContext null

        val avg = scores.map { it.score }.average()
        val high = scores.maxOf { it.score }
        val low = scores.minOf { it.score }
        val topApps = contextRepo.topAppsBetween(start, end, 3)
        val journalDates = journalRepo.distinctDates()
            .filter { d -> d >= today.minusDays(6).toString() }

        val sb = StringBuilder()
        sb.append("本周共记录 ${scores.size} 次评分，平均 ${"%.1f".format(avg)} 分，")
        sb.append("最高 $high / 最低 $low。")
        if (topApps.isNotEmpty()) {
            sb.append("最多使用：")
            topApps.take(3).joinTo(sb) { "${it.category ?: it.packageName}(${it.totalMinutes}min)" }
            sb.append("。")
        }
        sb.append("写下 ${journalDates.size} 天的反思记录。")
        sb.append(
            when {
                avg < 30 -> "整体克制有度，继续保持。"
                avg < 60 -> "状态平稳，可在高分时段多做停顿。"
                else -> "本周整体偏高，下周可尝试设一个 21:00 后不刷的小目标。"
            }
        )

        val text = sb.toString()
        reflectionRepo.record("weekly_report", text, scoreAtTime = avg.toInt())
        text
    }
}
