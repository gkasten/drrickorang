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

#ifndef _Included_org_drrickorang_loopback_loopback_sles
#define _Included_org_drrickorang_loopback_loopback_sles

#include "loopback.h"

#ifdef __cplusplus
extern "C" {
#endif

int slesComputeDefaultSettings(int performanceMode, int *samplingRate,
             int *playerBufferFrameCount, int *recorderBufferFrameCount);
int slesInit(void ** ppCtx, int samplingRate, int frameCount, int micSource,
             int performanceMode,
             int testType, double frequency1, char* byteBufferPtr, int byteBufferLength,
             short* loopbackTone, int maxRecordedLateCallbacks, int ignoreFirstFrames);
int slesDestroy(void ** ppCtx);
int slesProcessNext(void *pCtx, double *pSamples, long maxSamples);
int* slesGetRecorderBufferPeriod(void *pCtx);
int slesGetRecorderMaxBufferPeriod(void *pCtx);
int64_t slesGetRecorderVarianceBufferPeriod(void *pCtx);
int* slesGetPlayerBufferPeriod(void *pCtx);
int slesGetPlayerMaxBufferPeriod(void *pCtx);
int64_t slesGetPlayerVarianceBufferPeriod(void *pCtx);
int slesGetCaptureRank(void *pCtx);
int slesGetPlayerTimeStampsAndExpectedBufferPeriod(void *pCtx, callbackTimeStamps **ppTSs);
int slesGetRecorderTimeStampsAndExpectedBufferPeriod(void *pCtx, callbackTimeStamps **ppTSs);

#ifdef __cplusplus
}
#endif

#endif  // _Included_org_drrickorang_loopback_loopback_sles
