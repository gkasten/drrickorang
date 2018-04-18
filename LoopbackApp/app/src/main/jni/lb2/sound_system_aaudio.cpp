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

#include "lb2/sound_system_aaudio.h"

#include <aaudio/AAudio.h>

#define LOG_TAG "ss_aaudio"
#include "lb2/logging.h"
#include "lb2/oboe/src/aaudio/AAudioLoader.h"
#include "lb2/util.h"

namespace {

class Stream {
  public:
    explicit Stream(AAudioStream *stream);
    Stream(const Stream&) = delete;
    Stream& operator=(const Stream&) = delete;
    ~Stream();

    int getChannelCount() const { return mChannelCount; }
    int getFramesPerBurst() const { return mFramesPerBurst; }
    int getSamplingRateHz();
    ssize_t read(AudioBufferView<sample_t> buffer);
    bool setBufferFrameCount(int numFrames);
    bool start();
    bool stop();

  private:
    AAudioLoader *mAAudio;
    AAudioStream *mAAStream;
    const int mChannelCount;
    const int mFramesPerBurst;
};

Stream::Stream(AAudioStream *stream)
        : mAAudio(AAudioLoader::getInstance()),
          mAAStream(stream),
          mChannelCount(mAAudio->stream_getChannelCount(stream)),
          mFramesPerBurst(mAAudio->stream_getFramesPerBurst(stream)) {
    ALOGV("Created stream, channel count %d, frames per burst: %d",
            mChannelCount, mFramesPerBurst);
}

Stream::~Stream() {
    aaudio_result_t result = mAAudio->stream_close(mAAStream);
    if (result != AAUDIO_OK) {
        ALOGE("Failed to close stream %s (%d)", mAAudio->convertResultToText(result), result);
    }
}

int Stream::getSamplingRateHz() {
    return mAAudio->stream_getSampleRate(mAAStream);
}

ssize_t Stream::read(AudioBufferView<sample_t> buffer) {
    ATRACE_CALL();
    aaudio_result_t result = mAAudio->stream_read(
            mAAStream, buffer.getData(), buffer.getFrameCount(), 0 /* timeout */);
    if (result < 0) {
        ALOGE("Failed to read from the stream %s (%d)",
                mAAudio->convertResultToText(result), result);
    }
    return result;
}

bool Stream::setBufferFrameCount(int numFrames) {
    aaudio_result_t result = mAAudio->stream_setBufferSize(mAAStream, numFrames);
    if (result < 0) {
        ALOGE("Failed to set frame buffer size to %d frames: %s (%d)",
                numFrames, mAAudio->convertResultToText(result), result);
    }
    return result >= 0;
}

bool Stream::start() {
    aaudio_result_t result = mAAudio->stream_requestStart(mAAStream);
    if (result != AAUDIO_OK) {
        ALOGE("Failed to start the stream %s (%d)", mAAudio->convertResultToText(result), result);
        return false;
    }
    return true;
}

bool Stream::stop() {
    aaudio_result_t result = mAAudio->stream_requestStop(mAAStream);
    if (result != AAUDIO_OK) {
        ALOGE("Failed to stop the stream %s (%d)", mAAudio->convertResultToText(result), result);
        return false;
    }
    return true;
}


class StreamBuilder {
  public:
    explicit StreamBuilder(AAudioStreamBuilder *builder);
    StreamBuilder(const StreamBuilder&) = delete;
    StreamBuilder& operator=(const StreamBuilder&) = delete;
    ~StreamBuilder();

    std::unique_ptr<Stream> makeStream();
    void setCallbacks(AAudioStream_dataCallback dataCb,
            AAudioStream_errorCallback errorCb,
            void *userData) {
        mAAudio->builder_setDataCallback(mAABuilder, dataCb, userData);
        mAAudio->builder_setErrorCallback(mAABuilder, errorCb, userData);
    }
    void setChannelCount(int32_t channelCount) {
        mAAudio->builder_setChannelCount(mAABuilder, channelCount);
    }
    void setDirection(aaudio_direction_t direction) {
        mAAudio->builder_setDirection(mAABuilder, direction);
    }
    void setFormat(aaudio_format_t format) {
        mAAudio->builder_setFormat(mAABuilder, format);
    }
    void setPerformanceMode(aaudio_performance_mode_t mode) {
        mAAudio->builder_setPerformanceMode(mAABuilder, mode);
    }
    void setSampleRate(int32_t sampleRate) {
        mAAudio->builder_setSampleRate(mAABuilder, sampleRate);
    }
    void setSharingMode(aaudio_sharing_mode_t sharingMode) {
        mAAudio->builder_setSharingMode(mAABuilder, sharingMode);
    }

