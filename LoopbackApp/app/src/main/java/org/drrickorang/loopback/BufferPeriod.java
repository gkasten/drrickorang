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

import java.util.Arrays;

import android.util.Log;


/**
 * This class records the buffer period of the audio player or recorder when in Java mode.
 * Currently the accuracy is in 1ms.
 */

//TODO for native mode, should use a scale more accurate than the current 1ms
public class BufferPeriod {
    private static final String TAG = "BufferPeriod";

    private long mStartTimeNs = 0;  // first time collectBufferPeriod() is called
    private long mPreviousTimeNs = 0;
    private long mCurrentTimeNs = 0;

    private int       mMaxBufferPeriod = 0;
    private int       mCount = 0;
    private final int range = 1002; // store counts for 0ms to 1000ms, and for > 1000ms

    private int[] mBufferPeriod = new int[range];
    private BufferCallbackTimes mCallbackTimes;
    private CaptureHolder mCaptureHolder;

    /**
     * For player, this function is called before every AudioTrack.write().
     * For recorder, this function is called after every AudioRecord.read() with read > 0.
     */
    public void collectBufferPeriod() {
        mCurrentTimeNs = System.nanoTime();
        mCount++;

        // if mPreviousTimeNs = 0, it's the first time this function is called
        if (mPreviousTimeNs == 0) {
            mStartTimeNs = mCurrentTimeNs;
        }

        final int discard = 10; // discard the first few buffer period values
        if (mPreviousTimeNs != 0 && mCount > discard) {
            long diffInNano = mCurrentTimeNs - mPreviousTimeNs;
            // diffInMilli is rounded up
            int diffInMilli = (int) ((diffInNano + Constant.NANOS_PER_MILLI - 1) /
                                      Constant.NANOS_PER_MILLI);

            long timeStampInNano = mCurrentTimeNs - mStartTimeNs;
            int timeStampInMilli = (int) ((timeStampInNano + Constant.NANOS_PER_MILLI - 1) /
                                           Constant.NANOS_PER_MILLI);

            if (diffInMilli > mMaxBufferPeriod) {
                mMaxBufferPeriod = diffInMilli;
            }

            // from 0 ms to 1000 ms, plus a sum of all occurrences > 1000ms
            if (diffInMilli >= (range - 1)) {
                mBufferPeriod[range - 1]++;
            } else if (diffInMilli >= 0) {
                mBufferPeriod[diffInMilli]++;
            } else { // for diffInMilli < 0
                log("Having negative BufferPeriod.");
            }

            mCallbackTimes.recordCallbackTime(timeStampInMilli, (short) diffInMilli);

            // If diagnosing specific Java thread callback behavior set a conditional here and use
            // mCaptureHolder.captureState(rank); to capture systraces and bugreport and/or wav file
        }

        mPreviousTimeNs = mCurrentTimeNs;
    }


    /** Reset all variables, called if wants to start a new buffer period's record. */
    public void resetRecord() {
        mPreviousTimeNs = 0;
        mCurrentTimeNs = 0;
        Arrays.fill(mBufferPeriod, 0);
        mMaxBufferPeriod = 0;
        mCount = 0;
        mCallbackTimes = null;
    }

    public void prepareMemberObjects(int maxRecords, int expectedBufferPeriod,
                                     CaptureHolder captureHolder){
        mCallbackTimes = new BufferCallbackTimes(maxRecords, expectedBufferPeriod);
        mCaptureHolder = captureHolder;
    }

    public int[] getBufferPeriodArray() {
        return mBufferPeriod;
    }

    public int getMaxBufferPeriod() {
        return mMaxBufferPeriod;
    }

    public BufferCallbackTimes getCallbackTimes(){
        return mCallbackTimes;
    }

    private static void log(String msg) {
        Log.v(TAG, msg);
    }

}
