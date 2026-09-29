package com.blackvueeventos.app

/** Snapshot for the home progress bar. fraction < 0 means the total is still unknown. */
data class JobProgress(
    val phase: String,
    val file: String = "",
    val done: Int = 0,
    val total: Int = 0,
    val fraction: Float = -1f,
)

/**
 * File-count progress, plus the current file when its size is known.
 * Returns null while the list length is unknown.
 */
fun jobFraction(done: Int, total: Int, fileBytes: Long = 0L, fileSize: Long = -1L): Float? {
    if (total <= 0) return null
    val part = if (fileSize > 0L) (fileBytes.toDouble() / fileSize).coerceIn(0.0, 1.0) else 0.0
    return ((done + part) / total).toFloat().coerceIn(0f, 1f)
}

/** Completed files plus each in-flight file's own 0..1 fraction, over the same total. */
fun parallelFraction(done: Int, total: Int, inFlight: Iterable<Float>): Float? {
    if (total <= 0) return null
    var part = 0.0
    for (fraction in inFlight) part += fraction.toDouble().coerceIn(0.0, 1.0)
    return ((done + part) / total).toFloat().coerceIn(0f, 1f)
}
