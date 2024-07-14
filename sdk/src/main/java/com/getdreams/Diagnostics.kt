package com.getdreams

import okhttp3.Interceptor

/**
 * Collection of diagnostic setup.
 * @param interceptors Interceptors to set when optional headers are set and all internal WebView requests are re-sent using Okhttp client.
 */
data class Diagnostics(
    val interceptors: List<Interceptor>? = null,
)
