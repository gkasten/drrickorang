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

import java.util.Arrays;


/**
 * This activity will display a histogram that shows the recorder's buffer period.
 */

public class RecorderBufferPeriodActivity extends Activity {
    private static final String TAG = "RecorderBufferPeriodActivity";


    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View view = getLayoutInflater().inflate(R.layout.recorder_buffer_period_activity, null);
        setContentView(view);
        HistogramView histogramView = (HistogramView) findViewById(R.id.viewReadHistogram);
        Bundle bundle = getIntent().getExtras();

        // setup the histogram
        int[] bufferTimeStampData = bundle.getIntArray("recorderBufferPeriodTimeStampArray");
        int[] bufferData = bundle.getIntArray("recorderBufferPeriodArray");
        int bufferDataMax = bundle.getInt("recorderBufferPeriodMax");
        histogramView.setBufferPeriodTimeStampArray(bufferTimeStampData);
        histogramView.setBufferPeriodArray(bufferData);
        histogramView.setMaxBufferPeriod(bufferDataMax);

        // do performance measurement if the there are buffer period data
        if (bufferData != null) {
            // this is the range of data that actually has values
            int usefulDataRange = Math.min(bufferDataMax + 1, bufferData.length);
            int[] usefulBufferData = Arrays.copyOfRange(bufferData, 0, usefulDataRange);
            int recorderBufferSize = bundle.getInt("recorderBufferSize");
            int samplingRate = bundle.getInt("samplingRate");
            PerformanceMeasurement measurement = new PerformanceMeasurement(recorderBufferSize,
                                                 samplingRate, usefulBufferData);
            measurement.measurePerformance();
        }

    }


    private static void log(String msg) {
        Log.v(TAG, msg);
    }

}
