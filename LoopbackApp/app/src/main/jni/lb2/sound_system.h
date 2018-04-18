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

#ifndef LB2_SOUND_SYSTEM_H_
#define LB2_SOUND_SYSTEM_H_

#include <functional>

#include "lb2/audio_buffer.h"
#include "lb2/test_context.h"  // for PerformanceMode

// Interface for sound systems.
// It is assumed that "pull" model (callback) is used for providing
// sound data to the system, and "push" model (sync read) is used
// for sound input.
class SoundSystem {
  public:
    // The memory region pointed by this buffer must remain
    // valid until the write callback is called the next time,
    // or until 'shutdown' is called.
    using WriteCallback = std::function<AudioBufferView<sample_t>(size_t expectedFrames)>;

    SoundSystem() = default;
    SoundSystem(const SoundSystem&) = delete;
    SoundSystem& operator=(const SoundSystem&) = delete;
    virtual ~SoundSystem() {}

    // Probes the output hardware for the recommended parameters for input
    // and output streams. Returns 'false' if probing is impossible or has failed.
    // Note that this is a separate use case for the sound system. After commencing
    // probing, the instance of the sound system used for probing must be shut down.
    virtual bool probeDefaultSettings(PerformanceMode /*performanceMode*/, int* /*samplingRate*/,
            int* /*playerBufferFrameCount*/, int* /*recorderBufferFrameCount*/) { return false; }
    // Initializes the sound system for the regular testing scenario.
    // Returns 'true' if initialization was successful, 'false' otherwise.
    virtual bool init(WriteCallback callback) = 0;
    // Make sure the buffer of the input stream is empty, so fresh audio data
    // can be received immediately on the next call to 'readAudio'.
    // Returns 'true' if there were no errors, 'false' otherwise.
    virtual bool drainInput() = 0;
    // Reads from audio input into the provided buffer. A non-negative result value
    // indicates success, a negative return value indicates an error.
    virtual ssize_t readAudio(AudioBufferView<sample_t> buffer) = 0;
    // Shuts the sound system down.
    virtual void shutdown() = 0;
};

#endif  // LB2_SOUND_SYSTEM_H_
