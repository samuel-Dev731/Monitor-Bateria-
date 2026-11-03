package com.samueldev.monitorbateriapro

import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerResponse
import com.android.installreferrer.api.InstallReferrerStateListener

/**
 * Reads Google Play attribution once and stores it locally.
 * The API only returns Play Store referral data; sideloaded installs may have none.
 */
internal object InstallReferrerTracker {
    fun collectOnce(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("play_install_referrer", Context.MODE_PRIVATE)
        if (prefs.getBoolean("collection_complete", false)) return

        val client = InstallReferrerClient.newBuilder(appContext).build()
        try {
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    try {
                        if (responseCode == InstallReferrerResponse.OK) {
                            val details = client.installReferrer
                            prefs.edit()
                                .putString("referrer_url", details.installReferrer)
                                .putLong("referrer_click_time_seconds", details.referrerClickTimestampSeconds)
                                .putLong("install_begin_time_seconds", details.installBeginTimestampSeconds)
                                .putLong("referrer_click_time_server_seconds", details.referrerClickTimestampServerSeconds)
                                .putLong("install_begin_time_server_seconds", details.installBeginTimestampServerSeconds)
                                .putBoolean("instant_experience", details.googlePlayInstantParam)
                                .putBoolean("collection_complete", true)
                                .apply()
                        }
                    } catch (_: Exception) {
                        // Keep collection incomplete so a later app launch can retry.
                    } finally {
                        client.endConnection()
                    }
                }

                override fun onInstallReferrerServiceDisconnected() {
                    client.endConnection()
                }
            })
        } catch (_: Exception) {
            client.endConnection()
        }
    }
}
