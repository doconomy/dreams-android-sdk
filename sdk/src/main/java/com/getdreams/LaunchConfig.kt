package com.getdreams

/**
 * Collection of extra data to be passed during launch procedure.
 * @param location The location that Dreams should navigate to on a successful launch.
 * @param theme Whether sdk should launch/initialize in light/dark mode.
 * @param timezone Timezone to provide to sdk during launch/initialize.
 */
data class LaunchConfig(
    val location: String? = null,
    val theme: String? = null,
    val timezone: String? = null,
)
