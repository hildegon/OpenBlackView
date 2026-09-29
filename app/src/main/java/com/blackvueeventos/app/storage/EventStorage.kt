package com.blackvueeventos.app.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.blackvueeventos.app.blackvue.CATEGORY_BUCKETS
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class StorageException(message: String) : IOException(message)

data class StorageAccess(
    val ready: Boolean,
    val label: String,
    val needsAllFiles: Boolean,
    val needsWritePermission: Boolean,
    val needsTree: Boolean,
)

class LocalVideo(
    val name: String,
    val relativePath: String,
    val size: Long,
    val open: () -> InputStream,
)

/**
 * Videos live under a base folder, default internal storage `blackvue`.
 * Category folders (`eventos`, `parking`, `normal`, `geocerca`, `conductor`)
 * and any missing parents are created before a download.
 * Android 11+ needs all-files access. Android 10 uses the folder picker when
 * the path is left empty. Older versions use the storage permission.
 */
class EventStorage(
    private val context: Context,
    private val downloadRoot: String = "",
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val dirLock = Any()

    fun access(): StorageAccess {
        val root = fileRoot()
        if (canUseDirect()) {
            return StorageAccess(
                ready = true,
                label = root.absolutePath,
                needsAllFiles = false,
                needsWritePermission = false,
                needsTree = false,
            )
        }
        if (downloadRoot.isNotBlank()) {
            return StorageAccess(
                ready = false,
                label = root.absolutePath,
                needsAllFiles = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
                needsWritePermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P,
                needsTree = Build.VERSION.SDK_INT == Build.VERSION_CODES.Q,
            )
        }
        val tree = persistedTree()
        if (tree != null) {
            return StorageAccess(
                ready = true,
                label = "Carpeta elegida · blackvue",
                needsAllFiles = false,
                needsWritePermission = false,
                needsTree = false,
            )
        }
        return StorageAccess(
            ready = false,
            label = "Sin acceso a la carpeta de destino",
            needsAllFiles = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
            needsWritePermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P,
            needsTree = Build.VERSION.SDK_INT == Build.VERSION_CODES.Q ||
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
        )
    }

    fun allFilesIntent(): Intent {
        val packageUri = Uri.parse("package:${context.packageName}")
        val specific = Intent(
            android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            packageUri,
        )
        return if (specific.resolveActivity(context.packageManager) != null) {
            specific
        } else {
            Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        }
    }

    fun persistTree(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
        prefs.edit().putString(KEY_TREE, uri.toString()).apply()
    }

    fun existingMp4Names(): Set<String> {
        return listVideos().map { it.name }.toSet()
    }

    fun listVideos(): List<LocalVideo> {
        return when (val mode = resolve()) {
            is Mode.Direct -> listDirect(mode.root)
            is Mode.Tree -> listTree(mode.uri)
            Mode.Missing -> emptyList()
        }
    }

    /** Creates the base path and each category folder. Missing parents are created too. */
    fun ensureLayout() {
        when (val mode = resolve()) {
            is Mode.Direct -> ensureDirectories(mode.root, CATEGORY_BUCKETS)
            is Mode.Tree -> {
                val root = DocumentFile.fromTreeUri(context, mode.uri)
                    ?: throw StorageException("La carpeta elegida ya no está disponible. Vuelve a seleccionarla.")
                for (bucket in CATEGORY_BUCKETS) {
                    childDir(root, bucket)
                }
            }
            Mode.Missing -> throw StorageException(
                "No hay permiso para escribir en la carpeta de destino.",
            )
        }
    }

    /** Writes the file and returns its size on disk, or -1 if the size cannot be read. */
    fun save(bucket: String, dayFolder: String, filename: String, write: (OutputStream) -> Unit): Long {
        return when (val mode = resolve()) {
            is Mode.Direct -> saveDirect(mode.root, bucket, dayFolder, filename, write)
            is Mode.Tree -> saveTree(mode.uri, bucket, dayFolder, filename, write)
            Mode.Missing -> throw StorageException(
                "No hay permiso para escribir en Almacenamiento interno/blackvue.",
            )
        }
    }

    fun freeBytes(): Long? {
        return try {
            StatFs(Environment.getExternalStorageDirectory().absolutePath).availableBytes
        } catch (_: Exception) {
            null
        }
    }

    private fun saveDirect(
        root: File,
        bucket: String,
        day: String,
        filename: String,
        write: (OutputStream) -> Unit,
    ): Long {
        val dir = File(File(root, bucket), day)
        if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) {
            throw StorageException("No se pudo crear ${dir.absolutePath}. Revisa el permiso de almacenamiento.")
        }
        val finalFile = File(dir, filename)
        val partial = File(dir, "$filename.partial")
        if (partial.exists() && !partial.delete()) {
            throw StorageException("No se pudo reemplazar la descarga a medias de $filename.")
        }
        try {
            partial.outputStream().use { write(it) }
            if (finalFile.exists() && !finalFile.delete()) {
                throw StorageException("No se pudo reemplazar $filename.")
            }
            if (!partial.renameTo(finalFile)) {
                partial.copyTo(finalFile, overwrite = true)
                partial.delete()
            }
            return finalFile.length()
        } catch (error: Throwable) {
            partial.delete()
            throw error
        }
    }

    private fun saveTree(
        tree: Uri,
        bucket: String,
        day: String,
        filename: String,
        write: (OutputStream) -> Unit,
    ): Long {
        val root = DocumentFile.fromTreeUri(context, tree)
            ?: throw StorageException("La carpeta elegida ya no está disponible. Vuelve a seleccionarla.")
        val bucketDir = childDir(root, bucket)
        val dayDir = childDir(bucketDir, day)
        dayDir.findFile("$filename.partial")?.delete()
        val partial = dayDir.createFile("application/octet-stream", "$filename.partial")
            ?: throw StorageException("No se pudo crear $filename en el destino.")
        try {
            val stream = context.contentResolver.openOutputStream(partial.uri)
                ?: throw StorageException("No se pudo abrir $filename para escribir.")
            stream.use { write(it) }
            val renamed = DocumentsContract.renameDocument(
                context.contentResolver,
                partial.uri,
                filename,
            )
            if (renamed == null) {
                throw StorageException("Se descargó $filename pero no se pudo quitar el sufijo .partial.")
            }
            return DocumentFile.fromSingleUri(context, renamed)?.length() ?: -1L
        } catch (error: Throwable) {
            partial.delete()
            throw error
        }
    }

    private fun childDir(parent: DocumentFile, name: String): DocumentFile {
        synchronized(dirLock) {
            return parent.findFile(name)?.takeIf { it.isDirectory }
                ?: parent.createDirectory(name)
                ?: throw StorageException("No se pudo crear la carpeta $name en el destino.")
        }
    }

    private fun listDirect(root: File): List<LocalVideo> {
        if (!root.exists()) return emptyList()
        return root.walkTopDown()
            .onFail { _, _ -> }
            .filter { file ->
                file.isFile &&
                    file.name.endsWith(".mp4", ignoreCase = true) &&
                    !file.name.endsWith(".partial", ignoreCase = true) &&
                    file.length() > 0L
            }
            .map { file ->
                LocalVideo(
                    name = file.name,
                    relativePath = file.relativeTo(root).path.replace('\\', '/'),
                    size = file.length(),
                    open = { file.inputStream() },
                )
            }
            .sortedBy { it.relativePath }
            .toList()
    }

    private fun listTree(tree: Uri): List<LocalVideo> {
        val root = DocumentFile.fromTreeUri(context, tree) ?: return emptyList()
        val out = ArrayList<LocalVideo>()
        collectTree(root, "", out)
        return out.sortedBy { it.relativePath }
    }

    private fun collectTree(dir: DocumentFile, prefix: String, out: MutableList<LocalVideo>) {
        val children = dir.listFiles()
        for (child in children) {
            val name = child.name ?: continue
            if (child.isDirectory) {
                val next = if (prefix.isEmpty()) name else "$prefix/$name"
                collectTree(child, next, out)
            } else if (child.isFile && name.endsWith(".mp4", ignoreCase = true) && child.length() > 0L) {
                val relative = if (prefix.isEmpty()) name else "$prefix/$name"
                val uri = child.uri
                out += LocalVideo(
                    name = name,
                    relativePath = relative,
                    size = child.length(),
                    open = {
                        context.contentResolver.openInputStream(uri)
                            ?: throw StorageException("No se pudo leer $name.")
                    },
                )
            }
        }
    }

    private fun resolve(): Mode {
        if (canUseDirect()) return Mode.Direct(fileRoot())
        if (downloadRoot.isBlank()) {
            val tree = persistedTree()
            if (tree != null) return Mode.Tree(tree)
        }
        return Mode.Missing
    }

    private fun fileRoot(): File {
        return resolveBaseDirectory(Environment.getExternalStorageDirectory(), downloadRoot)
    }

    private fun canUseDirect(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P -> hasWritePermission()
            else -> false
        }
    }

    private fun hasWritePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun persistedTree(): Uri? {
        val raw = prefs.getString(KEY_TREE, null) ?: return null
        val uri = Uri.parse(raw)
        val ok = context.contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == uri && permission.isReadPermission && permission.isWritePermission
        }
        return if (ok) uri else null
    }

    private sealed interface Mode {
        data class Direct(val root: File) : Mode
        data class Tree(val uri: Uri) : Mode
        data object Missing : Mode
    }

    companion object {
        private const val PREFS = "storage"
        private const val KEY_TREE = "tree_uri"
    }
}
