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

#include "jni_native.h"

#include <stdlib.h>

#include <android/log.h>

#include "loopback.h"

#define LOG_TAG "jni_native"

static int nativeEngineFromThreadType(int threadType) {
    switch (threadType) {
        case AUDIO_THREAD_TYPE_NATIVE_SLES: return NATIVE_ENGINE_SLES;
        case AUDIO_THREAD_TYPE_NATIVE_AAUDIO: return NATIVE_ENGINE_AAUDIO;
    }
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
            "unsupported thread type %d", threadType);
    return -1;
}

JNIEXPORT jobject JNICALL
Java_org_drrickorang_loopback_NativeAudioThread_nativeComputeDefaultSettings
(JNIEnv *env, jobject obj __unused, jint bytesPerFrame, jint threadType, jint performanceMode) {
    int engine = nativeEngineFromThreadType(threadType);
    if (engine == -1) return NULL;
    int samplingRate, playerBufferFrameCount, recorderBufferFrameCount;
    if (sEngines[engine].computeDefaultSettings(performanceMode, &samplingRate,
                    &playerBufferFrameCount, &recorderBufferFrameCount) == STATUS_SUCCESS) {
        jclass cls = (*env)->FindClass(env, "org/drrickorang/loopback/TestSettings");
        jmethodID methodID = (*env)->GetMethodID(env, cls, "<init>", "(III)V");
        jobject testSettings = (*env)->NewObject(env, cls, methodID,
                samplingRate,
                playerBufferFrameCount * bytesPerFrame,
                recorderBufferFrameCount * bytesPerFrame);
        return testSettings;
    } else {
        return NULL;
    }
}

JNIEXPORT jlong JNICALL Java_org_drrickorang_loopback_NativeAudioThread_nativeInit
  (JNIEnv *env, jobject obj __unused, jint threadType, jint samplingRate, jint frameCount,
   jint micSource, jint performanceMode,
   jint testType, jdouble frequency1, jobject byteBuffer, jshortArray loopbackTone,
   jint maxRecordedLateCallbacks, jint ignoreFirstFrames) {

    int engine = nativeEngineFromThreadType(threadType);
    if (engine == -1) return 0;

    native_engine_instance_t *pInstance =
            (native_engine_instance_t*) malloc(sizeof(native_engine_instance_t));
    if (pInstance == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                "failed to allocate a native engine instance");
        return 0;
    }
    void *pContext = NULL;

    char *byteBufferPtr = (*env)->GetDirectBufferAddress(env, byteBuffer);
    int byteBufferLength = (*env)->GetDirectBufferCapacity(env, byteBuffer);

    short *loopbackToneArray = (*env)->GetShortArrayElements(env, loopbackTone, 0);

    if (sEngines[engine].init(&pContext, samplingRate, frameCount, micSource,
                 performanceMode,
                 testType, frequency1, byteBufferPtr, byteBufferLength,
                 loopbackToneArray, maxRecordedLateCallbacks, ignoreFirstFrames) != STATUS_FAIL) {
        pInstance->context = pContext;
        pInstance->methods = &sEngines[engine];
        return (long) pInstance;
    }

    free(pInstance);
    return 0;
}


JNIEXPORT jint JNICALL Java_org_drrickorang_loopback_NativeAudioThread_nativeProcessNext
(JNIEnv *env __unused, jobject obj __unused, jlong handle, jdoubleArray samplesArray,
jlong offset) {
    native_engine_instance_t *pInstance = (native_engine_instance_t*) handle;

    long maxSamples = (*env)->GetArrayLength(env, samplesArray);
    double *pSamples = (*env)->GetDoubleArrayElements(env, samplesArray, 0);

    long availableSamples = maxSamples-offset;
    double *pCurrentSample = pSamples+offset;

    __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
            "jni nativeProcessNext currentSample %p, availableSamples %ld ",
            pCurrentSample, availableSamples);

    int samplesRead = pInstance->methods->processNext(
            pInstance->context, pCurrentSample, availableSamples);
    return samplesRead;
}


JNIEXPORT jint JNICALL Java_org_drrickorang_loopback_NativeAudioThread_nativeDestroy
  (JNIEnv *env __unused, jobject obj __unused, jlong handle) {
    native_engine_instance_t *pInstance = (native_engine_instance_t*) handle;
    int status = pInstance->methods->destroy(&pInstance->context);
    free(pInstance);
    return status;
}


JNIEXPORT jintArray JNICALL
        Java_org_drrickorang_loopback_NativeAudioThread_nativeGetRecorderBufferPeriod
  (JNIEnv *env, jobject obj __unused, jlong handle) {
    native_engine_instance_t *pInstance = (native_engine_instance_t*) handle;
    int* recorderBufferPeriod = pInstance->methods->getRecorderBufferPeriod(
            pInstance->context);

    // get the length = RANGE
    jintArray result = (*env)->NewIntArray(env, RANGE);
    (*env)->SetIntArrayRegion(env, result, 0, RANGE, recorderBufferPeriod);

    return result;
}


