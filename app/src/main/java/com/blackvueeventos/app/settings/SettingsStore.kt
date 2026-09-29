package com.blackvueeventos.app.settings

import android.content.Context
import com.blackvueeventos.app.blackvue.DEFAULT_CAMERA_HOST
import com.blackvueeventos.app.blackvue.SyncSelection

data class AppSettings(
    val cameraHost: String = DEFAULT_CAMERA_HOST,
    val downloadEvents: Boolean = true,
    val downloadParking: Boolean = true,
    val downloadNormal: Boolean = false,
    val downloadGeofence: Boolean = false,
    val downloadDriver: Boolean = false,
    /** Blank keeps internal storage `blackvue`. */
    val downloadRoot: String = "",
    val downloadConcurrency: Int = 3,
)

fun clampConcurrency(value: Int): Int = value.coerceIn(1, 4)

fun AppSettings.toSelection(): SyncSelection = SyncSelection(
    events = downloadEvents,
    parking = downloadParking,
    normal = downloadNormal,
    geofence = downloadGeofence,
    driver = downloadDriver,
)

/** Blank or missing values stay on the placeholder until the user saves their own IP. */
fun resolveSettings(
    cameraHost: String?,
    downloadEvents: Boolean = true,
    downloadParking: Boolean = true,
    downloadNormal: Boolean = false,
    downloadGeofence: Boolean = false,
    downloadDriver: Boolean = false,
    downloadRoot: String = "",
    downloadConcurrency: Int = 3,
): AppSettings {
    return AppSettings(
        cameraHost = cameraHost?.trim().orEmpty().ifBlank { DEFAULT_CAMERA_HOST },
        downloadEvents = downloadEvents,
        downloadParking = downloadParking,
        downloadNormal = downloadNormal,
        downloadGeofence = downloadGeofence,
        downloadDriver = downloadDriver,
        downloadRoot = downloadRoot.trim(),
        downloadConcurrency = clampConcurrency(downloadConcurrency),
    )
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun read(): AppSettings = resolveSettings(
        prefs.getString(KEY_CAMERA, null),
        prefs.getBoolean(KEY_EVENTS, true),
        prefs.getBoolean(KEY_PARKING, true),
        prefs.getBoolean(KEY_NORMAL, false),
        prefs.getBoolean(KEY_GEOFENCE, false),
        prefs.getBoolean(KEY_DRIVER, false),
        prefs.getString(KEY_ROOT, "").orEmpty(),
        prefs.getInt(KEY_CONCURRENCY, 3),
    )

    fun write(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_CAMERA, settings.cameraHost.trim().ifBlank { DEFAULT_CAMERA_HOST })
            .putBoolean(KEY_EVENTS, settings.downloadEvents)
            .putBoolean(KEY_PARKING, settings.downloadParking)
            .putBoolean(KEY_NORMAL, settings.downloadNormal)
            .putBoolean(KEY_GEOFENCE, settings.downloadGeofence)
            .putBoolean(KEY_DRIVER, settings.downloadDriver)
            .putString(KEY_ROOT, settings.downloadRoot.trim())
            .putInt(KEY_CONCURRENCY, clampConcurrency(settings.downloadConcurrency))
            .commit()
    }

    companion object {
        private const val FILE_NAME = "blackvue_camera"
        private const val KEY_CAMERA = "camera_host"
        private const val KEY_EVENTS = "download_events"
        private const val KEY_PARKING = "download_parking"
        private const val KEY_NORMAL = "download_normal"
        private const val KEY_GEOFENCE = "download_geofence"
        private const val KEY_DRIVER = "download_driver"
        private const val KEY_ROOT = "download_root"
        private const val KEY_CONCURRENCY = "download_concurrency"
    }
}
