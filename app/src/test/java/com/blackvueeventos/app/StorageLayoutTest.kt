package com.blackvueeventos.app

import com.blackvueeventos.app.storage.ensureDirectories
import com.blackvueeventos.app.storage.resolveBaseDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StorageLayoutTest {
    @Test
    fun baseDirectory_blankUsesBlackvueAndRelativeStaysUnderExternal() {
        val external = File("/storage/emulated/0")
        assertEquals(File("/storage/emulated/0/blackvue"), resolveBaseDirectory(external, ""))
        assertEquals(File("/storage/emulated/0/blackvue"), resolveBaseDirectory(external, "  "))
        assertEquals(File("/storage/emulated/0/Movies/dash"), resolveBaseDirectory(external, "Movies/dash"))
        assertEquals(File("/mnt/sd/cam"), resolveBaseDirectory(external, "/mnt/sd/cam"))
    }

    @Test
    fun ensureDirectories_createsMissingParentsAndBuckets() {
        val root = File(System.getProperty("java.io.tmpdir"), "obv-" + System.nanoTime() + "/nested/blackvue")
        assertEquals(false, root.exists())
        val buckets = listOf("eventos", "parking", "normal", "geocerca", "conductor")
        ensureDirectories(root, buckets)
        for (name in buckets) {
            assertTrue(File(root, name).isDirectory)
        }
        ensureDirectories(root, buckets)
        assertTrue(File(root, "normal").isDirectory)
        root.parentFile?.parentFile?.deleteRecursively()
    }
}
