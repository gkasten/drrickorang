package org.drrickorang.loopback;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

/**
 * Created by ninatai on 5/11/15.
 */
public class AboutActivity extends Activity {
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String message = "Audio latency testing app using the Dr. Rick O'Rang audio loopback dongle.\n\n" +
                         "Author: Ricardo Garcia\n\n" +
                         "Open source project on: https://github.com/gkasten/drrickorang\n\n" +
                         "References: https://source.android.com/devices/audio/loopback.html\n" +
                         "https://source.android.com/devices/audio/latency_measure.html#loopback";

        // Create the text view
        TextView textView = new TextView(this);
        textView.setTextSize(20);
        textView.setText(message);

        // Set the text view as the activity layout
        setContentView(textView);
    }
}
