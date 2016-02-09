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
 * This class is used to generates different kinds of tones.
 */

public abstract class ToneGeneration {
    protected int     mSamplingRate;
    protected double  mAmplitude;  // this value should be from 0 to 1.0
    protected boolean mIsGlitchEnabled = false; // indicates we are inserting glitches or not


    public ToneGeneration(int samplingRate) {
        mSamplingRate = samplingRate;
    }


    /** Store samples into "tone". Value of samples are from -32768 to 32767. */
    public abstract void generateTone(short[] tone, int size);


    /**
     * Store samples into "tone". Value of samples are from -1.0 to 1.0.
     * This function is not supposed to be used to create tone that is going to pass
     * into AudioTrack.write() as it only takes in float.
     */
    public abstract void generateTone(double[] tone, int size);


    /** Reset all the phases to zero. */
    public abstract void resetPhases();


    /**
     * Set the value of mIsGlitchEnabled. If mIsGlitchEnabled == true, will insert glitches to
     * the generated tone.
     */
    public void setGlitchEnabled(boolean isGlitchEnabled) {
        mIsGlitchEnabled = isGlitchEnabled;
    }

    public void setAmplitude(double amplitude) {
        mAmplitude = amplitude;
    }

}
