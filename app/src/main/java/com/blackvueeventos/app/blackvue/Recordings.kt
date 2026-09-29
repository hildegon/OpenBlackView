package com.blackvueeventos.app.blackvue

import java.time.DateTimeException
import java.time.LocalDate

/**
 * BlackVue clip names look like `20260928_153045_EF.mp4`:
 * date, time, type letter, direction letter, optional L/S upload flag.
 *
 * Event-like types match blackvuesync `--include E,M,I,O,A,T,B`:
 * Event, Manual, Impact, Overspeed, Acceleration, Cornering, Braking.
 */
data class Recording(
    val filename: String,
    val dayFolder: String,
    val type: Char,
    val direction: Char,
) {
    val isEventLike: Boolean get() = type in EVENT_TYPES
    val isParking: Boolean get() = type == 'P'
}

data class CameraListing(
    val legacyApi: Boolean,
    val names: List<String>,
)

/** Usual address of the camera's own Wi‑Fi on current BlackVue firmware. Not a home LAN address. */
const val DEFAULT_CAMERA_HOST = "10.99.77.1"

val EVENT_TYPES: Set<Char> = setOf('E', 'M', 'I', 'O', 'A', 'T', 'B')

const val EVENTS_BUCKET = "eventos"
const val PARKING_BUCKET = "parking"

fun includeInSync(recording: Recording, downloadParking: Boolean): Boolean {
    return recording.isEventLike || (downloadParking && recording.isParking)
}

fun storageBucket(type: Char): String = if (type == 'P') PARKING_BUCKET else EVENTS_BUCKET

private val TYPE_LABELS = mapOf(
    'E' to "evento",
    'M' to "manual",
    'I' to "impacto",
    'O' to "exceso",
    'A' to "aceleracion",
    'T' to "curva",
    'B' to "frenada",
    'P' to "parking",
    'N' to "normal",
)

private val DIRECTION_LABELS = mapOf(
    'F' to "frente",
    'R' to "trasera",
    'I' to "interior",
    'O' to "opcional",
)

/**
 * Phone copy of a camera clip, e.g. `2026-09-29_18-45-03_evento_frente.mp4`.
 * The optional L/S flag stays on the end so those variants do not overwrite each other.
 * Names that do not match a BlackVue clip are returned unchanged.
 */
fun localFilename(cameraName: String): String {
    val base = cameraName.substringAfterLast('/').substringAfterLast('\\').trim()
    val match = NAME.matchEntire(base) ?: return base
    val date = "${match.groupValues[1]}-${match.groupValues[2]}-${match.groupValues[3]}"
    val time = "${match.groupValues[4]}-${match.groupValues[5]}-${match.groupValues[6]}"
    val typeChar = match.groupValues[7].uppercase()[0]
    val directionChar = match.groupValues[8].uppercase()[0]
    val type = TYPE_LABELS[typeChar] ?: typeChar.lowercase()
    val direction = DIRECTION_LABELS[directionChar] ?: directionChar.lowercase()
    val flag = match.groupValues[9].uppercase()
    val extra = if (flag.isEmpty()) "" else "_$flag"
    return "${date}_${time}_${type}_${direction}$extra.mp4"
}

/** True if this clip is already stored under the camera name or the readable local name. */
fun alreadyOnPhone(cameraName: String, present: Set<String>): Boolean {
    val base = cameraName.substringAfterLast('/').substringAfterLast('\\').trim()
    if (base in present) return true
    val local = localFilename(base)
    return local != base && local in present
}

private val DIRECTIONS: Set<Char> = setOf('F', 'R', 'I', 'O')

private val NAME = Regex(
    """^(\d{4})(\d{2})(\d{2})_(\d{2})(\d{2})(\d{2})_([A-Za-z])([A-Za-z])([LSls])?\.mp4$""",
)

private val LEGACY_LINE = Regex(
    """^n:(?:/Record/)?(\S+?\.mp4)""",
    RegexOption.IGNORE_CASE,
)

private val VOD_FILENAME = Regex(""""filename"\s*:\s*"([^"]+)"""")

fun parseRecordingName(raw: String): Recording? {
    val base = raw.substringAfterLast('/').substringAfterLast('\\').trim()
    val match = NAME.matchEntire(base) ?: return null
    val hour = match.groupValues[4].toInt()
    val minute = match.groupValues[5].toInt()
    val second = match.groupValues[6].toInt()
    if (hour !in 0..23 || minute !in 0..59 || second !in 0..59) return null
    val date = try {
        LocalDate.of(
            match.groupValues[1].toInt(),
            match.groupValues[2].toInt(),
            match.groupValues[3].toInt(),
        )
    } catch (_: DateTimeException) {
        return null
    }
    val direction = match.groupValues[8].uppercase()[0]
    if (direction !in DIRECTIONS) return null
    return Recording(
        filename = base,
        dayFolder = date.toString(),
        type = match.groupValues[7].uppercase()[0],
        direction = direction,
    )
}

/** Plain-text index from `GET /blackvue_vod.cgi` on pre-V1.009 firmware. */
fun parseLegacyIndex(body: String): List<String> {
    return body.lineSequence().mapNotNull { raw ->
        val match = LEGACY_LINE.find(raw.trim()) ?: return@mapNotNull null
        match.groupValues[1].substringAfterLast('/').substringAfterLast('\\')
    }.toList()
}

/** JSON index from `GET /vodList` on firmware V1.009+. */
fun parseVodList(body: String): List<String> {
    val normalized = body.replace("\\/", "/")
    return VOD_FILENAME.findAll(normalized).map { match ->
        match.groupValues[1].substringAfterLast('/').substringAfterLast('\\')
    }.filter { it.endsWith(".mp4", ignoreCase = true) }.toList()
}

fun normalizeCameraHost(raw: String): String {
    var host = raw.trim()
    if (host.isEmpty()) host = DEFAULT_CAMERA_HOST
    host = host.removePrefix("http://").removePrefix("https://").trim()
    val slash = host.indexOf('/')
    if (slash >= 0) host = host.substring(0, slash)
    return host.trim().ifEmpty { DEFAULT_CAMERA_HOST }
}

/** A file may be deleted on the camera only when the copy on the phone matches what was downloaded. */
fun eligibleForCameraDelete(bytesDownloaded: Long, bytesStored: Long): Boolean {
    return bytesDownloaded > 0L && bytesStored == bytesDownloaded
}

/** True when a fresh camera index no longer contains this recording. */
fun goneFromCameraIndex(names: List<String>, filename: String): Boolean {
    val base = filename.substringAfterLast('/').substringAfterLast('\\')
    return names.none { entry ->
        entry.substringAfterLast('/').substringAfterLast('\\').equals(base, ignoreCase = true)
    }
}

fun splitHostPort(host: String, defaultPort: Int): Pair<String, Int> {
    val idx = host.lastIndexOf(':')
    if (idx > 0 && host.indexOf(':') == idx) {
        val port = host.substring(idx + 1).toIntOrNull()
        if (port != null && port in 1..65535) {
            return host.substring(0, idx) to port
        }
    }
    return host to defaultPort
}
