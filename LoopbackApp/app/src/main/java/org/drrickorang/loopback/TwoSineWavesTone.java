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
 * This class generates a mix of two sine waves with frequency1, frequency2, and samplingRate.
 * It keeps two member variable "mPhase1" and "mPhase2", so as it continually be called,
 * it will continue to generate the next section of the sine wave.
 */

/*
public class TwoSineWavesTone extends ToneGeneration {
    private int          mCount; // counts the total samples produced.
    private double       mPhase1; // current phase associated with mFrequency1
    private double       mPhase2; // current phase associated with mFrequency2
    private final double mPhaseIncrement1; // phase incrementation associated with mFrequency1
    private final double mPhaseIncrement2; // phase incrementation associated with mFrequency2
*/


    /**
     * Currently, this class is never used, but it can be used in the future to create a different
     * kind of wave when running the test.
     */
/*
    public TwoSineWavesTone(int samplingRate, double frequency1, double frequency2) {
        super(samplingRate);
        mCount = 0;
        mPhaseIncrement1 = Constant.TWO_PI * (frequency1 / mSamplingRate); // should < 2pi
        mPhaseIncrement2 = Constant.TWO_PI * (frequency2 / mSamplingRate); // should < 2pi
        mAmplitude = Constant.TWO_SINE_WAVES_AMPLITUDE;
    }


    @Override
    public void generateTone(short[] tone, int size) {
        for (int i = 0; i < size; i++) {
            short value1 = (short) (mAmplitude * Math.sin(mPhase1) * Short.MAX_VALUE);
            short value2 = (short) (mAmplitude * Math.sin(mPhase2) * Short.MAX_VALUE);
            tone[i] = (short) (value1 + value2);

            mPhase1 += mPhaseIncrement1;
            mPhase2 += mPhaseIncrement2;

            // insert glitches for every second if mIsGlitchEnabled == true.
            if (mIsGlitchEnabled && (mCount % mSamplingRate == 0)) {
                mPhase1 += mPhaseIncrement1;
                mPhase2 += mPhaseIncrement2;
            }

            mCount++;

            if (mPhase1 > Constant.TWO_PI) {
                mPhase1 -= Constant.TWO_PI;
            }
            if (mPhase2 > Constant.TWO_PI) {
                mPhase2 -= Constant.TWO_PI;
            }

        }
    }


    @Override
    public void generateTone(double[] tone, int size) {
        for (int i = 0; i < size; i++) {
            double value1 = mAmplitude * Math.sin(mPhase1);
            double value2 = mAmplitude * Math.sin(mPhase2);
            tone[i] = value1 + value2;

            mPhase1 += mPhaseIncrement1;
            mPhase2 += mPhaseIncrement2;
            // insert glitches if mIsGlitchEnabled == true, and insert it for every second
            if (mIsGlitchEnabled && (mCount % mSamplingRate == 0)) {
                mPhase1 += mPhaseIncrement1;
                mPhase2 += mPhaseIncrement2;
            }

            mCount++;

            if (mPhase1 > Constant.TWO_PI) {
                mPhase1 -= Constant.TWO_PI;
            }
            if (mPhase2 > Constant.TWO_PI) {
                mPhase2 -= Constant.TWO_PI;
            }

        }
    }


    @Override
    public void resetPhases() {
        mPhase1 = 0;
        mPhase2 = 0;
    }
}
*/
