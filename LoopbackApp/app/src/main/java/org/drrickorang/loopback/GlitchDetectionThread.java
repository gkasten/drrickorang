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

import java.util.Arrays;


/**
 * This thread is responsible for detecting glitches in the samples.
 */

public class GlitchDetectionThread extends Thread {
    private static final String TAG = "GlitchDetectionThread";
    // the acceptable difference between the expected center of mass and what we actually get
    private static final double mAcceptablePercentDifference = 0.02; // change this if necessary

    // Measured in FFT samples
    private static final int GLITCH_CONCENTRATION_WINDOW_SIZE = 1500; // approx 30 seconds at 48kHz
    private static final int COOLDOWN_WINDOW = 4500; // approx 90 seconds at 48kHz

    private boolean mIsRunning; // condition must be true for the thread to run
    private short   mShortBuffer[]; // keep the data read from Pipe
    private int     mShortBufferIndex = 0;
    private Pipe    mPipe;
    private static int mThreadSleepDurationMs;

    private double  mDoubleBuffer[]; // keep the data used for FFT calculation
    private boolean mIsFirstFFT = true; // whether or not it's the first FFT calculation

    private WaveDataRingBuffer mWaveDataRing; // Record last n seconds of wave data

    private final double  mFrequency1;
    private final double  mFrequency2; //currently not used
    private final int     mSamplingRate;
    private final int     mFFTSamplingSize;   // amount of samples used to perform a FFT
    private final int     mFFTOverlapSamples; // amount of overlapped samples used between two FFTs
    private final int     mNewSamplesPerFFT;  // amount of new samples (not from last FFT) in a FFT
    private double  mCenterOfMass;  // expected center of mass of samples

    private final int[]   mGlitches;  // for every value = n, n is nth FFT where a glitch is found
    private int     mGlitchesIndex;
    private int     mFFTCount; // store the current number of FFT performed
    private FFT     mFFT;
    private boolean mGlitchingIntervalTooLong = false; // true if mGlitches is full

    // Pre-Allocated buffers for glitch detection process
    private final double[] mFFTResult;
    private final double[] mCurrentSamples;
    private final double[] mImagArray;

    // Used for captured SysTrace dumps
    private CaptureHolder mCaptureHolder;
    private int mLastGlitchCaptureAttempt = 0;

    GlitchDetectionThread(double frequency1, double frequency2, int samplingRate,
          int FFTSamplingSize, int FFTOverlapSamples, int bufferTestDurationInSeconds,
          int bufferTestWavePlotDurationInSeconds, Pipe pipe, CaptureHolder captureHolder) {
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
        mWaveDataRing = new WaveDataRingBuffer(mSamplingRate * bufferTestWavePlotDurationInSeconds);

        final int acceptableGlitchingIntervalsPerSecond = 10;
        mGlitches = new int[bufferTestDurationInSeconds * acceptableGlitchingIntervalsPerSecond];
        mGlitchesIndex = 0;
        mFFTCount = 0;

        mFFTResult = new double[mFFTSamplingSize/2];
        mCurrentSamples = new double[mFFTSamplingSize];
        mImagArray = new double[mFFTSamplingSize];

        mFFT = new FFT(mFFTSamplingSize);
        computeExpectedCenterOfMass();

        setName("Loopback_GlitchDetection");

        mCaptureHolder = captureHolder;
        mCaptureHolder.setWaveDataBuffer(mWaveDataRing);

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
                    mWaveDataRing.writeWaveData(mDoubleBuffer, 0, mFFTSamplingSize);
                    mIsFirstFFT = false;
                } else {
                    mWaveDataRing.writeWaveData(mDoubleBuffer, mFFTOverlapSamples,
                            mNewSamplesPerFFT);
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
    // TODO move to audio_utils
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

        // retrieve a copy of recorded wave data for manipulating and analyzing
        System.arraycopy(mDoubleBuffer, 0, mCurrentSamples, 0, mDoubleBuffer.length);

        Utilities.hanningWindow(mCurrentSamples);

        double width = (double) mSamplingRate / mCurrentSamples.length;
        computeFFT(mCurrentSamples, mFFTResult);     // gives an array of sampleSize / 2
        final double threshold = 0.1;

        // for all elements in the FFT result that are smaller than threshold,
        // eliminate them as they are probably noise
        for (int j = 0; j < mFFTResult.length; j++) {
            if (mFFTResult[j] < threshold) {
                mFFTResult[j] = 0;
            }
        }

        // calculate the center of mass of sample's FFT
        centerOfMass = computeCenterOfMass(mFFTResult, width);
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
                // Glitch Detected
                mGlitches[mGlitchesIndex] = mFFTCount;
                mGlitchesIndex++;
                if (mCaptureHolder.isCapturing()) {
                    checkGlitchConcentration();
                }
            }
        }
        mFFTCount++;
    }

    private void checkGlitchConcentration() {

        final int recordedGlitch = mGlitches[mGlitchesIndex-1];
        if (recordedGlitch - mLastGlitchCaptureAttempt <= COOLDOWN_WINDOW) {
            return;
        }

        final int windowBegin = recordedGlitch - GLITCH_CONCENTRATION_WINDOW_SIZE;

        int numGlitches = 0;
        for (int index = mGlitchesIndex-1; index >= 0 && mGlitches[index] >= windowBegin; --index) {
            ++numGlitches;
        }

        int captureResponse = mCaptureHolder.captureState(numGlitches);
        if (captureResponse != CaptureHolder.NEW_CAPTURE_IS_LEAST_INTERESTING) {
            mLastGlitchCaptureAttempt = recordedGlitch;
        }

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
    private void computeFFT(double[] src, double[] dst) {
        Arrays.fill(mImagArray, 0);
        mFFT.fft(src, mImagArray, 1);    // here src array and imagArray get set


        for (int i = 0; i < (src.length / 2); i++) {
            dst[i] = Math.sqrt(src[i] * src[i] + mImagArray[i] * mImagArray[i]);
        }

    }


    /** Compute the center of mass if the samples have no glitches. */
    private void computeExpectedCenterOfMass() {
        SineWaveTone sineWaveTone = new SineWaveTone(mSamplingRate, mFrequency1);
        double[] sineWave = new double[mFFTSamplingSize];
        double centerOfMass;
        double[] sineFFTResult = new double[mFFTSamplingSize/2];

        sineWaveTone.generateTone(sineWave, mFFTSamplingSize);
        Utilities.hanningWindow(sineWave);
        double width = (double) mSamplingRate / sineWave.length;

        computeFFT(sineWave, sineFFTResult);     // gives an array of sample sizes / 2
        centerOfMass = computeCenterOfMass(sineFFTResult, width);  // return center of mass
        mCenterOfMass = centerOfMass;
        log("the expected center of mass:" + Double.toString(mCenterOfMass));
    }


    public double[] getWaveData() {
        return mWaveDataRing.getWaveRecord();
    }


    public boolean getGlitchingIntervalTooLong() {
        return mGlitchingIntervalTooLong;
    }


    public int[] getGlitches() {
        //return a copy of recorded glitches in an array sized to hold only recorded glitches
        int[] output = new int[mGlitchesIndex];
        System.arraycopy(mGlitches, 0, output, 0, mGlitchesIndex);
        return output;
    }


    private static void log(String msg) {
        Log.v(TAG, msg);
    }

}
