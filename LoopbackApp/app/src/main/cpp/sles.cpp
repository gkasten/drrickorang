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

// FIXME taken from OpenSLES_AndroidConfiguration.h
#define SL_ANDROID_KEY_PERFORMANCE_MODE  ((const SLchar*) "androidPerformanceMode")

////////////////////////////////////////////
/// Actual sles functions.


// Test program to record from default audio input and playback to default audio output.
// It will generate feedback (Larsen effect) if played through on-device speakers,
// or acts as a delay if played through headset.

#define _USE_MATH_DEFINES
#include <cmath>
#include "sles.h"
#include "audio_utils/atomic.h"
#include "byte_buffer.h"
#include <unistd.h>
#include <string.h>

static int slesCreateServer(sles_data *pSles, int samplingRate, int frameCount, int micSource,
        int performanceMode,
        int testType, double frequency1, char* byteBufferPtr, int byteBufferLength,
        short* loopbackTone, int maxRecordedLateCallbacks, int ignoreFirstFrames);
static int slesDestroyServer(sles_data *pSles);

static void initBufferStats(bufferStats *stats);
static void collectBufferPeriod(bufferStats *stats, bufferStats *fdpStats,
        callbackTimeStamps *timeStamps, short expectedBufferPeriod);
static bool updateBufferStats(bufferStats *stats, int64_t diff_in_nano, int expectedBufferPeriod);
static void recordTimeStamp(callbackTimeStamps *timeStamps,
        int64_t callbackDuration, int64_t timeStamp);

int slesComputeDefaultSettings(int /*performanceMode*/, int* /*samplingRate*/,
            int* /*playerBufferFrameCount*/, int* /*recorderBufferFrameCount*/) {
    // For OpenSL ES, these parameters can be determined by NativeAudioThread itself.
    return STATUS_FAIL;
}

int slesInit(void ** ppCtx, int samplingRate, int frameCount, int micSource,
             int performanceMode,
             int testType, double frequency1, char* byteBufferPtr, int byteBufferLength,
             short* loopbackTone, int maxRecordedLateCallbacks, int ignoreFirstFrames) {
    sles_data ** ppSles = (sles_data**) ppCtx;
    int status = STATUS_FAIL;
    if (ppSles != NULL) {
        sles_data * pSles = (sles_data*) calloc(1, sizeof(sles_data));

        SLES_PRINTF("pSles malloc %zu bytes at %p", sizeof(sles_data), pSles);
        //__android_log_print(ANDROID_LOG_INFO, "sles_jni",
        //"malloc %d bytes at %p", sizeof(sles_data), pSles);//Or ANDROID_LOG_INFO, ...
        *ppSles = pSles;
        if (pSles != NULL)
        {
            SLES_PRINTF("creating server. Sampling rate =%d, frame count = %d",
                        samplingRate, frameCount);
            status = slesCreateServer(pSles, samplingRate, frameCount, micSource,
                                      performanceMode, testType,
                                      frequency1, byteBufferPtr, byteBufferLength, loopbackTone,
                                      maxRecordedLateCallbacks, ignoreFirstFrames);
            SLES_PRINTF("slesCreateServer =%d", status);
        }
    }

    return status;
}
int slesDestroy(void ** ppCtx) {
    sles_data ** ppSles = (sles_data**)ppCtx;
    int status = STATUS_FAIL;
    if (ppSles != NULL) {
        slesDestroyServer(*ppSles);

        if (*ppSles != NULL)
        {
            SLES_PRINTF("free memory at %p",*ppSles);
            free(*ppSles);
            *ppSles = 0;
        }
        status = STATUS_SUCCESS;
    }
    return status;
}

#define ASSERT(x) do { if(!(x)) { __android_log_assert("assert", "sles_jni", \
                    "ASSERTION FAILED: " #x); } } while (0)
#define ASSERT_EQ(x, y) do { if ((x) == (y)) ; else __android_log_assert("assert", "sles_jni", \
                    "ASSERTION FAILED: 0x%x != 0x%x\n", (unsigned) (x), (unsigned) (y)); } while (0)

