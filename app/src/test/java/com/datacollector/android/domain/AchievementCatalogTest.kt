package com.datacollector.android.domain

import com.datacollector.android.data.repository.AchievementCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementCatalogTest {

    @Test
    fun `catalog has expected codes`() {
        val codes = AchievementCatalog.all.map { it.code }
        listOf(
            "FIRST_REFLECTION", "FIRST_JOURNAL", "STREAK_7_JOURNAL",
            "STREAK_30_JOURNAL", "FIRST_GOAL", "GOAL_HIT_7"
        ).forEach { c -> assertTrue("$c missing", codes.contains(c)) }
    }

    @Test
    fun `byCode resolves`() {
        assertNotNull(AchievementCatalog.byCode("FIRST_JOURNAL"))
    }

    @Test
    fun `streak achievements have target greater than one`() {
        val s = AchievementCatalog.byCode("STREAK_7_JOURNAL")!!
        assertEquals(7, s.target)
    }
}
