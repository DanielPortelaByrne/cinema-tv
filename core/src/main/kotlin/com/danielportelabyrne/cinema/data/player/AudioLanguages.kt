package com.danielportelabyrne.cinema.data.player

/** Which soundtrack languages the player asks for, so a multi-language file never falls back to its first or "default" track. */
object AudioLanguages {
    private val THREE_TO_TWO = mapOf("eng" to "en", "ita" to "it", "fra" to "fr", "fre" to "fr", "deu" to "de", "ger" to "de", "spa" to "es", "por" to "pt")

    /** One language code from a tag such as "en", "eng", "en-US" or "en_GB"; null for blank or "und". */
    fun normalize(tag: String?): String? {
        val primary = tag?.trim()?.lowercase()?.split('-', '_')?.firstOrNull().orEmpty()
        if (primary.isBlank() || primary == "und") return null
        return THREE_TO_TWO[primary] ?: primary
    }

    /** The device's languages in order, then English as the established fallback, without repeats. */
    fun preferred(deviceTags: List<String>): List<String> = (deviceTags.mapNotNull { normalize(it) } + "en").distinct()
}
