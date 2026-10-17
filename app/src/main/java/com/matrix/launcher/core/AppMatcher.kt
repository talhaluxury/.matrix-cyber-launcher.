package com.matrix.launcher.core

data class AppRef(val key: String, val label: String)

sealed class MatchResult {
    object None : MatchResult()
    data class Single(val app: AppRef) : MatchResult()
    data class Multiple(val apps: List<AppRef>) : MatchResult()
}

object AppMatcher {
    fun match(query: String, apps: List<AppRef>): MatchResult {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return MatchResult.None
        val exact = apps.filter { it.label.lowercase() == q }
        if (exact.size == 1) return MatchResult.Single(exact[0])
        if (exact.size > 1) return MatchResult.Multiple(exact)
        val starts = apps.filter { it.label.lowercase().startsWith(q) }
        if (starts.size == 1) return MatchResult.Single(starts[0])
        val contains = apps.filter { it.label.lowercase().contains(q) }
        return when {
            contains.isEmpty() -> MatchResult.None
            contains.size == 1 -> MatchResult.Single(contains[0])
            starts.size > 1 -> MatchResult.Multiple(starts)
            else -> MatchResult.Multiple(contains)
        }
    }
}
