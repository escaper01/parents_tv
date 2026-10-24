package com.osama.parentstv.data

/**
 * One TV channel. The channel number shown on screen is its 1-based position in the list,
 * so reordering never produces duplicate or missing numbers.
 */
data class Channel(
    val id: String,
    val name: String,
    val group: String = "",
    val logo: String = "",
    /** Stream URLs in priority order; later ones are fallbacks when earlier ones fail. */
    val urls: List<String>,
    val enabled: Boolean = true,
)
