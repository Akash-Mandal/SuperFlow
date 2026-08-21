package com.superflow.util

/**
 * Input limits enforced at the domain/capability layer so a paste, an AI
 * argument or a field without an XML `maxLength` cannot write unbounded text
 * into the database (#28). Limits are generous — they catch accidents and
 * abuse, never legitimate input.
 */
object Limits {
    const val TITLE = 100
    const val SHORT_TEXT = 200
    const val DESCRIPTION = 500
    const val NOTE = 1_000
    const val LONG_TEXT = 5_000

    /** Clamp [s] to [max] characters after trimming, unless it is an identity/goal statement where we preserve words. */
    fun title(s: String): String = s.trim().take(TITLE)
    fun shortText(s: String): String = s.trim().take(SHORT_TEXT)
    fun description(s: String): String = s.trim().take(DESCRIPTION)
    fun note(s: String): String = s.take(NOTE)
    fun longText(s: String): String = s.take(LONG_TEXT)
}