// Called after audio recorder fills a buffer with data, then we can read from this filled buffer
static void recorderCallback(SLAndroidSimpleBufferQueueItf caller __unused, void *context) {
    sles_data *pSles = (sles_data*) context;
    if (pSles != NULL) {
        collectBufferPeriod(&pSles->recorderBufferStats, NULL /*fdpStats*/,
                            &pSles->recorderTimeStamps, pSles->expectedBufferPeriod);

        //__android_log_print(ANDROID_LOG_INFO, "sles_jni", "in recorderCallback");
        SLresult result;

        //ee  SLES_PRINTF("<R");

        // We should only be called when a recording buffer is done
        ASSERT(pSles->rxFront <= pSles->rxBufCount);
        ASSERT(pSles->rxRear <= pSles->rxBufCount);
        ASSERT(pSles->rxFront != pSles->rxRear);
        char *buffer = pSles->rxBuffers[pSles->rxFront]; //pSles->rxBuffers stores the data recorded


        // Remove buffer from record queue
        if (++pSles->rxFront > pSles->rxBufCount) {
            pSles->rxFront = 0;
        }

        if (pSles->testType == TEST_TYPE_LATENCY) {
            // Throw out first frames
            if (pSles->ignoreFirstFrames) {
                int framesToErase = pSles->ignoreFirstFrames;
                if (framesToErase > (int) pSles->bufSizeInFrames) {
                    framesToErase = pSles->bufSizeInFrames;
                }
                pSles->ignoreFirstFrames -= framesToErase;
                memset(buffer, 0, framesToErase * pSles->channels * sizeof(short));
            }

            ssize_t actual = audio_utils_fifo_write(&(pSles->fifo), buffer,
                    (size_t) pSles->bufSizeInFrames);

            if (actual != (ssize_t) pSles->bufSizeInFrames) {
                write(1, "?", 1);
            }

            // This is called by a realtime (SCHED_FIFO) thread,
            // and it is unsafe to do I/O as it could block for unbounded time.
            // Flash filesystem is especially notorious for blocking.
            if (pSles->fifo2Buffer != NULL) {
                actual = audio_utils_fifo_write(&(pSles->fifo2), buffer,
                        (size_t) pSles->bufSizeInFrames);
                if (actual != (ssize_t) pSles->bufSizeInFrames) {
                    write(1, "?", 1);
                }
            }
        } else if (pSles->testType == TEST_TYPE_BUFFER_PERIOD) {
            if (pSles->fifo2Buffer != NULL) {
                ssize_t actual = byteBuffer_write(pSles->byteBufferPtr, pSles->byteBufferLength,
                        buffer, (size_t) pSles->bufSizeInFrames, pSles->channels);

                //FIXME should log errors using other methods instead of printing to terminal
                if (actual != (ssize_t) pSles->bufSizeInFrames) {
                    write(1, "?", 1);
                }
            }
        }


        // Enqueue this same buffer for the recorder to fill again.
        result = (*(pSles->recorderBufferQueue))->Enqueue(pSles->recorderBufferQueue, buffer,
                                                          pSles->bufSizeInBytes);
        //__android_log_print(ANDROID_LOG_INFO, "recorderCallback", "recorder buffer size: %i",
        //                    pSles->bufSizeInBytes);
        ASSERT_EQ(SL_RESULT_SUCCESS, result);


        // Update our model of the record queue
        SLuint32 rxRearNext = pSles->rxRear + 1;
        if (rxRearNext > pSles->rxBufCount) {
            rxRearNext = 0;
        }
        ASSERT(rxRearNext != pSles->rxFront);
        pSles->rxBuffers[pSles->rxRear] = buffer;
        pSles->rxRear = rxRearNext;



      //ee  SLES_PRINTF("r>");

    } //pSles not null
}


// Calculate nanosecond difference between two timespec structs from clock_gettime(CLOCK_MONOTONIC)
// tv_sec [0, max time_t] , tv_nsec [0, 999999999]
static int64_t diffInNano(struct timespec previousTime, struct timespec currentTime) {
    return (int64_t) (currentTime.tv_sec - previousTime.tv_sec) * (int64_t) NANOS_PER_SECOND +
            currentTime.tv_nsec - previousTime.tv_nsec;
}

// Called after audio player empties a buffer of data
static void playerCallback(SLBufferQueueItf caller __unused, void *context) {
    sles_data *pSles = (sles_data*) context;
    if (pSles != NULL) {
        collectBufferPeriod(&pSles->playerBufferStats, &pSles->recorderBufferStats /*fdpStats*/,
                            &pSles->playerTimeStamps, pSles->expectedBufferPeriod);
        SLresult result;

        //ee  SLES_PRINTF("<P");

        // Get the buffer that just finished playing
        ASSERT(pSles->txFront <= pSles->txBufCount);
        ASSERT(pSles->txRear <= pSles->txBufCount);
        ASSERT(pSles->txFront != pSles->txRear);
        char *buffer = pSles->txBuffers[pSles->txFront];
        if (++pSles->txFront > pSles->txBufCount) {
            pSles->txFront = 0;
        }

        if (pSles->testType == TEST_TYPE_LATENCY) {
            // Jitter buffer should have strictly less than 2 buffers worth of data in it.
            // This is to prevent the test itself from adding too much latency.
            size_t discardedInputFrames = 0;
            for (;;) {
                size_t availToRead = audio_utils_fifo_availToRead(&pSles->fifo);
                if (availToRead < pSles->bufSizeInFrames * 2) {
                    break;
                }
                ssize_t actual = audio_utils_fifo_read(&pSles->fifo, buffer,
                        pSles->bufSizeInFrames);
                if (actual > 0) {
                    discardedInputFrames += actual;
                }
                if (actual != (ssize_t) pSles->bufSizeInFrames) {
                    break;
                }
            }
            if (discardedInputFrames > 0) {
                if (pSles->totalDiscardedInputFrames > 0) {
                    __android_log_print(ANDROID_LOG_WARN, "sles_jni",
                        "Discarded an additional %zu input frames after a total of %zu input frames"
                        " had previously been discarded",
                        discardedInputFrames, pSles->totalDiscardedInputFrames);
                }
                pSles->totalDiscardedInputFrames += discardedInputFrames;
            }

            ssize_t actual = audio_utils_fifo_read(&(pSles->fifo), buffer, pSles->bufSizeInFrames);
            if (actual != (ssize_t) pSles->bufSizeInFrames) {
                write(1, "/", 1);
                // on underrun from pipe, substitute silence
                memset(buffer, 0, pSles->bufSizeInFrames * pSles->channels * sizeof(short));
            }

            if (pSles->injectImpulse == -1) {   // here we inject pulse

                /*// Experimentally, a single frame impulse was insufficient to trigger feedback.
                // Also a Nyquist frequency signal was also insufficient, probably because
                // the response of output and/or input path was not adequate at high frequencies.
                // This short burst of a few cycles of square wave at Nyquist/4 found to work well.
                for (unsigned i = 0; i < pSles->bufSizeInFrames / 8; i += 8) {
                    for (int j = 0; j < 8; j++) {
                        for (unsigned k = 0; k < pSles->channels; k++) {
                            ((short *) buffer)[(i + j) * pSles->channels + k] =
                                                                            j < 4 ? 0x7FFF : 0x8000;
                        }
                    }
                }*/

                //inject java generated tone
                for (unsigned i = 0; i < pSles->bufSizeInFrames; ++i) {
                    for (unsigned k = 0; k < pSles->channels; ++k) {
                        ((short *) buffer)[i * pSles->channels + k] = pSles->loopbackTone[i];
                    }
                }

                pSles->injectImpulse = 0;
                pSles->totalDiscardedInputFrames = 0;
            }
        } else if (pSles->testType == TEST_TYPE_BUFFER_PERIOD) {
            double twoPi = M_PI * 2;
            int maxShort = 32767;
            float amplitude = 0.8;
            short value;
            double phaseIncrement = pSles->frequency1 / pSles->sampleRate;
            bool isGlitchEnabled = false;
            for (unsigned i = 0; i < pSles->bufSizeInFrames; i++) {
                value = (short) (sin(pSles->bufferTestPhase1) * maxShort * amplitude);
                for (unsigned k = 0; k < pSles->channels; ++k) {
                    ((short *) buffer)[i* pSles->channels + k] = value;
                }

                pSles->bufferTestPhase1 += twoPi * phaseIncrement;
                // insert glitches if isGlitchEnabled == true, and insert it for every second
                if (isGlitchEnabled && (pSles->count % pSles->sampleRate == 0)) {
                    pSles->bufferTestPhase1 += twoPi * phaseIncrement;
                }

                pSles->count++;

                while (pSles->bufferTestPhase1 > twoPi) {
                    pSles->bufferTestPhase1 -= twoPi;
                }
            }
        }

        // Enqueue the filled buffer for playing
        result = (*(pSles->playerBufferQueue))->Enqueue(pSles->playerBufferQueue, buffer,
                                                        pSles->bufSizeInBytes);
        ASSERT_EQ(SL_RESULT_SUCCESS, result);

        // Update our model of the player queue
        ASSERT(pSles->txFront <= pSles->txBufCount);
        ASSERT(pSles->txRear <= pSles->txBufCount);
        SLuint32 txRearNext = pSles->txRear + 1;
        if (txRearNext > pSles->txBufCount) {
            txRearNext = 0;
        }
        ASSERT(txRearNext != pSles->txFront);
        pSles->txBuffers[pSles->txRear] = buffer;
        pSles->txRear = txRearNext;

    } //pSles not null
}

