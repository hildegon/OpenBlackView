package com.blackvueeventos.app.blackvue

import com.blackvueeventos.app.net.CAMERA_UNREACHABLE
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.OutputStream
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class CameraHttpException(message: String) : IOException(message)

class CameraUnreachable(message: String, cause: Throwable) : IOException(message, cause)

/** Result of asking the camera to remove one recording. Success is only [Removed]. */
enum class CameraDelete { Removed, StillThere, NotSupported }

/**
 * Talks to the dashcam's local HTTP server.
 *
 * Older firmware (DR590X and anything before V1.009) lists files at
 * `/blackvue_vod.cgi` and serves them from `/Record/<file>`.
 * V1.009+ answers `GET /accessible` with 200, lists JSON at `/vodList`,
 * and serves the mp4 from the server root. Same split as blackvuesync.
 */
class BlackvueClient(
    private val http: OkHttpClient = defaultClient(),
) {
    private val probeHttp: OkHttpClient = http.newBuilder()
        .callTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val deleteHttp: OkHttpClient = http.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private var skipVodDelete = false
    private var skipLegacyDelete = false

    fun list(rawHost: String): CameraListing {
        val (host, name, port) = endpoint(rawHost)
        val accessible = url(name, port, "accessible")
        val vodList = url(name, port, "vodList")
        val legacyUrl = url(name, port, "blackvue_vod.cgi")

        var hardFailure: IOException? = null
        val probe = try {
            get(probeHttp, accessible)
        } catch (error: IOException) {
            if (isHardUnreachable(error)) hardFailure = error
            null
        }

        if (probe != null && probe.code == 200) {
            val vod = try {
                get(http, vodList)
            } catch (error: IOException) {
                throw unreachable(error)
            }
            if (vod.code == 200) {
                val names = parseVodList(vod.body)
                if (names.isNotEmpty() || vod.body.contains("\"filelist\"")) {
                    return CameraListing(legacyApi = false, names = names)
                }
            }
        }

        if (hardFailure != null && isHardUnreachable(hardFailure)) {
            throw unreachable(hardFailure)
        }

        val legacy = try {
            get(http, legacyUrl)
        } catch (error: IOException) {
            throw unreachable(hardFailure ?: error)
        }
        if (legacy.code != 200) {
            throw CameraHttpException(
                "La cámara respondió HTTP ${legacy.code} al listar grabaciones. " +
                    "Comprueba que el teléfono está en el Wi‑Fi de la BlackVue y que la IP es $host.",
            )
        }
        val names = parseLegacyIndex(legacy.body)
        if (names.isEmpty() && legacy.body.contains("mp4 only", ignoreCase = true)) {
            throw CameraHttpException(
                "La cámara no entregó el listado (respondió «mp4 only»). " +
                    "El firmware nuevo usa /vodList y esa petición tampoco devolvió archivos.",
            )
        }
        return CameraListing(legacyApi = true, names = names)
    }

    /**
     * Best-effort delete of one event file.
     *
     * Many models only document list + download (tried on a DR590XP). "Delete Video" was added
     * in 2025 for some DR970X/DR770X firmware. We try the file-scoped requests that pair
     * with those APIs, then trust only a fresh index that no longer contains the name.
     * Nothing here formats the card or deletes by wildcard.
     */
    fun deleteVerified(rawHost: String, filename: String): CameraDelete {
        val safe = filename.substringAfterLast('/').substringAfterLast('\\')
        val parsed = parseRecordingName(safe)
        if (parsed == null || categoryOf(parsed.type) == null) {
            throw CameraHttpException("Solo se pueden borrar archivos que esta app acaba de comprobar.")
        }
        val (_, name, port) = endpoint(rawHost)
        val vodDelete = url(name, port, "vodDelete")
        val legacyDelete = url(name, port, "blackvue_vod.cgi").newBuilder()
            .addQueryParameter("delete", safe)
            .build()
        if (!skipVodDelete) {
            val recordPath = postJson(deleteHttp, vodDelete, """{"filename":"/Record/$safe"}""")
            val bare = postJson(deleteHttp, vodDelete, """{"filename":"$safe"}""")
            if (unsupported(recordPath) && unsupported(bare)) skipVodDelete = true
        }
        if (!skipLegacyDelete) {
            try {
                val legacy = get(deleteHttp, legacyDelete)
                if (unsupported(legacy)) skipLegacyDelete = true
            } catch (error: IOException) {
                if (isHardUnreachable(error)) throw unreachable(error)
                skipLegacyDelete = true
            }
        }
        val names = list(rawHost).names
        if (goneFromCameraIndex(names, safe)) return CameraDelete.Removed
        if (skipVodDelete && skipLegacyDelete) return CameraDelete.NotSupported
        return CameraDelete.StillThere
    }

    fun download(
        rawHost: String,
        legacyApi: Boolean,
        filename: String,
        output: OutputStream,
        onBytes: (read: Long, total: Long) -> Unit = { _, _ -> },
    ): Long {
        val (host, name, port) = endpoint(rawHost)
        val fileUrl = try {
            HttpUrl.Builder()
                .scheme("http")
                .host(name)
                .port(port)
                .apply {
                    if (legacyApi) addPathSegment("Record")
                    addPathSegment(filename)
                }
                .build()
        } catch (_: IllegalArgumentException) {
            throw CameraHttpException("La IP de la cámara no es válida. Ejemplo: 10.99.77.1")
        }
        val request = Request.Builder().url(fileUrl).header("User-Agent", USER_AGENT).build()
        val response = try {
            http.newCall(request).execute()
        } catch (error: IOException) {
            throw unreachable(error)
        }
        response.use {
            if (!response.isSuccessful) {
                throw CameraHttpException(
                    "La cámara respondió HTTP ${response.code} al pedir $filename.",
                )
            }
            val body = response.body
                ?: throw CameraHttpException("La cámara envió una respuesta vacía para $filename.")
            val length = body.contentLength()
            return body.byteStream().use { input ->
                val buf = ByteArray(BUFFER)
                var read = 0L
                while (true) {
                    val n = try {
                        input.read(buf)
                    } catch (error: IOException) {
                        throw unreachable(error)
                    }
                    if (n < 0) break
                    output.write(buf, 0, n)
                    read += n
                    onBytes(read, length)
                }
                read
            }
        }
    }

    private fun endpoint(rawHost: String): Triple<String, String, Int> {
        val host = normalizeCameraHost(rawHost)
        val (name, port) = splitHostPort(host, 80)
        if (name.isBlank()) {
            throw CameraHttpException("La IP de la cámara no es válida. Ejemplo: 10.99.77.1")
        }
        return Triple(host, name, port)
    }

    private fun url(host: String, port: Int, segment: String): HttpUrl {
        return try {
            HttpUrl.Builder()
                .scheme("http")
                .host(host)
                .port(port)
                .addPathSegment(segment)
                .build()
        } catch (_: IllegalArgumentException) {
            throw CameraHttpException("La IP de la cámara no es válida. Ejemplo: 10.99.77.1")
        }
    }

    private fun unsupported(body: HttpBody?): Boolean {
        return body == null || body.code == 404 || body.code == 405
    }

    private fun postJson(client: OkHttpClient, url: HttpUrl, json: String): HttpBody? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .post(json.toRequestBody(JSON))
                .build()
            client.newCall(request).execute().use { response ->
                HttpBody(response.code, response.body?.string().orEmpty())
            }
        } catch (error: IOException) {
            if (isHardUnreachable(error)) throw unreachable(error)
            null
        }
    }

    private fun get(client: OkHttpClient, url: HttpUrl): HttpBody {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            return HttpBody(response.code, response.body?.string().orEmpty())
        }
    }

    private fun unreachable(cause: IOException): CameraUnreachable {
        return CameraUnreachable(CAMERA_UNREACHABLE, cause)
    }

    private fun isHardUnreachable(error: IOException): Boolean {
        return error is ConnectException ||
            error is UnknownHostException ||
            error is NoRouteToHostException ||
            error is PortUnreachableException ||
            error is SocketTimeoutException
    }

    private data class HttpBody(val code: Int, val body: String)

    companion object {
        private const val USER_AGENT = "BlackvueEventos/1.0"
        private const val BUFFER = 256 * 1024
        private val JSON = "application/json".toMediaType()

        fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(0, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }
}
