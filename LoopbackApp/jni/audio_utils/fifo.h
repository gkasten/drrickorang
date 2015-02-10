/*
 * Copyright (C) 2015 The Android Open Source Project
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

#ifndef ANDROID_AUDIO_FIFO_H
#define ANDROID_AUDIO_FIFO_H

#include <stdlib.h>

// FIXME use atomic_int_least32_t and new atomic operations instead of legacy Android ones
// #include <stdatomic.h>

#ifdef __cplusplus
extern "C" {
#endif

// Single writer, single reader non-blocking FIFO.
// Writer and reader must be in same process.

// No user-serviceable parts within.
struct audio_utils_fifo {
    // These fields are const after initialization
    size_t     mFrameCount;   // max number of significant frames to be stored in the FIFO > 0
    size_t     mFrameCountP2; // roundup(mFrameCount)
    size_t     mFudgeFactor;  // mFrameCountP2 - mFrameCount, the number of "wasted" frames after
                              // the end of mBuffer.  Only the indices are wasted, not any memory.
    size_t     mFrameSize;    // size of each frame in bytes
    void      *mBuffer;       // pointer to caller-allocated buffer of size mFrameCount frames

    volatile int32_t mFront; // frame index of first frame slot available to read, or read index
    volatile int32_t mRear;  // frame index of next frame slot available to write, or write index
};

// Initialize a FIFO object.
// Input parameters:
//  fifo        Pointer to the FIFO object.
//  frameCount  Max number of significant frames to be stored in the FIFO > 0.
//              If writes and reads always use the same count, and that count is a divisor of
//              frameCount, then the writes and reads will never do a partial transfer.
//  frameSize   Size of each frame in bytes.
//  buffer      Pointer to a caller-allocated buffer of frameCount frames.
void audio_utils_fifo_init(struct audio_utils_fifo *fifo, size_t frameCount, size_t frameSize,
        void *buffer);

// De-initialize a FIFO object.
// Input parameters:
//  fifo        Pointer to the FIFO object.
void audio_utils_fifo_deinit(struct audio_utils_fifo *fifo);

// Write to FIFO.
// Input parameters:
//  fifo        Pointer to the FIFO object.
//  buffer      Pointer to source buffer containing 'count' frames of data.
// Returns actual number of frames written <= count.
// The actual transfer count may be zero if the FIFO is full,
// or partial if the FIFO was almost full.
// A negative return value indicates an error.  Currently there are no errors defined.
ssize_t audio_utils_fifo_write(struct audio_utils_fifo *fifo, const void *buffer, size_t count);

// Read from FIFO.
// Input parameters:
//  fifo        Pointer to the FIFO object.
//  buffer      Pointer to destination buffer to be filled with up to 'count' frames of data.
// Returns actual number of frames read <= count.
// The actual transfer count may be zero if the FIFO is empty,
// or partial if the FIFO was almost empty.
// A negative return value indicates an error.  Currently there are no errors defined.
ssize_t audio_utils_fifo_read(struct audio_utils_fifo *fifo, void *buffer, size_t count);

#ifdef __cplusplus
}
#endif

#endif  // !ANDROID_AUDIO_FIFO_H