  private:
    AAudioLoader *mAAudio;
    AAudioStreamBuilder *mAABuilder;
};

StreamBuilder::StreamBuilder(AAudioStreamBuilder *builder)
        : mAAudio(AAudioLoader::getInstance()),
          mAABuilder(builder) {
}

StreamBuilder::~StreamBuilder() {
    aaudio_result_t result = mAAudio->builder_delete(mAABuilder);
    if (result != AAUDIO_OK) {
        ALOGE("Failed to delete stream builder %s (%d)",
                mAAudio->convertResultToText(result), result);
    }
}

std::unique_ptr<Stream> StreamBuilder::makeStream() {
    AAudioStream *stream = nullptr;
    aaudio_result_t result = mAAudio->builder_openStream(mAABuilder, &stream);
    if (result != AAUDIO_OK || stream == nullptr) {
        ALOGE("Failed to create stream %s (%d) %p",
                mAAudio->convertResultToText(result), result, stream);
        return nullptr;
    }
    return std::unique_ptr<Stream>(new Stream(stream));
}

std::unique_ptr<StreamBuilder> makeStreamBuilder() {
    AAudioStreamBuilder *builder = nullptr;
    aaudio_result_t result = AAudioLoader::getInstance()->createStreamBuilder(&builder);
    if (result != AAUDIO_OK || builder == nullptr) {
        ALOGE("Failed to create stream builder %s (%d) %p",
                AAudioLoader::getInstance()->convertResultToText(result), result, builder);
        return nullptr;
    }
    return std::unique_ptr<StreamBuilder>(new StreamBuilder(builder));
}

aaudio_performance_mode_t getAAudioPerfMode(PerformanceMode performanceMode) {
    switch (performanceMode) {
        case PerformanceMode::NONE: return AAUDIO_PERFORMANCE_MODE_NONE;
        case PerformanceMode::DEFAULT:  // The testing mode we should use by default is low latency.
        case PerformanceMode::LATENCY:
        case PerformanceMode::LATENCY_EFFECTS: return AAUDIO_PERFORMANCE_MODE_LOW_LATENCY;
        case PerformanceMode::POWER_SAVING: return AAUDIO_PERFORMANCE_MODE_POWER_SAVING;
    }
    ALOGE("Invalid performance mode value %d", static_cast<int>(performanceMode));
    return AAUDIO_PERFORMANCE_MODE_NONE;
}

int calculateBufferSizeInFrames(int burstSizeInFrames, int bufferSizeMs, int samplingRateHz) {
    const int desiredBufferSizeInFrames = wholeMultiplier(
            bufferSizeMs * samplingRateHz, MS_PER_SECOND);
    // Figure out how many bursts we need to cover the desired buffer size completely, and multiply
    // that number by the burst size.
    return wholeMultiplier(desiredBufferSizeInFrames, burstSizeInFrames) * burstSizeInFrames;
}


class Player {
  public:
    using ErrorCallback = std::function<void(aaudio_result_t)>;

    Player() {}
    Player(const Player&) = delete;
    Player& operator=(const Player&) = delete;
    ~Player() { shutdown(); }

    bool probeDefaults(
            PerformanceMode performanceMode, int *samplingRate, int *playerBufferFrameCount);
    bool init(const TestContext *testCtx,
            SoundSystem::WriteCallback writeClb,
            ErrorCallback errorClb);
    void shutdown();

  private:
    // Output stream buffer size in milliseconds. Larger values increase
    // latency, but reduce possibility of glitching. AAudio operates in
    // 2ms "bursts" by default (controlled by "aaudio.hw_burst_min_usec"
    // system property), so 4 ms is 2 bursts--"double buffering".
    // TODO: May actually read the property value to derive this
    //       value, but property reading isn't exposed in NDK.
    static constexpr int MINIMUM_STREAM_BUFFER_SIZE_MS = 4;

    static aaudio_data_callback_result_t aaudioDataCallback(AAudioStream *stream,
            void *userData,
            void *audioData,
            int32_t numFrames);
    static void aaudioErrorCallback(AAudioStream *stream,
            void *userData,
            aaudio_result_t error);

    std::unique_ptr<StreamBuilder> createBuilder(PerformanceMode performanceMode);

