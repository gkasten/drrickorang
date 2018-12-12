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

#include "lb2/loopback_test.h"

#include <chrono>
#include <thread>

#include "byte_buffer.h"
#include "lb2/logging.h"
#include "lb2/util.h"

constexpr size_t LoopbackTest::COLLECTION_PERIOD_MS;

LoopbackTest::LoopbackTest(SoundSystem* soundSys, TestContext* testCtx) :
        mSoundSys(soundSys),
        mReadBuffer(testCtx->createAudioBuffer()),
        mTestCtx(testCtx),
        mRecordingFifoData(new sample_t[RECORDING_FIFO_FRAMES * testCtx->getChannelCount()]) {
    audio_utils_fifo_init(
            &mRecordingFifo,
            RECORDING_FIFO_FRAMES,
            mTestCtx->getFrameSize(),
            mRecordingFifoData.get());
}

LoopbackTest::~LoopbackTest() {
    audio_utils_fifo_deinit(&mRecordingFifo);
}

bool LoopbackTest::init() {
    return true;
}

int LoopbackTest::collectRecording(AudioBufferView<double> buffer) {
    int framesRead = 0;
    AudioBuffer<sample_t> readBuffer(mTestCtx->createAudioBuffer());

    for (size_t i = 0; i < COLLECTION_LOOPS; ++i) {
        std::this_thread::sleep_for(std::chrono::milliseconds(COLLECTION_PERIOD_MS));
        if (i != 0) {
            readBuffer.clear();
        }
        while (framesRead <= static_cast<int>(buffer.getFrameCount())) {
            // Note that we always read in mTestCtx->getFrameCount() chunks.
            // This is how the legacy version works, but it's not clear whether
            // this is correct, since some data from the fifo may be lost
            // if the size of the buffer provided by Java isn't a multiple of
            // getFrameCount().
            ssize_t actualFrames = audio_utils_fifo_read(
                    &mRecordingFifo, readBuffer.getData(), readBuffer.getFrameCount());
            if (actualFrames <= 0) break;
            AudioBufferView<double> dst = buffer.getView(framesRead, actualFrames);
            convertAudioBufferViewType(readBuffer.getView(0, dst.getFrameCount()), dst);
            framesRead += actualFrames;
        }
    }
    return framesRead * mTestCtx->getChannelCount();
}

void LoopbackTest::receiveRecording(size_t framesRead) {
    ssize_t actualFrames =
            audio_utils_fifo_write(&mRecordingFifo, mReadBuffer.getData(), framesRead);
    if (actualFrames >= 0 && static_cast<size_t>(actualFrames) != framesRead) {
        ALOGW("recording pipe problem (expected %lld): %lld",
                (long long)framesRead, (long long)actualFrames);
    } else if (actualFrames < 0) {
        ALOGW("pipe write returned negative value: %lld", (long long)actualFrames);
    }
}


LatencyTest::LatencyTest(SoundSystem* soundSys, LatencyTestContext* testCtx)
        : LoopbackTest(soundSys, testCtx),
          //mTestCtx(testCtx),
          mDrainInput(true),
          mInputFramesToDiscard(testCtx->getInputFramesToDiscard()),
          mInitialSilenceFrameCount(wholeMultiplier(
                          testCtx->getSamplingRateHz() * INITIAL_SILENCE_MS, MS_PER_SECOND)),
          mInjectImpulseNextFramePos(0),
          mImpulse(testCtx->getImpulse()) {
}

LatencyTest::~LatencyTest() {
    mSoundSys->shutdown();
}

bool LatencyTest::init() {
    if (!LoopbackTest::init()) return false;
    return mSoundSys->init(std::bind(&LatencyTest::writeCallback, this, std::placeholders::_1));
}

AudioBufferView<sample_t> LatencyTest::writeCallback(size_t expectedFrames) {
    // Always perform a read operation first since the read buffer is always
    // filling in. But depending on the conditions, the read data is either
    // completely discarded, or being sent to the Java layer, and may in addition
    // be written back to the output.
    //
    // There are strange side effects on Pixel 2 if the app is trying to read
    // too much data, so always read only as many frames as we can currently write.
    // See b/68003241.
    AudioBufferView<sample_t> readBuffer = mReadBuffer.getView(0, expectedFrames);
    ssize_t framesRead = mSoundSys->readAudio(readBuffer);
    // ALOGV("Read %lld frames of %lld",
    //         (long long)framesRead, (long long)readBuffer.getFrameCount());
    if (mInputFramesToDiscard > 0 || mInitialSilenceFrameCount > 0) {
        if (mInputFramesToDiscard > 0) {
            mInputFramesToDiscard -= framesRead;
        } else {
            if (framesRead > 0) {
                receiveRecording(framesRead);
            }
            mInitialSilenceFrameCount -= expectedFrames;
        }
    } else if (mDrainInput) {
        if (mSoundSys->drainInput()) {
            mDrainInput = false;
        }
    } else {
        if (framesRead > 0) {
            receiveRecording(framesRead);
        }
        if (mInjectImpulseNextFramePos >= 0) {
            ALOGV("Injecting impulse from pos %d", mInjectImpulseNextFramePos);
            AudioBufferView<sample_t> impulseChunk =
                    mImpulse.getView(mInjectImpulseNextFramePos, expectedFrames);
            mInjectImpulseNextFramePos += impulseChunk.getFrameCount();
            if (mInjectImpulseNextFramePos >= static_cast<int>(mImpulse.getFrameCount())) {
                mInjectImpulseNextFramePos = -1;
            }
            return impulseChunk;
        } else if (framesRead > 0) {
            return readBuffer.getView(0, framesRead);
        }
    }
    return AudioBuffer<sample_t>();
}


GlitchTest::GlitchTest(SoundSystem* soundSys, GlitchTestContext* testCtx)
        : LoopbackTest(soundSys, testCtx),
          mTestCtx(testCtx) {
}

GlitchTest::~GlitchTest() {
    mSoundSys->shutdown();
}

bool GlitchTest::init() {
    if (!LoopbackTest::init()) return false;
    return mSoundSys->init(std::bind(&GlitchTest::writeCallback, this, std::placeholders::_1));
}

AudioBufferView<sample_t> GlitchTest::writeCallback(size_t expectedFrames) {
    ssize_t framesRead = mSoundSys->readAudio(mReadBuffer);
    if (framesRead > 0) {
        receiveRecording(framesRead);
        ssize_t bbResult = byteBuffer_write(
                reinterpret_cast<char*>(mTestCtx->getByteBuffer().getData()),
                mTestCtx->getByteBuffer().getFrameCount(),
                reinterpret_cast<const char*>(mReadBuffer.getData()),
                framesRead, mTestCtx->getChannelCount());
        if (bbResult >= 0 && bbResult < framesRead) {
            ALOGW("ByteBuffer only consumed %lld bytes from %lld",
                    (long long)bbResult, (long long)framesRead);
        } else if (bbResult < 0) {
            ALOGW("ByteBuffer error: %lld", (long long)bbResult);
        }
    }
    return mTestCtx->getNextImpulse(expectedFrames);
}