// Used to set initial values for the bufferStats struct before values can be recorded.
static void initBufferStats(bufferStats *stats) {
    stats->buffer_period = new int[RANGE](); // initialized to zeros
    stats->previous_time = {0,0};
    stats->current_time = {0,0};

    stats->buffer_count = 0;
    stats->max_buffer_period = 0;

    stats->measurement_count = 0;
    stats->SDM = 0;
    stats->var = 0;
}

// Called in the beginning of playerCallback() to collect the interval between each callback.
// fdpStats is either NULL or a pointer to the buffer statistics for the full-duplex partner.
static void collectBufferPeriod(bufferStats *stats, bufferStats *fdpStats,
        callbackTimeStamps *timeStamps, short expectedBufferPeriod) {
    clock_gettime(CLOCK_MONOTONIC, &(stats->current_time));

    if (timeStamps->startTime.tv_sec == 0 && timeStamps->startTime.tv_nsec == 0) {
        timeStamps->startTime = stats->current_time;
    }

    (stats->buffer_count)++;

    if (stats->previous_time.tv_sec != 0 && stats->buffer_count > BUFFER_PERIOD_DISCARD &&
         (fdpStats == NULL || fdpStats->buffer_count > BUFFER_PERIOD_DISCARD_FULL_DUPLEX_PARTNER)) {

        int64_t callbackDuration = diffInNano(stats->previous_time, stats->current_time);

        bool outlier = updateBufferStats(stats, callbackDuration, expectedBufferPeriod);

        //recording timestamps of buffer periods not at expected buffer period
        if (outlier) {
            int64_t timeStamp = diffInNano(timeStamps->startTime, stats->current_time);
            recordTimeStamp(timeStamps, callbackDuration, timeStamp);
        }
    }

    stats->previous_time = stats->current_time;
}

// Records an outlier given the duration in nanoseconds and the number of nanoseconds
// between it and the start of the test.
static void recordTimeStamp(callbackTimeStamps *timeStamps,
        int64_t callbackDuration, int64_t timeStamp) {
    if (timeStamps->exceededCapacity) {
        return;
    }

    //only marked as exceeded if attempting to record a late callback after arrays full
    if (timeStamps->index == timeStamps->capacity){
        timeStamps->exceededCapacity = true;
    } else {
        timeStamps->callbackDurations[timeStamps->index] =
                (short) ((callbackDuration + NANOS_PER_MILLI - 1) / NANOS_PER_MILLI);
        timeStamps->timeStampsMs[timeStamps->index] =
                (int) ((timeStamp + NANOS_PER_MILLI - 1) / NANOS_PER_MILLI);
        timeStamps->index++;
    }
}

static void atomicSetIfGreater(volatile int32_t *addr, int32_t val) {
    // TODO: rewrite this to avoid the need for unbounded spinning
    int32_t old;
    do {
        old = *addr;
        if (val < old) return;
    } while(!android_atomic_compare_exchange(&old, val, addr));
}

