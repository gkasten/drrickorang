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

#include "lb2/loopback2.h"
#include "loopback_sles.h"

native_engine_t sEngines[NATIVE_ENGINE_COUNT] = {
    // NATIVE_ENGINE_SLES
    {
        slesComputeDefaultSettings,
        slesInit,
        slesDestroy,
        slesProcessNext,
        slesGetRecorderBufferPeriod,
        slesGetRecorderMaxBufferPeriod,
        slesGetRecorderVarianceBufferPeriod,
        slesGetPlayerBufferPeriod,
        slesGetPlayerMaxBufferPeriod,
        slesGetPlayerVarianceBufferPeriod,
        slesGetCaptureRank,
        slesGetPlayerTimeStampsAndExpectedBufferPeriod,
        slesGetRecorderTimeStampsAndExpectedBufferPeriod
    },
    // NATIVE_ENGINE_AAUDIO
    {
        lb2ComputeDefaultSettings,
        lb2Init,
        lb2Destroy,
        lb2ProcessNext,
        lb2GetRecorderBufferPeriod,
        lb2GetRecorderMaxBufferPeriod,
        lb2GetRecorderVarianceBufferPeriod,
        lb2GetPlayerBufferPeriod,
        lb2GetPlayerMaxBufferPeriod,
        lb2GetPlayerVarianceBufferPeriod,
        lb2GetCaptureRank,
        lb2GetPlayerTimeStampsAndExpectedBufferPeriod,
        lb2GetRecorderTimeStampsAndExpectedBufferPeriod
    }
};