    const TestContext *mTestCtx;
    std::unique_ptr<Stream> mStream;
    SoundSystem::WriteCallback mWriteCallback;
    ErrorCallback mErrorCallback;
};

std::unique_ptr<StreamBuilder> Player::createBuilder(PerformanceMode performanceMode) {
    std::unique_ptr<StreamBuilder> builder = makeStreamBuilder();
    if (builder) {
        builder->setDirection(AAUDIO_DIRECTION_OUTPUT);
        builder->setSharingMode(AAUDIO_SHARING_MODE_EXCLUSIVE);
        builder->setPerformanceMode(getAAudioPerfMode(performanceMode));
        static_assert(sizeof(sample_t) == sizeof(int16_t), "sample format must be int16");
        builder->setFormat(AAUDIO_FORMAT_PCM_I16);
        builder->setCallbacks(&Player::aaudioDataCallback, &Player::aaudioErrorCallback, this);
    }
    return builder;
}

bool Player::probeDefaults(
        PerformanceMode performanceMode, int *samplingRate, int *playerBufferFrameCount) {
    std::unique_ptr<StreamBuilder> builder = createBuilder(performanceMode);
    if (!builder) return false;
    mStream = builder->makeStream();
    if (!mStream) return false;
    *samplingRate = mStream->getSamplingRateHz();
    *playerBufferFrameCount = calculateBufferSizeInFrames(
            mStream->getFramesPerBurst(), MINIMUM_STREAM_BUFFER_SIZE_MS, *samplingRate);
    return true;
}

bool Player::init(const TestContext *testCtx,
        SoundSystem::WriteCallback writeClb,
        ErrorCallback errorClb) {
    mTestCtx = testCtx;
    std::unique_ptr<StreamBuilder> builder = createBuilder(testCtx->getPerformanceMode());
    if (!builder) return false;
    // Do not set channel count, because AAudio doesn't perform channel count conversion
    // in the exclusive mode.
    builder->setSampleRate(testCtx->getSamplingRateHz());
    mStream = builder->makeStream();
    if (!mStream) return false;
    mStream->setBufferFrameCount(testCtx->getFrameCount());
    mWriteCallback = writeClb;
    mErrorCallback = errorClb;
    return mStream->start();
}

void Player::shutdown() {
    if (mStream) {
        mStream->stop();
        mStream.reset();
    }
}

aaudio_data_callback_result_t Player::aaudioDataCallback(AAudioStream* /*stream*/,
        void *userData,
        void *audioData,
        int32_t numFrames) {
    ATRACE_CALL();
    Player *self = static_cast<Player*>(userData);
    AudioBufferView<sample_t> outputWave = self->mWriteCallback(numFrames);
    if (outputWave.getFrameCount() > static_cast<size_t>(numFrames)) {
        ALOGW("Output wave has more frames than callback allows: %lld > %d",
                (long long)outputWave.getFrameCount(), numFrames);
    }

    copyAudioBufferViewData(outputWave,
            AudioBufferView<sample_t>(static_cast<sample_t*>(audioData),
                    numFrames, self->mStream->getChannelCount()));

    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

void Player::aaudioErrorCallback(AAudioStream* /*stream*/,
        void *userData,
        aaudio_result_t error) {
    Player *self = static_cast<Player*>(userData);
    self->mErrorCallback(error);
}


class Recorder {
  public:
    Recorder() {}
    Recorder(const Recorder&) = delete;
    Recorder& operator=(const Recorder&) = delete;
    ~Recorder() { shutdown(); }

    bool probeDefaults(
            PerformanceMode performanceMode, int *samplingRate, int *recorderBufferFrameCount);
    bool init(const TestContext *testCtx);
    bool drain();
    ssize_t read(AudioBufferView<sample_t> buffer);
    void shutdown();

  private:
    // The input stream buffer size in milliseconds. For the input, buffer
    // size affects latency less than for the output stream (at least in MMAP mode),
    // because the app normally drains the input buffer and should keep it low.
    // Using twice the size of the Player buffer as an educated guess.
    static constexpr int MINIMUM_STREAM_BUFFER_SIZE_MS = 8;

    std::unique_ptr<StreamBuilder> createBuilder(PerformanceMode performanceMode);

    const TestContext *mTestCtx;
    std::unique_ptr<Stream> mStream;
    std::unique_ptr<AudioBuffer<sample_t>> mConversionBuffer;
};

std::unique_ptr<StreamBuilder> Recorder::createBuilder(PerformanceMode performanceMode) {
    std::unique_ptr<StreamBuilder> builder = makeStreamBuilder();
    if (builder) {
        builder->setDirection(AAUDIO_DIRECTION_INPUT);
        builder->setSharingMode(AAUDIO_SHARING_MODE_EXCLUSIVE);
        builder->setPerformanceMode(getAAudioPerfMode(performanceMode));
        static_assert(sizeof(sample_t) == sizeof(int16_t), "sample format must be int16");
        builder->setFormat(AAUDIO_FORMAT_PCM_I16);
    }
    return builder;
}

bool Recorder::probeDefaults(
        PerformanceMode performanceMode, int *samplingRate, int *recorderBufferFrameCount) {
    std::unique_ptr<StreamBuilder> builder = createBuilder(performanceMode);
    if (!builder) return false;
    mStream = builder->makeStream();
    if (!mStream) return false;
    *samplingRate = mStream->getSamplingRateHz();
    *recorderBufferFrameCount = calculateBufferSizeInFrames(
            mStream->getFramesPerBurst(), MINIMUM_STREAM_BUFFER_SIZE_MS, *samplingRate);
    return true;
}

bool Recorder::init(const TestContext *testCtx) {
    mTestCtx = testCtx;
    std::unique_ptr<StreamBuilder> builder = createBuilder(testCtx->getPerformanceMode());
    if (!builder) return false;
    builder->setChannelCount(testCtx->getChannelCount());
    builder->setSampleRate(testCtx->getSamplingRateHz());
    mStream = builder->makeStream();
    if (!mStream) return false;
    if (mStream->getChannelCount() != mTestCtx->getChannelCount()) {
        mConversionBuffer.reset(new AudioBuffer<sample_t>(
                        mTestCtx->getFrameCount(), mStream->getChannelCount()));
    }
    mStream->setBufferFrameCount(testCtx->getFrameCount());
    return mStream->start();
}

bool Recorder::drain() {
    ATRACE_CALL();
    AudioBuffer<sample_t> drainBuffer(mStream->getFramesPerBurst(), mStream->getChannelCount());
    ssize_t framesRead;
    do {
        framesRead = mStream->read(drainBuffer);
        if (framesRead < 0) return false;
    } while (framesRead > 0);
    return true;
}

ssize_t Recorder::read(AudioBufferView<sample_t> buffer) {
    if (!mConversionBuffer) {
        return mStream->read(buffer);
    } else {
        ssize_t result = mStream->read(mConversionBuffer->getView(0, buffer.getFrameCount()));
        if (result <= 0) return result;

        size_t framesRead = result;
        copyAudioBufferViewData(mConversionBuffer->getView(0, framesRead), buffer);
        return framesRead;
    }
}

void Recorder::shutdown() {
    if (mStream) {
        mStream->stop();
        mStream.reset();
    }
}


}  // namespace

struct SoundSystemAAudio::Impl {
    Impl() : lastError(AAUDIO_OK) {}
    Impl(const Impl&) = delete;
    Impl& operator=(const Impl&) = delete;

