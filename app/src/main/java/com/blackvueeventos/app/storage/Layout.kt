package com.blackvueeventos.app.storage

import java.io.File

/**
 * Blank means internal storage `blackvue`. A relative path is under [externalRoot].
 * An absolute path is used as written.
 */
fun resolveBaseDirectory(externalRoot: File, downloadRoot: String): File {
    val raw = downloadRoot.trim()
    if (raw.isEmpty()) return File(externalRoot, "blackvue")
    val file = File(raw)
    return if (file.isAbsolute) file else File(externalRoot, raw)
}

/**
 * Creates [root] and each category folder under it, including missing parents.
 * A second call is a no-op when the folders are already there.
 */
fun ensureDirectories(root: File, buckets: Collection<String>) {
    if (!makeDirectory(root)) {
        throw StorageException("No se pudo crear ${root.path}. Revisa el permiso de almacenamiento.")
    }
    for (name in buckets) {
        val dir = File(root, name)
        if (!makeDirectory(dir)) {
            throw StorageException("No se pudo crear ${dir.path}. Revisa el permiso de almacenamiento.")
        }
    }
}

private fun makeDirectory(dir: File): Boolean {
    return dir.isDirectory || dir.mkdirs() || dir.isDirectory
}
