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

#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#include <pthread.h>
#include <android/log.h>

#ifndef _Included_org_drrickorang_loopback_sles
#define _Included_org_drrickorang_loopback_sles

//struct audio_utils_fifo;
#define SLES_PRINTF(...)  __android_log_print(ANDROID_LOG_INFO, "sles_jni", __VA_ARGS__);


#ifdef __cplusplus
extern "C" {
#endif
#include <audio_utils/fifo.h>

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

    struct audio_utils_fifo fifo; //(*)
    struct audio_utils_fifo fifo2;
    short *fifo2Buffer;
    short *fifoBuffer;
    SLAndroidSimpleBufferQueueItf recorderBufferQueue;
    SLBufferQueueItf playerBufferQueue;

    pthread_mutex_t mutex;// = PTHREAD_MUTEX_INITIALIZER;

    //other things that belong here
    SLObjectItf playerObject;
    SLObjectItf recorderObject;
    SLObjectItf outputmixObject;
    SLObjectItf engineObject;
} sles_data;

enum {
    SLES_SUCCESS = 0,
    SLES_FAIL = 1,
} SLES_STATUS_ENUM;

int slesInit( sles_data ** ppSles, int samplingRate, int frameCount, int micSource);
//note the double pointer to properly free the memory of the structure
int slesDestroy( sles_data ** ppSles);


///full
int slesFull(sles_data *pSles);

int slesCreateServer(sles_data *pSles, int samplingRate, int frameCount, int micSource);
int slesProcessNext(sles_data *pSles, double *pSamples, long maxSamples);
int slesDestroyServer(sles_data *pSles);

#ifdef __cplusplus
}
#endif
#endif //_Included_org_drrickorang_loopback_sles