JNIEXPORT jint JNICALL
        Java_org_drrickorang_loopback_NativeAudioThread_nativeGetRecorderMaxBufferPeriod
  (JNIEnv *env __unused, jobject obj __unused, jlong handle) {
    native_engine_instance_t *pInstance = (native_engine_instance_t*) handle;
    int recorderMaxBufferPeriod = pInstance->methods->getRecorderMaxBufferPeriod(
            pInstance->context);

    return recorderMaxBufferPeriod;
}


JNIEXPORT jdouble JNICALL
        Java_org_drrickorang_loopback_NativeAudioThread_nativeGetRecorderVarianceBufferPeriod
        (JNIEnv *env __unused, jobject obj __unused, jlong handle) {
    native_engine_instance_t *pInstance = (native_engine_instance_t*) handle;
    int64_t result = pInstance->methods->getRecorderVarianceBufferPeriod(pInstance->context);
    // variance has units ns^2 so we have to square the conversion factor
    double scaled = (double) result / ((double) NANOS_PER_MILLI * (double) NANOS_PER_MILLI);
    return scaled;
}


JNIEXPORT jintArray
JNICALL Java_org_drrickorang_loopback_NativeAudioThread_nativeGetPlayerBufferPeriod
  (JNIEnv *env __unused, jobject obj __unused, jlong handle) {
    native_engine_instance_t *pInstance = (native_engine_instance_t*) handle;
    int* playerBufferPeriod = pInstance->methods->getPlayerBufferPeriod(pInstance->context);

    jintArray result = (*env)->NewIntArray(env, RANGE);
    (*env)->SetIntArrayRegion(env, result, 0, RANGE, playerBufferPeriod);

    return result;
}


JNIEXPORT jint JNICALL
        Java_org_drrickorang_loopback_NativeAudioThread_nativeGetPlayerMaxBufferPeriod
  (JNIEnv *env __unused, jobject obj __unused, jlong handle) {
    native_engine_instance_t *pInstance = (native_engine_instance_t*) handle;
    int playerMaxBufferPeriod = pInstance->methods->getPlayerMaxBufferPeriod(pInstance->context);

    return playerMaxBufferPeriod;
}


JNIEXPORT jdouble JNICALL
Java_org_drrickorang_loopback_NativeAudioThread_nativeGetPlayerVarianceBufferPeriod
        (JNIEnv *env __unused, jobject obj __unused, jlong handle) {
    native_engine_instance_t *pInstance = (native_engine_instance_t*) handle;
    int64_t result = pInstance->methods->getPlayerVarianceBufferPeriod(pInstance->context);
    // variance has units ns^2 so we have to square the conversion factor
    double scaled = (double) result / ((double) NANOS_PER_MILLI * (double) NANOS_PER_MILLI);
    return scaled;
}


jobject getCallbackTimes(JNIEnv *env, callbackTimeStamps *callbacks, short expectedBufferPeriod){
    jintArray timeStamps = (*env)->NewIntArray(env, callbacks->index);
    (*env)->SetIntArrayRegion(env, timeStamps, 0, callbacks->index, callbacks->timeStampsMs);

    jshortArray callbackLengths = (*env)->NewShortArray(env, callbacks->index);
    (*env)->SetShortArrayRegion(env, callbackLengths, 0, callbacks->index,
                                callbacks->callbackDurations);

    jclass cls = (*env)->FindClass(env, "org/drrickorang/loopback/BufferCallbackTimes");
    jmethodID methodID = (*env)->GetMethodID(env, cls, "<init>", "([I[SZS)V");
    jobject callbackTimes=(*env)->NewObject(env,cls, methodID, timeStamps, callbackLengths,
                                            callbacks->exceededCapacity, expectedBufferPeriod);
    return callbackTimes;
}

JNIEXPORT jobject
JNICALL Java_org_drrickorang_loopback_NativeAudioThread_nativeGetPlayerCallbackTimeStamps
        (JNIEnv *env, jobject obj __unused, jlong handle) {
    native_engine_instance_t *pInstance = (native_engine_instance_t*) handle;
    callbackTimeStamps *pTSs;
    int expectedBufferPeriod = pInstance->methods->getPlayerTimeStampsAndExpectedBufferPeriod(
            pInstance->context, &pTSs);
    return getCallbackTimes(env, pTSs, expectedBufferPeriod);
}

JNIEXPORT jobject
JNICALL Java_org_drrickorang_loopback_NativeAudioThread_nativeGetRecorderCallbackTimeStamps
        (JNIEnv *env, jobject obj __unused, jlong handle) {
    native_engine_instance_t *pInstance = (native_engine_instance_t*) handle;
    callbackTimeStamps *pTSs;
    int expectedBufferPeriod = pInstance->methods->getRecorderTimeStampsAndExpectedBufferPeriod(
            pInstance->context, &pTSs);
    return getCallbackTimes(env, pTSs, expectedBufferPeriod);
}

JNIEXPORT jint
JNICALL Java_org_drrickorang_loopback_NativeAudioThread_nativeGetCaptureRank
        (JNIEnv *env __unused, jobject obj __unused, jlong handle) {
    native_engine_instance_t *pInstance = (native_engine_instance_t*) handle;
    return pInstance->methods->getCaptureRank(pInstance->context);
}
