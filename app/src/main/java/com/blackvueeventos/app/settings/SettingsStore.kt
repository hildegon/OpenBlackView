package com.blackvueeventos.app.settings

import android.content.Context
import com.blackvueeventos.app.blackvue.DEFAULT_CAMERA_HOST

data class AppSettings(
    val cameraHost: String = DEFAULT_CAMERA_HOST,
    val downloadParking: Boolean = true,
    val downloadConcurrency: Int = 3,
)

fun clampConcurrency(value: Int): Int = value.coerceIn(1, 4)

/** Blank or missing values stay on the placeholder until the user saves their own IP. */
fun resolveSettings(
    cameraHost: String?,
    downloadParking: Boolean = true,
    downloadConcurrency: Int = 3,
): AppSettings {
    return AppSettings(
        cameraHost = cameraHost?.trim().orEmpty().ifBlank { DEFAULT_CAMERA_HOST },
        downloadParking = downloadParking,
        downloadConcurrency = clampConcurrency(downloadConcurrency),
    )
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun read(): AppSettings = resolveSettings(
        prefs.getString(KEY_CAMERA, null),
        prefs.getBoolean(KEY_PARKING, true),
        prefs.getInt(KEY_CONCURRENCY, 3),
    )

    fun write(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_CAMERA, settings.cameraHost.trim().ifBlank { DEFAULT_CAMERA_HOST })
            .putBoolean(KEY_PARKING, settings.downloadParking)
            .putInt(KEY_CONCURRENCY, clampConcurrency(settings.downloadConcurrency))
            .commit()
    }

    companion object {
        private const val FILE_NAME = "blackvue_camera"
        private const val KEY_CAMERA = "camera_host"
        private const val KEY_PARKING = "download_parking"
        private const val KEY_CONCURRENCY = "download_concurrency"
    }
}
