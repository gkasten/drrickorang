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

/**
 * Creates a tone that can be injected (and then looped back) in the Latency test.
 * The generated tone is a sine wave whose amplitude linearly increases than decreases linearly,
 * that is it has a triangular window.
 */
public class RampedSineTone extends SineWaveTone {

    public RampedSineTone(int samplingRate, double frequency) {
        super(samplingRate, frequency);
        mAmplitude = Constant.LOOPBACK_AMPLITUDE;
    }

    /**
     * Modifies SineWaveTone by creating an ramp up in amplitude followed by an immediate ramp down
     */
    @Override
    public void generateTone(short[] tone, int size) {
        super.generateTone(tone, size);

        for (int i = 0; i < size; i++) {
            double factor;    // applied to the amplitude of the sine wave

            //for first half of sample amplitude is increasing hence i < size / 2
            if (i < size / 2) {
                factor = (i / (float) size) * 2;
            } else {
                factor = ((size - i) / (float) size) * 2;
            }
            tone[i] *= factor;
        }
    }

    /**
     * Modifies SineWaveTone by creating an ramp up in amplitude followed by an immediate ramp down
     */
    @Override
    public void generateTone(double[] tone, int size) {
        super.generateTone(tone, size);

        for (int i = 0; i < size; i++) {
            double factor;    // applied to the amplitude of the sine wave

            //for first half of sample amplitude is increasing hence i < size / 2
            if (i < size / 2) {
                factor = Constant.LOOPBACK_AMPLITUDE * i / size;
            } else {
                factor = Constant.LOOPBACK_AMPLITUDE * (size - i) / size;
            }
            tone[i] *= factor;
        }
    }

}
