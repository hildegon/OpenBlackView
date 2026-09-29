package com.blackvueeventos.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blackvueeventos.app.blackvue.BlackvueClient
import com.blackvueeventos.app.blackvue.CameraDelete
import com.blackvueeventos.app.blackvue.CameraUnreachable
import com.blackvueeventos.app.blackvue.ClipCategory
import com.blackvueeventos.app.blackvue.Recording
import com.blackvueeventos.app.blackvue.SyncSelection
import com.blackvueeventos.app.blackvue.alreadyOnPhone
import com.blackvueeventos.app.blackvue.anySelected
import com.blackvueeventos.app.blackvue.categoryOf
import com.blackvueeventos.app.blackvue.eligibleForCameraDelete
import com.blackvueeventos.app.blackvue.includeInSync
import com.blackvueeventos.app.blackvue.localFilename
import com.blackvueeventos.app.blackvue.parseRecordingName
import com.blackvueeventos.app.blackvue.selected
import com.blackvueeventos.app.blackvue.storageBucket
import com.blackvueeventos.app.settings.toSelection
import com.blackvueeventos.app.format.formatBytes
import com.blackvueeventos.app.keep.KeepAliveService
import com.blackvueeventos.app.net.CAMERA_UNREACHABLE
import com.blackvueeventos.app.net.isWifiConnected
import com.blackvueeventos.app.settings.AppSettings
import com.blackvueeventos.app.settings.SettingsStore
import com.blackvueeventos.app.settings.clampConcurrency
import com.blackvueeventos.app.storage.EventStorage
import com.blackvueeventos.app.storage.StorageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    private val camera = BlackvueClient()
    private val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _readyToDelete = MutableStateFlow<List<String>>(emptyList())
    val readyToDelete: StateFlow<List<String>> = _readyToDelete.asStateFlow()

    private val _progress = MutableStateFlow<JobProgress?>(null)
    val progress: StateFlow<JobProgress?> = _progress.asStateFlow()

    private val saveMutex = Mutex()

    @Volatile
    private var stop = false

    private var markComplete = false

    private var lastReportAt = 0L

    init {
        try {
            _settings.value = store.read()
        } catch (error: Exception) {
            log("No se pudieron leer los ajustes: ${error.message ?: error.javaClass.simpleName}")
        }
        _loaded.value = true
    }

    fun updateSettings(next: AppSettings) {
        _settings.value = next
        viewModelScope.launch(Dispatchers.IO) {
            saveMutex.withLock { persist(_settings.value) }
        }
    }

    fun flushSettings() {
        runBlocking(Dispatchers.IO) {
            saveMutex.withLock { persist(_settings.value) }
        }
    }

    fun sync() = runJob("Descargando…") { doSync() }

    fun deleteFromCamera() {
        if (_readyToDelete.value.isEmpty()) return
        runJob("Borrando…") { doDelete() }
    }

    private fun runJob(phase: String, block: suspend () -> Unit) {
        if (_running.value) return
        stop = false
        markComplete = false
        viewModelScope.launch {
            _running.value = true
            _progress.value = JobProgress(phase = phase)
            val app = getApplication<Application>()
            try {
                KeepAliveService.start(app, phase)
            } catch (_: Exception) {
                log("No se pudo mostrar la notificación. Deja la app abierta hasta que termine.")
            }
            var cancelled = false
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (_: CancellationException) {
                cancelled = true
                log("Cancelado.")
            } finally {
                val current = _progress.value
                _progress.value = when {
                    cancelled || stop -> (current ?: JobProgress(phase = "Cancelado")).copy(phase = "Cancelado")
                    markComplete && current != null && current.total > 0 ->
                        current.copy(phase = "Completado", file = "", done = current.total, fraction = 1f)
                    else -> null
                }
                KeepAliveService.stop(app)
                _running.value = false
            }
        }
    }

    fun cancel() {
        if (!_running.value) return
        stop = true
        val current = _progress.value ?: JobProgress(phase = "Cancelando…")
        _progress.value = current.copy(phase = "Cancelando…")
    }

    private suspend fun doSync() {
        val settings = _settings.value
        val storage = EventStorage(getApplication(), settings.downloadRoot)
        val selection = settings.toSelection()
        _readyToDelete.value = emptyList()
        log("— Sincronizar cámara —")
        if (!storage.access().ready) {
            log("Falta permiso para guardar en ${storage.access().label}.")
            return
        }
        log("Destino: ${storage.access().label}")
        try {
            storage.ensureLayout()
        } catch (error: StorageException) {
            log(error.message ?: "No se pudo crear la carpeta de destino.")
            return
        }
        if (!anySelected(selection)) {
            log("Ningún tipo está activado en Ajustes.")
            return
        }
        if (!isWifiConnected(getApplication())) {
            log("Aviso: no hay Wi‑Fi activo. La cámara solo responde en su propia red.")
        }
        val host = settings.cameraHost
        val workers = clampConcurrency(settings.downloadConcurrency)
        log("Conectando con $host…")
        val listing = try {
            camera.list(host)
        } catch (error: Exception) {
            log(if (error is CameraUnreachable) CAMERA_UNREACHABLE else error.message ?: "No se pudo listar la cámara.")
            return
        }
        val api = if (listing.legacyApi) "API clásica /blackvue_vod.cgi" else "API nueva /vodList"
        log("Listado recibido: ${listing.names.size} vídeos ($api).")
        val parsed = listing.names.mapNotNull { parseRecordingName(it) }
        val unknown = listing.names.size - parsed.size
        if (unknown > 0) {
            log("Se ignoraron $unknown nombres que no parecen grabaciones BlackVue.")
        }
        val odd = parsed.filter { categoryOf(it.type) == null }
        if (odd.isNotEmpty()) {
            val letters = odd.map { it.type }.distinct().sorted().joinToString(", ")
            log("Se ignoraron ${odd.size} vídeos de tipo no reconocido ($letters).")
        }
        val wanted = parsed.filter { includeInSync(it, selection) }
            .distinctBy { it.filename }
            .sortedBy { it.filename }
        val present = storage.existingMp4Names()
        val pending = wanted.filter { !alreadyOnPhone(it.filename, present) }
        log(describeWanted(selection, wanted, pending.size))
        if (pending.isEmpty()) {
            log("Nada nuevo. Esos vídeos ya están en el teléfono.")
            return
        }
        log("Descargas a la vez: $workers.")
        report(
            JobProgress(phase = "Descargando…", total = pending.size, fraction = 0f),
            force = true,
        )
        val queue = ArrayDeque(pending)
        val verified = ArrayList<String>()
        val active = linkedMapOf<String, Pair<String, Float>>()
        val lock = Any()
        var done = 0
        var failed = 0
        var hardStop = false
        var flake = false
        var lowSpaceLogged = false

        fun publish(force: Boolean = false) {
            report(
                JobProgress(
                    phase = "Descargando…",
                    file = active.values.joinToString(", ") { it.first },
                    done = done,
                    total = pending.size,
                    fraction = parallelFraction(done, pending.size, active.values.map { it.second }) ?: 0f,
                ),
                force = force,
            )
        }

        suspend fun pass(workerCount: Int) {
            coroutineScope {
                for (worker in 1..workerCount) {
                    launch(Dispatchers.IO) {
                        while (true) {
                            val recording = synchronized(lock) {
                                if (stop || hardStop || flake) {
                                    null
                                } else {
                                    val free = storage.freeBytes()
                                    if (free != null && free < MIN_FREE_BYTES) {
                                        hardStop = true
                                        if (!lowSpaceLogged) {
                                            lowSpaceLogged = true
                                            log("Queda poco espacio (${formatBytes(free)}). Se detiene la descarga.")
                                        }
                                        null
                                    } else {
                                        queue.removeFirstOrNull()
                                    }
                                }
                            } ?: break
                            if (stop) break
                            val kind = categoryOf(recording.type)?.bucket ?: "video"
                            val savedName = localFilename(recording.filename)
                            val label = "$kind · $savedName"
                            synchronized(lock) {
                                active[recording.filename] = label to 0f
                                publish(force = true)
                            }
                            log("Descargando ($kind): $savedName")
                            try {
                                var bytes = 0L
                                val stored = storage.save(
                                    storageBucket(recording.type),
                                    recording.dayFolder,
                                    savedName,
                                ) { output ->
                                    bytes = camera.download(host, listing.legacyApi, recording.filename, output) { read, total ->
                                        if (stop) throw CancellationException()
                                        val part = if (total > 0L) {
                                            (read.toDouble() / total).toFloat().coerceIn(0f, 1f)
                                        } else {
                                            0f
                                        }
                                        synchronized(lock) {
                                            if (active.containsKey(recording.filename)) {
                                                active[recording.filename] = label to part
                                                publish()
                                            }
                                        }
                                    }
                                }
                                val ok = eligibleForCameraDelete(bytes, stored)
                                synchronized(lock) {
                                    active.remove(recording.filename)
                                    done++
                                    if (ok) verified += recording.filename
                                    publish(force = true)
                                }
                                log(
                                    "Guardado ${storageBucket(recording.type)}/${recording.dayFolder}/" +
                                        "$savedName (${formatBytes(bytes)})",
                                )
                                if (!ok) {
                                    log("El tamaño de $savedName no coincide. No se ofrecerá borrarlo de la cámara.")
                                }
                            } catch (error: CancellationException) {
                                synchronized(lock) {
                                    active.remove(recording.filename)
                                    publish(force = true)
                                }
                                break
                            } catch (error: CameraUnreachable) {
                                synchronized(lock) {
                                    active.remove(recording.filename)
                                    queue.addFirst(recording)
                                    flake = true
                                    publish(force = true)
                                }
                            } catch (error: StorageException) {
                                synchronized(lock) {
                                    active.remove(recording.filename)
                                    failed++
                                    hardStop = true
                                    publish(force = true)
                                }
                                log("Error en $savedName: ${error.message ?: error.javaClass.simpleName}")
                            } catch (error: Exception) {
                                synchronized(lock) {
                                    active.remove(recording.filename)
                                    failed++
                                    publish(force = true)
                                }
                                log("Error en $savedName: ${error.message ?: error.javaClass.simpleName}")
                            }
                        }
                    }
                }
            }
        }

        pass(workers)
        if (stop) {
            _readyToDelete.value = verified.toList()
            throw CancellationException()
        }
        if (flake && !hardStop && workers > 1) {
            log("La cámara falló con varias descargas a la vez. Se continúa de una en una.")
            flake = false
            pass(1)
        }
        if (stop) {
            _readyToDelete.value = verified.toList()
            throw CancellationException()
        }
        if (flake) log(CAMERA_UNREACHABLE)
        if (queue.isNotEmpty() && (flake || hardStop)) {
            log("Sin bajar: ${queue.size}.")
        }
        markComplete = !hardStop && !flake
        _readyToDelete.value = verified.toList()
        log("Sincronización terminada. Descargados: $done. Errores: $failed. Omitidos: ${wanted.size - pending.size}.")
        if (done > 0) {
            log("Quedan en el teléfono, en ${storage.access().label}.")
        }
        if (verified.isNotEmpty()) {
            log("Comprobados para borrar de la cámara: ${verified.size}. Pulsa «Borrar de la cámara»; pedirá confirmación.")
        }
    }

    private fun doDelete() {
        val files = _readyToDelete.value
        if (files.isEmpty()) return
        val host = _settings.value.cameraHost
        log("— Borrar de la cámara —")
        log("Solo ${files.size} archivos comprobados en esta sincronización. El resto de la tarjeta no se toca.")
        report(JobProgress(phase = "Borrando…", total = files.size, fraction = 0f), force = true)
        val left = files.toMutableList()
        var removed = 0
        var failed = 0
        for ((index, filename) in files.withIndex()) {
            if (stop) throw CancellationException()
            log("Borrando ${index + 1}/${files.size}: $filename")
            report(
                JobProgress(
                    phase = "Borrando…",
                    file = filename,
                    done = index,
                    total = files.size,
                    fraction = jobFraction(index, files.size) ?: 0f,
                ),
                force = true,
            )
            try {
                when (camera.deleteVerified(host, filename)) {
                    CameraDelete.Removed -> {
                        removed++
                        left.remove(filename)
                        _readyToDelete.value = left.toList()
                        log("Borrado de la cámara: $filename")
                    }
                    CameraDelete.NotSupported -> {
                        failed++
                        log(
                            "No se borró $filename. La cámara no acepta borrado por Wi‑Fi. " +
                                "Hay que quitar el archivo con la tarjeta SD.",
                        )
                    }
                    CameraDelete.StillThere -> {
                        failed++
                        log("No se borró $filename. Sigue en el listado de la cámara.")
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: CameraUnreachable) {
                log(CAMERA_UNREACHABLE)
                break
            } catch (error: Exception) {
                failed++
                log("No se borró $filename: ${error.message ?: error.javaClass.simpleName}")
            }
        }
        markComplete = !stop && removed == files.size && failed == 0
        log("Borrado terminado. Borrados: $removed. Siguen en la cámara: ${files.size - removed}.")
    }

    private fun describeWanted(
        selection: SyncSelection,
        wanted: List<Recording>,
        pending: Int,
    ): String {
        val parts = ClipCategory.entries.joinToString(" ") { category ->
            if (category.selected(selection)) {
                val count = wanted.count { categoryOf(it.type) == category }
                "${category.title} (${category.codes}): $count."
            } else {
                "${category.title} desactivado."
            }
        }
        return "$parts Ya en el teléfono: ${wanted.size - pending}. Por bajar: $pending."
    }

    private fun persist(settings: AppSettings) {
        try {
            store.write(settings)
        } catch (error: Exception) {
            log("No se pudieron guardar los ajustes: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun report(next: JobProgress, force: Boolean = false) {
        val now = android.os.SystemClock.elapsedRealtime()
        val prev = _progress.value
        val moved = prev == null ||
            prev.phase != next.phase ||
            prev.file != next.file ||
            prev.done != next.done ||
            kotlin.math.abs(prev.fraction - next.fraction) >= 0.01f
        if (!force && !moved && now - lastReportAt < 250) return
        lastReportAt = now
        _progress.value = next
    }

    private fun log(message: String) {
        val line = LocalTime.now().format(clock) + "  " + message
        _lines.update { (it + line).takeLast(400) }
    }

    companion object {
        private const val MIN_FREE_BYTES = 200L * 1024L * 1024L
    }
}
