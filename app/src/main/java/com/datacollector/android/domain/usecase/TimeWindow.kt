package com.datacollector.android.domain.usecase

import java.time.LocalDate
import java.time.ZoneId

/**
 * Helpers to compute UTC ms boundaries for "today" / "this week" / "this month"
 * in the device's local time zone.
 */
object TimeWindow {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun startOfDay(date: LocalDate = LocalDate.now()): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun endOfDay(date: LocalDate = LocalDate.now()): Long =
        date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun startOfWeek(date: LocalDate = LocalDate.now()): Long {
        val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
        return startOfDay(monday)
    }

    fun endOfWeek(date: LocalDate = LocalDate.now()): Long {
        val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
        return endOfDay(monday.plusDays(6))
    }

    fun startOfMonth(date: LocalDate = LocalDate.now()): Long =
        startOfDay(date.withDayOfMonth(1))

    fun endOfMonth(date: LocalDate = LocalDate.now()): Long {
        val last = date.withDayOfMonth(date.lengthOfMonth())
        return endOfDay(last)
    }

    fun daysAgo(days: Int): Long =
        startOfDay(LocalDate.now().minusDays(days.toLong()))
}
