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

#include "lb2/sound_system_echo.h"

#include <chrono>
#include <functional>

#define LOG_TAG "ss_echo"
#include "lb2/logging.h"
#include "lb2/util.h"

SoundSystemEcho::SoundSystemEcho(const TestContext *testCtx)
        : mTestCtx(testCtx),
          mFifoData(new sample_t[testCtx->getSampleCount()]),
          mMsecPerBuffer(calculateMsecPerBuffer(testCtx)),
          mThreadRunning(false) {
    audio_utils_fifo_init(
            &mFifo,
            mTestCtx->getFrameCount(),
            mTestCtx->getFrameSize(),
            mFifoData.get());
}

SoundSystemEcho::~SoundSystemEcho() {
    shutdown();
    audio_utils_fifo_deinit(&mFifo);
}

int SoundSystemEcho::calculateMsecPerBuffer(const TestContext *testCtx) {
    return wholeMultiplier(MS_PER_SECOND * testCtx->getFrameCount(), testCtx->getSamplingRateHz());
}

void SoundSystemEcho::startThread() {
    mThreadRunning = true;
    mThread.reset(new std::thread(std::bind(&SoundSystemEcho::threadLoop, this)));
}

void SoundSystemEcho::stopThread() {
    mThreadRunning = false;
    mThread->join();
    mThread.reset();
}

void SoundSystemEcho::threadLoop() {
    while (mThreadRunning) {
        AudioBufferView<sample_t> buffer = mWriteCallback(mTestCtx->getFrameCount());
        // The FIFO will cut the data if it exceeds the buffer size.
        audio_utils_fifo_write(&mFifo, buffer.getData(), buffer.getFrameCount());
        std::this_thread::sleep_for(std::chrono::milliseconds(mMsecPerBuffer));
    }
}

bool SoundSystemEcho::init(WriteCallback callback) {
    if (mThreadRunning) {
        shutdown();
    }
    mWriteCallback = callback;
    startThread();
    return true;
}

bool SoundSystemEcho::drainInput() {
    AudioBuffer<sample_t> drainBuffer(
            audio_utils_fifo_availToRead(&mFifo), mTestCtx->getChannelCount());
    return audio_utils_fifo_read(&mFifo, drainBuffer.getData(), drainBuffer.getFrameCount()) >= 0;
}

ssize_t SoundSystemEcho::readAudio(AudioBufferView<sample_t> buffer) {
    std::this_thread::sleep_for(std::chrono::milliseconds(mMsecPerBuffer));
    ssize_t result = audio_utils_fifo_read(&mFifo, buffer.getData(), buffer.getFrameCount());
    if (result != 0) return result;
    buffer.clear();
    return buffer.getFrameCount();
}

void SoundSystemEcho::shutdown() {
    if (!mThreadRunning) return;
    stopThread();
    mWriteCallback = nullptr;
}
