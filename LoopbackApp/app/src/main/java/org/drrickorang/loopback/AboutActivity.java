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
        t3.setText(Html.fromHtml("Audio latency testing app using the Dr. Rick O'Rang audio loopback dongle." + "<br />" + "<br />" +
                                 "Author: Ricardo Garcia and Tzu-Yin Tai" + "<br />" + "<br />" +
                                 "Open source project on:" + "<br />" +
                                 "<a href=\"https://github.com/gkasten/drrickorang\">https://github.com/gkasten/drrickorang</a>" + "<br />" + "<br />" +
                                 "References:" + "<br />" +
                                 "<a href=\"https://source.android.com/devices/audio/loopback.html\">https://source.android.com/devices/audio/loopback.html</a>" + "<br />" + "<br />" +
                                 "<a href=\"https://source.android.com/devices/audio/latency_measure.html#loopback\">https://source.android.com/devices/audio/latency_measure.html#loopback</a>"+
                                 "<br />" + "<br />"));
        t3.setMovementMethod(LinkMovementMethod.getInstance());

        // Set the text view as the activity layout
        setContentView(t3);
    }
}
