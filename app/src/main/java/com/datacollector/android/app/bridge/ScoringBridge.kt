package com.datacollector.android.app.bridge

import com.datacollector.android.data.repository.ScoreRepository
import com.datacollector.android.processing.LLMScoringEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Listens to legacy [LLMScoringEngine] score updates and persists each one
 * to the Room `score_entry` table for trend / weekly-report queries.
 */
@Singleton
class ScoringBridge @Inject constructor(
    private val scoreRepo: ScoreRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun install() {
        LLMScoringEngine.setScoreObserver(object : LLMScoringEngine.ScoreObserver {
            override fun onScoreUpdated(newScore: Int, delta: Int, reason: String?) {
                scope.launch {
                    runCatching {
                        scoreRepo.record(newScore, delta, reason, snapshotFile = null)
                    }
                }
            }
        })
    }
}
