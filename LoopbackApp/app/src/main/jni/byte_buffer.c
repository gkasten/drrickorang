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

#include "byte_buffer.h"

#include <stdatomic.h>
#include <string.h>

typedef _Atomic int32_t writer_pos_t;

ssize_t byteBuffer_write(byte_buffer_t byteBuffer, size_t byteBufferSize,
        const char *srcBuffer, size_t frameCount, int channels) {
    // bytebufferSize is in bytes
    const size_t dataSectionSize = byteBufferSize - sizeof(writer_pos_t);
    writer_pos_t *rear_ptr = (writer_pos_t*)(byteBuffer + dataSectionSize);
    writer_pos_t rear = *rear_ptr;
    // rear should not exceed 2^31 - 1, or else overflow will happen

    size_t frameSize = channels * sizeof(short); // only one channel
    int32_t maxLengthInShort = dataSectionSize / frameSize;
    // mask the upper bits to get the correct position in the pipe
    writer_pos_t tempRear = rear & (maxLengthInShort - 1);
    size_t part1 = maxLengthInShort - tempRear;

    if (part1 > frameCount) {
        part1 = frameCount;
    }

    if (part1 > 0) {
        memcpy(byteBuffer + (tempRear * frameSize), srcBuffer,
               part1 * frameSize);

        size_t part2 = frameCount - part1;
        if (part2 > 0) {
            memcpy(byteBuffer, (srcBuffer + (part1 * frameSize)),
                   part2 * frameSize);
        }
    }

    // increase value of rear using the strongest memory ordering
    // (since it's being read by Java we can't control the ordering
    // used by the other side).
    atomic_store(rear_ptr, rear + frameCount);
    return frameCount;
}
