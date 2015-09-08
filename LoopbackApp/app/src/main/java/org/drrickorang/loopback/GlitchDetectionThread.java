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
 * This thread is responsible for detecting glitches in the samples.
 */

public class GlitchDetectionThread extends Thread {
    private static final String TAG = "GlitchDetectionThread";
    // the acceptable difference between the expected center of mass and what we actually get
    private static final double mAcceptablePercentDifference = 0.02; // change this if necessary


    private boolean mIsRunning; // condition must be true for the thread to run
    private short   mShortBuffer[]; // keep the data read from Pipe
    private int     mShortBufferIndex = 0;
    private Pipe    mPipe;
    private static int mThreadSleepDurationMs;

    private double  mDoubleBuffer[]; // keep the data used for FFT calculation
    private boolean mIsFirstFFT = true; // whether or not it's the first FFT calculation
    private double  mWaveData[]; // data that will be plotted
    private int     mWaveDataIndex = 0;

    private double  mFrequency1;
    private double  mFrequency2; //currently not used
    private int     mSamplingRate;
    private int     mFFTSamplingSize;   // amount of samples used to perform a FFT
    private int     mFFTOverlapSamples; // amount of overlapped samples used between two FFTs
    private int     mNewSamplesPerFFT;  // amount of new samples (not from last FFT) in a FFT
    private double  mCenterOfMass;  // expected center of mass of samples
    private int[]   mGlitches;  // for every value = n, n is the nth FFT where a glitch is found
    private int     mGlitchesIndex;
    private int     mFFTCount; // store the current number of FFT performed
    private FFT     mFFT;
    private boolean mGlitchingIntervalTooLong = false; // true if mGlitches is full


    GlitchDetectionThread(double frequency1, double frequency2, int samplingRate,
          int FFTSamplingSize, int FFTOverlapSamples, int bufferTestDurationInSeconds,
          int bufferTestWavePlotDurationInSeconds, Pipe pipe) {
        mPipe = pipe;
        mFrequency1 = frequency1;
        mFrequency2 = frequency2;
        mFFTSamplingSize = FFTSamplingSize;
        mFFTOverlapSamples = FFTOverlapSamples;
        mNewSamplesPerFFT = mFFTSamplingSize - mFFTOverlapSamples;
        mSamplingRate = samplingRate;
        mIsRunning = true;

        mShortBuffer = new short[mFFTSamplingSize];
        mDoubleBuffer = new double[mFFTSamplingSize];
        mWaveData = new double[mSamplingRate * bufferTestWavePlotDurationInSeconds];

        final int acceptableGlitchingIntervalsPerSecond = 10;
        mGlitches = new int[bufferTestDurationInSeconds * acceptableGlitchingIntervalsPerSecond];
        Arrays.fill(mGlitches, 0);
        mGlitchesIndex = 0;
        mFFTCount = 1;

        mFFT = new FFT(mFFTSamplingSize);
        computeExpectedCenterOfMass();

        mThreadSleepDurationMs = FFTOverlapSamples * Constant.MILLIS_PER_SECOND / mSamplingRate;
        if (mThreadSleepDurationMs < 1) {
            mThreadSleepDurationMs = 1; // sleeps at least 1ms
        }
    }


