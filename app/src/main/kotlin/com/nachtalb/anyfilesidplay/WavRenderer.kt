// SPDX-License-Identifier: GPL-2.0-or-later
package com.nachtalb.anyfilesidplay

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Renders one subsong to a 16-bit PCM WAV file, streaming chunk by chunk.
 *
 * SID tunes have no length field, so rendering stops at [maxSeconds], or earlier once the
 * tune has gone silent for [SILENCE_SECONDS] (most tunes end by stopping all voices).
 */
internal object WavRenderer {

    const val RATE = 44100
    private const val SILENCE_SECONDS = 5
    private const val SILENCE_LEVEL = 64      // |sample| at or below this counts as silence

    fun render(tune: ByteArray, subsong: Int, maxSeconds: Int, out: File, progress: (Int, Int) -> Unit) {
        val handle = SidNative.nativeOpen(tune, subsong, RATE)
        try {
            val ch = SidNative.nativeChannels(handle)
            val chunk = ShortArray(RATE / 10 * ch)  // 100 ms
            val bytes = ByteBuffer.allocate(chunk.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            val maxFrames = maxSeconds.toLong() * RATE
            var written = 0L
            var silentFrames = 0L
            var lastAudible = 0L  // frames up to the end of the last non-silent chunk
            RandomAccessFile(out, "rw").use { f ->
                f.setLength(0)
                f.write(ByteArray(44))
                while (written < maxFrames) {
                    val frames = SidNative.nativeRender(handle, chunk)
                    if (frames <= 0) break
                    bytes.clear()
                    var silent = true
                    for (i in 0 until frames * ch) {
                        val v = chunk[i]
                        if (v > SILENCE_LEVEL || v < -SILENCE_LEVEL) silent = false
                        bytes.putShort(v)
                    }
                    f.write(bytes.array(), 0, frames * ch * 2)
                    written += frames
                    if (silent) silentFrames += frames else { silentFrames = 0; lastAudible = written }
                    if (written % RATE < frames) progress((written / RATE).toInt(), maxSeconds)
                    if (silentFrames >= SILENCE_SECONDS.toLong() * RATE && lastAudible > 0) break
                }
                // Drop the trailing silence that ended the render (keep a short tail).
                val keep = if (silentFrames >= SILENCE_SECONDS.toLong() * RATE) minOf(written, lastAudible + RATE / 2) else written
                val dataBytes = keep * ch * 2
                f.setLength(44 + dataBytes)
                f.seek(0)
                f.write(header(ch, dataBytes))
            }
        } finally {
            SidNative.nativeClose(handle)
        }
    }

    private fun header(ch: Int, dataBytes: Long): ByteArray =
        ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt((36 + dataBytes).toInt()); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(ch.toShort())
            putInt(RATE); putInt(RATE * ch * 2); putShort((ch * 2).toShort()); putShort(16)
            put("data".toByteArray()); putInt(dataBytes.toInt())
        }.array()
}
