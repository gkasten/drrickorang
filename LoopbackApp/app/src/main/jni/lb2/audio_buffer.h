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

#ifndef LB2_AUDIO_BUFFER_H_
#define LB2_AUDIO_BUFFER_H_

#include <algorithm>
#include <functional>
#include <memory>
#include <string.h>

#include <android/log.h>

#include "lb2/sample.h"
#include "lb2/util.h"

// Implements sample / frame / byte count conversions. Not to be used directly.
template<class T>
class CountsConverter {
  public:
    size_t getDataSize() const { return getSampleCount() * sizeof(T); }
    size_t getFrameCount() const { return mFrameCount; }
    size_t getFrameSize() const { return mChannelCount * sizeof(T); }
    size_t getSampleCount() const { return mFrameCount * mChannelCount; }
    int getChannelCount() const { return mChannelCount; }

  protected:
    CountsConverter(size_t frameCount, int channelCount) :
            mFrameCount(frameCount), mChannelCount(channelCount) {}
    CountsConverter(const CountsConverter<T>&) = default;
    CountsConverter(CountsConverter<T>&&) = default;
    CountsConverter<T>& operator=(const CountsConverter<T>&) = default;
    CountsConverter<T>& operator=(CountsConverter<T>&&) = default;

  private:
    // Fields are logically const, but can be overwritten during an object assignment.
    size_t mFrameCount;
    int mChannelCount;
};

// Implements the common parts of AudioBuffer and AudioBufferView.
// Not to be used directly.
//
// Although AudioBuffer could be considered as an extension of AudioBufferView,
// they have different copy/move semantics, and thus AudioBuffer
// doesn't satisfy Liskov Substitution Principle. That's why these classes are
// implemented as siblings instead, with an implicit conversion constructor of
// AudioBufferView from AudioBuffer.
template<class T>
class AudioBufferBase : public CountsConverter<T> {
  public:
    void clear() { memset(mData, 0, CountsConverter<T>::getDataSize()); }
    T* getData() const { return mData; }
    T* getFrameAt(int offsetInFrames) const {
        return mData + offsetInFrames * CountsConverter<T>::getChannelCount();
    }

  protected:
    static constexpr size_t npos = static_cast<size_t>(-1);

    AudioBufferBase(T* const data, size_t frameCount, int channelCount)
            : CountsConverter<T>(frameCount, channelCount), mData(data) {}
    AudioBufferBase(const AudioBufferBase<T>&) = default;
    AudioBufferBase(AudioBufferBase<T>&&) = default;
    AudioBufferBase<T>& operator=(const AudioBufferBase<T>&) = default;
    AudioBufferBase<T>& operator=(AudioBufferBase<T>&&) = default;

    AudioBufferBase<T> getView(int offsetInFrames, size_t lengthInFrames) const {
        if (offsetInFrames < 0) {
            __android_log_assert("assert", "lb2", "Negative buffer offset %d", offsetInFrames);
        }
        if (lengthInFrames > CountsConverter<T>::getFrameCount() - offsetInFrames) {
            lengthInFrames = CountsConverter<T>::getFrameCount() - offsetInFrames;
        }
        return AudioBufferBase<T>(
                getFrameAt(offsetInFrames), lengthInFrames, CountsConverter<T>::getChannelCount());
    }

  private:
    // Fields are logically const, but can be overwritten during an object assignment.
    T* mData;
};

template<class T> class AudioBufferView;

// Container for PCM audio data, allocates the data buffer via 'new' and owns it.
// Allows modification of the data. Does not support copying,
// move only. For passing audio data around it's recommended
// to use instances of AudioBufferView class instead.
template<class T>
class AudioBuffer : public AudioBufferBase<T> {
  public:
    // Null AudioBuffer constructor.
    constexpr AudioBuffer(): AudioBufferBase<T>(nullptr, 0, 1), mBuffer() {}
    AudioBuffer(size_t frameCount, int channelCount)
            : AudioBufferBase<T>(new T[frameCount * channelCount], frameCount, channelCount),
            mBuffer(AudioBufferBase<T>::getData()) {
        AudioBufferBase<T>::clear();
    }
    AudioBuffer(const AudioBuffer<T>&) = delete;
    AudioBuffer(AudioBuffer<T>&&) = default;
    AudioBuffer<T>& operator=(const AudioBuffer<T>&) = delete;
    AudioBuffer<T>& operator=(AudioBuffer<T>&&) = default;

    AudioBufferView<T> getView(
            int offsetInFrames = 0, size_t lengthInFrames = AudioBufferBase<T>::npos) const {
        return AudioBufferBase<T>::getView(offsetInFrames, lengthInFrames);
    }

  private:
    std::unique_ptr<T[]> mBuffer;
};

