package com.datacollector.android.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.datacollector.android.data.local.AppDatabase
import com.datacollector.android.data.repository.GoalRepository
import com.datacollector.android.domain.model.GoalTargetType
import com.datacollector.android.domain.usecase.CheckInGoalUseCase
import com.datacollector.android.domain.usecase.GenerateWeeklyReportUseCase
import com.datacollector.android.domain.usecase.UnlockAchievementsUseCase
import com.datacollector.android.managers.WallpaperGenerationManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.LocalDate

/** Sunday-evening reflective summary. */
@HiltWorker
class WeeklyReportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val generate: GenerateWeeklyReportUseCase,
    private val unlockAchievements: UnlockAchievementsUseCase,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        generate(LocalDate.now())
        unlockAchievements.checkScoreAchievements()
        Result.success()
    } catch (t: Throwable) {
        android.util.Log.w("WeeklyReportWorker", "failed", t)
        Result.retry()
    }
}

/** Nightly worker that materialises a daily-journal goal placeholder check-in. */
@HiltWorker
class GoalEvaluationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val goalRepo: GoalRepository,
    private val checkIn: CheckInGoalUseCase,
    private val unlockAchievements: UnlockAchievementsUseCase,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        val today = LocalDate.now().toString()
        val goals = goalRepo.observeActive().first()
        goals.filter { it.targetType == GoalTargetType.DAILY_JOURNAL }
            .forEach { goal ->
                val existing = goalRepo.checkinsByDate(today).firstOrNull { it.goalId == goal.id }
                if (existing == null) checkIn(goal.id, achieved = false)
            }
        unlockAchievements.checkGoalAchievements()
        Result.success()
    } catch (t: Throwable) {
        android.util.Log.w("GoalEvalWorker", "failed", t)
        Result.retry()
    }
}

/** Wallpaper generation — replaces ad-hoc Thread in legacy WallpaperGenerationManager. */
@HiltWorker
class WallpaperWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val mgr = WallpaperGenerationManager(applicationContext)
        if (!mgr.shouldGenerate()) return Result.success()
        return try {
            suspendCancellableCoroutine<Result> { cont ->
                mgr.generateAndSetWallpaper(object :
                    WallpaperGenerationManager.WallpaperGenerationCallback {
                    override fun onSuccess(message: String?) {
                        if (cont.isActive) cont.resume(Result.success())
                    }
                    override fun onError(error: String?) {
                        if (cont.isActive) cont.resume(Result.retry())
                    }
                    override fun onProgress(status: String?) {}
                })
            }
        } catch (t: Throwable) {
            android.util.Log.w("WallpaperWorker", "failed", t)
            Result.retry()
        } finally {
            mgr.shutdown()
        }
    }
}

/** Periodic data cleanup — purges old DB rows. */
@HiltWorker
class DataCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val db: AppDatabase,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        val cutoff90 = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
        val cutoff14 = System.currentTimeMillis() - 14L * 24 * 60 * 60 * 1000
        db.scoreDao().deleteOlderThan(cutoff90)
        db.contextSummaryDao().deleteOlderThan(cutoff90)
        db.reflectionMessageDao().deleteOlderThan(cutoff90)
        db.wallpaperRecordDao().deleteOlderThan(cutoff14)
        Result.success()
    } catch (t: Throwable) {
        Result.retry()
    }
}
