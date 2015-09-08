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

import android.util.Log;


/**
 * This class is used to automatically estimate latency and its confidence.
 */

public class Correlation {
    private static final String TAG = "Correlation";

    private int       mBlockSize = 4096;
    private int       mSamplingRate;
    private double [] mDataDownsampled = new double [mBlockSize];
    private double [] mDataAutocorrelated = new double[mBlockSize];

    public double mEstimatedLatencySamples = 0;
    public double mEstimatedLatencyMs = 0;
    public double mEstimatedLatencyConfidence = 0.0;


    public void init(int blockSize, int samplingRate) {
        mBlockSize = blockSize;
        mSamplingRate = samplingRate;
    }


    public boolean computeCorrelation(double [] data, int samplingRate) {
        boolean status;
        log("Started Auto Correlation for data with " + data.length + " points");
        mSamplingRate = samplingRate;

        downsampleData(data, mDataDownsampled);

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

        status = true;
        return status;
    }


    private boolean downsampleData(double [] data, double [] dataDownsampled) {

        boolean status;
        for (int i = 0; i < mBlockSize; i++) {
            dataDownsampled[i] = 0;
        }

        int N = data.length; //all samples available
        double groupSize =  (double) N / mBlockSize;

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
            dataDownsampled[currentIndex] += Math.abs(data[i]);
        }

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


    private static void log(String msg) {
        Log.v(TAG, msg);
    }
}
