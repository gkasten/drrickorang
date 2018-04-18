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

#ifndef LB2_SOUND_SYSTEM_ECHO_H_
#define LB2_SOUND_SYSTEM_ECHO_H_

#include <atomic>
#include <memory>
#include <thread>

#include <audio_utils/fifo.h>

#include "lb2/sound_system.h"
#include "lb2/test_context.h"

// Simplest implementation of a sound system that echoes written data
// back to the reader. This represents an ideal model of a physical loopback dongle.
class SoundSystemEcho : public SoundSystem {
  public:
    SoundSystemEcho(const TestContext *testCtx);
    SoundSystemEcho(const SoundSystemEcho&) = delete;
    SoundSystemEcho& operator=(const SoundSystemEcho&) = delete;
    virtual ~SoundSystemEcho();

    bool init(WriteCallback callback) override;
    bool drainInput() override;
    ssize_t readAudio(AudioBufferView<sample_t> buffer) override;
    void shutdown() override;

  private:
    static int calculateMsecPerBuffer(const TestContext *testCtx);

    void startThread();
    void stopThread();
    void threadLoop();

    const TestContext* mTestCtx;
    std::unique_ptr<sample_t[]> mFifoData;
    struct audio_utils_fifo mFifo;
    const int mMsecPerBuffer;
    WriteCallback mWriteCallback;      // accessed by mThread
    std::atomic<bool> mThreadRunning;  // accessed by mThread
    std::unique_ptr<std::thread> mThread;
};

#endif  // LB2_SOUND_SYSTEM_ECHO_H_
