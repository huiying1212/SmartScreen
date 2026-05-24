package com.datacollector.android.domain

import com.datacollector.android.domain.usecase.TimeWindow
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TimeWindowTest {
    @Test
    fun `start of day is before end of day`() {
        val s = TimeWindow.startOfDay()
        val e = TimeWindow.endOfDay()
        assertTrue(s < e)
        assertTrue(e - s in 23L * 3600 * 1000..25L * 3600 * 1000)
    }

    @Test
    fun `week window covers seven days`() {
        val today = LocalDate.of(2024, 1, 17) // a Wednesday
        val s = TimeWindow.startOfWeek(today)
        val e = TimeWindow.endOfWeek(today)
        val days = (e - s) / (24L * 3600 * 1000)
        assertTrue(days == 7L)
    }

    @Test
    fun `month window starts on day 1`() {
        val today = LocalDate.of(2024, 5, 15)
        val s = TimeWindow.startOfMonth(today)
        val e = TimeWindow.endOfMonth(today)
        assertTrue(s < e)
    }
}