// Updates the stats being collected about buffer periods. Returns true if this is an outlier.
static bool updateBufferStats(bufferStats *stats, int64_t diff_in_nano, int expectedBufferPeriod) {
    stats->measurement_count++;

    // round up to nearest millisecond
    int diff_in_milli = (int) ((diff_in_nano + NANOS_PER_MILLI - 1) / NANOS_PER_MILLI);

    if (diff_in_milli > stats->max_buffer_period) {
        stats->max_buffer_period = diff_in_milli;
    }

    // from 0 ms to 1000 ms, plus a sum of all instances > 1000ms
    if (diff_in_milli >= (RANGE - 1)) {
        (stats->buffer_period)[RANGE-1]++;
    } else if (diff_in_milli >= 0) {
        (stats->buffer_period)[diff_in_milli]++;
    } else { // for diff_in_milli < 0
        __android_log_print(ANDROID_LOG_INFO, "sles_player", "Having negative BufferPeriod.");
    }

    int64_t delta = diff_in_nano - (int64_t) expectedBufferPeriod * NANOS_PER_MILLI;
    stats->SDM += delta * delta;
    if (stats->measurement_count > 1) {
        stats->var = stats->SDM / stats->measurement_count;
    }

    // check if the lateness is so bad that a systrace should be captured
    // TODO: replace static threshold of lateness with a dynamic determination
    if (diff_in_milli > expectedBufferPeriod + LATE_CALLBACK_CAPTURE_THRESHOLD) {
        // TODO: log in a non-blocking way
        //__android_log_print(ANDROID_LOG_INFO, "buffer_stats", "Callback late by %d ms",
        //                    diff_in_milli - expectedBufferPeriod);
        atomicSetIfGreater(&(stats->captureRank), diff_in_milli - expectedBufferPeriod);
    }
    return diff_in_milli > expectedBufferPeriod + LATE_CALLBACK_OUTLIER_THRESHOLD;
}