// Lightweight view into the PCM audio data provided by AudioBuffer.
// AudioBufferView does *not* own buffer memory. Data can be modified
// via the view. Thanks to its small size, should be passed by value.
template<class T>
class AudioBufferView : public AudioBufferBase<T> {
  public:
    AudioBufferView(T* const data, size_t frameCount, int channelCount)
            : AudioBufferBase<T>(data, frameCount, channelCount) {}
    // Implicit conversion from AudioBufferBase.
    AudioBufferView(const AudioBufferBase<T>& b)
            : AudioBufferBase<T>(b.getData(), b.getFrameCount(), b.getChannelCount()) {}
    AudioBufferView(const AudioBufferView<T>&) = default;
    AudioBufferView(AudioBufferView<T>&&) = default;
    AudioBufferView<T>& operator=(const AudioBufferView<T>&) = default;
    AudioBufferView<T>& operator=(AudioBufferView<T>&&) = default;

    AudioBufferView<T> getView(
            int offsetInFrames = 0, size_t lengthInFrames = AudioBufferBase<T>::npos) const {
        return AudioBufferBase<T>::getView(offsetInFrames, lengthInFrames);
    }
};


template<class S, class D>
inline void convertAudioBufferViewType(AudioBufferView<S> src, AudioBufferView<D> dst) {
    if (src.getChannelCount() != dst.getChannelCount()) {
        __android_log_assert("assert", "lb2", "Buffer channel counts differ: %d != %d",
                src.getChannelCount(), dst.getChannelCount());
    }
    if (src.getSampleCount() != dst.getSampleCount()) {
        __android_log_assert("assert", "lb2", "Buffer sample counts differ: %lld != %lld",
                (long long)src.getSampleCount(), (long long)dst.getChannelCount());
    }
    for (size_t i = 0; i < src.getSampleCount(); ++i) {
        dst.getData()[i] = convertSampleType(src.getData()[i]);
    }
}

template<class T>
inline void forEachFrame(AudioBufferView<T> src, AudioBufferView<T> dst,
        std::function<void(T* srcFrame, T* dstFrame)> op) {
    T *srcData = src.getData();
    T *dstData = dst.getData();
    for (size_t i = 0;
             i < std::min(src.getFrameCount(), dst.getFrameCount());
             ++i, srcData += src.getChannelCount(), dstData += dst.getChannelCount()) {
        op(srcData, dstData);
    }
}

// Copies audio buffers data frame by frame. Initially fills the
// destination buffer with zeroes. Ignores extra channels in the
// source buffer.
template<class T>
inline void strideCopyAudioBufferViewData(AudioBufferView<T> src, AudioBufferView<T> dst) {
    dst.clear();
    forEachFrame<T>(src, dst,
            [&](T* srcFrame, T* dstFrame) {
                memcpy(dstFrame, srcFrame, std::min(src.getFrameSize(), dst.getFrameSize()));
            });
}

// Copies audio buffers data frame by frame. If there are more
// channels in the destination buffer than in the source buffer, the source
// buffer content is duplicated to the extra channels until the entire frame
// gets filled. E.g. if the source buffer has two channels, and the destination
// buffer has five, then each frame of the destination buffer will be filled
// as follows: 12121.
// If the destination buffer has more frames than the source, the extra frames
// a zeroed out.
template<class T>
inline void fillCopyAudioBufferViewData(AudioBufferView<T> src, AudioBufferView<T> dst) {
    dst.clear();
    const int srcFrameCopies = wholeMultiplier(dst.getChannelCount(), src.getChannelCount());
    // A temporary buffer allowing to avoid dealing with copying a fraction of the source frame.
    T srcFramePatch[srcFrameCopies * src.getChannelCount()];
    forEachFrame<T>(src, dst,
            [&](T* srcFrame, T* dstFrame) {
               // Fill the temporary buffer with copies of the source frame.
               T* patch = srcFramePatch;
               for (int j = 0; j < srcFrameCopies; ++j, patch += src.getChannelCount()) {
                   memcpy(patch, srcFrame, src.getFrameSize());
               }
               memcpy(dstFrame, srcFramePatch, dst.getFrameSize());
            });
}


// Copies audio data between the AudioBufferViews of the same type.
// Any missing audio data in the source buffer (not enough frames, or less
// channels) is filled with zeroes in the destination buffer.
template<class T>
inline void copyAudioBufferViewData(AudioBufferView<T> src, AudioBufferView<T> dst) {
    if (src.getChannelCount() == dst.getChannelCount()) {
        size_t framesToCopy = std::min(src.getFrameCount(), dst.getFrameCount());
        if (framesToCopy > 0) {
            memcpy(dst.getData(), src.getData(), framesToCopy * dst.getFrameSize());
        }
        if (dst.getFrameCount() > framesToCopy) {
            dst.getView(framesToCopy).clear();
        }
    } else {
        fillCopyAudioBufferViewData(src, dst);
    }
}

#endif  // LB2_AUDIO_BUFFER_H_
