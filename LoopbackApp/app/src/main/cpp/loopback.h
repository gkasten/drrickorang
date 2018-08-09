/*
 * Copyright (C) 2017 The Android Open Source Project
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

#ifndef _Included_org_drrickorang_loopback_loopback
#define _Included_org_drrickorang_loopback_loopback

#include <stdbool.h>
#include <time.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    int* timeStampsMs;          // Array of milliseconds since first callback
    short* callbackDurations;   // Array of milliseconds between callback and previous callback
    short index;                // Current write position
    struct timespec startTime;  // Time of first callback {seconds,nanoseconds}
    int capacity;               // Total number of callback times/lengths that can be recorded
    bool exceededCapacity;      // Set only if late callbacks come after array is full
} callbackTimeStamps;

#define NANOS_PER_SECOND 1000000000
#define NANOS_PER_MILLI 1000000
#define MILLIS_PER_SECOND 1000

enum STATUS_ENUM {
    STATUS_SUCCESS = 0,
    STATUS_FAIL = 1
};

enum JAVA_CONSTANTS_ENUM {
    // Must match constant 'range' in BufferPeriod.java
    RANGE = 1002,
    // Must match constants in Constant.java
    TEST_TYPE_LATENCY = 222,
    TEST_TYPE_BUFFER_PERIOD = 223,
    AUDIO_THREAD_TYPE_JAVA = 0,
    AUDIO_THREAD_TYPE_NATIVE_SLES = 1,
    AUDIO_THREAD_TYPE_NATIVE_AAUDIO = 2,
};

typedef struct {
    int (*computeDefaultSettings)(int performanceMode, int *samplingRate,
            int *playerBufferFrameCount, int *recorderBufferFrameCount);
    int (*init)(void **ppCtx, int samplingRate, int frameCount, int micSource,
            int performanceMode,
            int testType, double frequency1, char* byteBufferPtr, int byteBufferLength,
            short* loopbackTone, int maxRecordedLateCallbacks, int ignoreFirstFrames);
    int (*destroy)(void **ppCtx);
    int (*processNext)(void *pCtx, double *pSamples, long maxSamples);
    int* (*getRecorderBufferPeriod)(void *pCtx);
    int (*getRecorderMaxBufferPeriod)(void *pCtx);
    int64_t (*getRecorderVarianceBufferPeriod)(void *pCtx);
    int* (*getPlayerBufferPeriod)(void *pCtx);
    int (*getPlayerMaxBufferPeriod)(void *pCtx);
    int64_t (*getPlayerVarianceBufferPeriod)(void *pCtx);
    int (*getCaptureRank)(void *pCtx);
    int (*getPlayerTimeStampsAndExpectedBufferPeriod)(void *pCtx, callbackTimeStamps **ppTSs);
    int (*getRecorderTimeStampsAndExpectedBufferPeriod)(void *pCtx, callbackTimeStamps **ppTSs);
} native_engine_t;

typedef struct {
    void *context;
    native_engine_t *methods;
} native_engine_instance_t;

enum NATIVE_ENGINE_ENUM {
    NATIVE_ENGINE_SLES = 0,
    NATIVE_ENGINE_AAUDIO = 1,
    NATIVE_ENGINE_COUNT = NATIVE_ENGINE_AAUDIO + 1
};

extern native_engine_t sEngines[NATIVE_ENGINE_COUNT];

#ifdef __cplusplus
}
#endif

#endif  // _Included_org_drrickorang_loopback_loopback
