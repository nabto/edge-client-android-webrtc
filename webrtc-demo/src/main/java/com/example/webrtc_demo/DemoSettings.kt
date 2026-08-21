package com.example.webrtc_demo

import android.content.Context
import android.content.SharedPreferences

/**
 * Connection settings for the demo, persisted in SharedPreferences and
 * editable from the Settings screen.
 */
object DemoSettings {
    private const val PREFS_NAME = "webrtc_demo_settings"
    private const val KEY_PRODUCT_ID = "product_id"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_SCT = "sct"

    const val DEFAULT_PRODUCT_ID = "pr-3cbjt7cj"
    const val DEFAULT_DEVICE_ID = "de-dmexphxx"
    const val DEFAULT_SCT = "demosct"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun productId(context: Context): String =
        prefs(context).getString(KEY_PRODUCT_ID, null)?.ifBlank { null } ?: DEFAULT_PRODUCT_ID

    fun deviceId(context: Context): String =
        prefs(context).getString(KEY_DEVICE_ID, null)?.ifBlank { null } ?: DEFAULT_DEVICE_ID

    fun sct(context: Context): String =
        prefs(context).getString(KEY_SCT, null)?.ifBlank { null } ?: DEFAULT_SCT

    fun save(context: Context, productId: String, deviceId: String, sct: String) {
        prefs(context).edit()
            .putString(KEY_PRODUCT_ID, productId.trim())
            .putString(KEY_DEVICE_ID, deviceId.trim())
            .putString(KEY_SCT, sct.trim())
            .apply()
    }
}
