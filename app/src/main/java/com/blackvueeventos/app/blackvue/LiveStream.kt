package com.blackvueeventos.app.blackvue

import com.blackvueeventos.app.net.CAMERA_UNREACHABLE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

const val REAR_UNAVAILABLE = "No llega la cámara trasera."

/** Front is `/blackvue_live.cgi`. Rear adds `direction=R`. */
fun liveStreamUrl(rawHost: String, rear: Boolean): String {
    val host = normalizeCameraHost(rawHost)
    val (name, port) = splitHostPort(host, 80)
    val url = HttpUrl.Builder()
        .scheme("http")
        .host(name)
        .port(port)
        .addPathSegment("blackvue_live.cgi")
    if (rear) url.addQueryParameter("direction", "R")
    return url.build().toString()
}

/**
 * Pulls complete JPEG frames out of a BlackVue MJPEG body.
 * A marker split across reads stays in the buffer until the next chunk.
 */
class JpegAssembler {
    private var pending = ByteArray(0)

    fun push(chunk: ByteArray, length: Int = chunk.size): List<ByteArray> {
        if (length <= 0) return emptyList()
        val combined = ByteArray(pending.size + length)
        pending.copyInto(combined)
        chunk.copyInto(combined, destinationOffset = pending.size, startIndex = 0, endIndex = length)
        val frames = ArrayList<ByteArray>()
        var index = 0
        var start = -1
        while (index < combined.size - 1) {
            val marker = combined[index] == MARKER
            val next = combined[index + 1]
            if (marker && next == SOI) {
                start = index
                index += 2
                continue
            }
            if (start >= 0 && marker && next == EOI) {
                frames += combined.copyOfRange(start, index + 2)
                start = -1
                index += 2
                continue
            }
            index++
        }
        val keep = when {
            start >= 0 -> start
            combined.isNotEmpty() && combined.last() == MARKER -> combined.size - 1
            else -> combined.size
        }
        pending = if (keep < combined.size) combined.copyOfRange(keep, combined.size) else ByteArray(0)
        if (pending.size > MAX_PENDING) pending = ByteArray(0)
        return frames
    }

    private companion object {
        const val MARKER = 0xFF.toByte()
        const val SOI = 0xD8.toByte()
        const val EOI = 0xD9.toByte()
        const val MAX_PENDING = 2 * 1024 * 1024
    }
}

private val liveHttp: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(12, TimeUnit.SECONDS)
    .writeTimeout(8, TimeUnit.SECONDS)
    .callTimeout(0, TimeUnit.SECONDS)
    .retryOnConnectionFailure(false)
    .build()

/**
 * Reads the camera MJPEG until the caller cancels.
 * Cancellation closes the socket so a blocked read does not keep the Wi-Fi busy.
 */
suspend fun streamLive(
    rawHost: String,
    rear: Boolean,
    onFrame: suspend (ByteArray) -> Unit,
) {
    val request = try {
        Request.Builder()
            .url(liveStreamUrl(rawHost, rear))
            .header("User-Agent", "BlackvueEventos/1.0")
            .header("Accept", "multipart/x-mixed-replace")
            .build()
    } catch (_: IllegalArgumentException) {
        throw CameraHttpException("La IP de la cámara no es válida. Ejemplo: 10.99.77.1")
    }
    val call = liveHttp.newCall(request)
    coroutineContext[Job]?.invokeOnCompletion { call.cancel() }
    var frames = 0
    try {
        val response = call.execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw CameraHttpException(
                if (rear) REAR_UNAVAILABLE else "La cámara respondió HTTP $code al abrir el vídeo en vivo.",
            )
        }
        response.use { open ->
            val body = open.body
                ?: throw CameraHttpException(
                    if (rear) REAR_UNAVAILABLE else "La cámara envió una respuesta vacía.",
                )
            val input = body.byteStream()
            val assembler = JpegAssembler()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                coroutineContext.ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                for (jpeg in assembler.push(buffer, read)) {
                    frames++
                    onFrame(jpeg)
                }
            }
        }
        if (frames == 0) {
            throw CameraHttpException(
                if (rear) REAR_UNAVAILABLE else "La cámara no envió imagen.",
            )
        }
    } catch (error: CameraHttpException) {
        throw error
    } catch (error: CancellationException) {
        throw error
    } catch (error: IOException) {
        if (!coroutineContext.isActive || call.isCanceled()) throw CancellationException()
        if (frames == 0) throw CameraUnreachable(CAMERA_UNREACHABLE, error)
    }
}
