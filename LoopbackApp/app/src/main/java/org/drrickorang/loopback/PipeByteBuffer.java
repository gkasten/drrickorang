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

package org.drrickorang.loopback;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import android.util.Log;


/**
 * Non-blocking pipe where writer writes to the pipe using by knowing the address of "mByteBuffer",
 * and write to this ByteBuffer directly. On the other hand, reader reads from the pipe using
 * read(), which converts data in ByteBuffer into shorts.
 * Data in the pipe are stored in the ByteBuffer array "mByteBuffer".
 * The write side of a pipe permits overruns; flow control is the caller's responsibility.
 * TODO move to audio_utils
 */

public class PipeByteBuffer extends Pipe {
    private static final String TAG = "PipeByteBuffer";

    private final ByteBuffer mByteBuffer;
    private int              mFront = 0; // reader's current position


    /**
     * The ByteBuffer in this class consists of two sections. The first section is the actual pipe
     * to store data. This section must have a size in power of 2, and this is enforced by the
     * constructor through rounding maxSamples up to the nearest power of 2. This second section
     * is used to store metadata. Currently the only metadata is an integer that stores the rear,
     * where rear is the writer's current position. The metadata is at the end of ByteBuffer, and is
     * outside of the actual pipe.
     * IMPORTANT: The code is designed (in native code) such that metadata won't be overwritten when
     * the writer writes to the pipe. If changes to the code are required, please make sure the
     * metadata won't be overwritten.
     * IMPORTANT: Since a signed integer is used to store rear and mFront, their values should not
     * exceed 2^31 - 1, or else overflows happens and the positions of read and mFront becomes
     * incorrect.
     */
    public PipeByteBuffer(int maxSamples) {
        super(maxSamples);
        int extraInt = 1; // used to store rear
        int extraShort = extraInt * Constant.SHORTS_PER_INT;
        int numberOfShorts = mMaxValues + extraShort;
        mByteBuffer = ByteBuffer.allocateDirect(numberOfShorts * Constant.BYTES_PER_SHORT);
        mByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
    }


    /**
     * Convert data in mByteBuffer into short, and put them into "buffer".
     * Note: rear and mFront are kept in terms of number of shorts instead of number of bytes.
     */
    @Override
    public int read(short[] buffer, int offset, int requiredSamples) {
        // first, update the current rear
        int rear;
        synchronized (mByteBuffer) {
            rear = mByteBuffer.getInt(mMaxValues * Constant.BYTES_PER_SHORT);
        }
        //log("initial offset: " + offset + "\n initial requiredSamples: " + requiredSamples);

        // after here, rear may actually be updated further. However, we don't care. If at the point
        // of checking there's enough data then we will read it. If not just wait until next call
        // of read.
        int avail = availableToRead(rear, mFront);
        if (avail <= 0) {   //return -2 for overrun
            return avail;
        }

        // if not enough samples, just read partial samples
        if (requiredSamples > avail) {
            requiredSamples = avail;
        }

        // mask the upper bits to get the correct position in the pipe
        int front = mFront & (mMaxValues - 1);
        int read = mMaxValues - front;   // total samples from currentIndex until the end of array
        if (read > requiredSamples) {
            read = requiredSamples;
        }

        int byteBufferFront = front * Constant.BYTES_PER_SHORT; // start reading from here
        byteBufferToArray(buffer, offset, read, byteBufferFront);

        if (front + read == mMaxValues) {
            int samplesLeft = requiredSamples - read;
            if (samplesLeft > 0) {
                byteBufferFront = 0;
                byteBufferToArray(buffer, offset + read, read + samplesLeft, byteBufferFront);
                read += samplesLeft;
            }
        }

        mFront += read;
        return read;
    }


    /**
     * Copy mByteBuffer's data (starting from "byteBufferFront") into double array "buffer".
     * "start" is the starting index of "buffer" and "length" is the amount of samples copying.
     */
    private void byteBufferToArray(short[] buffer, int start, int length, int byteBufferFront) {
        for (int i = start; i < (start + length); i++) {
            buffer[i] = mByteBuffer.getShort(byteBufferFront);
            byteBufferFront += Constant.BYTES_PER_SHORT;
        }
    }


    /** Private function that actually calculate the number of samples available to read. */
    private int availableToRead(int rear, int front) {
        int avail = rear - front;
        if (avail > mMaxValues) {
            // Discard 1/16 of the most recent data in pipe to avoid another overrun immediately
            int oldFront = mFront;
            mFront = rear - mMaxValues + (mMaxValues >> 5);
            mSamplesOverrun += mFront - oldFront;
            ++mOverruns;
            return OVERRUN;
        }

        return avail;
    }


    @Override
    public int availableToRead() {
        int rear;
        int avail;
        synchronized (mByteBuffer) {
            rear = mByteBuffer.getInt(mMaxValues * Constant.BYTES_PER_SHORT);
        }

        avail = availableToRead(rear, mFront);
        return avail;
    }


    public ByteBuffer getByteBuffer() {
        return mByteBuffer;
    }


    @Override
    public void flush() {
        //set rear and front to zero
        mFront = 0;
        synchronized (mByteBuffer) {
            mByteBuffer.putInt(mMaxValues * Constant.BYTES_PER_SHORT, 0);
        }
    }


    private static void log(String msg) {
        Log.v(TAG, msg);
    }

}
