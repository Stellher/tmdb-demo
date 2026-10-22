package com.stellher.tmdbdemo.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleMatcherTest {
    @Test fun typoFindsCorrectTitle() {
        assertTrue(TitleMatcher.isClose("兰花如故", "兰香如故"))
        assertTrue(TitleMatcher.fallbackQueries("兰花如故").contains("兰香如故"))
        assertTrue(TitleMatcher.fallbackQueries("兰花如故").contains("如故"))
    }

    @Test fun unrelatedTitlesAreRejected() {
        assertFalse(TitleMatcher.isClose("兰花如故", "周生如故"))
        assertFalse(TitleMatcher.isClose("兰花如故", "三体"))
        assertTrue(TitleMatcher.isClose("辉夜", "辉夜大小姐想让我告白"))
        assertEquals(1.0, TitleMatcher.score(" 兰香如故 ", "兰香如故"), 0.00001)
    }

    @Test fun shortBroadTitlesDoNotIssueFallbackSearches() {
        assertTrue(TitleMatcher.fallbackQueries("花").isEmpty())
        assertTrue(TitleMatcher.fallbackQueries("如故").isEmpty())
    }
}
