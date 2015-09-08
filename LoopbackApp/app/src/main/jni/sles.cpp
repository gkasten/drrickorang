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



////////////////////////////////////////////
/// Actual sles functions.


// Test program to record from default audio input and playback to default audio output.
// It will generate feedback (Larsen effect) if played through on-device speakers,
// or acts as a delay if played through headset.

#define _USE_MATH_DEFINES
#include <cmath>

#include "sles.h"
#include <stdio.h>
#include <stdlib.h>
#include <stddef.h>

#include <assert.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
//#include <jni.h>
#include <time.h>

int slesInit(sles_data ** ppSles, int samplingRate, int frameCount, int micSource,
             int testType, double frequency1, char* byteBufferPtr, int byteBufferLength) {
    int status = SLES_FAIL;
    if (ppSles != NULL) {
        sles_data * pSles = (sles_data*) malloc(sizeof(sles_data));

        memset(pSles, 0, sizeof(sles_data));

         SLES_PRINTF("malloc %d bytes at %p", sizeof(sles_data), pSles);
        //__android_log_print(ANDROID_LOG_INFO, "sles_jni",
        //"malloc %d bytes at %p", sizeof(sles_data), pSles);//Or ANDROID_LOG_INFO, ...
        *ppSles = pSles;
        if (pSles != NULL)
        {
            SLES_PRINTF("creating server. Sampling rate =%d, frame count = %d",
                        samplingRate, frameCount);
            status = slesCreateServer(pSles, samplingRate, frameCount, micSource,
                                      testType, frequency1, byteBufferPtr, byteBufferLength);
            SLES_PRINTF("slesCreateServer =%d", status);
        }
    }

    return status;
}
int slesDestroy(sles_data ** ppSles) {
    int status = SLES_FAIL;
    if (ppSles != NULL) {
        slesDestroyServer(*ppSles);

        if (*ppSles != NULL)
        {
            free(*ppSles);
            *ppSles = 0;
        }
        status = SLES_SUCCESS;
    }
    return status;
}

#define ASSERT_EQ(x, y) do { if ((x) == (y)) ; else { fprintf(stderr, "0x%x != 0x%x\n", \
    (unsigned) (x), (unsigned) (y)); assert((x) == (y)); } } while (0)