static int slesCreateServer(sles_data *pSles, int samplingRate, int frameCount, int micSource,
        int performanceMode,
        int testType, double frequency1, char *byteBufferPtr, int byteBufferLength,
        short *loopbackTone, int maxRecordedLateCallbacks, int ignoreFirstFrames) {
    int status = STATUS_FAIL;

    if (pSles != NULL) {

        //        adb shell slesTest_feedback -r1 -t1 -s48000 -f240 -i300 -e3 -o/sdcard/log.wav
        //            r1 and t1 are the receive and transmit buffer counts, typically 1
        //            s is the sample rate, typically 48000 or 44100
        //            f is the frame count per buffer, typically 240 or 256
        //            i is the number of milliseconds before impulse.  You may need to adjust this.
        //            e is number of seconds to record
        //            o is output .wav file name


        //        // default values
        //        SLuint32 rxBufCount = 1;     // -r#
        //        SLuint32 txBufCount = 1;     // -t#
        //        SLuint32 bufSizeInFrames = 240;  // -f#
        //        SLuint32 channels = 1;       // -c#
        //        SLuint32 sampleRate = 48000; // -s#
        //        SLuint32 exitAfterSeconds = 3; // -e#
        //        SLuint32 freeBufCount = 0;   // calculated
        //        SLuint32 bufSizeInBytes = 0; // calculated
        //        int injectImpulse = 300; // -i#i
        //
        //        // Storage area for the buffer queues
        //        char **rxBuffers;
        //        char **txBuffers;
        //        char **freeBuffers;
        //
        //        // Buffer indices
        //        SLuint32 rxFront;    // oldest recording
        //        SLuint32 rxRear;     // next to be recorded
        //        SLuint32 txFront;    // oldest playing
        //        SLuint32 txRear;     // next to be played
        //        SLuint32 freeFront;  // oldest free
        //        SLuint32 freeRear;   // next to be freed
        //
        //        audio_utils_fifo fifo; //(*)
        //        SLAndroidSimpleBufferQueueItf recorderBufferQueue;
        //        SLBufferQueueItf playerBufferQueue;

        // default values
        pSles->rxBufCount = 1;     // -r#
        pSles->txBufCount = 1;     // -t#
        pSles->bufSizeInFrames = frameCount;//240;  // -f#
        pSles->channels = 1;       // -c#
        pSles->sampleRate = samplingRate;//48000; // -s#
        pSles->exitAfterSeconds = 3; // -e#
        pSles->freeBufCount = 0;   // calculated
        pSles->bufSizeInBytes = 0; // calculated
        pSles->injectImpulse = 300; // -i#i
        pSles->totalDiscardedInputFrames = 0;
        pSles->ignoreFirstFrames = ignoreFirstFrames;

        // Storage area for the buffer queues
        //        char **rxBuffers;
        //        char **txBuffers;
        //        char **freeBuffers;

        // Buffer indices
#if 0
        pSles->rxFront;    // oldest recording
        pSles->rxRear;     // next to be recorded
        pSles->txFront;    // oldest playing
        pSles->txRear;     // next to be played
        pSles->freeFront;  // oldest free
        pSles->freeRear;   // next to be freed

        pSles->fifo; //(*)
#endif
        pSles->fifo2Buffer = NULL;  //this fifo is for sending data to java code (to plot it)
#if 0
        pSles->recorderBufferQueue;
        pSles->playerBufferQueue;
#endif



        // compute total free buffers as -r plus -t
        pSles->freeBufCount = pSles->rxBufCount + pSles->txBufCount;
        // compute buffer size
        pSles->bufSizeInBytes = pSles->channels * pSles->bufSizeInFrames * sizeof(short);

        // Initialize free buffers
        pSles->freeBuffers = (char **) calloc(pSles->freeBufCount + 1, sizeof(char *));
        SLES_PRINTF("  calloc freeBuffers %llu bytes at %p", (long long)pSles->freeBufCount + 1,
                    pSles->freeBuffers);
        unsigned j;
        for (j = 0; j < pSles->freeBufCount; ++j) {
            pSles->freeBuffers[j] = (char *) malloc(pSles->bufSizeInBytes);
            SLES_PRINTF(" buff%d malloc %llu bytes at %p",j, (long long)pSles->bufSizeInBytes,
                        pSles->freeBuffers[j]);
        }
        pSles->freeFront = 0;
        pSles->freeRear = pSles->freeBufCount;
        pSles->freeBuffers[j] = NULL;

        // Initialize record queue
        pSles->rxBuffers = (char **) calloc(pSles->rxBufCount + 1, sizeof(char *));
        SLES_PRINTF("  calloc rxBuffers %llu bytes at %p", (long long)pSles->rxBufCount + 1,
                pSles->rxBuffers);
        pSles->rxFront = 0;
        pSles->rxRear = 0;

        // Initialize play queue
        pSles->txBuffers = (char **) calloc(pSles->txBufCount + 1, sizeof(char *));
        SLES_PRINTF("  calloc txBuffers %llu bytes at %p", (long long)pSles->txBufCount + 1,
                pSles->txBuffers);
        pSles->txFront = 0;
        pSles->txRear = 0;

        size_t frameSize = pSles->channels * sizeof(short);
#define FIFO_FRAMES 1024
        pSles->fifoBuffer = new short[FIFO_FRAMES * pSles->channels];
        audio_utils_fifo_init(&(pSles->fifo), FIFO_FRAMES, frameSize, pSles->fifoBuffer);

        //        SNDFILE *sndfile;
        //        if (outFileName != NULL) {
        // create .wav writer
        //            SF_INFO info;
        //            info.frames = 0;
        //            info.samplerate = sampleRate;
        //            info.channels = channels;
        //            info.format = SF_FORMAT_WAV | SF_FORMAT_PCM_16;
        //            sndfile = sf_open(outFileName, SFM_WRITE, &info);
        //            if (sndfile != NULL) {
#define FIFO2_FRAMES 65536
        pSles->fifo2Buffer = new short[FIFO2_FRAMES * pSles->channels];
        audio_utils_fifo_init(&(pSles->fifo2), FIFO2_FRAMES, frameSize, pSles->fifo2Buffer);
        //            } else {
        //                fprintf(stderr, "sf_open failed\n");
        //            }
        //        } else {
        //            sndfile = NULL;
        //        }

        initBufferStats(&pSles->recorderBufferStats);
        initBufferStats(&pSles->playerBufferStats);

        // init other variables needed for buffer test
        pSles->testType = testType;
        pSles->frequency1 = frequency1;
        pSles->bufferTestPhase1 = 0;
        pSles->count = 0;
        pSles->byteBufferPtr = byteBufferPtr;
        pSles->byteBufferLength = byteBufferLength;

        //init loopback tone
        pSles->loopbackTone = loopbackTone;

        pSles->recorderTimeStamps = {
            new int[maxRecordedLateCallbacks],      //int* timeStampsMs
            new short[maxRecordedLateCallbacks],    //short* callbackDurations
            0,                                      //short index
            {0,0},                                  //struct timespec startTime;
            maxRecordedLateCallbacks,               //int capacity
            false                                   //bool exceededCapacity
        };

        pSles->playerTimeStamps = {
            new int[maxRecordedLateCallbacks],      //int* timeStampsMs
            new short[maxRecordedLateCallbacks],    //short* callbackDurations;
            0,                                      //short index
            {0,0},                                  //struct timespec startTime;
            maxRecordedLateCallbacks,               //int capacity
            false                                   //bool exceededCapacity
        };

        pSles->expectedBufferPeriod = (short) (
                round(pSles->bufSizeInFrames * MILLIS_PER_SECOND / (float) pSles->sampleRate));

        SLresult result;

        // create engine
#if 0
        pSles->engineObject;
#endif
        result = slCreateEngine(&(pSles->engineObject), 0, NULL, 0, NULL, NULL);
        ASSERT_EQ(SL_RESULT_SUCCESS, result);
        result = (*(pSles->engineObject))->Realize(pSles->engineObject, SL_BOOLEAN_FALSE);
        ASSERT_EQ(SL_RESULT_SUCCESS, result);
        SLEngineItf engineEngine;
        result = (*(pSles->engineObject))->GetInterface(pSles->engineObject, SL_IID_ENGINE,
                &engineEngine);
        ASSERT_EQ(SL_RESULT_SUCCESS, result);

        // create output mix
#if 0
        pSles->outputmixObject;
#endif
        result = (*engineEngine)->CreateOutputMix(engineEngine, &(pSles->outputmixObject), 0, NULL,
                NULL);
        ASSERT_EQ(SL_RESULT_SUCCESS, result);
        result = (*(pSles->outputmixObject))->Realize(pSles->outputmixObject, SL_BOOLEAN_FALSE);
        ASSERT_EQ(SL_RESULT_SUCCESS, result);

        // create an audio player with buffer queue source and output mix sink
        SLDataSource audiosrc;
        SLDataSink audiosnk;
        SLDataFormat_PCM pcm;
        SLDataLocator_OutputMix locator_outputmix;
        SLDataLocator_BufferQueue locator_bufferqueue_tx;
        locator_bufferqueue_tx.locatorType = SL_DATALOCATOR_BUFFERQUEUE;
        locator_bufferqueue_tx.numBuffers = pSles->txBufCount;
        locator_outputmix.locatorType = SL_DATALOCATOR_OUTPUTMIX;
        locator_outputmix.outputMix = pSles->outputmixObject;
        pcm.formatType = SL_DATAFORMAT_PCM;
        pcm.numChannels = pSles->channels;
        pcm.samplesPerSec = pSles->sampleRate * 1000;
        pcm.bitsPerSample = SL_PCMSAMPLEFORMAT_FIXED_16;
        pcm.containerSize = 16;
        pcm.channelMask = pSles->channels == 1 ? SL_SPEAKER_FRONT_CENTER :
                (SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT);
        pcm.endianness = SL_BYTEORDER_LITTLEENDIAN;
        audiosrc.pLocator = &locator_bufferqueue_tx;
        audiosrc.pFormat = &pcm;
        audiosnk.pLocator = &locator_outputmix;
        audiosnk.pFormat = NULL;
        pSles->playerObject = NULL;
        pSles->recorderObject = NULL;
        SLInterfaceID ids_tx[2] = {SL_IID_BUFFERQUEUE, SL_IID_ANDROIDCONFIGURATION};
        SLboolean flags_tx[2] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE};
        result = (*engineEngine)->CreateAudioPlayer(engineEngine, &(pSles->playerObject),
                &audiosrc, &audiosnk, 2, ids_tx, flags_tx);
        if (SL_RESULT_CONTENT_UNSUPPORTED == result) {
            SLES_PRINTF("ERROR: Could not create audio player (result %x), check sample rate\n",
                                                     result);
            goto cleanup;
        }
        ASSERT_EQ(SL_RESULT_SUCCESS, result);

        {
           /* Get the Android configuration interface which is explicit */
            SLAndroidConfigurationItf configItf;
            result = (*(pSles->playerObject))->GetInterface(pSles->playerObject,
                                                 SL_IID_ANDROIDCONFIGURATION, (void*)&configItf);
            ASSERT_EQ(SL_RESULT_SUCCESS, result);

            /* Use the configuration interface to configure the player before it's realized */
            if (performanceMode != -1) {
                SLuint32 performanceMode32 = performanceMode;
                result = (*configItf)->SetConfiguration(configItf, SL_ANDROID_KEY_PERFORMANCE_MODE,
                        &performanceMode32, sizeof(SLuint32));
                ASSERT_EQ(SL_RESULT_SUCCESS, result);
            }

        }

        result = (*(pSles->playerObject))->Realize(pSles->playerObject, SL_BOOLEAN_FALSE);
        ASSERT_EQ(SL_RESULT_SUCCESS, result);
        SLPlayItf playerPlay;
        result = (*(pSles->playerObject))->GetInterface(pSles->playerObject, SL_IID_PLAY,
                &playerPlay);
        ASSERT_EQ(SL_RESULT_SUCCESS, result);
        result = (*(pSles->playerObject))->GetInterface(pSles->playerObject, SL_IID_BUFFERQUEUE,
                &(pSles->playerBufferQueue));
        ASSERT_EQ(SL_RESULT_SUCCESS, result);
        result = (*(pSles->playerBufferQueue))->RegisterCallback(pSles->playerBufferQueue,
                playerCallback, pSles); //playerCallback is the name of callback function
        ASSERT_EQ(SL_RESULT_SUCCESS, result);

        // Enqueue some zero buffers for the player
        for (j = 0; j < pSles->txBufCount; ++j) {

            // allocate a free buffer
            ASSERT(pSles->freeFront != pSles->freeRear);
            char *buffer = pSles->freeBuffers[pSles->freeFront];
            if (++pSles->freeFront > pSles->freeBufCount) {
                pSles->freeFront = 0;
            }

            // put on play queue
            SLuint32 txRearNext = pSles->txRear + 1;
            if (txRearNext > pSles->txBufCount) {
                txRearNext = 0;
            }
            ASSERT(txRearNext != pSles->txFront);
            pSles->txBuffers[pSles->txRear] = buffer;
            pSles->txRear = txRearNext;
            result = (*(pSles->playerBufferQueue))->Enqueue(pSles->playerBufferQueue,
                    buffer, pSles->bufSizeInBytes);
            ASSERT_EQ(SL_RESULT_SUCCESS, result);
        }

        result = (*playerPlay)->SetPlayState(playerPlay, SL_PLAYSTATE_PLAYING);
        ASSERT_EQ(SL_RESULT_SUCCESS, result);

        // Create an audio recorder with microphone device source and buffer queue sink.
        // The buffer queue as sink is an Android-specific extension.
        SLDataLocator_IODevice locator_iodevice;
        SLDataLocator_AndroidSimpleBufferQueue locator_bufferqueue_rx;

        locator_iodevice.locatorType = SL_DATALOCATOR_IODEVICE;
        locator_iodevice.deviceType = SL_IODEVICE_AUDIOINPUT;
        locator_iodevice.deviceID = SL_DEFAULTDEVICEID_AUDIOINPUT;
        locator_iodevice.device = NULL;

        audiosrc.pLocator = &locator_iodevice;
        audiosrc.pFormat = NULL;

        locator_bufferqueue_rx.locatorType = SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE;
        locator_bufferqueue_rx.numBuffers = pSles->rxBufCount;

        audiosnk.pLocator = &locator_bufferqueue_rx;
        audiosnk.pFormat = &pcm;

        {   //why brackets here?
            SLInterfaceID ids_rx[2] = {SL_IID_ANDROIDSIMPLEBUFFERQUEUE,
                                       SL_IID_ANDROIDCONFIGURATION};
            SLboolean flags_rx[2] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE};
            result = (*engineEngine)->CreateAudioRecorder(engineEngine, &(pSles->recorderObject),
                    &audiosrc, &audiosnk, 2, ids_rx, flags_rx);
            if (SL_RESULT_SUCCESS != result) {
                status = STATUS_FAIL;

                SLES_PRINTF("ERROR: Could not create audio recorder (result %x), "
                             "check sample rate and channel count\n", result);
                goto cleanup;
            }
        }
        ASSERT_EQ(SL_RESULT_SUCCESS, result);

        {
           /* Get the Android configuration interface which is explicit */
            SLAndroidConfigurationItf configItf;
            result = (*(pSles->recorderObject))->GetInterface(pSles->recorderObject,
                                                 SL_IID_ANDROIDCONFIGURATION, (void*)&configItf);
            ASSERT_EQ(SL_RESULT_SUCCESS, result);

            SLuint32 presetValue = micSource;
            //SL_ANDROID_RECORDING_PRESET_CAMCORDER;//SL_ANDROID_RECORDING_PRESET_NONE;

            /* Use the configuration interface to configure the recorder before it's realized */
            if (presetValue != SL_ANDROID_RECORDING_PRESET_NONE) {
                result = (*configItf)->SetConfiguration(configItf, SL_ANDROID_KEY_RECORDING_PRESET,
                        &presetValue, sizeof(SLuint32));
                ASSERT_EQ(SL_RESULT_SUCCESS, result);
            }
            if (performanceMode != -1) {
                SLuint32 performanceMode32 = performanceMode;
                result = (*configItf)->SetConfiguration(configItf, SL_ANDROID_KEY_PERFORMANCE_MODE,
                        &performanceMode32, sizeof(SLuint32));
                ASSERT_EQ(SL_RESULT_SUCCESS, result);
            }

        }

        result = (*(pSles->recorderObject))->Realize(pSles->recorderObject, SL_BOOLEAN_FALSE);
        ASSERT_EQ(SL_RESULT_SUCCESS, result);

        SLRecordItf recorderRecord;
        result = (*(pSles->recorderObject))->GetInterface(pSles->recorderObject, SL_IID_RECORD,
                &recorderRecord);
        ASSERT_EQ(SL_RESULT_SUCCESS, result);

        result = (*(pSles->recorderObject))->GetInterface(pSles->recorderObject,
                SL_IID_ANDROIDSIMPLEBUFFERQUEUE, &(pSles->recorderBufferQueue));
        ASSERT_EQ(SL_RESULT_SUCCESS, result);

        result = (*(pSles->recorderBufferQueue))->RegisterCallback(pSles->recorderBufferQueue,
                recorderCallback, pSles);
        ASSERT_EQ(SL_RESULT_SUCCESS, result);

        // Enqueue some empty buffers for the recorder
        for (j = 0; j < pSles->rxBufCount; ++j) {

            // allocate a free buffer
            ASSERT(pSles->freeFront != pSles->freeRear);
            char *buffer = pSles->freeBuffers[pSles->freeFront];
            if (++pSles->freeFront > pSles->freeBufCount) {
                pSles->freeFront = 0;
            }

            // put on record queue
            SLuint32 rxRearNext = pSles->rxRear + 1;
            if (rxRearNext > pSles->rxBufCount) {
                rxRearNext = 0;
            }
            ASSERT(rxRearNext != pSles->rxFront);
            pSles->rxBuffers[pSles->rxRear] = buffer;
            pSles->rxRear = rxRearNext;
            result = (*(pSles->recorderBufferQueue))->Enqueue(pSles->recorderBufferQueue,
                    buffer, pSles->bufSizeInBytes);
            ASSERT_EQ(SL_RESULT_SUCCESS, result);
        }

        // Kick off the recorder
        result = (*recorderRecord)->SetRecordState(recorderRecord, SL_RECORDSTATE_RECORDING);
        ASSERT_EQ(SL_RESULT_SUCCESS, result);



        // Tear down the objects and exit
        status = STATUS_SUCCESS;
        cleanup:

        SLES_PRINTF("Finished initialization with status: %d", status);

    }
    return status;
}

