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

#include <memory>

#include <android/log.h>

#include "lb2/logging.h"
#include "lb2/loopback2.h"
#include "lb2/loopback_test.h"
#include "lb2/sound_system_aaudio.h"
#include "lb2/sound_system_echo.h"

// The Java layer always uses "mono" mode for native tests.
static constexpr int CHANNEL_COUNT = 1;

struct LbData {
    std::unique_ptr<TestContext> testContext;
    std::unique_ptr<SoundSystem> soundSys;
    std::unique_ptr<LoopbackTest> currentTest;
};

int lb2ComputeDefaultSettings(int performanceMode, int *samplingRate,
             int *playerBufferFrameCount, int *recorderBufferFrameCount) {
    SoundSystemAAudio ss;
    return ss.probeDefaultSettings(static_cast<PerformanceMode>(performanceMode),
            samplingRate, playerBufferFrameCount, recorderBufferFrameCount) ?
            STATUS_SUCCESS : STATUS_FAIL;
}

int lb2Init(void **ppLbData, int samplingRate, int frameCount, int /*micSource*/,
        int performanceMode, int testType, double frequency1, char* byteBufferPtr,
        int byteBufferLength, short* loopbackTone, int /*maxRecordedLateCallbacks*/,
        int ignoreFirstFrames) {
    *ppLbData = nullptr;
    std::unique_ptr<LbData> lbData(new LbData());  // will auto-release in case if init fails.
    switch (testType) {
        case TEST_TYPE_LATENCY:
            lbData->testContext.reset(new LatencyTestContext(
                            static_cast<PerformanceMode>(performanceMode), frameCount,
                            CHANNEL_COUNT, samplingRate, ignoreFirstFrames, loopbackTone));
            break;
        case TEST_TYPE_BUFFER_PERIOD: {
            // TODO: Get rid of ByteBuffer.
            static_assert(
                    sizeof(sample_t) == sizeof(short), "byteBuffer only supports short samples");
            AudioBufferView<sample_t> byteBuffer(
                    reinterpret_cast<sample_t*>(byteBufferPtr), byteBufferLength, CHANNEL_COUNT);
            lbData->testContext.reset(new GlitchTestContext(
                            static_cast<PerformanceMode>(performanceMode),frameCount,
                            CHANNEL_COUNT, samplingRate, frequency1, std::move(byteBuffer)));
            break;
        }
        default:
            ALOGE("Invalid test type: %d", testType);
            return STATUS_FAIL;
    }
    // TODO: Implement switching from the Java side.
    lbData->soundSys.reset(new SoundSystemAAudio(lbData->testContext.get()));
    // lbData->soundSys.reset(new SoundSystemEcho(lbData->testContext.get()));
    switch (testType) {
        case TEST_TYPE_LATENCY:
            lbData->currentTest.reset(new LatencyTest(
                            lbData->soundSys.get(),
                            static_cast<LatencyTestContext*>(lbData->testContext.get())));
            break;
        case TEST_TYPE_BUFFER_PERIOD:
            lbData->currentTest.reset(new GlitchTest(
                            lbData->soundSys.get(),
                            static_cast<GlitchTestContext*>(lbData->testContext.get())));
            break;
    }
    if (!lbData->currentTest->init()) return STATUS_FAIL;
    *ppLbData = lbData.release();
    return STATUS_SUCCESS;
}

int lb2ProcessNext(void *pLbData, double *pSamples, long maxSamples) {
    if (pLbData == nullptr) return 0;
    LbData *lbData = static_cast<LbData*>(pLbData);
    return lbData->currentTest->collectRecording(
            AudioBufferView<double>(pSamples, maxSamples / CHANNEL_COUNT, CHANNEL_COUNT));
}

int lb2Destroy(void **ppCtx) {
    LbData** ppLbData = reinterpret_cast<LbData**>(ppCtx);
    if (ppLbData != nullptr) {
        delete *ppLbData;
        *ppLbData = nullptr;
        return STATUS_SUCCESS;
    } else {
        return STATUS_FAIL;
    }
}

int* lb2GetRecorderBufferPeriod(void*) {
    static int *bufferPeriod = new int[1002]();
    return bufferPeriod;
}

int lb2GetRecorderMaxBufferPeriod(void*) {
    return 0;
}

int64_t lb2GetRecorderVarianceBufferPeriod(void*) {
    return 0;
}

int* lb2GetPlayerBufferPeriod(void*) {
    static int *bufferPeriod = new int[1002]();
    return bufferPeriod;
}

int lb2GetPlayerMaxBufferPeriod(void*) {
    return 0;
}

int64_t lb2GetPlayerVarianceBufferPeriod(void*) {
    return 0;
}

int lb2GetCaptureRank(void*) {
    return 0;
}

int lb2GetPlayerTimeStampsAndExpectedBufferPeriod(void*, callbackTimeStamps **ppTSs) {
    static callbackTimeStamps tss = {
        new int[10],               //int* timeStampsMs
        new short[10],             //short* callbackDurations
        0,                         //short index
        {0,0},                     //struct timespec startTime;
        0,                         //int capacity
        false                      //bool exceededCapacity
    };
    *ppTSs = &tss;
    return 0;
}

int lb2GetRecorderTimeStampsAndExpectedBufferPeriod(void*, callbackTimeStamps **ppTSs) {
    static callbackTimeStamps tss = {
        new int[10],               //int* timeStampsMs
        new short[10],             //short* callbackDurations
        0,                         //short index
        {0,0},                     //struct timespec startTime;
        0,                         //int capacity
        false                      //bool exceededCapacity
    };
    *ppTSs = &tss;
    return 0;
}
