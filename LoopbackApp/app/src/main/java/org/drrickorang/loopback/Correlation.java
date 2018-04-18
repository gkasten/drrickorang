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


/**
 * This class is used to automatically estimate latency and its confidence.
 */

public class Correlation implements Parcelable {
    private static final String TAG = "Correlation";

    private int       mBlockSize = Constant.DEFAULT_CORRELATION_BLOCK_SIZE;
    private int       mSamplingRate;
    private double [] mDataDownsampled;
    private double [] mDataAutocorrelated;

    public double mEstimatedLatencySamples = 0;
    public double mEstimatedLatencyMs = 0;
    public double mEstimatedLatencyConfidence = 0.0;
    public double mAverage = 0.0;
    public double mRms = 0.0;

    private double mAmplitudeThreshold = 0.001;  // 0.001 = -60 dB noise

    private boolean mDataIsValid = false; // Used to mark computed latency information is available

    public Correlation() {
        // Default constructor for when no data will be restored

    }

    public void init(int blockSize, int samplingRate) {
        setBlockSize(blockSize);
        mSamplingRate = samplingRate;
    }

    public void computeCorrelation(double [] data, int samplingRate) {
        log("Started Auto Correlation for data with " + data.length + " points");
        mSamplingRate = samplingRate;
        mDataDownsampled = new double [mBlockSize];
        mDataAutocorrelated = new double[mBlockSize];
        downsampleData(data, mDataDownsampled, mAmplitudeThreshold);

        //correlation vector
        autocorrelation(mDataDownsampled, mDataAutocorrelated);


        int N = data.length; //all samples available
        double groupSize =  (double) N / mBlockSize;  //samples per downsample point.

        double maxValue = 0;
        int maxIndex = -1;

        double minLatencyMs = 8; //min latency expected. This algorithm should be improved.
        int minIndex = (int) (0.5 + minLatencyMs * mSamplingRate / (groupSize * 1000));

        double average = 0;
        double rms = 0;

        //find max
        for (int i = minIndex; i < mDataAutocorrelated.length; i++) {
            average += mDataAutocorrelated[i];
            rms += mDataAutocorrelated[i] * mDataAutocorrelated[i];
           if (mDataAutocorrelated[i] > maxValue) {
               maxValue = mDataAutocorrelated[i];
               maxIndex = i;
           }
        }

        rms = Math.sqrt(rms / mDataAutocorrelated.length);
        average = average / mDataAutocorrelated.length;
        log(String.format(" Maxvalue %f, max Index : %d/%d (%d)  minIndex = %d", maxValue, maxIndex,
                          mDataAutocorrelated.length, data.length, minIndex));
        log(String.format("  average : %.3f  rms: %.3f", average, rms));

        mAverage = average;
        mRms = rms;

        mEstimatedLatencyConfidence = 0.0;
        if (average > 0) {
            double factor = 3.0;

            double raw = (rms - average) / (factor * average);
            log(String.format("Raw: %.3f", raw));
            mEstimatedLatencyConfidence = Math.max(Math.min(raw, 1.0), 0.0);
        }
        log(String.format(" ****Confidence: %.2f", mEstimatedLatencyConfidence));

        mEstimatedLatencySamples = maxIndex * groupSize;
        mEstimatedLatencyMs = mEstimatedLatencySamples * 1000 / mSamplingRate;
        log(String.format(" latencySamples: %.2f  %.2f ms", mEstimatedLatencySamples,
                          mEstimatedLatencyMs));

        mDataIsValid = mEstimatedLatencyMs > 0.0001;
    }

    // Called by LoopbackActivity before displaying latency test results
    public boolean isValid() {
        return mDataIsValid;
    }

    // Called at beginning of new test
    public void invalidate() {
        mDataIsValid = false;
    }

    public void setBlockSize(int blockSize) {
        mBlockSize = clamp(blockSize, Constant.CORRELATION_BLOCK_SIZE_MIN,
                Constant.CORRELATION_BLOCK_SIZE_MAX);
    }

    private boolean downsampleData(double [] data, double [] dataDownsampled, double threshold) {
        log("Correlation block size used in down sample: " + mBlockSize);

        boolean status;
        for (int i = 0; i < mBlockSize; i++) {
            dataDownsampled[i] = 0;
        }

        int N = data.length; //all samples available
        double groupSize =  (double) N / mBlockSize;

        int ignored = 0;

        int currentIndex = 0;
        double nextGroup = groupSize;
        for (int i = 0; i < N && currentIndex < mBlockSize; i++) {

            if (i > nextGroup) { //advanced to next group.
                currentIndex++;
                nextGroup += groupSize;
            }

            if (currentIndex >= mBlockSize) {
                break;
            }

            double value =  Math.abs(data[i]);
            if (value >= threshold) {
                dataDownsampled[currentIndex] += value;
            } else {
                ignored++;
            }
        }

        log(String.format(" Threshold: %.3f, ignored:%d/%d (%%.2f)",
                threshold, ignored, N, (double) ignored/(double)N));

        status = true;
        return status;
    }


    private boolean autocorrelation(double [] data, double [] dataOut) {
        boolean status = false;

        double sumsquared = 0;
        int N = data.length;
        for (int i = 0; i < N; i++) {
            double value = data[i];
            sumsquared += value * value;
        }

        if (sumsquared > 0) {
            //correlate (not circular correlation)
            for (int i = 0; i < N; i++) {
                dataOut[i] = 0;
                for (int j = 0; j < N - i; j++) {

                    dataOut[i] += data[j] * data[i + j];
                }
                dataOut[i] = dataOut[i] / sumsquared;
            }
            status = true;
        }

        return status;
    }

    /**
     * Returns value if value is within inclusive bounds min through max
     * otherwise returns min or max according to if value is less than or greater than the range
     */
    // TODO move to audio_utils
    private int clamp(int value, int min, int max) {

        if (max < min) throw new UnsupportedOperationException("min must be <= max");

        if (value < min) return min;
        else if (value > max) return max;
        else return value;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    // Store the results before this object is destroyed
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Bundle bundle = new Bundle();
        bundle.putBoolean("mDataIsValid", mDataIsValid);
        if (mDataIsValid) {
            bundle.putDouble("mEstimatedLatencySamples", mEstimatedLatencySamples);
            bundle.putDouble("mEstimatedLatencyMs", mEstimatedLatencyMs);
            bundle.putDouble("mEstimatedLatencyConfidence", mEstimatedLatencyConfidence);
            bundle.putDouble("mAverage", mAverage);
            bundle.putDouble("mRms", mRms);
        }
        dest.writeBundle(bundle);
    }

    // Restore the results which were previously calculated
    private Correlation(Parcel in) {
        Bundle bundle = in.readBundle(getClass().getClassLoader());
        mDataIsValid = bundle.getBoolean("mDataIsValid");
        if (mDataIsValid) {
            mEstimatedLatencySamples    = bundle.getDouble("mEstimatedLatencySamples");
            mEstimatedLatencyMs         = bundle.getDouble("mEstimatedLatencyMs");
            mEstimatedLatencyConfidence = bundle.getDouble("mEstimatedLatencyConfidence");
            mAverage                    = bundle.getDouble("mAverage");
            mRms                        = bundle.getDouble("mRms");
        }
    }

    public static final Parcelable.Creator<Correlation> CREATOR
            = new Parcelable.Creator<Correlation>() {
        public Correlation createFromParcel(Parcel in) {
            return new Correlation(in);
        }

        public Correlation[] newArray(int size) {
            return new Correlation[size];
        }
    };

    private static void log(String msg) {
        Log.v(TAG, msg);
    }

}
