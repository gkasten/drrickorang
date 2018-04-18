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
 * This class generates a sine wave with given frequency and samplingRate.
 * It keeps a member variable "mPhase", so as it continually be called, it will continue to generate
 * the next section of the sine wave.
 * TODO move to audio_utils
 */

public class SineWaveTone extends ToneGeneration {
    private int          mCount; // counts the total samples produced.
    private double       mPhase; // current phase
    private final double mPhaseIncrement; // phase incrementation associated with mFrequency


    public SineWaveTone(int samplingRate, double frequency) {
        super(samplingRate);
        mCount = 0;
        mPhaseIncrement = Constant.TWO_PI * (frequency / mSamplingRate); // should < 2pi
        mAmplitude = Constant.SINE_WAVE_AMPLITUDE;
    }


    @Override
    public void generateTone(short[] tone, int size) {
        for (int i = 0; i < size; i++) {
            short value1 = (short) (mAmplitude * Math.sin(mPhase) * Short.MAX_VALUE);
            tone[i] = value1;

            mPhase += mPhaseIncrement;
            // insert glitches if mIsGlitchEnabled == true, and insert it for every second
            if (mIsGlitchEnabled && (mCount % mSamplingRate == 0)) {
                mPhase += mPhaseIncrement;
            }

            mCount++;

            if (mPhase >= Constant.TWO_PI) {
                mPhase -= Constant.TWO_PI;
            }
        }
    }


    @Override
    public void generateTone(double[] tone, int size) {
        for (int i = 0; i < size; i++) {
            double value1 = mAmplitude * Math.sin(mPhase);
            tone[i] = value1;

            mPhase += mPhaseIncrement;
            // insert glitches if mIsGlitchEnabled == true, and insert it for every second
            if (mIsGlitchEnabled && (mCount % mSamplingRate == 0)) {
                mPhase += mPhaseIncrement;
            }

            mCount++;

            if (mPhase >= Constant.TWO_PI) {
                mPhase -= Constant.TWO_PI;
            }
        }
    }


    @Override
    public void resetPhases() {
        mPhase = 0;
    }

}
