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

#ifndef _Included_org_drrickorang_loopback_sles
#define _Included_org_drrickorang_loopback_sles

#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#include <pthread.h>
#include <android/log.h>
#include <jni.h>
#include <stdbool.h>

//struct audio_utils_fifo;
#define SLES_PRINTF(...)  __android_log_print(ANDROID_LOG_INFO, "sles_jni", __VA_ARGS__);

#include <audio_utils/fifo.h>

#include "loopback_sles.h"

typedef struct {
    int* buffer_period;
    struct timespec previous_time;
    struct timespec current_time;
    int buffer_count;
    int max_buffer_period;

    volatile int32_t captureRank;   // Set > 0 when the callback requests a systrace/bug report

    int measurement_count; // number of measurements which were actually recorded
    int64_t SDM; // sum of squares of deviations from the expected mean
    int64_t var; // variance in nanoseconds^2
} bufferStats;

//TODO fix this
typedef struct {
    SLuint32 rxBufCount;     // -r#
    SLuint32 txBufCount;     // -t#
    SLuint32 bufSizeInFrames;  // -f#
    SLuint32 channels;       // -c#
    SLuint32 sampleRate; // -s#
    SLuint32 exitAfterSeconds; // -e#
    SLuint32 freeBufCount;   // calculated
    SLuint32 bufSizeInBytes; // calculated
    int injectImpulse; // -i#i
    size_t totalDiscardedInputFrames;   // total number of input frames discarded
    int ignoreFirstFrames;

    // Storage area for the buffer queues
    char **rxBuffers;
    char **txBuffers;
    char **freeBuffers;

    // Buffer indices
    SLuint32 rxFront;    // oldest recording
    SLuint32 rxRear;     // next to be recorded
    SLuint32 txFront;    // oldest playing
    SLuint32 txRear;     // next to be played
    SLuint32 freeFront;  // oldest free
    SLuint32 freeRear;   // next to be freed

    struct audio_utils_fifo fifo;   // jitter buffer between recorder and player callbacks,
                                    // to mitigate unpredictable phase difference between these,
                                    // or even concurrent callbacks on two CPU cores
    struct audio_utils_fifo fifo2;  // For sending data to java code (to plot it)
    short *fifo2Buffer;
    short *fifoBuffer;
    SLAndroidSimpleBufferQueueItf recorderBufferQueue;
    SLBufferQueueItf playerBufferQueue;

    //other things that belong here
    SLObjectItf playerObject;
    SLObjectItf recorderObject;
    SLObjectItf outputmixObject;
    SLObjectItf engineObject;

    bufferStats recorderBufferStats;
    bufferStats playerBufferStats;

    int testType;
    double frequency1;
    double bufferTestPhase1;
    int count;
    char* byteBufferPtr;
    int byteBufferLength;

    short* loopbackTone;

    callbackTimeStamps recorderTimeStamps;
    callbackTimeStamps playerTimeStamps;
    short expectedBufferPeriod;
} sles_data;

// how late in ms a callback must be to trigger a systrace/bugreport
#define LATE_CALLBACK_CAPTURE_THRESHOLD 4
#define LATE_CALLBACK_OUTLIER_THRESHOLD 1
#define BUFFER_PERIOD_DISCARD 10
#define BUFFER_PERIOD_DISCARD_FULL_DUPLEX_PARTNER 2

#endif //_Included_org_drrickorang_loopback_sles
