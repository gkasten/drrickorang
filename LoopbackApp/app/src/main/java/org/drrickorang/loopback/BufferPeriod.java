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

/**
 * Created by ninatai on 5/12/15.
 */

import android.util.Log;

import org.drrickorang.loopback.LoopbackAudioThread.RecorderRunnable;

import java.util.Arrays;
import java.util.HashMap;

// TODO add record for native audio thread
public class BufferPeriod {
    private static long mPreviousTime = 0;
    private static long mCurrentTime = 0;
    private static final int range = 102; //TODO adjust this value
    private static int mMaxBufferPeriod = 0;
    private static boolean exceedRange = false;
    private static int mCount = 0;
    private static int mDiscard = 5;  // discard the first few buffer period values

    private static int[] mJavaBufferPeriod = new int[range];


    public static void collectBufferPeriod()  {
        mCurrentTime = System.nanoTime();
        mCount += 1;

        // if = 0 it's the first time the thread runs, so don't record the interval
        if (mPreviousTime != 0 && mCurrentTime != 0 && mCount > mDiscard) {
            long diffInNano = mCurrentTime - mPreviousTime;
            int diffInMilli = (int) Math.ceil(( ((double)diffInNano / 1000000))); // round up

            if (diffInMilli > mMaxBufferPeriod) {
                mMaxBufferPeriod = diffInMilli;
            }

            // from 0 ms to 1000 ms, plus a sum of all instances > 1000ms
            if (diffInMilli >= 0 && diffInMilli < (range - 1)) {
                mJavaBufferPeriod[diffInMilli] += 1;
            } else if (diffInMilli >= (range - 1)) {
                mJavaBufferPeriod[range-1] += 1;
            } else if (diffInMilli < 0) {
                // throw new IllegalBufferPeriodException("BufferPeriod must be >= 0");
                errorLog("Having negative BufferPeriod.");
            }

        }

        mPreviousTime = mCurrentTime;
    }

    // Check if max BufferPeriod exceeds the range of latencies that are going to be displayed on histogram
    public static void setExceedRange() {
        if (mMaxBufferPeriod > (range - 2)) {
            exceedRange = true;
        } else {
            exceedRange = false;
        }
    }

    public static void resetRecord() {
        mPreviousTime = 0;
        mCurrentTime = 0;
        Arrays.fill(mJavaBufferPeriod, 0);
        mMaxBufferPeriod = 0;
        mCount = 0;

    }

    public static int[] getBufferPeriodArray() {
        return mJavaBufferPeriod;

    }

    public static int getMaxBufferPeriod() {
        return mMaxBufferPeriod;
    }




    private static void errorLog(String msg) {
        Log.e("BufferPeriodTracker", msg);
    }

    private static void log(String msg) {
        Log.v("BufferPeriodTracker", msg);
    }

    public static class IllegalBufferPeriodException extends Exception {

        public IllegalBufferPeriodException(String message)
        {
            super(message);
        }
    }



}
