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

#ifndef _Included_org_drrickorang_loopback_byte_buffer
#define _Included_org_drrickorang_loopback_byte_buffer

#include <sys/types.h>

// Introduce a dedicated type because the destination buffer
// is special, and needs to be obtained from the Java side.
typedef char* byte_buffer_t;

#ifdef __cplusplus
extern "C" {
#endif

// Writes data to a ByteBuffer for consumption on the Java side
// via PipeByteBuffer class. The function assumes sample size being "short".
// Returns the actual number of frames written.
ssize_t byteBuffer_write(byte_buffer_t byteBuffer, size_t byteBufferSize,
        const char *srcBuffer, size_t frameCount, int channels);

#ifdef __cplusplus
}
#endif

#endif  // _Included_org_drrickorang_loopback_byte_buffer
