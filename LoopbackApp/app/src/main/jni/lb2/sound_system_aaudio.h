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

#ifndef LB2_SOUND_SYSTEM_AAUDIO_H_
#define LB2_SOUND_SYSTEM_AAUDIO_H_

#include <memory>

#include "lb2/sound_system.h"
#include "lb2/test_context.h"

// Implementation of a sound system via AAudio API.
class SoundSystemAAudio : public SoundSystem {
  public:
    // Default constructor--for probing.
    SoundSystemAAudio();
    // Constructor with a test context--for testing.
    explicit SoundSystemAAudio(const TestContext *testCtx);
    SoundSystemAAudio(const SoundSystemAAudio&) = delete;
    SoundSystemAAudio& operator=(const SoundSystemAAudio&) = delete;
    virtual ~SoundSystemAAudio();

    bool probeDefaultSettings(PerformanceMode performanceMode, int *samplingRate,
            int *playerBufferFrameCount, int *recorderBufferFrameCount) override;
    bool init(WriteCallback callback) override;
    bool drainInput() override;
    ssize_t readAudio(AudioBufferView<sample_t> buffer) override;
    void shutdown() override;

  private:
    struct Impl;  // AAudio-specific details.

    const TestContext* mTestCtx;
    const std::unique_ptr<Impl> mImpl;
};

#endif  // LB2_SOUND_SYSTEM_AAUDIO_H_
