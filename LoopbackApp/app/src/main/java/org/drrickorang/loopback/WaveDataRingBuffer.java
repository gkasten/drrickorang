/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * Maintains a recording of wave data of last n seconds
 */
public class WaveDataRingBuffer {

    private final double[] mWaveRecord;
    private volatile int index = 0; // between 0 and mWaveRecord.length - 1
    private boolean arrayFull = false; // true after index has wrapped

    public WaveDataRingBuffer(int size) {
        if (size < Constant.SAMPLING_RATE_MIN * Constant.BUFFER_TEST_DURATION_SECONDS_MIN) {
            size = Constant.SAMPLING_RATE_MIN * Constant.BUFFER_TEST_DURATION_SECONDS_MIN;
        } else if (size > Constant.SAMPLING_RATE_MAX * Constant.BUFFER_TEST_DURATION_SECONDS_MAX) {
            size = Constant.SAMPLING_RATE_MAX * Constant.BUFFER_TEST_DURATION_SECONDS_MAX;
        }

        mWaveRecord = new double[size];
    }

    /**
     * Write length number of doubles from data into ring buffer from starting srcPos
     */
    public synchronized void writeWaveData(double[] data, int srcPos, int length) {
        if (length > data.length - srcPos) {
            // requested to write more data than available
            // bad request leave data un-affected
            return;
        }

        if (length >= mWaveRecord.length) {
            // requested write would fill or exceed ring buffer capacity
            // fill ring buffer with last segment of requested write
            System.arraycopy(data, srcPos + (length - mWaveRecord.length), mWaveRecord, 0,
                    mWaveRecord.length);
            index = 0;
        } else if (mWaveRecord.length - index > length) {
            // write requested data from current offset
            System.arraycopy(data, srcPos, mWaveRecord, index, length);
            index += length;
        } else {
            // write to available buffer then wrap and overwrite previous records
            if (!arrayFull) {
                arrayFull = true;
            }

            int availBuff = mWaveRecord.length - index;

            System.arraycopy(data, srcPos, mWaveRecord, index, availBuff);
            System.arraycopy(data, srcPos + availBuff, mWaveRecord, 0, length - availBuff);

            index = length - availBuff;

        }

    }

    /**
     * Returns a private copy of recorded wave data
     *
     * @return double array of wave recording, rearranged with oldest sample at first index
     */
    public synchronized double[] getWaveRecord() {
        double outputBuffer[] = new double[mWaveRecord.length];

        if (!arrayFull) {
            //return partially filled sample with trailing zeroes
            System.arraycopy(mWaveRecord, 0, outputBuffer, 0, index);
        } else {
            //copy buffer to contiguous sample and return unwrapped array
            System.arraycopy(mWaveRecord, index, outputBuffer, 0, mWaveRecord.length - index);
            System.arraycopy(mWaveRecord, 0, outputBuffer, mWaveRecord.length - index, index);
        }

        return outputBuffer;

    }

}
