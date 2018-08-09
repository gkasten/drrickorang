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

#ifndef LB2_TEST_CONTEXT_H_
#define LB2_TEST_CONTEXT_H_

#include <memory>

#include <SLES/OpenSLES.h>  // for SLuint... types use by performance mode consts
#include <SLES/OpenSLES_AndroidConfiguration.h>

#include "lb2/audio_buffer.h"

// The Java side uses the same numbers as OpenSL ES, and '-1' for default,
// see LoopbackApplication.java.
enum class PerformanceMode {
    DEFAULT = -1,
    NONE = SL_ANDROID_PERFORMANCE_NONE,
    LATENCY = SL_ANDROID_PERFORMANCE_LATENCY,
    LATENCY_EFFECTS = SL_ANDROID_PERFORMANCE_LATENCY_EFFECTS,
    POWER_SAVING = SL_ANDROID_PERFORMANCE_POWER_SAVING
};

// Generic context describing test parameters.
// Made non-copyable because descendants can contain buffers.
class TestContext : public CountsConverter<sample_t> {
  public:
    TestContext(PerformanceMode perfMode,
            int testFrameCount,
            int channelCount,
            int samplingRateHz)
            : CountsConverter<sample_t>(testFrameCount, channelCount),
              mPerfMode(perfMode),
              mSamplingRateHz(samplingRateHz) {}
    TestContext(const TestContext&) = delete;
    TestContext& operator=(const TestContext&) = delete;

    // Allocates an audio buffer with the size enough to hold audio test data.
    AudioBuffer<sample_t> createAudioBuffer() const {
        return AudioBuffer<sample_t>(getFrameCount(), getChannelCount());
    }
    PerformanceMode getPerformanceMode() const { return mPerfMode; }
    int getSamplingRateHz() const { return mSamplingRateHz; }

  private:
    const PerformanceMode mPerfMode;
    const int mSamplingRateHz;
};


// Context describing latency test parameters.
// Carries test impulse data, but doesn't own it.
// The size of the impulse is assumed to be 1 frame buffer.
class LatencyTestContext : public TestContext {
  public:
    LatencyTestContext(PerformanceMode perfMode,
            int testFrameCount,
            int channelCount,
            int samplingRateHz,
            int inputFramesToDiscard,
            sample_t *impulse)
            : TestContext(perfMode, testFrameCount, channelCount, samplingRateHz),
              mInputFramesToDiscard(inputFramesToDiscard),
              mImpulse(impulse, testFrameCount, channelCount) {}
    LatencyTestContext(const LatencyTestContext&) = delete;
    LatencyTestContext& operator=(const LatencyTestContext&) = delete;

    int getInputFramesToDiscard() const { return mInputFramesToDiscard; }
    AudioBufferView<sample_t> getImpulse() const { return mImpulse; }

  private:
    const int mInputFramesToDiscard;
    const AudioBufferView<sample_t> mImpulse;
};


// Context describing glitch test parameters.
// Generates test signal. Since the period of the test signal
// is not necessarily aligned with the test buffer size,
// the operation of getting next impulse piece is idempotent.
class GlitchTestContext : public TestContext {
  public:
    GlitchTestContext(PerformanceMode perfMode,
            int testFrameCount,
            int channelCount,
            int samplingRateHz,
            double signalFrequencyHz,
            AudioBufferView<sample_t> byteBuffer)
            : TestContext(perfMode, testFrameCount, channelCount, samplingRateHz),
              mByteBuffer(byteBuffer),
              mPhaseIncrementPerFrame(signalFrequencyHz / samplingRateHz),
              mSineBuffer(createAudioBuffer()),
              mPhaseRad(0) {}
    GlitchTestContext(const GlitchTestContext&) = delete;
    GlitchTestContext& operator=(const GlitchTestContext&) = delete;

    const AudioBufferView<sample_t>& getByteBuffer() const { return mByteBuffer; }
    AudioBufferView<sample_t> getNextImpulse(size_t frameCount);  // non-idempotent

  private:
    static constexpr double SIGNAL_AMPLITUDE = 0.8;

    const AudioBufferView<sample_t> mByteBuffer;
    const double mPhaseIncrementPerFrame;
    AudioBuffer<sample_t> mSineBuffer;
    double mPhaseRad;
};


#endif  // LB2_TEST_CONTEXT_H_
