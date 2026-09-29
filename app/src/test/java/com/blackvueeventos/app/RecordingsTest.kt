package com.blackvueeventos.app

import com.blackvueeventos.app.blackvue.alreadyOnPhone
import com.blackvueeventos.app.blackvue.eligibleForCameraDelete
import com.blackvueeventos.app.blackvue.localFilename
import com.blackvueeventos.app.blackvue.includeInSync
import com.blackvueeventos.app.blackvue.storageBucket
import com.blackvueeventos.app.blackvue.goneFromCameraIndex
import com.blackvueeventos.app.blackvue.normalizeCameraHost
import com.blackvueeventos.app.blackvue.parseLegacyIndex
import com.blackvueeventos.app.blackvue.parseRecordingName
import com.blackvueeventos.app.blackvue.parseVodList
import com.blackvueeventos.app.blackvue.splitHostPort
import com.blackvueeventos.app.settings.resolveSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordingsTest {
    @Test
    fun legacyIndex_keepsEventTypesAndDailyFolder() {
        val body = """
            v:1.00
            n:/Record/20260928_101500_NF.mp4,s:1000000
            n:/Record/20260928_101530_EF.mp4,s:1000000
            n:/Record/20260928_101530_ER.mp4,s:1000000
            n:/Record/20260928_110000_MF.mp4,s:1000000
            n:/Record/20260928_120000_PF.mp4,s:1000000
            n:/Record/20260928_130000_IF.mp4,s:1000000
            n:/Record/20260928_140000_OF.mp4,s:123
            n:/Record/20260928_150000_AF.mp4,s:1000000
            n:/Record/20260928_160000_TF.mp4,s:1000000
            n:/Record/20260928_170000_BF.mp4,s:1000000
            n:/Record/20260928_180000_EFL.mp4,s:1000000
            n:/Record/20260928_180100_EFS.mp4,s:1000000
            n:/Record/20260928_180200_NR.mp4,s:1000000
        """.trimIndent()

        val events = parseLegacyIndex(body)
            .mapNotNull { parseRecordingName(it) }
            .filter { it.isEventLike }

        assertEquals(
            listOf("EF", "ER", "MF", "IF", "OF", "AF", "TF", "BF", "EFL", "EFS"),
            events.map { it.filename.substringAfterLast('_').substringBefore('.') },
        )
        assertEquals("2026-09-28", events.first().dayFolder)
        assertEquals(13, parseLegacyIndex(body).size)
    }

    @Test
    fun vodList_readsFilenamesAndStripsRecordPrefix() {
        val body = """
            {"filelist":[
              {"filename":"20260929_090000_PF.mp4"},
              {"filename":"/Record/20260929_090100_EF.mp4"},
              {"filename":"\/Record\/20260929_090200_BF.mp4"}
            ]}
        """.trimIndent()
        val names = parseVodList(body)
        assertEquals(
            listOf(
                "20260929_090000_PF.mp4",
                "20260929_090100_EF.mp4",
                "20260929_090200_BF.mp4",
            ),
            names,
        )
        val events = names.mapNotNull { parseRecordingName(it) }.filter { it.isEventLike }
        assertEquals(2, events.size)
        assertEquals("2026-09-29", events[0].dayFolder)
    }

    @Test
    fun rejectsBadDatesAndNormalParking() {
        assertNull(parseRecordingName("20261340_101500_EF.mp4"))
        assertEquals(false, parseRecordingName("20260928_101500_NF.mp4")!!.isEventLike)
        val parking = parseRecordingName("20260928_101500_PF.mp4")!!
        assertEquals(false, parking.isEventLike)
        assertEquals(true, parking.isParking)
        assertEquals(false, includeInSync(parking, downloadParking = false))
        assertEquals(true, includeInSync(parking, downloadParking = true))
        assertEquals("parking", storageBucket(parking.type))
        assertEquals("eventos", storageBucket('E'))
    }

    @Test
    fun localFilename_spellsTimeTypeAndCameraWithoutCollisions() {
        assertEquals(
            "2026-09-29_18-45-03_evento_frente.mp4",
            localFilename("20260929_184503_EF.mp4"),
        )
        assertEquals(
            "2026-09-28_18-00-00_evento_frente_L.mp4",
            localFilename("/Record/20260928_180000_EFL.mp4"),
        )
        assertEquals(
            "2026-09-28_18-01-00_evento_frente_S.mp4",
            localFilename("20260928_180100_EFS.mp4"),
        )
        assertEquals("2026-09-28_12-00-00_parking_frente.mp4", localFilename("20260928_120000_PF.mp4"))
        assertEquals("2026-09-28_10-15-30_evento_trasera.mp4", localFilename("20260928_101530_ER.mp4"))
        assertEquals("2026-09-28_13-00-00_impacto_frente.mp4", localFilename("20260928_130000_IF.mp4"))
        assertEquals("2026-09-28_14-00-00_exceso_frente.mp4", localFilename("20260928_140000_OF.mp4"))
        assertEquals("2026-09-28_15-00-00_aceleracion_frente.mp4", localFilename("20260928_150000_AF.mp4"))
        assertEquals("2026-09-28_16-00-00_curva_frente.mp4", localFilename("20260928_160000_TF.mp4"))
        assertEquals("2026-09-28_17-00-00_frenada_frente.mp4", localFilename("20260928_170000_BF.mp4"))
        assertEquals("2026-09-28_11-00-00_manual_frente.mp4", localFilename("20260928_110000_MF.mp4"))
        assertEquals("2026-09-29_18-45-03_evento_interior.mp4", localFilename("20260929_184503_EI.mp4"))
        assertEquals("2026-09-29_18-45-03_evento_opcional.mp4", localFilename("20260929_184503_EO.mp4"))
        val locals = listOf(
            "20260929_184503_EF.mp4",
            "20260929_184503_ER.mp4",
            "20260929_184503_EI.mp4",
            "20260929_184503_EO.mp4",
            "20260929_184503_EFL.mp4",
            "20260929_184503_EFS.mp4",
            "20260929_184503_PF.mp4",
            "20260929_184503_MF.mp4",
            "20260929_184503_IF.mp4",
            "20260929_184503_OF.mp4",
            "20260929_184503_AF.mp4",
            "20260929_184503_TF.mp4",
            "20260929_184503_BF.mp4",
        ).map { localFilename(it) }
        assertEquals(locals.size, locals.toSet().size)
        assertEquals(true, alreadyOnPhone("20260929_184503_EF.mp4", setOf("20260929_184503_EF.mp4")))
        assertEquals(
            true,
            alreadyOnPhone("20260929_184503_EF.mp4", setOf("2026-09-29_18-45-03_evento_frente.mp4")),
        )
        assertEquals(false, alreadyOnPhone("20260929_184503_EF.mp4", setOf("2026-09-29_18-45-03_evento_trasera.mp4")))
    }

    @Test
    fun cameraHost_stripsUrlAndDefaults() {
        assertEquals("10.99.77.1", normalizeCameraHost(""))
        assertEquals("10.99.77.1", normalizeCameraHost("http://10.99.77.1/blackvue_vod.cgi"))
        assertEquals("10.99.77.1" to 80, splitHostPort(normalizeCameraHost("10.99.77.1"), 80))
        assertEquals("10.99.77.1" to 8080, splitHostPort("10.99.77.1:8080", 80))
    }

    @Test
    fun cameraDelete_requiresASizeMatchAndAMissingIndexEntry() {
        assertEquals(false, eligibleForCameraDelete(bytesDownloaded = 0, bytesStored = 0))
        assertEquals(false, eligibleForCameraDelete(bytesDownloaded = 10, bytesStored = 9))
        assertEquals(true, eligibleForCameraDelete(bytesDownloaded = 10, bytesStored = 10))
        assertEquals(false, goneFromCameraIndex(listOf("/Record/a.mp4"), "a.mp4"))
        assertEquals(true, goneFromCameraIndex(listOf("/Record/b.mp4"), "a.mp4"))
    }

    @Test
    fun savedCameraIp_replacesThePlaceholder() {
        val fresh = com.blackvueeventos.app.settings.AppSettings()
        assertEquals("10.99.77.1", fresh.cameraHost)
        assertEquals(true, fresh.downloadParking)
        assertEquals(3, fresh.downloadConcurrency)
        assertEquals(1, resolveSettings("10.0.0.1", downloadConcurrency = 0).downloadConcurrency)
        assertEquals(4, resolveSettings("10.0.0.1", downloadConcurrency = 8).downloadConcurrency)
        assertEquals(false, resolveSettings("10.99.77.1", downloadParking = false).downloadParking)
        assertEquals("10.99.77.1", resolveSettings("10.99.77.1").cameraHost)
        assertEquals("10.99.77.1", resolveSettings("  ").cameraHost)
    }
}
