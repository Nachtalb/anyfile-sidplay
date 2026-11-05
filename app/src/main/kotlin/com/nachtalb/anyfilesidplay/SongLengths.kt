// SPDX-License-Identifier: GPL-2.0-or-later
package com.nachtalb.anyfilesidplay

import java.io.BufferedReader
import java.security.MessageDigest

/**
 * Real tune lengths from the HVSC song-length database (`assets/songlengths.txt`: the `md5=lengths`
 * lines of HVSC's `DOCUMENTS/Songlengths.md5`, comments stripped). Keyed by the MD5 of the whole
 * file, one `m:ss[.mmm]` per subsong.
 */
internal object SongLengths {

    const val ASSET = "songlengths.txt"

    fun md5(tune: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(tune).joinToString("") { "%02x".format(it) }

    /** Length in ms of 1-based [song], or null when the tune or the song isn't listed. */
    fun find(db: BufferedReader, md5: String, song: Int): Long? {
        val prefix = "$md5="
        val line = db.lineSequence().firstOrNull { it.startsWith(prefix) } ?: return null
        val entry = line.substring(prefix.length).split(' ').getOrNull(song - 1) ?: return null
        return parse(entry)?.takeIf { it > 0 }
    }

    /** `m:ss` or `m:ss.mmm`. */
    fun parse(entry: String): Long? {
        val m = Regex("""^(\d+):(\d{1,2})(?:\.(\d{1,3}))?""").find(entry) ?: return null
        val (min, sec, frac) = m.destructured
        val ms = if (frac.isEmpty()) 0 else frac.padEnd(3, '0').toInt()
        return (min.toLong() * 60 + sec.toLong()) * 1000 + ms
    }

    /** The subsong a PSID/RSID plays for [subsong] (0 = the header's start song), 1-based. */
    fun resolveSong(tune: ByteArray, subsong: Int): Int {
        if (subsong > 0 || tune.size < 0x12) return subsong.coerceAtLeast(1)
        val songs = u16(tune, 0x0E)
        val start = u16(tune, 0x10)
        return if (start in 1..songs) start else 1
    }

    private fun u16(b: ByteArray, at: Int) = (b[at].toInt() and 0xff shl 8) or (b[at + 1].toInt() and 0xff)
}
