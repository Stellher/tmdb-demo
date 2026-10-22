package com.stellher.tmdbdemo.core.model

/**
 * Text search on TMDB is not guaranteed to correct misspellings.
 * Use these candidates ONLY after an exact API search fails to find a close result.
 * Never auto-bind the returned TMDB ID on the basis of fuzzy search alone.
 */
object TitleMatcher {
    private val nonName = Regex("""[\s·._:：\-—《》「」『』（）()\[\]]""")
    private val commonCorrectedTitles = mapOf(
        "兰花如故" to "兰香如故"
    )

    fun normalize(text: String): String = text.trim().lowercase().replace(nonName, "")

    fun fallbackQueries(raw: String): List<String> {
        val normalized = normalize(raw)
        if (normalized.length < 3) return emptyList()
        val queries = mutableListOf<String>()
        commonCorrectedTitles[normalized]?.let(queries::add)
        // Suffix/prefix fragments are only discovery terms, not accepted matches.
        // This also helps other 4+ character titles with one incorrect character.
        val size = if (normalized.length >= 6) 3 else 2
        queries.add(normalized.takeLast(size))
        queries.add(normalized.take(size))
        return queries.filter { it != normalized }.distinct().take(3)
    }

    fun score(raw: String, candidate: String): Double {
        val a = normalize(raw)
        val b = normalize(candidate)
        if (a.isBlank() || b.isBlank()) return 0.0
        if (a == b) return 1.0
        // Legitimate substring search (e.g., "辉夜" -> "辉夜大小姐").
        if (a.length >= 2 && b.contains(a)) return 0.85
        val longest = maxOf(a.length, b.length)
        val shortest = minOf(a.length, b.length)
        if (shortest < 3 || longest - shortest > 2) return 0.0
        val distance = levenshtein(a, b)
        return 1.0 - distance.toDouble() / longest
    }

    fun isClose(raw: String, candidate: String): Boolean {
        val a = normalize(raw)
        val b = normalize(candidate)
        val distance = levenshtein(a, b)
        return a == b || (a.length >= 2 && b.contains(a)) ||
            (minOf(a.length, b.length) >= 4 &&
                distance <= (if (maxOf(a.length, b.length) >= 8) 2 else 1) &&
                score(a, b) >= 0.74)
    }

    private fun levenshtein(a: String, b: String): Int {
        var row = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val next = IntArray(b.length + 1)
            next[0] = i
            for (j in 1..b.length) {
                next[j] = minOf(
                    next[j - 1] + 1,
                    row[j] + 1,
                    row[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                )
            }
            row = next
        }
        return row[b.length]
    }
}
