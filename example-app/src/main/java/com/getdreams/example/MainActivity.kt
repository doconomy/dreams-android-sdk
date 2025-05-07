/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.getdreams.example

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.getdreams.Credentials
import com.getdreams.LaunchConfig
import com.getdreams.Result
import com.getdreams.connections.EventListener
import com.getdreams.connections.webview.LaunchError
import com.getdreams.connections.webview.RequestInterface
import com.getdreams.events.Event
import com.getdreams.views.DreamsView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val dreamsView: DreamsView
        get() = findViewById(R.id.dreams)

    /**
     * This is the main event listener.
     * In this example it handles all events from Dreams, but you could split this into several listeners if needed.
     */
    private val listener = EventListener { event ->
        when (event) {
            is Event.CredentialsExpired -> {
                // Renew the token
                GlobalScope.launch {
                    val credentials = Credentials(idToken = FakeBackend.refreshIdToken())
                    dreamsView.updateCredentials(requestId = event.requestId, credentials)
                }
            }

            is Event.Telemetry -> {
                // Convert from JSONObject to Map
                val params = event.payload?.keys()?.asSequence()?.associate { it to event.payload?.get(it) }
                // Pass the event on
                FakeBackend.sendAnalyticsEvent(event.name, params)
            }

            is Event.AccountProvisionRequested -> {
                // Provision an account to the user
                GlobalScope.launch {
                    if (FakeBackend.provisionAccountAsync(this@MainActivity).await()) {
                        // Tell Dreams the account was provisioned
                        dreamsView.accountProvisionInitiated(requestId = event.requestId)
                    } else {
                        // Something went wrong with provisioning the account
                        GlobalScope.launch(Dispatchers.Main) {
                            Toast.makeText(
                                this@MainActivity,
                                "Could not provision account",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }

            is Event.ExitRequested -> {
                // We should exit the Dreams context
                this@MainActivity.finish()
            }

            is Event.AccountRequested -> {
                // Handle account requested, perhaps by calling accountRequestSucceeded or accountRequestFailed
                Log.d("ExampleApp", "AccountRequested event received: $event")
                // For demonstration, let's assume it succeeded.
                // In a real app, you would have actual logic here.
                dreamsView.accountRequestSucceeded(event.requestId)
            }

            is Event.TransferConsentRequested -> {
                Log.d("ExampleApp", "TransferConsentRequested event received: requestId=${event.requestId}, consentId=${event.consentId}")
                // Example: Show a dialog to the user to ask for consent.
                // For this demo, we'll just log and simulate a success after a short delay.
                // In a real app, you would get user input here.
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Transfer Consent")
                    .setMessage("Do you consent to this transfer? (consentId: ${event.consentId})")
                    .setPositiveButton("Yes") { _, _ ->
                        Log.d("ExampleApp", "User consented. Calling dreamsView.transferConsentSucceeded(${event.requestId})")
                        dreamsView.transferConsentSucceeded(event.requestId)
                        Toast.makeText(this@MainActivity, "Transfer Consent Succeeded (sent to WebView)", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("No") { _, _ ->
                        Log.d("ExampleApp", "User cancelled. Calling dreamsView.transferConsentCancelled(${event.requestId})")
                        dreamsView.transferConsentCancelled(event.requestId)
                        Toast.makeText(this@MainActivity, "Transfer Consent Cancelled (sent to WebView)", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Show that we are loading content
        val dialog = AlertDialog.Builder(this)
            .setCancelable(false)
            .setMessage("Loading Dreams...")
            .show()

        val onLaunch = RequestInterface.OnLaunchCompletion { result ->
            when (result) {
                is Result.Success -> {
                    // Dreams was launched, dismiss loading indicator
                    Log.v("ExampleApp", "Launch was successful")
                    dialog.dismiss()
                }

                is Result.Failure -> {
                    // Something went wrong, handle the error
                    Log.w("ExampleApp", "Launch failed with ${result.error.message}", result.error.cause)

                    val message = when (val e = result.error) {
                        is LaunchError.InvalidCredentials -> e.message
                        is LaunchError.HttpError -> "HTTP Error ${e.responseCode}\n${e.message}"
                        is LaunchError.UnexpectedError -> e.message
                    }

                    GlobalScope.launch(Dispatchers.Main) {
                        dialog.setMessage(message)
                        dialog.setCancelable(true)
                    }
                }
            }
        }

        val location = intent?.data?.path
        dreamsView.launch(
            credentials = Credentials("token"),
            location = location?.takeIf { it.isNotBlank() },
            launchConfig = LaunchConfig(
                locale = Locale.ENGLISH,
                timezone = "myTimezone",
                theme = "myTheme",
            ),
            headers = mapOf(
                "someHeader" to "someValue"
            ),
            onCompletion = onLaunch
        )

        dreamsView.registerEventListener(listener)
    }

    override fun onDestroy() {
        // As we are about to be destroyed we no longer need to listen for events from Dreams.
        dreamsView.removeEventListener(listener)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return
        setIntent(intent)
        val location = intent.data?.path
        if (!location.isNullOrBlank()) {
            dreamsView.navigateTo(location)
        }
    }

    override fun onBackPressed() {
        if (dreamsView.canGoBack()) {
            dreamsView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