// Read data from fifo2Buffer and store into pSamples.
int slesProcessNext(void *pCtx, double *pSamples, long maxSamples) {
    //int status = STATUS_FAIL;
    sles_data *pSles = (sles_data*)pCtx;

    SLES_PRINTF("slesProcessNext: pSles = %p, currentSample: %p,  maxSamples = %ld",
                pSles, pSamples, maxSamples);

    int samplesRead = 0;

    int currentSample = 0;
    double *pCurrentSample = pSamples;
    int maxValue = 32768;

    if (pSles != NULL) {

        SLresult result;
        for (int i = 0; i < 10; i++) {
            usleep(100000);         // sleep for 0.1s
            if (pSles->fifo2Buffer != NULL) {
                for (;;) {
                    short buffer[pSles->bufSizeInFrames * pSles->channels];
                    ssize_t actual = audio_utils_fifo_read(&(pSles->fifo2), buffer,
                            pSles->bufSizeInFrames);
                    if (actual <= 0)
                        break;
                    {
                        for (int jj = 0; jj < actual && currentSample < maxSamples; jj++) {
                            *(pCurrentSample++) = ((double) buffer[jj]) / maxValue;
                            currentSample++;
                        }
                    }
                    samplesRead += actual;
                }
            }
            if (pSles->injectImpulse > 0) {
                if (pSles->injectImpulse <= 100) {
                    pSles->injectImpulse = -1;
                    write(1, "I", 1);
                } else {
                    if ((pSles->injectImpulse % 1000) < 100) {
                        write(1, "i", 1);
                    }
                    pSles->injectImpulse -= 100;
                }
            } else if (i == 9) {
                write(1, ".", 1);
            }
        }
        SLBufferQueueState playerBQState;
        result = (*(pSles->playerBufferQueue))->GetState(pSles->playerBufferQueue,
                  &playerBQState);
        ASSERT_EQ(SL_RESULT_SUCCESS, result);
        SLAndroidSimpleBufferQueueState recorderBQState;
        result = (*(pSles->recorderBufferQueue))->GetState(pSles->recorderBufferQueue,
                  &recorderBQState);
        ASSERT_EQ(SL_RESULT_SUCCESS, result);

        SLES_PRINTF("End of slesProcessNext: pSles = %p, samplesRead = %d, maxSamples = %ld",
                    pSles, samplesRead, maxSamples);
    }
    return samplesRead;
}


