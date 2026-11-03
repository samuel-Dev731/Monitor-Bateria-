package com.samueldev.monitorbateriapro

import android.content.Context
import android.os.Build
import android.os.BatteryManager
import android.content.Intent
import android.content.IntentFilter
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores one encrypted battery snapshot every four minutes.
 * Plaintext exists only in memory while creating/reading a snapshot.
 * The AES key remains in Android Keystore on this installation.
 */
internal object EncryptedBatteryHistory {
    private const val PREFS = "encrypted_battery_history"
    private const val DATA_KEY = "snapshots"
    private const val LAST_CAPTURE_KEY = "last_capture_at"
    private const val INTERVAL_MS = 4 * 60 * 1000L
    private const val MAX_RECORDS = 1000

    @Synchronized
    fun captureIfDue(context: Context, now: Long = System.currentTimeMillis()) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastCapture = prefs.getLong(LAST_CAPTURE_KEY, 0L)
        if (lastCapture > 0L && now - lastCapture < INTERVAL_MS) return

        val batteryIntent = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val rawLevel = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        if (rawLevel < 0) return
        val percent = (rawLevel * 100 / scale).coerceIn(0, 100)

        val snapshot = JSONObject()
            .put("device_model", Build.MODEL ?: "Dispositivo Android")
            .put("battery_percent", percent)
            .put("captured_at_epoch_ms", now)
            .toString()
            .toByteArray(Charsets.UTF_8)

        val previous = prefs.getString(DATA_KEY, null)
        val history = JSONArray()
        if (!previous.isNullOrBlank()) {
            try {
                val oldPlaintext = SecurePayloadCipher.decrypt(Base64.decode(previous, Base64.NO_WRAP))
                val oldHistory = JSONArray(String(oldPlaintext, Charsets.UTF_8))
                val start = (oldHistory.length() - (MAX_RECORDS - 1)).coerceAtLeast(0)
                for (index in start until oldHistory.length()) history.put(oldHistory.getJSONObject(index))
            } catch (_: Exception) {
                // Do not overwrite unreadable history; this avoids silent data loss.
                return
            }
        }
        history.put(JSONObject().put("encrypted", Base64.encodeToString(SecurePayloadCipher.encrypt(snapshot), Base64.NO_WRAP)))
        val encryptedHistory = SecurePayloadCipher.encrypt(history.toString().toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(DATA_KEY, Base64.encodeToString(encryptedHistory, Base64.NO_WRAP))
            .putLong(LAST_CAPTURE_KEY, now)
            .apply()
    }

    /** Returns the locally stored history for future in-app history UI. */
    @Synchronized
    fun readDecryptedHistory(context: Context): JSONArray {
        val encoded = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(DATA_KEY, null) ?: return JSONArray()
        val encrypted = Base64.decode(encoded, Base64.NO_WRAP)
        val plaintext = SecurePayloadCipher.decrypt(encrypted)
        val stored = JSONArray(String(plaintext, Charsets.UTF_8))
        val result = JSONArray()
        for (index in 0 until stored.length()) {
            val item = stored.getJSONObject(index)
            val snapshotBytes = SecurePayloadCipher.decrypt(Base64.decode(item.getString("encrypted"), Base64.NO_WRAP))
            result.put(JSONObject(String(snapshotBytes, Charsets.UTF_8)))
        }
        return result
    }
}
