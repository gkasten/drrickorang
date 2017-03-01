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

#include <jni.h>

#ifndef _Included_org_drrickorang_loopback_jni
#define _Included_org_drrickorang_loopback_jni
#ifdef __cplusplus
extern "C" {
#endif


////////////////////////
////SLE
JNIEXPORT jlong JNICALL Java_org_drrickorang_loopback_NativeAudioThread_slesInit
  (JNIEnv *, jobject, jint, jint, jint, jint, jint, jdouble, jobject byteBuffer,
   jshortArray loopbackTone, jint maxRecordedLateCallbacks, jint ignoreFirstFrames);

JNIEXPORT jint JNICALL Java_org_drrickorang_loopback_NativeAudioThread_slesProcessNext
  (JNIEnv *, jobject, jlong, jdoubleArray, jlong);

JNIEXPORT jint JNICALL Java_org_drrickorang_loopback_NativeAudioThread_slesDestroy
  (JNIEnv *, jobject, jlong);

JNIEXPORT jintArray JNICALL
        Java_org_drrickorang_loopback_NativeAudioThread_slesGetRecorderBufferPeriod
  (JNIEnv *, jobject, jlong);

JNIEXPORT jint JNICALL
        Java_org_drrickorang_loopback_NativeAudioThread_slesGetRecorderMaxBufferPeriod
  (JNIEnv *, jobject, jlong);

JNIEXPORT jdouble JNICALL
        Java_org_drrickorang_loopback_NativeAudioThread_slesGetRecorderVarianceBufferPeriod
  (JNIEnv *, jobject, jlong);

JNIEXPORT jintArray JNICALL
        Java_org_drrickorang_loopback_NativeAudioThread_slesGetPlayerBufferPeriod
  (JNIEnv *, jobject, jlong);

JNIEXPORT jint JNICALL
        Java_org_drrickorang_loopback_NativeAudioThread_slesGetPlayerMaxBufferPeriod
  (JNIEnv *, jobject, jlong);

JNIEXPORT jdouble JNICALL
        Java_org_drrickorang_loopback_NativeAudioThread_slesGetPlayerVarianceBufferPeriod
  (JNIEnv *, jobject, jlong);

JNIEXPORT jint JNICALL
        Java_org_drrickorang_loopback_NativeAudioThread_slesGetCaptureRank
  (JNIEnv *, jobject, jlong);

#ifdef __cplusplus
}
#endif
#endif //_Included_org_drrickorang_loopback_jni
