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

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;


/**
 * This activity shows a list of time intervals where a glitch occurs.
 */

public class GlitchesActivity extends Activity {
    private static final String TAG = "GlitchesActivity";


    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View view = getLayoutInflater().inflate(R.layout.glitches_activity, null);
        setContentView(view);

        Bundle bundle = getIntent().getExtras();
        int FFTSamplingSize = bundle.getInt("FFTSamplingSize");
        int FFTOverlapSamples = bundle.getInt("FFTOverlapSamples");
        int[] glitchesData = bundle.getIntArray("glitchesArray");
        int samplingRate = bundle.getInt("samplingRate");
        boolean glitchingIntervalTooLong = bundle.getBoolean("glitchingIntervalTooLong");
        int newSamplesPerFFT = FFTSamplingSize - FFTOverlapSamples;
        int numberOfGlitches = bundle.getInt("numberOfGlitches");

        // the time span of new samples for a single FFT in ms
        double newSamplesInMs = ((double) newSamplesPerFFT / samplingRate) *
                                Constant.MILLIS_PER_SECOND;
        log("newSamplesInMs: " + Double.toString(newSamplesInMs));

        // the time span of all samples for a single FFT in ms
        double allSamplesInMs = ((double) FFTSamplingSize / samplingRate) *
                                Constant.MILLIS_PER_SECOND;
        log("allSamplesInMs: " + Double.toString(allSamplesInMs));

        StringBuilder listOfGlitches = new StringBuilder();
        listOfGlitches.append("Total Glitching Interval too long: " +
                glitchingIntervalTooLong + "\n");
        listOfGlitches.append("Estimated number of glitches: " + numberOfGlitches + "\n");
        listOfGlitches.append("List of glitching intervals: \n");

        int timeInMs; // starting time of glitches
        for (int i = 0; i < glitchesData.length; i++) {
            //log("glitchesData" + i + " :" + glitchesData[i]);
            if (glitchesData[i] > 0) {
                //append the time of glitches to "listOfGlitches"
                timeInMs = (int) ((glitchesData[i] - 1) * newSamplesInMs); // round down
                listOfGlitches.append(Integer.toString(timeInMs) + "~" +
                        Integer.toString(timeInMs + (int) allSamplesInMs) + "ms\n");
            }
        }



        // Set the textView
        TextView textView = (TextView) findViewById(R.id.GlitchesInfo);
        textView.setTextSize(12);
        textView.setText(listOfGlitches.toString());
    }


    private static void log(String msg) {
        Log.v(TAG, msg);
    }

}