static int slesDestroyServer(sles_data *pSles) {
    int status = STATUS_FAIL;

     SLES_PRINTF("Start slesDestroyServer: pSles = %p", pSles);

    if (pSles != NULL) {
        if (NULL != pSles->playerObject) {
            SLES_PRINTF("stopping player...");
            SLPlayItf playerPlay;
            SLresult result = (*(pSles->playerObject))->GetInterface(pSles->playerObject,
                                                        SL_IID_PLAY, &playerPlay);

            ASSERT_EQ(SL_RESULT_SUCCESS, result);

            //stop player and recorder if they exist
             result = (*playerPlay)->SetPlayState(playerPlay, SL_PLAYSTATE_STOPPED);
            ASSERT_EQ(SL_RESULT_SUCCESS, result);
        }

        if (NULL != pSles->recorderObject) {
            SLES_PRINTF("stopping recorder...");
            SLRecordItf recorderRecord;
            SLresult result = (*(pSles->recorderObject))->GetInterface(pSles->recorderObject,
                                                          SL_IID_RECORD, &recorderRecord);
            ASSERT_EQ(SL_RESULT_SUCCESS, result);

            result = (*recorderRecord)->SetRecordState(recorderRecord, SL_RECORDSTATE_STOPPED);
            ASSERT_EQ(SL_RESULT_SUCCESS, result);
        }

        usleep(1000);

        audio_utils_fifo_deinit(&(pSles->fifo));
        delete[] pSles->fifoBuffer;

        SLES_PRINTF("slesDestroyServer 2");

        //        if (sndfile != NULL) {
        audio_utils_fifo_deinit(&(pSles->fifo2));
        delete[] pSles->fifo2Buffer;

        SLES_PRINTF("slesDestroyServer 3");

        //            sf_close(sndfile);
        //        }
        if (NULL != pSles->playerObject) {
            (*(pSles->playerObject))->Destroy(pSles->playerObject);
        }

        SLES_PRINTF("slesDestroyServer 4");

        if (NULL != pSles->recorderObject) {
            (*(pSles->recorderObject))->Destroy(pSles->recorderObject);
        }

        SLES_PRINTF("slesDestroyServer 5");

        (*(pSles->outputmixObject))->Destroy(pSles->outputmixObject);
        SLES_PRINTF("slesDestroyServer 6");
        (*(pSles->engineObject))->Destroy(pSles->engineObject);
        SLES_PRINTF("slesDestroyServer 7");

        //free buffers
        if (NULL != pSles->freeBuffers) {
            for (unsigned j = 0; j < pSles->freeBufCount; ++j) {
                if (NULL != pSles->freeBuffers[j]) {
                    SLES_PRINTF(" free buff%d at %p",j, pSles->freeBuffers[j]);
                    free (pSles->freeBuffers[j]);
                }
            }
            SLES_PRINTF("  free freeBuffers at %p", pSles->freeBuffers);
            free(pSles->freeBuffers);
        } else {
            SLES_PRINTF("  freeBuffers NULL, no need to free");
        }


        if (NULL != pSles->rxBuffers) {
            SLES_PRINTF("  free rxBuffers at %p", pSles->rxBuffers);
            free(pSles->rxBuffers);
        } else {
            SLES_PRINTF("  rxBuffers NULL, no need to free");
        }

        if (NULL != pSles->txBuffers) {
            SLES_PRINTF("  free txBuffers at %p", pSles->txBuffers);
            free(pSles->txBuffers);
        } else {
            SLES_PRINTF("  txBuffers NULL, no need to free");
        }


        status = STATUS_SUCCESS;
    }
    SLES_PRINTF("End slesDestroyServer: status = %d", status);
    return status;
}


