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

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Iterator;

/**
 * Maintains and returns pairs of callback timestamps (in milliseconds since beginning of test) and
 * lengths (milliseconds between a callback and the previous callback).
 */
public class BufferCallbackTimes implements Iterable<BufferCallbackTimes.BufferCallback>,
        Parcelable {
    private final int[] mTimeStamps;
    private final short[] mCallbackDurations;
    private final short mExpectedBufferPeriod;
    private boolean mExceededCapacity;
    private int mIndex;

    public BufferCallbackTimes(int maxRecords, int expectedBufferPeriod) {
        mIndex = 0;
        mTimeStamps = new int[maxRecords];
        mCallbackDurations = new short[maxRecords];
        mExceededCapacity = false;
        mExpectedBufferPeriod = (short) expectedBufferPeriod;
    }

    /**
     * Instantiates an iterable object with already recorded callback times and lengths
     * used for callbacks recorded by native sles callback functions.
     *
     * exceededCapacity should be set to true only when there were late callbacks observed but
     * unable to be recorded because allocated arrays were already at capacity
     */
    public BufferCallbackTimes(int[] timeStamps, short[] callbackDurations,
                               boolean exceededCapacity, short expectedBufferPeriod) {
        mTimeStamps = timeStamps;
        mCallbackDurations = callbackDurations;
        mExceededCapacity = exceededCapacity;
        mIndex = mTimeStamps.length;
        mExpectedBufferPeriod = expectedBufferPeriod;
    }

    /** Record the length of a late/early callback and the time it occurred. Used by Java Thread. */
    public void recordCallbackTime(int timeStamp, short callbackLength) {
        if (!mExceededCapacity && callbackLength != mExpectedBufferPeriod
                && callbackLength != mExpectedBufferPeriod + 1) {
            //only marked as exceeded if attempting to record a late callback after arrays full
            if (mIndex == mTimeStamps.length) {
                mExceededCapacity = true;
                return;
            }
            mTimeStamps[mIndex] = timeStamp;
            mCallbackDurations[mIndex] = callbackLength;
            mIndex++;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (BufferCallback callback : this) {
            sb.append(callback.timeStamp);
            sb.append(",");
            sb.append(callback.callbackDuration);
            sb.append("\n");
        }
        return sb.toString();
    }

    // True only if arrays are full and recording more late or early callbacks is attempted.
    public boolean isCapacityExceeded() {
        return mExceededCapacity;
    }

    public int getNumLateOrEarlyCallbacks() {
        return mIndex;
    }

    public short getExpectedBufferPeriod() {
        return mExpectedBufferPeriod;
    }

    @Override
    public Iterator<BufferCallback> iterator() {
        return new Iterator<BufferCallback>() {
            int mIteratorIndex = 0;

            @Override
            public boolean hasNext() {
                return mIteratorIndex < mIndex;
            }

            @Override
            public BufferCallback next() {
                return new BufferCallback(mTimeStamps[mIteratorIndex],
                        mCallbackDurations[mIteratorIndex++]);
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("Buffer Time Stamps are Immutable");
            }
        };
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Bundle out = new Bundle();
        out.putIntArray("mTimeStamps", mTimeStamps);
        out.putShortArray("mCallbackDurations", mCallbackDurations);
        out.putShort("mExpectedBufferPeriod", mExpectedBufferPeriod);
        out.putBoolean("mExceededCapacity", mExceededCapacity);
        out.putInt("mIndex", mIndex);
        dest.writeBundle(out);
    }

    private BufferCallbackTimes(Parcel source) {
        Bundle in = source.readBundle(getClass().getClassLoader());
        mTimeStamps = in.getIntArray("mTimeStamps");
        mCallbackDurations = in.getShortArray("mCallbackDurations");
        mExpectedBufferPeriod = in.getShort("mExpectedBufferPeriod");
        mExceededCapacity = in.getBoolean("mExceededCapacity");
        mIndex = in.getInt("mIndex");
    }

    public static final Parcelable.Creator<BufferCallbackTimes> CREATOR
             = new Parcelable.Creator<BufferCallbackTimes>() {
         public BufferCallbackTimes createFromParcel(Parcel in) {
             return new BufferCallbackTimes(in);
         }

         public BufferCallbackTimes[] newArray(int size) {
             return new BufferCallbackTimes[size];
         }
     };

    /** Wrapper for iteration over timestamp and length pairs */
    public class BufferCallback {
        public final int timeStamp;
        public final short callbackDuration;

        BufferCallback(final int ts, final short cd) {
            timeStamp = ts;
            callbackDuration = cd;
        }
    }

}
