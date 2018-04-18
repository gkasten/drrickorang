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

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.util.Arrays;


/**
 * This class records the buffer period of the audio player or recorder when in Java mode.
 * Currently the accuracy is in 1ms.
 */

//TODO for native mode, should use a scale more accurate than the current 1ms
public class BufferPeriod implements Parcelable {
    private static final String TAG = "BufferPeriod";

    private long mStartTimeNs = 0;  // first time collectBufferPeriod() is called
    private long mPreviousTimeNs = 0;
    private long mCurrentTimeNs = 0;

    private int       mMeasurements = 0;
    private long      mVar;     // variance in nanoseconds^2
    private long      mSDM = 0; // sum of squares of deviations from the expected mean
    private int       mMaxBufferPeriod = 0;

    private int       mCount = 0;
    // Must match constant 'RANGE' in jni/loopback.h
    private final int range = 1002; // store counts for 0ms to 1000ms, and for > 1000ms
    private int       mExpectedBufferPeriod = 0;

    private int[] mBufferPeriod = new int[range];
    private BufferCallbackTimes mCallbackTimes;
    private CaptureHolder mCaptureHolder;

    public BufferPeriod() {
        // Default constructor for when no data will be restored
    }

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

        if (mPreviousTimeNs != 0 && mCount > Constant.BUFFER_PERIOD_DISCARD) {
            mMeasurements++;

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

            long delta = diffInNano - (long) mExpectedBufferPeriod * Constant.NANOS_PER_MILLI;
            mSDM += delta * delta;
            if (mCount > 1) {
                mVar = mSDM / mMeasurements;
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
        mMeasurements = 0;
        mExpectedBufferPeriod = 0;
        mCount = 0;
        mCallbackTimes = null;
    }

    public void prepareMemberObjects(int maxRecords, int expectedBufferPeriod,
                                     CaptureHolder captureHolder) {
        mCallbackTimes = new BufferCallbackTimes(maxRecords, expectedBufferPeriod);
        mCaptureHolder = captureHolder;
        mExpectedBufferPeriod = expectedBufferPeriod;
    }

    public int[] getBufferPeriodArray() {
        return mBufferPeriod;
    }

    public double getStdDevBufferPeriod() {
        return Math.sqrt(mVar) / (double) Constant.NANOS_PER_MILLI;
    }

    public int getMaxBufferPeriod() {
        return mMaxBufferPeriod;
    }

    public BufferCallbackTimes getCallbackTimes() {
        return mCallbackTimes;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    // Only save values which represent the results. Any ongoing timing would not give useful
    // results after a save/restore.
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Bundle out = new Bundle();
        out.putInt("mMaxBufferPeriod", mMaxBufferPeriod);
        out.putIntArray("mBufferPeriod", mBufferPeriod);
        out.putInt("mExpectedBufferPeriod", mExpectedBufferPeriod);
        out.putParcelable("mCallbackTimes", mCallbackTimes);
        dest.writeBundle(out);
    }

    private BufferPeriod(Parcel source) {
        Bundle in = source.readBundle(getClass().getClassLoader());
        mMaxBufferPeriod = in.getInt("mMaxBufferPeriod");
        mBufferPeriod = in.getIntArray("mBufferPeriod");
        mExpectedBufferPeriod = in.getInt("mExpectedBufferPeriod");
        mCallbackTimes = in.getParcelable("mCallbackTimes");
    }

    public static final Parcelable.Creator<BufferPeriod> CREATOR
             = new Parcelable.Creator<BufferPeriod>() {
         public BufferPeriod createFromParcel(Parcel in) {
             return new BufferPeriod(in);
         }

         public BufferPeriod[] newArray(int size) {
             return new BufferPeriod[size];
         }
     };

    private static void log(String msg) {
        Log.v(TAG, msg);
    }

}