    void errorCallback(aaudio_result_t error) {
        lastError = error;
        ALOGE("Error callback received %s (%d)",
                AAudioLoader::getInstance()->convertResultToText(error), error);
    }

    Player player;
    Recorder recorder;
    std::atomic<aaudio_result_t> lastError;
};

SoundSystemAAudio::SoundSystemAAudio()
        : mTestCtx(nullptr), mImpl(new Impl()) {
}

SoundSystemAAudio::SoundSystemAAudio(const TestContext *testCtx)
        : mTestCtx(testCtx), mImpl(new Impl()) {
}

SoundSystemAAudio::~SoundSystemAAudio() {
    shutdown();
}

bool SoundSystemAAudio::probeDefaultSettings(PerformanceMode performanceMode, int *samplingRate,
        int *playerBufferFrameCount, int *recorderBufferFrameCount) {
    return (AAudioLoader::getInstance()->open() == 0)
            && mImpl->recorder.probeDefaults(
                    performanceMode, samplingRate, recorderBufferFrameCount)
            && mImpl->player.probeDefaults(performanceMode, samplingRate, playerBufferFrameCount);
}

bool SoundSystemAAudio::init(WriteCallback callback) {
    if (!mTestCtx) {
        ALOGF("Attempting to use SoundSystemAAudio probing instance for testing!");
    }
    return (AAudioLoader::getInstance()->open() == 0)
            && mImpl->recorder.init(mTestCtx)
            && mImpl->player.init(
                    mTestCtx,
                    callback,
                    std::bind(&Impl::errorCallback, mImpl.get(), std::placeholders::_1));
}

bool SoundSystemAAudio::drainInput() {
    if (mImpl->lastError != AAUDIO_OK) return false;
    return mImpl->recorder.drain();
}

ssize_t SoundSystemAAudio::readAudio(AudioBufferView<sample_t> buffer) {
    if (mImpl->lastError != AAUDIO_OK) return -1;
    return mImpl->recorder.read(buffer);
}

void SoundSystemAAudio::shutdown() {
    mImpl->player.shutdown();
    mImpl->recorder.shutdown();
    AAudioLoader::getInstance()->close();
}
