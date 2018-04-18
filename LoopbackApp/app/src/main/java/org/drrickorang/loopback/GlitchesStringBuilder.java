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

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;


/**
 * Creates a list of time intervals where glitches occurred.
 */

public class GlitchesStringBuilder {
    private static final String TAG = "GlitchesStringBuilder";

    private GlitchesStringBuilder() {
        // not instantiable
        throw new RuntimeException("not reachable");
    }

    public static String getGlitchString(int fftsamplingsize, int FFTOverlapSamples,
                                         int[] glitchesData, int samplingRate,
                                         boolean glitchingIntervalTooLong, int numberOfGlitches) {
        int newSamplesPerFFT = fftsamplingsize - FFTOverlapSamples;

        // the time span of new samples for a single FFT in ms
        double newSamplesInMs = ((double) newSamplesPerFFT / samplingRate) *
                                Constant.MILLIS_PER_SECOND;
        log("newSamplesInMs: " + Double.toString(newSamplesInMs));

        // the time span of all samples for a single FFT in ms
        double allSamplesInMs = ((double) fftsamplingsize / samplingRate) *
                                Constant.MILLIS_PER_SECOND;
        log("allSamplesInMs: " + Double.toString(allSamplesInMs));

        StringBuilder listOfGlitches = new StringBuilder();
        listOfGlitches.append("Total Glitching Interval too long: " +
                glitchingIntervalTooLong + "\n");
        listOfGlitches.append("Estimated number of glitches: " + numberOfGlitches + "\n");
        listOfGlitches.append("List of glitching intervals: \n");

        for (int i = 0; i < glitchesData.length; i++) {
            int timeInMs; // starting time of glitches
            //append the time of glitches to "listOfGlitches"
            timeInMs = (int) (glitchesData[i] * newSamplesInMs); // round down
            listOfGlitches.append(timeInMs + "~" + (timeInMs + (int) allSamplesInMs) + "ms\n");
        }

        return listOfGlitches.toString();
    }

    /** Generate String of Glitch Times in ms return separated. */
    public static String getGlitchStringForFile(int fftSamplingSize, int FFTOverlapSamples,
                                                int[] glitchesData, int samplingRate) {
        int newSamplesPerFFT = fftSamplingSize - FFTOverlapSamples;

        // the time span of new samples for a single FFT in ms
        double newSamplesInMs = ((double) newSamplesPerFFT / samplingRate) *
                Constant.MILLIS_PER_SECOND;

        StringBuilder listOfGlitches = new StringBuilder();

        for (int i = 0; i < glitchesData.length; i++) {
            int timeInMs; // starting time of glitches
            //append the time of glitches to "listOfGlitches"
            timeInMs = (int) (glitchesData[i] * newSamplesInMs); // round down
            listOfGlitches.append(timeInMs + "\n");
        }

        return listOfGlitches.toString();
    }

    /** Generate array of Glitch Times in ms */
    public static int[] getGlitchMilliseconds(int fftSamplingSize, int FFTOverlapSamples,
                                                int[] glitchesData, int samplingRate) {
        int[] glitchMilliseconds = new int[glitchesData.length];
        int newSamplesPerFFT = fftSamplingSize - FFTOverlapSamples;

        // the time span of new samples for a single FFT in ms
        double newSamplesInMs = ((double) newSamplesPerFFT / samplingRate) *
                Constant.MILLIS_PER_SECOND;

        for (int i = 0; i < glitchesData.length; i++) {
            glitchMilliseconds[i] = (int) (glitchesData[i] * newSamplesInMs); // round down
        }

        return glitchMilliseconds;
    }

    private static void log(String msg) {
        Log.v(TAG, msg);
    }

}
