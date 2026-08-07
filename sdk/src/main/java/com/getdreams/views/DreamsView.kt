/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.getdreams.views

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import com.getdreams.Credentials
import com.getdreams.Dreams
import com.getdreams.LaunchConfig
import com.getdreams.R
import com.getdreams.Result
import com.getdreams.Result.Companion.failure
import com.getdreams.Result.Companion.success
import com.getdreams.connections.EventListener
import com.getdreams.connections.webview.LaunchError
import com.getdreams.connections.webview.RequestInterface.OnLaunchCompletion
import com.getdreams.connections.webview.ResponseInterface
import com.getdreams.events.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.net.HttpURLConnection
import java.net.HttpURLConnection.HTTP_BAD_REQUEST
import java.net.HttpURLConnection.HTTP_INTERNAL_ERROR
import java.net.HttpURLConnection.HTTP_MOVED_PERM
import java.net.HttpURLConnection.HTTP_MOVED_TEMP
import java.net.HttpURLConnection.HTTP_NOT_MODIFIED
import java.net.HttpURLConnection.HTTP_OK
import java.net.HttpURLConnection.HTTP_SEE_OTHER
import java.net.URL
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The main view to present Dreams to users.
 */
class DreamsView : FrameLayout, DreamsViewInterface {
    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        webView = WebView(context, attrs, defStyleAttr)
        init(context, attrs, defStyleAttr)
    }

    @Suppress("unused")
    constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0, defStyleRes: Int = 0
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        webView = WebView(context, attrs, defStyleAttr, defStyleRes)
        init(context, attrs, defStyleAttr, defStyleRes)
    }

    private val webView: WebView
    private val responseListeners = CopyOnWriteArrayList<EventListener>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            webView.goBack()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        findViewTreeOnBackPressedDispatcherOwner()
            ?.onBackPressedDispatcher
            ?.addCallback(backCallback)
        backCallback.isEnabled = webView.canGoBack()
    }

    /**
     * Add and setup the web view.
     */
    private fun init(context: Context, attrs: AttributeSet?, defStyleAttr: Int = 0, defStyleRes: Int = 0) {
        val attributes = context.obtainStyledAttributes(attrs, R.styleable.DreamsView, defStyleAttr, defStyleRes)
        val focusableInTouchMode = attributes.getBoolean(R.styleable.DreamsView_android_focusableInTouchMode, true)
        attributes.recycle()

        isFocusableInTouchMode = focusableInTouchMode
        webView.isFocusableInTouchMode = focusableInTouchMode

        addView(webView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ViewCompat.setWindowInsetsAnimationCallback(
                webView,
                object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
                    override fun onProgress(
                        insets: WindowInsetsCompat,
                        runningAnimations: List<WindowInsetsAnimationCompat>
                    ): WindowInsetsCompat {
                        val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
                        val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                        webView.setPadding(
                            0,
                            0,
                            0,
                            (imeInsets.bottom - navInsets.bottom).coerceAtLeast(0)
                        )
                        return insets
                    }
                }
            )
        }

        webView.apply {
            settings.apply {
                @SuppressLint("SetJavaScriptEnabled")
                javaScriptEnabled = true
                domStorageEnabled = true
                // Fix for CVE-2020-16873
                setSupportMultipleWindows(true)

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    val cache = File(context.cacheDir, "dreams")
                    try {
                        cache.mkdirs()
                        this.javaClass.getMethod("setAppCachePath", String::class.java).invoke(this, cache.absolutePath)
                        this.javaClass.getMethod("setAppCacheEnabled", Boolean::class.javaPrimitiveType).invoke(this, true)
                    } catch (e: SecurityException) {
                        Log.e("Dreams", "Failed to create directories in cache", e)
                    } catch (e: Exception) {
                        Log.w("Dreams", "setAppCache not available", e)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    offscreenPreRaster = true
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                return null
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                backCallback.isEnabled = view.canGoBack()
            }
        }

        webView.addJavascriptInterface(object : ResponseInterface {
            @JavascriptInterface
            override fun onIdTokenDidExpire(requestData: String) {
                try {
                    val json = JSONTokener(requestData).nextValue() as? JSONObject?
                    val requestId = json?.getString("requestId")
                    if (requestId != null) {
                        this@DreamsView.onResponse(Event.CredentialsExpired(requestId))
                    }
                } catch (e: JSONException) {
                    Log.w("Dreams", "Unable to parse request data", e)
                }
            }

            @JavascriptInterface
            override fun onTelemetryEvent(data: String) {
                try {
                    val json = JSONTokener(data).nextValue() as? JSONObject?
                    val name = json?.getString("name")
                    if (name != null) {
                        val event = Event.Telemetry(name = name, payload = json.optJSONObject("payload"))
                        Log.v("Dreams", "Got telemetry event: $event")
                        this@DreamsView.onResponse(event)
                    }
                } catch (e: JSONException) {
                    Log.w("Dreams", "Unable to parse telemetry", e)
                }
            }

            @JavascriptInterface
            override fun onAccountProvisionRequested(requestData: String) {
                try {
                    val json = JSONTokener(requestData).nextValue() as? JSONObject?
                    val requestId = json?.getString("requestId")
                    if (requestId != null) {
                        this@DreamsView.onResponse(Event.AccountProvisionRequested(requestId))
                    }
                } catch (e: JSONException) {
                    Log.w("Dreams", "Unable to parse request data", e)
                }
            }

            @JavascriptInterface
            override fun onAccountRequested(requestData: String) {
                try {
                    val json = JSONTokener(requestData).nextValue() as? JSONObject?
                    val dreamJson = json?.getJSONObject("dream")

                    val requestId = json?.getString("requestId")
                    if (requestId != null && dreamJson != null) {
                        this@DreamsView.onResponse(Event.AccountRequested(requestId, dreamJson))
                    }
                } catch (e: JSONException) {
                    Log.w("Dreams", "Unable to parse request data", e)
                }
            }

            @JavascriptInterface
            override fun onExitRequested() {
                this@DreamsView.onResponse(Event.ExitRequested)
            }

            @JavascriptInterface
            override fun onShare(data: String) {
                try {
                    val json = JSONTokener(data).nextValue() as? JSONObject?
                    val text = json?.getString("text")
                    val url = json?.optString("url")
                    val title = json?.optString("title")

                    if (text != null) {
                        Log.v("Dreams", "Got Share event")
                        openShareDialog(text, title, url)
                        this@DreamsView.onResponse(Event.Share)
                    }
                } catch (e: JSONException) {
                    Log.w("Dreams", "Unable to parse share json", e)
                }
            }
        }, "JSBridge")
    }

    /**
     * The response from the init call.
     */
    internal data class InitResponse(val url: String, val cookies: List<String>?)

    /**
     * Make the initial request to the web app.
     */
    private fun verifyTokenRequest(
        uri: Uri,
        jsonBody: JSONObject,
        headers: Map<String, String>?,
    ): Result<InitResponse, LaunchError> {
        val uriBuilder = uri.buildUpon()
            .appendPath("users")
            .appendPath("verify_token")

        val url = URL(uriBuilder.build().toString())
        val connection = try {
            url.openConnection() as? HttpURLConnection? ?: throw NullPointerException("Connection was null")
        } catch (e: Exception) {
            return failure(LaunchError.UnexpectedError("Unable to open connection", e))
        }

        connection.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; utf-8")
            setRequestProperty("Accept", "application/json")
            headers?.forEach {
                setRequestProperty(it.key, it.value)
            }
            doOutput = true
            doInput = false
            instanceFollowRedirects = false
        }

        return try {
            with(connection) {
                outputStream.write(jsonBody.toString().toByteArray())

                when (connection.responseCode) {
                    HTTP_MOVED_PERM, HTTP_MOVED_TEMP, HTTP_SEE_OTHER, HTTP_NOT_MODIFIED, 307, 308 -> {
                        getHeaderField("Location")?.let {
                            success(InitResponse(it, headerFields["Set-Cookie"]?.filterNotNull()))
                        } ?: failure(
                            LaunchError.UnexpectedError(
                                message = "Location header missing in $responseCode ($responseMessage)",
                                cause = RuntimeException("No location header in redirection response")
                            )
                        )
                    }

                    in 200..299 -> {
                        success(InitResponse(getURL().toString(), headerFields["Set-Cookie"]?.filterNotNull()))
                    }

                    422 -> {
                        failure(LaunchError.InvalidCredentials(message = "Invalid token", cause = null))
                    }

                    in 400..499, HTTP_INTERNAL_ERROR -> {
                        failure(
                            LaunchError.HttpError(
                                responseCode,
                                message = "Got message: $responseMessage",
                                cause = null
                            )
                        )
                    }

                    else -> failure(
                        LaunchError.HttpError(
                            responseCode,
                            message = "Got message: $responseMessage",
                            cause = null
                        )
                    )
                }
            }
        } catch (e: Exception) {
            failure(LaunchError.UnexpectedError(e.message ?: "Unknown error when trying to verify credentials", e))
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun initializeWebApp(
        credentials: Credentials,
        location: String?,
        launchConfig: LaunchConfig,
        headers: Map<String, String>?,
    ): Result<String, LaunchError> {
        val jsonBody = JSONObject()
            .put("client_id", Dreams.instance.clientId) // TODO: remove this legacy param?
            .put("token", credentials.idToken)

        if (location != null) {
            jsonBody.put("location", location)
        }
        if (launchConfig.locale != null) {
            jsonBody.put("locale", launchConfig.locale.toLanguageTag())
        }
        if (!launchConfig.theme.isNullOrEmpty()) {
            jsonBody.put("theme", launchConfig.theme)
        }
        if (!launchConfig.timezone.isNullOrEmpty()) {
            jsonBody.put("timezone", launchConfig.timezone)
        }

        val result = withContext(Dispatchers.IO) {
            verifyTokenRequest(
                Dreams.instance.baseUri,
                jsonBody,
                headers,
            )
        }
        return when (result) {
            is Result.Success -> {
                with(result.value) {
                    // If we got cookies, set them now
                    if (!cookies.isNullOrEmpty()) {
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookies.forEach { cookie ->
                            cookieManager.setCookie(url, cookie)
                        }
                    }
                    return@with success(url)
                }
            }

            is Result.Failure -> {
                failure(result.error)
            }
        }
    }

    private fun WebView.loadUrlWithOptionalHeaders(url: String, headers: Map<String, String>?) {
        when (headers) {
            null -> loadUrl(url)
            else -> loadUrl(url, headers)
        }
    }

    override fun launch(
        credentials: Credentials,
        location: String?,
        launchConfig: LaunchConfig,
        headers: Map<String, String>?,
        onCompletion: OnLaunchCompletion
    ) {
        scope.launch {
            when (val result =  initializeWebApp(credentials, location, launchConfig, headers)) {
                is Result.Success -> {
                    withContext(Dispatchers.Main) {
                        webView.loadUrlWithOptionalHeaders(result.value, headers)
                    }
                    onCompletion.onResult(success(Unit))
                }

                is Result.Failure -> {
                    onCompletion.onResult(failure(result.error))
                }
            }
        }
    }

    override fun updateHeaders(headers: Map<String, String>) {
        val jsonData = JSONObject()
        headers.forEach {
            jsonData.put(it.key, it.value)
        }
        scope.launch(Dispatchers.Main.immediate) {
            webView.evaluateJavascript("setAdditionalHeaders('${jsonData}')") {
                Log.v("Dreams", "setAdditionalHeaders returned $it")
            }
        }
    }

    override fun updateLocale(locale: Locale) {
        val jsonData: JSONObject = JSONObject()
            .put("locale", locale.toLanguageTag())
        scope.launch(Dispatchers.Main.immediate) {
            webView.evaluateJavascript("updateLocale('${jsonData}')") {
                Log.v("Dreams", "updateLocale returned $it")
            }
        }
    }

    override fun updateCredentials(requestId: String, credentials: Credentials) {
        val jsonData: JSONObject = JSONObject()
            .put("requestId", requestId)
            .put("idToken", credentials.idToken)
        scope.launch(Dispatchers.Main.immediate) {
            webView.evaluateJavascript("updateIdToken('${jsonData}')") {
                Log.v("Dreams", "updateIdToken returned $it")
            }
        }
    }

    override fun accountProvisionInitiated(requestId: String) {
        val jsonData: JSONObject = JSONObject()
            .put("requestId", requestId)
        scope.launch(Dispatchers.Main.immediate) {
            webView.evaluateJavascript("accountProvisionInitiated('${jsonData}')") {
                Log.v("Dreams", "accountProvisioned returned $it")
            }
        }
    }

    override fun accountRequestSucceeded(requestId: String) {
        val jsonData: JSONObject = JSONObject()
            .put("requestId", requestId)
        scope.launch(Dispatchers.Main.immediate) {
            webView.evaluateJavascript("accountRequestedSucceeded('${jsonData}')") {
            }
        }
    }

    override fun accountRequestFailed(requestId: String, reason: String) {
        val jsonData: JSONObject = JSONObject()
            .put("requestId", requestId)
            .put("reason", reason)
        scope.launch(Dispatchers.Main.immediate) {
            webView.evaluateJavascript("accountRequestedFailed('${jsonData}')") {
            }
        }
    }

    override fun navigateTo(location: String) {
        val jsonData: JSONObject = JSONObject()
            .put("location", location)
        scope.launch(Dispatchers.Main.immediate) {
            webView.evaluateJavascript("navigateTo('${jsonData}')") {
                Log.v("Dreams", "navigateTo returned $it")
            }
        }
    }

    fun openShareDialog(text: String, title: String?, url: String?) {
        val share = Intent.createChooser(Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(
                Intent.EXTRA_TEXT, if (url.isNullOrEmpty()) {
                    text
                } else {
                    "$text\n$url"
                }
            )

            if (title != null) {
                putExtra(Intent.EXTRA_TITLE, title)
            }

            type = "text/plain"

        }, null)
        context.startActivity(share)
    }

    override fun registerEventListener(listener: EventListener): Boolean {
        return responseListeners.add(listener)
    }

    override fun removeEventListener(listener: EventListener): Boolean {
        return responseListeners.remove(listener)
    }

    override fun clearEventListeners() {
        responseListeners.clear()
    }

    internal fun onResponse(type: Event) {
        responseListeners.forEach { it.onEvent(type) }
    }

    override fun onDetachedFromWindow() {
        backCallback.remove()
        scope.cancel()
        super.onDetachedFromWindow()
    }

    override fun canGoBack(): Boolean {
        return webView.canGoBack()
    }

    override fun goBack() {
        webView.goBack()
    }
}
