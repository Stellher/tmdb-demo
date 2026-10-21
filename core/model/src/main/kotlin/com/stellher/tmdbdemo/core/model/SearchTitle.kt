package com.stellher.tmdbdemo.core.model

data class SearchTitle(val name: String, val requestedSeason: Int?)

object TitleNormalizer {
    private val seasonSuffix = Regex(
        """(?:[\\s·._:：-]*)(?:第\\s*([0-9一二三四五六七八九十]+)\\s*季|S(?:eason)?\\s*0?([0-9]{1,2})|([0-9]{1,2})(?:st|nd|rd|th)\\s+Season)$""",
        RegexOption.IGNORE_CASE
    )

    fun parse(raw: String): SearchTitle {
        val cleaned = raw.trim().replace(Regex("""[\\u3000\\s]+"""), " ")
        val match = seasonSuffix.find(cleaned) ?: return SearchTitle(cleaned, null)
        val value = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }
        val season = value?.toIntOrNull() ?: chineseNumber(value.orEmpty())
        val title = cleaned.substring(0, match.range.first).trim().ifBlank { cleaned }
        return SearchTitle(title, season?.takeIf { it > 0 })
    }

    private fun chineseNumber(value: String): Int? {
        if (value.isBlank()) return null
        val chars = "一二三四五六七八九"
        if (value == "十") return 10
        val tens = if (value.startsWith("十")) 10 else if ('十' in value) {
            val prefix = value.substringBefore('十')
            val digit = prefix.singleOrNull()?.let { chars.indexOf(it) }?.takeIf { it >= 0 }?.plus(1)
            (digit ?: return null) * 10
        } else 0
        val onesText = if ('十' in value) value.substringAfter('十') else value
        if (onesText.isBlank()) return tens
        val one = onesText.singleOrNull()?.let { chars.indexOf(it) }?.takeIf { it >= 0 }?.plus(1) ?: return null
        return tens + one
    }
}
