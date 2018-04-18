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
 * This class is used to automatically measure the audio performance according to recorder/player
 * buffer period.
 */

public class PerformanceMeasurement {
    public static final String TAG = "PerformanceMeasurement";

    // this is used to enlarge the benchmark, so that it can be displayed with better accuracy on
    // the dashboard
    private static final int mMultiplicationFactor = 10000;

    private int   mExpectedBufferPeriodMs;
    private int[] mBufferData;
    private int   mTotalOccurrence;

    // used to determine buffer sizes mismatch
    private static final double mPercentOccurrenceThreshold = 0.95;
    // used to count the number of outliers
    private static final int    mOutliersThreshold = 3;


    /**
     * Note: if mBufferSize * Constant.MILLIS_PER_SECOND / mSamplingRate == Integer is satisfied,
     * the measurement will be more accurate, but this is not necessary.
     */
    public PerformanceMeasurement(int expectedBufferPeriod, int[] bufferData) {
        mBufferData = bufferData;

        mTotalOccurrence = 0;
        for (int i = 0; i < mBufferData.length; i++) {
            mTotalOccurrence += mBufferData[i];
        }

        mExpectedBufferPeriodMs = expectedBufferPeriod;
    }


    /**
     * Measure the performance according to the collected buffer period.
     * First, determine if there is a buffer sizes mismatch. If there is, then the performance
     * measurement should be disregarded since it won't be accurate. If there isn't a mismatch,
     * then a benchmark and a count on outliers can be produced as a measurement of performance.
     * The benchmark should be as small as possible, so is the number of outliers.
     * Note: This is a wrapper method that calls different methods and prints their results. It is
     * also possible to call individual method to obtain specific result.
     * Note: Should try to compare the number of outliers with the number of glitches and see if
     * they match.
     */
    public void measurePerformance() {
        // calculate standard deviation and mean of mBufferData
        double mean = computeMean(mBufferData);
        double standardDeviation = computeStandardDeviation(mBufferData, mean);
        log("mean before discarding 99% data: " + mean);
        log("standard deviation before discarding 99% data: " + standardDeviation);
        log("stdev/mean before discarding 99% data: " + (standardDeviation / mean));

        // calculate standard deviation and mean of dataAfterDiscard
        int[] dataAfterDiscard = computeDataAfterDiscard(mBufferData);
        double meanAfterDiscard = computeMean(dataAfterDiscard);
        double standardDeviationAfterDiscard = computeStandardDeviation(dataAfterDiscard,
                                                                        meanAfterDiscard);
        log("mean after discarding 99% data: " + meanAfterDiscard);
        log("standard deviation after discarding 99% data: " + standardDeviationAfterDiscard);
        log("stdev/mean after discarding 99% data: " + (standardDeviationAfterDiscard /
                                                        meanAfterDiscard));
        log("percent difference between two means: " + (Math.abs(meanAfterDiscard - mean) / mean));

        // determine if there's a buffer sizes mismatch
        boolean isBufferSizesMismatch =
                percentBufferPeriodsAtExpected() > mPercentOccurrenceThreshold;

        // compute benchmark and count the number of outliers
        double benchmark = computeWeightedBenchmark();
        int outliers = countOutliers();

        log("total occurrence: " + mTotalOccurrence);
        log("buffer size mismatch: " + isBufferSizesMismatch);
        log("benchmark: " + benchmark);
        log("number of outliers: " + outliers);
        log("expected buffer period: " + mExpectedBufferPeriodMs + " ms");
        int maxPeriod = (mBufferData.length - 1);
        log("max buffer period: " + maxPeriod + " ms");
    }


    /**
     * Determine percent of Buffer Period Callbacks that occurred at the expected time
     * Returns a value between 0 and 1
     */
    public double percentBufferPeriodsAtExpected() {
        int occurrenceNearExpectedBufferPeriod = 0;
        // indicate how many buckets around mExpectedBufferPeriod do we want to add to the count
        int acceptableOffset = 2;
        int start = Math.max(0, mExpectedBufferPeriodMs - acceptableOffset);
        int end = Math.min(mBufferData.length - 1, mExpectedBufferPeriodMs + acceptableOffset);
        // include the next bucket too because the period is rounded up
        for (int i = start; i <= end; i++) {
            occurrenceNearExpectedBufferPeriod += mBufferData[i];
        }
        return ((double) occurrenceNearExpectedBufferPeriod) / mTotalOccurrence;
    }


