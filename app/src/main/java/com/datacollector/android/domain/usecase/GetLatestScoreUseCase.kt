package com.datacollector.android.domain.usecase

import com.datacollector.android.data.repository.ScoreRepository
import com.datacollector.android.domain.model.ScorePoint
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetLatestScoreUseCase @Inject constructor(
    private val repo: ScoreRepository,
) {
    operator fun invoke(): Flow<ScorePoint?> = repo.observeLatest()
}
