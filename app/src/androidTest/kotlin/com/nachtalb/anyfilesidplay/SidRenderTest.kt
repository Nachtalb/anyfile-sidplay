// SPDX-License-Identifier: GPL-2.0-or-later
package com.nachtalb.anyfilesidplay

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * On-device check of the render path (the app has no UI, so this is the device harness).
 * Push a PSID first:
 *   adb -s <serial> push sample.sid /sdcard/Android/data/com.nachtalb.anyfilesidplay/files/sample.sid
 */
class SidRenderTest {

    @Test
    fun rendersSampleToAudibleWav() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val input = File(ctx.getExternalFilesDir(null), "sample.sid")
        assertTrue("push sample.sid to ${input.absolutePath} first", input.exists())

        val out = File(ctx.cacheDir, "out.wav")
        val start = System.nanoTime()
        WavRenderer.render(input.readBytes(), 0, 20, out) { _, _ -> }
        val ms = (System.nanoTime() - start) / 1_000_000
        android.util.Log.i("SidRenderTest", "rendered 20 s in $ms ms, ${out.length()} bytes")

        val b = ByteBuffer.wrap(out.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(out.readBytes(), 0, 4))
        val dataBytes = b.getInt(40)
        assertEquals(out.length() - 44, dataBytes.toLong())
        b.position(44)
        var sum = 0.0
        val n = dataBytes / 2
        repeat(n) { val v = b.short.toDouble(); sum += v * v }
        val rms = sqrt(sum / n)
        assertTrue("audible (rms=$rms)", rms > 500)
    }
}
