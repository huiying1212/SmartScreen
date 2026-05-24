package com.datacollector.android.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralised scheduling. All periodic work is registered with KEEP policy
 * so repeated calls are idempotent.
 */
@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun scheduleAllPeriodic() {
        val wm = WorkManager.getInstance(context)

        wm.enqueueUniquePeriodicWork(
            "wallpaper",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<WallpaperWorker>(2, java.util.concurrent.TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
        )

        wm.enqueueUniquePeriodicWork(
            "weekly_report",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<WeeklyReportWorker>(7, java.util.concurrent.TimeUnit.DAYS)
                .setInitialDelay(initialDelayUntilSundayEvening())
                .build()
        )

        wm.enqueueUniquePeriodicWork(
            "goal_eval",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<GoalEvaluationWorker>(1, java.util.concurrent.TimeUnit.DAYS)
                .setInitialDelay(initialDelayUntil(22, 0))
                .build()
        )

        wm.enqueueUniquePeriodicWork(
            "data_cleanup",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DataCleanupWorker>(1, java.util.concurrent.TimeUnit.DAYS)
                .build()
        )
    }

    private fun initialDelayUntil(hour: Int, minute: Int): Duration {
        val now = LocalDateTime.now(ZoneId.systemDefault())
        var target = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return Duration.between(now, target)
    }

    private fun initialDelayUntilSundayEvening(): Duration {
        val now = LocalDateTime.now(ZoneId.systemDefault())
        var target = now.withHour(20).withMinute(0).withSecond(0).withNano(0)
        // DayOfWeek.SUNDAY.value == 7
        val daysUntilSunday = (7 - now.dayOfWeek.value) % 7
        target = target.plusDays(daysUntilSunday.toLong())
        if (!target.isAfter(now)) target = target.plusDays(7)
        return Duration.between(now, target)
    }
}
