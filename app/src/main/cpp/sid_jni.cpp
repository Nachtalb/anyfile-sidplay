// SPDX-License-Identifier: GPL-2.0-or-later
#include <jni.h>
#include <string>

#include "sid_core.h"

static void throwIo(JNIEnv* env, const std::string& msg) {
    jclass cls = env->FindClass("java/io/IOException");
    if (cls) env->ThrowNew(cls, msg.c_str());
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_nachtalb_anyfilesidplay_SidNative_nativeOpen(JNIEnv* env, jobject, jbyteArray tune, jint subsong, jint rate) {
    jsize len = env->GetArrayLength(tune);
    jbyte* data = env->GetByteArrayElements(tune, nullptr);
    std::string err;
    SidSession* s = sid_open(reinterpret_cast<const uint8_t*>(data), static_cast<size_t>(len),
                             static_cast<unsigned>(subsong), static_cast<unsigned>(rate), err);
    env->ReleaseByteArrayElements(tune, data, JNI_ABORT);
    if (!s) throwIo(env, err.empty() ? "not a SID tune" : err);
    return reinterpret_cast<jlong>(s);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_nachtalb_anyfilesidplay_SidNative_nativeChannels(JNIEnv*, jobject, jlong h) {
    return static_cast<jint>(sid_channels(reinterpret_cast<SidSession*>(h)));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_nachtalb_anyfilesidplay_SidNative_nativeRender(JNIEnv* env, jobject, jlong h, jshortArray out) {
    auto* s = reinterpret_cast<SidSession*>(h);
    jsize len = env->GetArrayLength(out);
    jshort* buf = env->GetShortArrayElements(out, nullptr);
    int frames = sid_render(s, reinterpret_cast<int16_t*>(buf), static_cast<unsigned>(len) / sid_channels(s));
    env->ReleaseShortArrayElements(out, buf, 0);
    if (frames < 0) throwIo(env, "SID emulation error");
    return frames;
}

extern "C" JNIEXPORT void JNICALL
Java_com_nachtalb_anyfilesidplay_SidNative_nativeClose(JNIEnv*, jobject, jlong h) {
    sid_close(reinterpret_cast<SidSession*>(h));
}
