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

import java.util.Arrays;

/**
 * Maintains two ring buffers for recording wav data
 * At any one time one buffer is available for writing to file while one is recording incoming data
 */
public class WaveDataRingBuffer {

    public interface ReadableWaveDeck {
        boolean writeToFile(AudioFileOutput audioFile);
    }

    private WaveDeck mLoadedDeck;
    private WaveDeck mShelvedDeck;

    public WaveDataRingBuffer(int size) {
        if (size < Constant.SAMPLING_RATE_MIN * Constant.BUFFER_TEST_DURATION_SECONDS_MIN) {
            size = Constant.SAMPLING_RATE_MIN * Constant.BUFFER_TEST_DURATION_SECONDS_MIN;
        } else if (size > Constant.SAMPLING_RATE_MAX * Constant.BUFFER_TEST_DURATION_SECONDS_MAX) {
            size = Constant.SAMPLING_RATE_MAX * Constant.BUFFER_TEST_DURATION_SECONDS_MAX;
        }
        mLoadedDeck = new WaveDeck(size);
        mShelvedDeck = new WaveDeck(size);
    }

    public synchronized void writeWaveData(double[] data, int srcPos, int length) {
        mLoadedDeck.writeWaveData(data, srcPos, length);
    }

    public synchronized double[] getWaveRecord() {
        return mLoadedDeck.getWaveRecord();
    }

    private void swapDecks() {
        WaveDeck temp = mShelvedDeck;
        mShelvedDeck = mLoadedDeck;
        mLoadedDeck = temp;
    }

    /**
     * Returns currently writing buffer as writeToFile interface, load erased shelved deck for write
     * If shelved deck is still being read returns null
     **/
    public synchronized ReadableWaveDeck getWaveDeck() {
        if (!mShelvedDeck.isBeingRead()) {
            swapDecks();
            mShelvedDeck.readyForRead();
            mLoadedDeck.reset();
            return mShelvedDeck;
        } else {
            return null;
        }
    }

    /**
     * Maintains a recording of wave data of last n seconds
     */
    public class WaveDeck implements ReadableWaveDeck {

        private double[] mWaveRecord;
        private volatile int mIndex = 0; // between 0 and mWaveRecord.length - 1
        private boolean mArrayFull = false; // true after mIndex has wrapped
        private boolean mIsBeingRead = false;

        public WaveDeck(int size) {
            mWaveRecord = new double[size];
        }

        /**
         * Write length number of doubles from data into ring buffer from starting srcPos
         */
        public void writeWaveData(double[] data, int srcPos, int length) {
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
                mIndex = 0;
            } else if (mWaveRecord.length - mIndex > length) {
                // write requested data from current offset
                System.arraycopy(data, srcPos, mWaveRecord, mIndex, length);
                mIndex += length;
            } else {
                // write to available buffer then wrap and overwrite previous records
                if (!mArrayFull) {
                    mArrayFull = true;
                }

                int availBuff = mWaveRecord.length - mIndex;

                System.arraycopy(data, srcPos, mWaveRecord, mIndex, availBuff);
                System.arraycopy(data, srcPos + availBuff, mWaveRecord, 0, length - availBuff);

                mIndex = length - availBuff;

            }

        }

        /**
         * Returns a private copy of recorded wave data
         *
         * @return double array of wave recording, rearranged with oldest sample at first index
         */
        public double[] getWaveRecord() {
            double outputBuffer[] = new double[mWaveRecord.length];

            if (!mArrayFull) {
                //return partially filled sample with trailing zeroes
                System.arraycopy(mWaveRecord, 0, outputBuffer, 0, mIndex);
                Arrays.fill(outputBuffer, mIndex+1, outputBuffer.length-1, 0);
            } else {
                //copy buffer to contiguous sample and return unwrapped array
                System.arraycopy(mWaveRecord, mIndex, outputBuffer, 0, mWaveRecord.length - mIndex);
                System.arraycopy(mWaveRecord, 0, outputBuffer, mWaveRecord.length - mIndex, mIndex);
            }

            return outputBuffer;
        }

        /** Make buffer available for new recording **/
        private void reset() {
            mIndex = 0;
            mArrayFull = false;
        }

        private boolean isBeingRead() {
            return mIsBeingRead;
        }

        private void readyForRead() {
            mIsBeingRead = true;
        }

        @Override
        public boolean writeToFile(AudioFileOutput audioFile) {
            boolean successfulWrite;
            if (mArrayFull) {
                successfulWrite = audioFile.writeRingBufferData(mWaveRecord, mIndex, mIndex);
            } else {
                // Write only filled part of array to file
                successfulWrite = audioFile.writeRingBufferData(mWaveRecord, 0, mIndex);
            }

            mIsBeingRead = false;
            return successfulWrite;
        }
    }

}
