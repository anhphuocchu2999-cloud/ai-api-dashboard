package com.java.myapplication.discovery

object DashboardAiRequestPolicy {
    const val CONNECT_TIMEOUT_MS = 20_000
    const val READ_TIMEOUT_MS = 120_000
    const val MAX_PROMPT_CANDIDATES = 8
    const val MAX_PROMPT_BODY_CHARS = 4_000
    const val MAX_PROMPT_VISIBLE_TEXT_CHARS = 4_000

    fun <T> selectPromptCandidates(candidates: List<T>): List<T> {
        return candidates.take(MAX_PROMPT_CANDIDATES)
    }

    fun limitBody(value: String): String = value.take(MAX_PROMPT_BODY_CHARS)

    fun limitVisibleText(value: String): String = value.take(MAX_PROMPT_VISIBLE_TEXT_CHARS)
}
