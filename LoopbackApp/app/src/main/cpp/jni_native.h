/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef _Included_org_drrickorang_loopback_jni
#define _Included_org_drrickorang_loopback_jni

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif


////////////////////////
JNIEXPORT jobject JNICALL
Java_org_drrickorang_loopback_NativeAudioThread_nativeComputeDefaultSettings
(JNIEnv *, jobject, jint bytesPerFrame, jint threadType, jint performanceMode);

JNIEXPORT jlong JNICALL Java_org_drrickorang_loopback_NativeAudioThread_nativeInit
  (JNIEnv *, jobject, jint, jint, jint, jint, jint, jint, jdouble, jobject byteBuffer,
   jshortArray loopbackTone, jint maxRecordedLateCallbacks, jint ignoreFirstFrames);

JNIEXPORT jint JNICALL Java_org_drrickorang_loopback_NativeAudioThread_nativeProcessNext
  (JNIEnv *, jobject, jlong, jdoubleArray, jlong);

JNIEXPORT jint JNICALL Java_org_drrickorang_loopback_NativeAudioThread_nativeDestroy
  (JNIEnv *, jobject, jlong);

JNIEXPORT jintArray JNICALL
        Java_org_drrickorang_loopback_NativeAudioThread_nativeGetRecorderBufferPeriod
  (JNIEnv *, jobject, jlong);

JNIEXPORT jint JNICALL
        Java_org_drrickorang_loopback_NativeAudioThread_nativeGetRecorderMaxBufferPeriod
  (JNIEnv *, jobject, jlong);

JNIEXPORT jdouble JNICALL
        Java_org_drrickorang_loopback_NativeAudioThread_nativeGetRecorderVarianceBufferPeriod
  (JNIEnv *, jobject, jlong);

JNIEXPORT jintArray JNICALL
        Java_org_drrickorang_loopback_NativeAudioThread_nativeGetPlayerBufferPeriod
  (JNIEnv *, jobject, jlong);

JNIEXPORT jint JNICALL
        Java_org_drrickorang_loopback_NativeAudioThread_nativeGetPlayerMaxBufferPeriod
  (JNIEnv *, jobject, jlong);

JNIEXPORT jdouble JNICALL
        Java_org_drrickorang_loopback_NativeAudioThread_nativeGetPlayerVarianceBufferPeriod
  (JNIEnv *, jobject, jlong);

JNIEXPORT jint JNICALL
        Java_org_drrickorang_loopback_NativeAudioThread_nativeGetCaptureRank
  (JNIEnv *, jobject, jlong);

#ifdef __cplusplus
}
#endif

#endif //_Included_org_drrickorang_loopback_jni
