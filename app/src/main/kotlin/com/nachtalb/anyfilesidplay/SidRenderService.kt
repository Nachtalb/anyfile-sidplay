// SPDX-License-Identifier: GPL-2.0-or-later
package com.nachtalb.anyfilesidplay

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import com.nachtalb.anyfilecompanion.IConverter
import com.nachtalb.anyfilecompanion.IConverterCallback
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Headless SID → WAV render service, bound by AnyFile over the shared [IConverter] AIDL.
 * The app's only component. The result fd is a 48 kHz 16-bit PCM WAV that AnyFile plays
 * in its own audio player; libsidplayfp (GPL) never leaves this process.
 *
 * Options: "subsong" (1-based, 0/absent = the tune's start song), "seconds" (render length cap,
 * default 180, 1..1200). Tunes in the HVSC song-length database render at their real length
 * instead (up to 1200 s).
 */
class SidRenderService : Service() {

    private val executor = Executors.newSingleThreadExecutor()

    private val binder = object : IConverter.Stub() {
        override fun convert(input: ParcelFileDescriptor?, options: Bundle?, callback: IConverterCallback?) {
            if (input == null || callback == null) {
                input?.close()
                return
            }
            val subsong = options?.getInt(KEY_SUBSONG, 0)?.coerceAtLeast(0) ?: 0
            val seconds = options?.getInt(KEY_SECONDS, DEFAULT_SECONDS)?.takeIf { it in 1..MAX_SECONDS } ?: DEFAULT_SECONDS
            executor.execute { run(input, subsong, seconds, callback) }
        }
    }

    private fun run(input: ParcelFileDescriptor, subsong: Int, seconds: Int, callback: IConverterCallback) {
        val out = File.createTempFile("sid_", ".wav", cacheDir)
        try {
            val tune = try {
                readCapped(input)
            } catch (e: IOException) {
                callback.onError(SidError.IO, e.message ?: "read failed")
                return
            }
            val lengthMs = songLength(tune, subsong)
            try {
                WavRenderer.render(tune, subsong, if (lengthMs != null) MAX_SECONDS else seconds, out, lengthMs) { done, total ->
                    runCatching { callback.onProgress(done, total) }
                }
            } catch (e: IOException) {
                Log.w(TAG, "render failed", e)
                callback.onError(SidError.DECODE, e.message ?: "render failed")
                return
            }
            callback.onResult(ParcelFileDescriptor.open(out, ParcelFileDescriptor.MODE_READ_ONLY))
        } catch (e: Exception) {
            Log.e(TAG, "service error", e)
            runCatching { callback.onError(SidError.INTERNAL, e.message ?: "internal error") }
        } finally {
            runCatching { input.close() }
            out.delete()  // the client's dup'd fd keeps the data alive
        }
    }

    private fun songLength(tune: ByteArray, subsong: Int): Long? = runCatching {
        assets.open(SongLengths.ASSET).bufferedReader().use {
            SongLengths.find(it, SongLengths.md5(tune), SongLengths.resolveSong(tune, subsong))
        }
    }.onFailure { Log.w(TAG, "song-length lookup failed", it) }.getOrNull()

    /** A PSID/RSID file is at most ~64 KB of C64 memory plus header; anything far larger isn't a tune. */
    private fun readCapped(input: ParcelFileDescriptor): ByteArray {
        val buf = ByteArrayOutputStream()
        FileInputStream(input.fileDescriptor).use { src ->
            val chunk = ByteArray(16 * 1024)
            while (true) {
                val n = src.read(chunk)
                if (n < 0) break
                buf.write(chunk, 0, n)
                if (buf.size() > MAX_TUNE_BYTES) throw IOException("file too large for a SID tune")
            }
        }
        return buf.toByteArray()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        executor.shutdownNow()  // AnyFile unbound: stop a render nobody will play
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SidRenderService"
        const val KEY_SUBSONG = "subsong"
        const val KEY_SECONDS = "seconds"
        private const val DEFAULT_SECONDS = 180
        private const val MAX_SECONDS = 1200
        private const val MAX_TUNE_BYTES = 1 shl 20
    }
}

/** Error codes passed to IConverterCallback.onError (same values as the other companions). */
internal object SidError {
    const val IO = 1
    const val DECODE = 2
    const val INTERNAL = 3
}
