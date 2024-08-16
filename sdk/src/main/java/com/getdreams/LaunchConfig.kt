package com.getdreams

import java.util.Locale

/**
 * Collection of parameters to be passed to DES during launch procedure.
 * @param locale The end-user's preferred locale for i18n.
 * @param timezone The end-user's preferred timezone for datetime formatting.
 * @param theme Whether DES should be rendered in light or dark mode.
 */
data class LaunchConfig(
    val locale: Locale? = null,
    val timezone: String? = null,
    val theme: String? = null,
)