int* slesGetRecorderBufferPeriod(void *pCtx) {
    sles_data *pSles = (sles_data*)pCtx;
    return pSles->recorderBufferStats.buffer_period;
}

int slesGetRecorderMaxBufferPeriod(void *pCtx) {
    sles_data *pSles = (sles_data*)pCtx;
    return pSles->recorderBufferStats.max_buffer_period;
}

int64_t slesGetRecorderVarianceBufferPeriod(void *pCtx) {
    sles_data *pSles = (sles_data*)pCtx;
    return pSles->recorderBufferStats.var;
}

int* slesGetPlayerBufferPeriod(void *pCtx) {
    sles_data *pSles = (sles_data*)pCtx;
    return pSles->playerBufferStats.buffer_period;
}

int slesGetPlayerMaxBufferPeriod(void *pCtx) {
    sles_data *pSles = (sles_data*)pCtx;
    return pSles->playerBufferStats.max_buffer_period;
}

int64_t slesGetPlayerVarianceBufferPeriod(void *pCtx) {
    sles_data *pSles = (sles_data*)pCtx;
    return pSles->playerBufferStats.var;
}

int slesGetCaptureRank(void *pCtx) {
    sles_data *pSles = (sles_data*)pCtx;
    // clear the capture flags since they're being handled now
    int recorderRank = android_atomic_exchange(0, &pSles->recorderBufferStats.captureRank);
    int playerRank = android_atomic_exchange(0, &pSles->playerBufferStats.captureRank);

    if (recorderRank > playerRank) {
        return recorderRank;
    } else {
        return playerRank;
    }
}

int slesGetPlayerTimeStampsAndExpectedBufferPeriod(void *pCtx, callbackTimeStamps **ppTSs) {
    sles_data *pSles = (sles_data*)pCtx;
    *ppTSs = &pSles->playerTimeStamps;
    return pSles->expectedBufferPeriod;
}

int slesGetRecorderTimeStampsAndExpectedBufferPeriod(void *pCtx, callbackTimeStamps **ppTSs) {
    sles_data *pSles = (sles_data*)pCtx;
    *ppTSs = &pSles->recorderTimeStamps;
    return pSles->expectedBufferPeriod;
}
