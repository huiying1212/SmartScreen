package com.datacollector.android.domain

import com.datacollector.android.data.repository.GoalRepository
import com.datacollector.android.data.repository.JournalRepository
import com.datacollector.android.domain.usecase.ComputeStreakUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ComputeStreakUseCaseTest {

    private val journalRepo: JournalRepository = mockk()
    private val goalRepo: GoalRepository = mockk()
    private val uc = ComputeStreakUseCase(journalRepo, goalRepo)

    @Test
    fun `streak counts consecutive recent days`() = runTest {
        val today = LocalDate.of(2024, 6, 10)
        val dates = setOf(
            today.toString(),
            today.minusDays(1).toString(),
            today.minusDays(2).toString(),
        )
        coEvery { journalRepo.distinctDates() } returns dates.toList()
        coEvery { goalRepo.distinctAchievedDates() } returns emptyList()

        val s = uc(today)

        assertEquals(3, s.journalDays)
        assertEquals(0, s.goalCheckinDays)
    }

    @Test
    fun `streak tolerates today missing if yesterday present`() = runTest {
        val today = LocalDate.of(2024, 6, 10)
        val dates = setOf(
            today.minusDays(1).toString(),
            today.minusDays(2).toString(),
        )
        coEvery { journalRepo.distinctDates() } returns dates.toList()
        coEvery { goalRepo.distinctAchievedDates() } returns emptyList()

        val s = uc(today)

        assertEquals(2, s.journalDays)
    }

    @Test
    fun `streak is zero when no recent dates`() = runTest {
        val today = LocalDate.of(2024, 6, 10)
        coEvery { journalRepo.distinctDates() } returns
            listOf(today.minusDays(10).toString())
        coEvery { goalRepo.distinctAchievedDates() } returns emptyList()

        val s = uc(today)

        assertEquals(0, s.journalDays)
    }
}
