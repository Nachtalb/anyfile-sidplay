// SPDX-License-Identifier: GPL-2.0-or-later
package com.nachtalb.anyfilesidplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class SongLengthsTest {

    private val db = """
        5f08a730b280e54fd1e75a7046b93fdc=1:17
        dda8aee01dd2af356ea603877108bf9a=0:53.627
        c7c299ce06ec5ccffb2261fb11b42a73=4:33.108 0:05.5 0:00
    """.trimIndent()

    private fun find(md5: String, song: Int) = SongLengths.find(db.reader().buffered(), md5, song)

    @Test
    fun looksUpPerSubsong() {
        assertEquals(53_627L, find("dda8aee01dd2af356ea603877108bf9a", 1))
        assertEquals(77_000L, find("5f08a730b280e54fd1e75a7046b93fdc", 1))
        assertEquals(273_108L, find("c7c299ce06ec5ccffb2261fb11b42a73", 1))
        assertEquals(5_500L, find("c7c299ce06ec5ccffb2261fb11b42a73", 2))
    }

    @Test
    fun unknownTuneSongOrZeroLengthIsNull() {
        assertNull(find("00000000000000000000000000000000", 1))
        assertNull(find("5f08a730b280e54fd1e75a7046b93fdc", 2))
        assertNull(find("c7c299ce06ec5ccffb2261fb11b42a73", 3))
    }

    @Test
    fun bundledDatabaseKnowsTheAnyFileSample() {
        // Coffin Dance (anyfile Samples/Audio/sample_c64_short.sid), 0:53.627 in HVSC.
        val asset = File("src/main/assets/${SongLengths.ASSET}")
        assertEquals(53_627L, SongLengths.find(asset.bufferedReader(), "dda8aee01dd2af356ea603877108bf9a", 1))
    }

    @Test
    fun resolvesStartSongFromHeader() {
        val psid = ByteArray(0x7c).also { it[0x0f] = 13; it[0x11] = 12 }  // 13 songs, start 12
        assertEquals(12, SongLengths.resolveSong(psid, 0))
        assertEquals(3, SongLengths.resolveSong(psid, 3))
        psid[0x11] = 20  // start song out of range
        assertEquals(1, SongLengths.resolveSong(psid, 0))
    }
}
