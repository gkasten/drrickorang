/*
 * Copyright (C) 2012 The Android Open Source Project
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

package org.drrickorang.loopback;

// Non-blocking pipe supports a single writer and single reader.
// The write side of a pipe permits overruns; flow control is the caller's responsibility.

class Pipe {

    private int mFront;
    private int mRear;
    private byte mBuffer[];
    private volatile int mVolatileRear; // written by write(), read by read()
    private int mMaxBytes;
    private int mBytesOverrun;
    private int mOverruns;
    public static final int OVERRUN = -2;

    // maxBytes will be rounded up to a power of 2, and all slots are available. Must be >= 2.
    Pipe(int maxBytes)
    {
        mMaxBytes = roundup(maxBytes);
        mBuffer = new byte[mMaxBytes];
    }

    // buffer must != null.
    // offset must be >= 0.
    // count is maximum number of bytes to copy, and must be >= 0.
    // offset + count must be <= buffer.length.
    // Returns actual number of bytes copied >= 0.
    int write(byte[] buffer, int offset, int count)
    {
        int rear = mRear & (mMaxBytes - 1);
        int written = mMaxBytes - rear;
        if (written > count) {
            written = count;
        }
        System.arraycopy(buffer, offset, mBuffer, rear, written);
        if (rear + written == mMaxBytes) {
            if ((count -= written) > rear) {
                count = rear;
            }
            if (count > 0) {
                System.arraycopy(buffer, offset + written, mBuffer, 0, count);
                written += count;
            }
        }
        mRear += written;
        mVolatileRear = mRear;
        return written;
    }

    int availableToRead()
    {
        int rear = mVolatileRear;
        int avail = rear - mFront;
        if (avail > mMaxBytes) {
            // Discard 1/16 of the most recent data in pipe to avoid another overrun immediately
            int oldFront = mFront;
            mFront = rear - mMaxBytes + (mMaxBytes >> 4);
            mBytesOverrun += mFront - oldFront;
            ++mOverruns;
            return OVERRUN;
        }
        return avail;
    }

    // buffer must != null.
    // offset must be >= 0.
    // count is maximum number of bytes to copy, and must be >= 0.
    // offset + count must be <= buffer.length.
    // Returns actual number of bytes copied >= 0.
    int read(byte[] buffer, int offset, int count)
    {
        int avail = availableToRead();
        if (avail <= 0) {
            return avail;
        }
        // An overrun can occur from here on and be silently ignored,
        // but it will be caught at next read()
        if (count > avail) {
            count = avail;
        }
        int front = mFront & (mMaxBytes - 1);
        int red = mMaxBytes - front;
        if (red > count) {
            red = count;
        }
        // In particular, an overrun during the System.arraycopy will result in reading corrupt data
        System.arraycopy(mBuffer, front, buffer, offset, red);
        // We could re-read the rear pointer here to detect the corruption, but why bother?
        if (front + red == mMaxBytes) {
            if ((count -= red) > front) {
                count = front;
            }
            if (count > 0) {
                System.arraycopy(mBuffer, 0, buffer, offset + red, count);
                red += count;
            }
        }
        mFront += red;
        return red;
    }

    void flush()
    {
        mRear = mFront;
        mVolatileRear = mFront;
    }

    // Round up to the next highest power of 2
    private static int roundup(int v)
    {
        // Integer.numberOfLeadingZeros() returns 32 for zero input
        if (v == 0) {
            v = 1;
        }
        int lz = Integer.numberOfLeadingZeros(v);
        int rounded = 0x80000000 >>> lz;
        // 0x800000001 and higher are actually rounded _down_ to prevent overflow
        if (v > rounded && lz > 0) {
            rounded <<= 1;
        }
        return rounded;
    }

}
