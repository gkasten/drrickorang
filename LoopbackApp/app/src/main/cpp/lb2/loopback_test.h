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

#ifndef LB2_LOOPBACK_TEST_H_
#define LB2_LOOPBACK_TEST_H_

#include <atomic>
#include <memory>

#include <audio_utils/fifo.h>

#include "lb2/audio_buffer.h"
#include "lb2/sound_system.h"
#include "lb2/test_context.h"

// Generic test interface. The test is driven by the write callback
// of the sound system and periodic polling via 'collectRecording'
// method.
class LoopbackTest {
  public:
    LoopbackTest(SoundSystem* soundSys, TestContext* testCtx);
    LoopbackTest(const LoopbackTest&) = delete;
    LoopbackTest& operator=(const LoopbackTest&) = delete;
    virtual ~LoopbackTest();

    virtual bool init();
    virtual int collectRecording(AudioBufferView<double> buffer);

  protected:
    // This method is called on the sound system callback thread.
    void receiveRecording(size_t framesRead);

    SoundSystem* mSoundSys;
    AudioBuffer<sample_t> mReadBuffer;

  private:
    static constexpr size_t RECORDING_FIFO_FRAMES = 65536;
    static constexpr size_t COLLECTION_LOOPS = 10;
    static constexpr size_t COLLECTION_PERIOD_MS = 100;

    TestContext* mTestCtx;
    std::unique_ptr<sample_t[]> mRecordingFifoData;
    struct audio_utils_fifo mRecordingFifo;
};


// Latency test implementation. Using the parameters from the test
// context, first it drains the audio system read queue, then injects
// provided impulse, and then copies read audio input to output.
class LatencyTest : public LoopbackTest {
  public:
    LatencyTest(SoundSystem* soundSys, LatencyTestContext* testCtx);
    LatencyTest(const LatencyTest&) = delete;
    LatencyTest& operator=(const LatencyTest&) = delete;
    virtual ~LatencyTest();

    bool init() override;

  private:
    static constexpr size_t INITIAL_SILENCE_MS = 240;  // Approx. as in the legacy version.

    AudioBufferView<sample_t> writeCallback(size_t expectedFrames);

    //LatencyTestContext* mTestCtx;
    int mDrainInput;
    int mInputFramesToDiscard;
    int mInitialSilenceFrameCount;
    int mInjectImpulseNextFramePos;
    AudioBufferView<sample_t> mImpulse;
};


// Glitch test implementation. Writes the test signal to output,
// and reads back input.
class GlitchTest : public LoopbackTest {
  public:
    GlitchTest(SoundSystem* soundSys, GlitchTestContext* testCtx);
    GlitchTest(const GlitchTest&) = delete;
    GlitchTest& operator=(const GlitchTest&) = delete;
    virtual ~GlitchTest();

    bool init() override;

  private:
    AudioBufferView<sample_t> writeCallback(size_t expectedFrames);

    GlitchTestContext* mTestCtx;
};

#endif  // LB2_LOOPBACK_TEST_H_
