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
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

/**
 * Created by ninatai on 5/11/15.
 */
public class AboutActivity extends Activity {
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String message1 = "Audio latency testing app using the Dr. Rick O'Rang audio loopback dongle.\n\n" +
                          "Author: Ricardo Garcia and Tzu-Yin Tai\n\n" +
                          "Open source project on:\n";

        String message2 = "https://github.com/gkasten/drrickorang\n\n";
                          //"References:\n" +
                          //"https://source.android.com/devices/audio/loopback.html\n" +
                          //"https://source.android.com/devices/audio/latency_measure.html#loopback";

        // Create the text view
        //TextView textView = new TextView(this);
        //TextView t2 = (TextView) findViewById(R.id.text2);
        //t2.setTextSize(15);
        //t2.setMovementMethod(LinkMovementMethod.getInstance());

        //textView.setText(message1 + message2);
        TextView t3 = new TextView(this);
        t3.setTextSize(17);
        t3.setText(Html.fromHtml("Round-trip audio latency testing app" + "<br />" +
                                 "using the Dr. Rick O'Rang" + "<br />" +
                                 "audio loopback dongle." + "<br />" + "<br />" +
                                 "Authors: Ricardo Garcia and Tzu-Yin Tai" + "<br />" + "<br />" +
/*
                                 "Open source project on:" + "<br />" +
                                 "<a href=\"https://github.com/gkasten/drrickorang\">https://github.com/gkasten/drrickorang</a>" + "<br />" + "<br />" +
*/
                                 "References:" + "<br />" +

                                 "<a href=\"https://source.android.com/devices/audio/latency.html\">https://source.android.com/devices/audio/latency.html</a>" + "<br />" +
                                 "<a href=\"https://goo.gl/dxcw0d\">https://goog.gl/dxcw0d</a>"+
                                 "<br />" + "<br />"));
        t3.setMovementMethod(LinkMovementMethod.getInstance());

        // Set the text view as the activity layout
        setContentView(t3);
    }
}