    /**
     * Compute a benchmark using the following formula:
     * (1/totalOccurrence) sum_i(|i - expectedBufferPeriod|^2 * occurrence_i / expectedBufferPeriod)
     * , for i < expectedBufferPeriod * mOutliersThreshold
     * Also, the benchmark is additionally multiplied by mMultiplicationFactor. This is not in the
     * original formula, and it is used only because the original benchmark will be too small to
     * be displayed accurately on the dashboard.
     */
    public double computeWeightedBenchmark() {
        double weightedCount = 0;
        double weight;
        double benchmark;

        // don't count mExpectedBufferPeriodMs + 1 towards benchmark, cause this beam may be large
        // due to rounding issue (all results are rounded up when collecting buffer period.)
        int threshold = Math.min(mBufferData.length, mExpectedBufferPeriodMs * mOutliersThreshold);
        for (int i = 0; i < threshold; i++) {
            if (mBufferData[i] != 0 && (i != mExpectedBufferPeriodMs + 1)) {
                weight = Math.abs(i - mExpectedBufferPeriodMs);
                weight *= weight;   // squared
                weightedCount += weight * mBufferData[i];
            }
        }
        weightedCount /= mExpectedBufferPeriodMs;

        benchmark = (weightedCount / mTotalOccurrence) * mMultiplicationFactor;
        return benchmark;
    }


    /**
     * All occurrence that happens after (mExpectedBufferPeriodMs * mOutliersThreshold) ms, will
     * be considered as outliers.
     */
    public int countOutliers() {
        int outliersThresholdInMs = mExpectedBufferPeriodMs * mOutliersThreshold;
        int outliersCount = 0;
        for (int i = outliersThresholdInMs; i < mBufferData.length; i++) {
            outliersCount += mBufferData[i];
        }
        return outliersCount;
    }


    /**
     * Output an array that has discarded 99 % of the data in the middle. In this array,
     * data[i] = x means there are x occurrences of value i.
     */
    private int[] computeDataAfterDiscard(int[] data) {
        // calculate the total amount of data
        int totalCount = 0;
        int length = data.length;
        for (int i = 0; i < length; i++) {
            totalCount += data[i];
        }

        // we only want to keep a certain percent of data at the bottom and top
        final double percent = 0.005;
        int bar = (int) (totalCount * percent);
        if (bar == 0) { // at least keep the lowest and highest data
            bar = 1;
        }
        int count = 0;
        int[] dataAfterDiscard = new int[length];

        // for bottom data
        for (int i = 0; i < length; i++) {
            if (count > bar) {
                break;
            } else if (count + data[i] > bar) {
                dataAfterDiscard[i] += bar - count;
                break;
            } else {
                dataAfterDiscard[i] += data[i];
                count += data[i];
            }
        }

        // for top data
        count = 0;
        for (int i = length - 1; i >= 0; i--) {
            if (count > bar) {
                break;
            } else if (count + data[i] > bar) {
                dataAfterDiscard[i] += bar - count;
                break;
            } else {
                dataAfterDiscard[i] += data[i];
                count += data[i];
            }
        }

        return dataAfterDiscard;
    }


    /**
     * Calculate the mean of int array "data". In this array, data[i] = x means there are
     * x occurrences of value i.
     * TODO move to audio_utils
     */
    private double computeMean(int[] data) {
        int count = 0;
        int sum = 0;
        for (int i = 0; i < data.length; i++) {
            count += data[i];
            sum += data[i] * i;
        }

        double mean;
        if (count != 0) {
            mean = (double) sum / count;
        } else {
            mean = 0;
            log("zero count!");
        }

        return mean;
    }


    /**
     * Calculate the standard deviation of int array "data". In this array, data[i] = x means
     * there are x occurrences of value i.
     * TODO move to audio_utils
     */
    private double computeStandardDeviation(int[] data, double mean) {
        double sumDeviation = 0;
        int count = 0;
        double standardDeviation;

        for (int i = 0; i < data.length; i++) {
            if (data[i] != 0) {
                count += data[i];
                sumDeviation += (i - mean) * (i - mean) * data[i];
            }
        }

        standardDeviation = Math.sqrt(sumDeviation / (count - 1));
        return standardDeviation;
    }


    private static void log(String msg) {
        Log.v(TAG, msg);
    }

}
