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

#ifndef _Included_org_drrickorang_loopback_lb2_loopback2
#define _Included_org_drrickorang_loopback_lb2_loopback2

#include "loopback.h"

#ifdef __cplusplus
extern "C" {
#endif

int lb2ComputeDefaultSettings(int performanceMode, int *samplingRate,
             int *playerBufferFrameCount, int *recorderBufferFrameCount);
int lb2Init(void ** ppCtx, int samplingRate, int frameCount, int micSource,
             int performanceMode,
             int testType, double frequency1, char* byteBufferPtr, int byteBufferLength,
             short* loopbackTone, int maxRecordedLateCallbacks, int ignoreFirstFrames);
int lb2Destroy(void ** ppCtx);
int lb2ProcessNext(void *pCtx, double *pSamples, long maxSamples);
int* lb2GetRecorderBufferPeriod(void *pCtx);
int lb2GetRecorderMaxBufferPeriod(void *pCtx);
int64_t lb2GetRecorderVarianceBufferPeriod(void *pCtx);
int* lb2GetPlayerBufferPeriod(void *pCtx);
int lb2GetPlayerMaxBufferPeriod(void *pCtx);
int64_t lb2GetPlayerVarianceBufferPeriod(void *pCtx);
int lb2GetCaptureRank(void *pCtx);
int lb2GetPlayerTimeStampsAndExpectedBufferPeriod(void *pCtx, callbackTimeStamps **ppTSs);
int lb2GetRecorderTimeStampsAndExpectedBufferPeriod(void *pCtx, callbackTimeStamps **ppTSs);

#ifdef __cplusplus
}
#endif

#endif  // _Included_org_drrickorang_loopback_lb2_loopback2
