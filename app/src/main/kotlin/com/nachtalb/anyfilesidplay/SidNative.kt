// SPDX-License-Identifier: GPL-2.0-or-later
package com.nachtalb.anyfilesidplay

/** JNI wrapper over libsidplayfp + reSIDfp (`libsidjni.so`). One handle = one emulated C64 playing one subsong. */
internal object SidNative {

    init {
        System.loadLibrary("sidjni")
    }

    /** [subsong] is 1-based, 0 = the tune's start song. Throws IOException when the bytes aren't a playable tune. */
    external fun nativeOpen(tune: ByteArray, subsong: Int, rate: Int): Long

    /** 1 for single-SID tunes, 2 (interleaved stereo) for 2SID/3SID tunes. */
    external fun nativeChannels(handle: Long): Int

    /** Fills [out] with interleaved 16-bit PCM; returns frames written. */
    external fun nativeRender(handle: Long, out: ShortArray): Int

    external fun nativeClose(handle: Long)
}