    public void run() {
        while (mIsRunning) {
            int requiredRead;
            int actualRead;

            requiredRead = mFFTSamplingSize - mShortBufferIndex;
            actualRead = mPipe.read(mShortBuffer, mShortBufferIndex, requiredRead);

            if (actualRead > 0) {
                mShortBufferIndex += actualRead;
            }

            if (actualRead == Pipe.OVERRUN) {
                log("There's an overrun");
            }

            // Once we have enough data, we can do a FFT on it. Note that between two FFTs, part of
            // the samples (of size mFFTOverlapSamples) are used in both FFTs .
            if (mShortBufferIndex == mFFTSamplingSize) {
                bufferShortToDouble(mShortBuffer, mDoubleBuffer);

                // copy data in mDoubleBuffer to mWaveData
                if (mIsFirstFFT) {
                    // if it's the first FFT, copy the whole "mNativeBuffer" to mWaveData
                    System.arraycopy(mDoubleBuffer, 0, mWaveData,
                                     mWaveDataIndex, mFFTSamplingSize);
                    mWaveDataIndex += mFFTSamplingSize;
                    mIsFirstFFT = false;
                } else {
                    // if  mWaveData is all filled, clear it then starting writing from beginning.
                    //TODO make mWaveData into a circular buffer storing the last N seconds instead
                    if ((mWaveDataIndex + mNewSamplesPerFFT) >= mWaveData.length) {
                        Arrays.fill(mWaveData, 0);
                        mWaveDataIndex = 0;
                    }

                    // if it's not the first FFT, copy the new data in "mNativeBuffer" to mWaveData
                    System.arraycopy(mDoubleBuffer, mFFTOverlapSamples, mWaveData,
                                     mWaveDataIndex, mNewSamplesPerFFT);
                    mWaveDataIndex += mFFTOverlapSamples;
                }

                detectGlitches();
                // move new samples to the beginning of the array as they will be reused in next fft
                System.arraycopy(mShortBuffer, mNewSamplesPerFFT, mShortBuffer,
                                 0, mFFTOverlapSamples);
                mShortBufferIndex = mFFTOverlapSamples;
            } else {
                try {
                    sleep(mThreadSleepDurationMs);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

    }


    /** convert samples in shortBuffer to double, then copy into doubleBuffer. */
    private void bufferShortToDouble(short[] shortBuffer, double[] doubleBuffer) {
        double temp;
        for (int i = 0; i < shortBuffer.length; i++) {
            temp = (double) shortBuffer[i];
            temp *= (1.0 / Short.MAX_VALUE);
            doubleBuffer[i] = temp;
        }
    }


    /** Should be called by other thread to stop this thread */
    public void requestStop() {
        mIsRunning = false;
        interrupt();
    }


    /**
     * Use the data in mDoubleBuffer to do glitch detection since we know what
     * data we are expecting.
     */
    private void detectGlitches() {
        double centerOfMass;
        double[] fftResult;
        double[] currentSamples;

        currentSamples = Arrays.copyOfRange(mDoubleBuffer, 0, mDoubleBuffer.length);
        currentSamples = Utilities.hanningWindow(currentSamples);
        double width = (double) mSamplingRate / currentSamples.length;
        fftResult = computeFFT(currentSamples);     // gives an array of sampleSize / 2
        final double threshold = 0.1;

        // for all elements in the FFT result that are smaller than threshold,
        // eliminate them as they are probably noise
        for (int j = 0; j < fftResult.length; j++) {
            if (fftResult[j] < threshold) {
                fftResult[j] = 0;
            }
        }

        // calculate the center of mass of sample's FFT
        centerOfMass = computeCenterOfMass(fftResult, width);
        double difference = (Math.abs(centerOfMass - mCenterOfMass) / mCenterOfMass);
        if (mGlitchesIndex >= mGlitches.length) {
            // we just want to show this log once and set the flag once.
            if (!mGlitchingIntervalTooLong) {
                log("Not enough room to store glitches!");
                mGlitchingIntervalTooLong = true;
            }
        } else {
            // centerOfMass == -1 if the wave we get is silence.
            if (difference > mAcceptablePercentDifference || centerOfMass == -1) {
                mGlitches[mGlitchesIndex] = mFFTCount;
                mGlitchesIndex++;
            }
        }
        mFFTCount++;
    }


    /** Compute the center of mass of fftResults. Width is the width of each beam. */
    private double computeCenterOfMass(double[] fftResult, double width) {
        int length = fftResult.length;
        double weightedSum = 0;
        double totalWeight = 0;
        for (int i = 0; i < length; i++) {
            weightedSum += fftResult[i] * i;
            totalWeight += fftResult[i];
        }

        // this may happen since we are eliminating the noises. So if the wave we got is silence,
        // totalWeight might == 0.
        if (totalWeight == 0) {
            return -1;
        }

        return (weightedSum * width) / totalWeight;
    }


    /** Compute FFT of a set of data "samples". */
    private double[] computeFFT(double[] realArray) {
        int length = realArray.length;
        double[] imagArray = new double[length]; // all zeros
        Arrays.fill(imagArray, 0);
        mFFT.fft(realArray, imagArray, 1);    // here realArray and imagArray get set

        double[] absValue = new double[length / 2];  // don't use second portion of arrays

        for (int i = 0; i < (length / 2); i++) {
            absValue[i] = Math.sqrt(realArray[i] * realArray[i] + imagArray[i] * imagArray[i]);
        }

        return absValue;
    }


    /** Compute the center of mass if the samples have no glitches. */
    private void computeExpectedCenterOfMass() {
        SineWaveTone sineWaveTone = new SineWaveTone(mSamplingRate, mFrequency1);
        double[] sineWave = new double[mFFTSamplingSize];
        double centerOfMass;
        double[] sineFFTResult;

        sineWaveTone.generateTone(sineWave, mFFTSamplingSize);
        sineWave = Utilities.hanningWindow(sineWave);
        double width = (double) mSamplingRate / sineWave.length;

        sineFFTResult = computeFFT(sineWave);     // gives an array of sample sizes / 2
        centerOfMass = computeCenterOfMass(sineFFTResult, width);  // return center of mass
        mCenterOfMass = centerOfMass;
        log("the expected center of mass:" + Double.toString(mCenterOfMass));
    }


    public double[] getWaveData() {
        return mWaveData;
    }


    public boolean getGlitchingIntervalTooLong() {
        return mGlitchingIntervalTooLong;
    }


    public int[] getGlitches() {
        return mGlitches;
    }


    private static void log(String msg) {
        Log.v(TAG, msg);
    }

}
