package com.stellher.tmdbdemo.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleNormalizerTest {
    @Test fun stripsChineseSeason() {
        assertEquals(SearchTitle("辉夜大小姐", 2), TitleNormalizer.parse("辉夜大小姐 第二季"))
        assertEquals(SearchTitle("辉夜大小姐", 3), TitleNormalizer.parse("辉夜大小姐 第3季"))
    }

    @Test fun stripsWesternNotation() {
        assertEquals(SearchTitle("Kaguya-sama", 2), TitleNormalizer.parse("Kaguya-sama S02"))
        assertEquals(SearchTitle("Kaguya-sama", 3), TitleNormalizer.parse("Kaguya-sama 3rd Season"))
    }

    @Test fun keepsBareTitle() {
        assertEquals(SearchTitle("辉夜", null), TitleNormalizer.parse("辉夜"))
        assertEquals(SearchTitle("凡人修仙传", 11), TitleNormalizer.parse("凡人修仙传 第十一季"))
    }
}