// Called after audio recorder fills a buffer with data, then we can read from this filled buffer
static void recorderCallback(SLAndroidSimpleBufferQueueItf caller __unused, void *context) {
    sles_data *pSles = (sles_data*) context;
    if (pSles != NULL) {
        collectRecorderBufferPeriod(pSles);

        //__android_log_print(ANDROID_LOG_INFO, "sles_jni", "in the recordercallback");
        SLresult result;

        pthread_mutex_lock(&(pSles->mutex));
        //ee  SLES_PRINTF("<R");

        // We should only be called when a recording buffer is done
        assert(pSles->rxFront <= pSles->rxBufCount);
        assert(pSles->rxRear <= pSles->rxBufCount);
        assert(pSles->rxFront != pSles->rxRear);
        char *buffer = pSles->rxBuffers[pSles->rxFront]; //pSles->rxBuffers stores the data recorded


        // Remove buffer from record queue
        if (++pSles->rxFront > pSles->rxBufCount) {
            pSles->rxFront = 0;
        }

        if (pSles->testType == TEST_TYPE_LATENCY) {
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
                ssize_t actual = byteBuffer_write(pSles, buffer, (size_t) pSles->bufSizeInFrames);

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
        assert(rxRearNext != pSles->rxFront);
        pSles->rxBuffers[pSles->rxRear] = buffer;
        pSles->rxRear = rxRearNext;



      //ee  SLES_PRINTF("r>");
        pthread_mutex_unlock(&(pSles->mutex));

    } //pSles not null
}


// Write "count" amount of short from buffer to pSles->byteBufferPtr. This byteBuffer will read by
// java code.
ssize_t byteBuffer_write(sles_data *pSles, char *buffer, size_t count) {
    // bytebufferSize is in byte
    int32_t rear; // rear should not exceed 2^31 - 1, or else overflow will happen
    memcpy(&rear, (char *) (pSles->byteBufferPtr + pSles->byteBufferLength - 4), sizeof(rear));

    size_t frameSize = pSles->channels * sizeof(short); // only one channel
    int32_t maxLengthInShort = (pSles->byteBufferLength - 4) / frameSize;
    // mask the upper bits to get the correct position in the pipe
    int32_t tempRear = rear & (maxLengthInShort - 1);
    size_t part1 = maxLengthInShort - tempRear;

    if (part1 > count) {
        part1 = count;
    }

    if (part1 > 0) {
        memcpy(pSles->byteBufferPtr + (tempRear * frameSize), buffer,
               part1 * frameSize);

        size_t part2 = count - part1;
        if (part2 > 0) {
            memcpy(pSles->byteBufferPtr, (buffer + (part1 * frameSize)),
                   part2 * frameSize);
        }

        //TODO do we need something similar to the below function call?
        //android_atomic_release_store(audio_utils_fifo_sum(fifo, fifo->mRear, availToWrite),
        //        &fifo->mRear);
    }

    // increase value of rear
    int32_t* rear2 = (int32_t *) (pSles->byteBufferPtr + pSles->byteBufferLength - 4);
    *rear2 += count;
    return count;
}


// Called in the beginning of recorderCallback() to collect the interval between each
// recorderCallback().
void collectRecorderBufferPeriod(sles_data *pSles) {
    struct timespec recorder_time;
    clock_gettime(CLOCK_MONOTONIC, &recorder_time);

    pSles->recorder_current_time_sec = recorder_time.tv_sec;
    pSles->recorder_current_time_nsec = recorder_time.tv_nsec;
    (pSles->recorder_buffer_count)++;

    if (pSles->recorder_previous_time_sec != 0 &&
        pSles->recorder_buffer_count > BUFFER_PERIOD_DISCARD){
        int diff_in_second = pSles->recorder_current_time_sec - pSles->recorder_previous_time_sec;
        long diff_in_nano = pSles->recorder_current_time_nsec - pSles->recorder_previous_time_nsec;

        // diff_in_milli is rounded up
        long long total_diff_in_nano = (diff_in_second * NANOS_PER_SECOND) + diff_in_nano;
        int diff_in_milli = (int) ((total_diff_in_nano + NANOS_PER_MILLI - 1) / NANOS_PER_MILLI);

        if (diff_in_milli > pSles->recorder_max_buffer_period) {
            pSles->recorder_max_buffer_period = diff_in_milli;
        }

        // from 0 ms to 1000 ms, plus a sum of all instances > 1000ms
        if (diff_in_milli >= (RANGE - 1)) {
            (pSles->recorder_buffer_period)[RANGE-1]++;
        } else if (diff_in_milli >= 0) {
            (pSles->recorder_buffer_period)[diff_in_milli]++;
        } else { // for diff_in_milli < 0
            __android_log_print(ANDROID_LOG_INFO, "sles_recorder", "Having negative BufferPeriod.");
        }
    }

    pSles->recorder_previous_time_sec = pSles->recorder_current_time_sec;
    pSles->recorder_previous_time_nsec = pSles->recorder_current_time_nsec;
}


// Called after audio player empties a buffer of data
static void playerCallback(SLBufferQueueItf caller __unused, void *context) {
    sles_data *pSles = (sles_data*) context;
    if (pSles != NULL) {
        collectPlayerBufferPeriod(pSles);
        SLresult result;

        pthread_mutex_lock(&(pSles->mutex));
        //ee  SLES_PRINTF("<P");

        // Get the buffer that just finished playing
        assert(pSles->txFront <= pSles->txBufCount);
        assert(pSles->txRear <= pSles->txBufCount);
        assert(pSles->txFront != pSles->txRear);
        char *buffer = pSles->txBuffers[pSles->txFront];
        if (++pSles->txFront > pSles->txBufCount) {
            pSles->txFront = 0;
        }

        if (pSles->testType == TEST_TYPE_LATENCY) {
            ssize_t actual = audio_utils_fifo_read(&(pSles->fifo), buffer, pSles->bufSizeInFrames);
            if (actual != (ssize_t) pSles->bufSizeInFrames) {
                write(1, "/", 1);
                // on underrun from pipe, substitute silence
                memset(buffer, 0, pSles->bufSizeInFrames * pSles->channels * sizeof(short));
            }

            if (pSles->injectImpulse == -1) {   // here we inject pulse
                // Experimentally, a single frame impulse was insufficient to trigger feedback.
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
                }
                pSles->injectImpulse = 0;
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
                ((short *) buffer)[i] = value;

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
        assert(pSles->txFront <= pSles->txBufCount);
        assert(pSles->txRear <= pSles->txBufCount);
        SLuint32 txRearNext = pSles->txRear + 1;
        if (txRearNext > pSles->txBufCount) {
            txRearNext = 0;
        }
        assert(txRearNext != pSles->txFront);
        pSles->txBuffers[pSles->txRear] = buffer;
        pSles->txRear = txRearNext;

        pthread_mutex_unlock(&(pSles->mutex));
    } //pSles not null
}

// Called in the beginning of playerCallback() to collect the interval between each
// playerCallback().
void collectPlayerBufferPeriod(sles_data *pSles) {
    struct timespec player_time;
    clock_gettime(CLOCK_MONOTONIC, &player_time);

    pSles->player_current_time_sec = player_time.tv_sec;
    pSles->player_current_time_nsec = player_time.tv_nsec;
    (pSles->player_buffer_count)++;

    if (pSles->player_previous_time_sec != 0 &&
        pSles->player_buffer_count > BUFFER_PERIOD_DISCARD) {
        int diff_in_second = pSles->player_current_time_sec - pSles->player_previous_time_sec;
        long diff_in_nano = pSles->player_current_time_nsec - pSles->player_previous_time_nsec;

        // diff_in_milli is rounded up
        long long total_diff_in_nano = (diff_in_second * NANOS_PER_SECOND) + diff_in_nano;
        int diff_in_milli = (int) ((total_diff_in_nano + NANOS_PER_MILLI - 1) / NANOS_PER_MILLI);

        if (diff_in_milli > pSles->player_max_buffer_period) {
            pSles->player_max_buffer_period = diff_in_milli;
        }

        // from 0 ms to 1000 ms, plus a sum of all instances > 1000ms
        if (diff_in_milli >= (RANGE - 1)) {
            (pSles->player_buffer_period)[RANGE-1]++;
        } else if (diff_in_milli >= 0) {
            (pSles->player_buffer_period)[diff_in_milli]++;
        } else { // for diff_in_milli < 0
            __android_log_print(ANDROID_LOG_INFO, "sles_player", "Having negative BufferPeriod.");
        }
    }

    pSles->player_previous_time_sec = pSles->player_current_time_sec;
    pSles->player_previous_time_nsec = pSles->player_current_time_nsec;
}


int slesCreateServer(sles_data *pSles, int samplingRate, int frameCount, int micSource,
                     int testType, double frequency1, char* byteBufferPtr, int byteBufferLength) {
    int status = SLES_FAIL;

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

        // Storage area for the buffer queues
        //        char **rxBuffers;
        //        char **txBuffers;
        //        char **freeBuffers;

        // Buffer indices
        pSles->rxFront;    // oldest recording
        pSles->rxRear;     // next to be recorded
        pSles->txFront;    // oldest playing
        pSles->txRear;     // next to be played
        pSles->freeFront;  // oldest free
        pSles->freeRear;   // next to be freed

        pSles->fifo; //(*)
        pSles->fifo2Buffer = NULL;  //this fifo is for sending data to java code (to plot it)
        pSles->recorderBufferQueue;
        pSles->playerBufferQueue;



        // compute total free buffers as -r plus -t
        pSles->freeBufCount = pSles->rxBufCount + pSles->txBufCount;
        // compute buffer size
        pSles->bufSizeInBytes = pSles->channels * pSles->bufSizeInFrames * sizeof(short);

        // Initialize free buffers
        pSles->freeBuffers = (char **) calloc(pSles->freeBufCount + 1, sizeof(char *));
        unsigned j;
        for (j = 0; j < pSles->freeBufCount; ++j) {
            pSles->freeBuffers[j] = (char *) malloc(pSles->bufSizeInBytes);
        }
        pSles->freeFront = 0;
        pSles->freeRear = pSles->freeBufCount;
        pSles->freeBuffers[j] = NULL;

        // Initialize record queue
        pSles->rxBuffers = (char **) calloc(pSles->rxBufCount + 1, sizeof(char *));
        pSles->rxFront = 0;
        pSles->rxRear = 0;

        // Initialize play queue
        pSles->txBuffers = (char **) calloc(pSles->txBufCount + 1, sizeof(char *));
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

        //init recorder buffer period data
        pSles->recorder_buffer_period = new int[RANGE](); // initialized to zeros
        pSles->recorder_previous_time_sec = 0;
        pSles->recorder_previous_time_nsec = 0;
        pSles->recorder_current_time_sec = 0;
        pSles->recorder_current_time_nsec = 0;
        pSles->recorder_buffer_count = 0;
        pSles->recorder_max_buffer_period = 0;

        //init player buffer period data
        pSles->player_buffer_period = new int[RANGE](); // initialized to zeros
        pSles->player_previous_time_sec = 0;
        pSles->player_previous_time_nsec = 0;
        pSles->player_current_time_sec = 0;
        pSles->player_current_time_nsec = 0;
        pSles->player_buffer_count = 0;
        pSles->player_max_buffer_period = 0;

        // init other variables needed for buffer test
        pSles->testType = testType;
        pSles->frequency1 = frequency1;
        pSles->bufferTestPhase1 = 0;
        pSles->count = 0;
        pSles->byteBufferPtr = byteBufferPtr;
        pSles->byteBufferLength = byteBufferLength;

        SLresult result;

        // create engine
        pSles->engineObject;
        result = slCreateEngine(&(pSles->engineObject), 0, NULL, 0, NULL, NULL);
        ASSERT_EQ(SL_RESULT_SUCCESS, result);
        result = (*(pSles->engineObject))->Realize(pSles->engineObject, SL_BOOLEAN_FALSE);
        ASSERT_EQ(SL_RESULT_SUCCESS, result);
        SLEngineItf engineEngine;
        result = (*(pSles->engineObject))->GetInterface(pSles->engineObject, SL_IID_ENGINE,
                &engineEngine);
        ASSERT_EQ(SL_RESULT_SUCCESS, result);

        // create output mix
        pSles->outputmixObject;
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
        SLInterfaceID ids_tx[1] = {SL_IID_BUFFERQUEUE};
        SLboolean flags_tx[1] = {SL_BOOLEAN_TRUE};
        result = (*engineEngine)->CreateAudioPlayer(engineEngine, &(pSles->playerObject),
                &audiosrc, &audiosnk, 1, ids_tx, flags_tx);
        if (SL_RESULT_CONTENT_UNSUPPORTED == result) {
            fprintf(stderr, "Could not create audio player (result %x), check sample rate\n",
                    result);
            SLES_PRINTF("ERROR: Could not create audio player (result %x), check sample rate\n",
                                                     result);
            goto cleanup;
        }
        ASSERT_EQ(SL_RESULT_SUCCESS, result);
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
            assert(pSles->freeFront != pSles->freeRear);
            char *buffer = pSles->freeBuffers[pSles->freeFront];
            if (++pSles->freeFront > pSles->freeBufCount) {
                pSles->freeFront = 0;
            }

            // put on play queue
            SLuint32 txRearNext = pSles->txRear + 1;
            if (txRearNext > pSles->txBufCount) {
                txRearNext = 0;
            }
            assert(txRearNext != pSles->txFront);
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
                fprintf(stderr, "Could not create audio recorder (result %x), "
                        "check sample rate and channel count\n", result);
                status = SLES_FAIL;

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
            assert(pSles->freeFront != pSles->freeRear);
            char *buffer = pSles->freeBuffers[pSles->freeFront];
            if (++pSles->freeFront > pSles->freeBufCount) {
                pSles->freeFront = 0;
            }

            // put on record queue
            SLuint32 rxRearNext = pSles->rxRear + 1;
            if (rxRearNext > pSles->rxBufCount) {
                rxRearNext = 0;
            }
            assert(rxRearNext != pSles->rxFront);
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
        status = SLES_SUCCESS;
        cleanup:

        SLES_PRINTF("Finished initialization with status: %d", status);

        int xx = 1;

    }
    return status;
}

// Read data from fifo2Buffer and store into pSamples.
int slesProcessNext(sles_data *pSles, double *pSamples, long maxSamples) {
    //int status = SLES_FAIL;

    SLES_PRINTF("slesProcessNext: pSles = %p, currentSample: %p,  maxSamples = %d",
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

        SLES_PRINTF("End of slesProcessNext: pSles = %p, samplesRead = %d, maxSamples = %d",
                    pSles, samplesRead, maxSamples);
    }
    return samplesRead;
}


int slesDestroyServer(sles_data *pSles) {
    int status = SLES_FAIL;

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

//        free(pSles);
//        pSles = NULL;

        status = SLES_SUCCESS;
    }
    SLES_PRINTF("End slesDestroyServer: status = %d", status);
    return status;
}


int* slesGetRecorderBufferPeriod(sles_data *pSles) {
    return pSles->recorder_buffer_period;
}


int slesGetRecorderMaxBufferPeriod(sles_data *pSles) {
    return pSles->recorder_max_buffer_period;
}


int* slesGetPlayerBufferPeriod(sles_data *pSles) {
    return pSles->player_buffer_period;
}


int slesGetPlayerMaxBufferPeriod(sles_data *pSles) {
    return pSles->player_max_buffer_period;
}