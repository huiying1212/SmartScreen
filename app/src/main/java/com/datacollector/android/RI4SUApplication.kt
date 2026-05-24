package com.datacollector.android

import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.datacollector.android.app.bridge.BubbleTextRecorder
import com.datacollector.android.app.bridge.ScoringBridge
import com.datacollector.android.app.crash.CrashLogger
import com.datacollector.android.data.config.DataStoreConfigMigrator
import com.datacollector.android.work.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. Initialises Hilt, WorkManager (Hilt-aware),
 * the local crash logger, and one-time SharedPreferences -> DataStore
 * migration on first launch after upgrade.
 */
@HiltAndroidApp
class RI4SUApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var configMigrator: DataStoreConfigMigrator
    @Inject lateinit var workScheduler: WorkScheduler
    @Inject lateinit var crashLogger: CrashLogger
    @Inject lateinit var scoringBridge: ScoringBridge
    @Inject lateinit var bubbleRecorder: BubbleTextRecorder

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        crashLogger.install()

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectLeakedRegistrationObjects()
                    .penaltyLog()
                    .build()
            )
        }

        scoringBridge.install()
        bubbleRecorder.install()
        configMigrator.migrateOnceIfNeeded()
        workScheduler.scheduleAllPeriodic()
    }
}
