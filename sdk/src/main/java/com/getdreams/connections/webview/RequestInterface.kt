/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.getdreams.connections.webview

import android.util.Log
import com.getdreams.Credentials
import com.getdreams.LaunchConfig
import java.util.Locale
import com.getdreams.Result

/**
 * Interface for requests to the Dreams web app.
 */
interface RequestInterface {
    /**
     * Functional interface for receiving the result of a launch call.
     */
    fun interface OnLaunchCompletion {
        /**
         * Called with the result of the launch call.
         */
        fun onResult(result: Result<Unit, LaunchError>)
    }

    /**
     * Launch Dreams.
     *
     * @param credentials Credentials used to authenticate the user.
     * @param location Optional path to navigate to after successful authentication (if other than default).
     * @param launchConfig Optional launch configuration with end-user settings.
     * @param headers Optional HTTP headers to be sent with first request.
     * @param onCompletion Called when [launch] has completed.
     */
    fun launch(
        credentials: Credentials,
        location: String? = null,
        launchConfig: LaunchConfig = LaunchConfig(),
        headers: Map<String, String>? = null,
        onCompletion: OnLaunchCompletion = OnLaunchCompletion {
            if (it is Result.Failure) {
                Log.e("Dreams", "Failed to launch due to ${it.error.message}", it.error.cause)
            }
        }
    )

    /**
     * Set the locale used in Dreams.
     *
     * @param locale The new locale.
     */
    fun updateLocale(locale: Locale)

    /**
     * This method can be called at all times after the WebView is presented, the Request interface will send the headers with every request.
     * @param headers Set optional HTTP headers
     */
    fun updateHeaders(headers: Map<String, String>)

    /**
     * Update the id token.
     *
     * @param requestId The request id of the event that informed that the token was expired.
     * @param credentials The new credentials to use.
     */
    fun updateCredentials(requestId: String, credentials: Credentials)

    /**
     * Inform the web app that an account was provisioned.
     *
     * @param requestId The request id of the event that triggered the account provisioning.
     */
    fun accountProvisionInitiated(requestId: String)
    fun accountRequestSucceeded(requestId: String)
    fun accountRequestFailed(requestId: String, reason: String)

    /**
     * Instruct the web app to navigate to a [location].
     *
     * @param location Where to navigate to.
     */
    fun navigateTo(location: String)
}
