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


/**
 * Non-blocking pipe where writer writes to the pipe using write() and read reads from the pipe
 * using read(). Data in the pipe are stored in the short array "mBuffer".
 * The write side of a pipe permits overruns; flow control is the caller's responsibility.
 */

public class PipeShort extends Pipe {
    private int          mFront; // writer's current position
    private int          mRear; // reader's current position
    private final short  mBuffer[]; // store that data in the pipe
    private volatile int mVolatileRear; // used to keep rear synchronized


    /**
     * IMPORTANT: Since a signed integer is used to store mRear and mFront, their values should not
     * exceed 2^31 - 1, or else overflows happens and the positions of read and mFront becomes
     * incorrect.
     */
    public PipeShort(int maxSamples) {
        super(maxSamples);
        mBuffer = new short[mMaxValues];
    }


    /**
     * offset must be >= 0.
     * count is maximum number of bytes to copy, and must be >= 0.
     * offset + count must be <= buffer.length.
     * Return actual number of shorts copied, which will be >= 0.
     */
    public int write(short[] buffer, int offset, int count) {
        // mask the upper bits to get the correct position in the pipe
        int rear = mRear & (mMaxValues - 1);
        int written = mMaxValues - rear;
        if (written > count) {
            written = count;
        }

        System.arraycopy(buffer, offset, mBuffer, rear, written);
        if (rear + written == mMaxValues) {
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


    @Override
    public int read(short[] buffer, int offset, int count) {
        int avail = availableToRead();
        if (avail <= 0) {
            return avail;
        }

        // An overrun can occur from here on and be silently ignored,
        // but it will be caught at next read()
        if (count > avail) {
            count = avail;
        }

        // mask the upper bits to get the correct position in the pipe
        int front = mFront & (mMaxValues - 1);
        int read = mMaxValues - front;

        if (read > count) {
            read = count;
        }

        // In particular, an overrun during the System.arraycopy will result in reading corrupt data
        System.arraycopy(mBuffer, front, buffer, offset, read);
        // We could re-read the rear pointer here to detect the corruption, but why bother?
        if (front + read == mMaxValues) {
            if ((count -= read) > front) {
                count = front;
            }

            if (count > 0) {
                System.arraycopy(mBuffer, 0, buffer, offset + read, count);
                read += count;
            }
        }

        mFront += read;
        return read;
    }



    @Override
    public int availableToRead() {
        int rear = mVolatileRear;
        int avail = rear - mFront;
        if (avail > mMaxValues) {
            // Discard 1/16 of the most recent data in pipe to avoid another overrun immediately
            int oldFront = mFront;
            mFront = rear - mMaxValues + (mMaxValues >> 4);
            mSamplesOverrun += mFront - oldFront;
            ++mOverruns;
            return OVERRUN;
        }

        return avail;
    }


    @Override
    public void flush() {
        mRear = mFront;
        mVolatileRear = mFront;
    }

}
