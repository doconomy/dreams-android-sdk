/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.getdreams.example

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object FakeBackend {
    /**
     * Pretend to do a async refresh of a token
     */
    suspend fun refreshIdToken(): String {
        delay(250)
        return "a fresh token"
    }

    /**
     * Pretend to send a event
     */
    fun sendAnalyticsEvent(name: String, params: Map<String, Any?>?) {
        Log.v("FakeBackend", "Got event ${name}: $params")
    }

    /**
     * Pretend to provision an account. Returns true if provisioned, false if cancelled.
     */
    suspend fun provisionAccountAsync(context: Context): Boolean = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            AlertDialog.Builder(context)
                .setMessage("Provision Account?")
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    continuation.resume(true)
                }
                .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                    dialog.cancel()
                    continuation.resume(false)
                }
                .show()
        }
    }
}
