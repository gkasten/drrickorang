package org.drrickorang.loopback;

import android.app.Activity;
import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import java.lang.reflect.AccessibleObject;

/**
 * Created by ninatai on 5/13/15.
 */
public class LatencyActivity extends Activity {

    private HistogramView mHistogramView;
    private TextView mTextView;

    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        View view = getLayoutInflater().inflate(R.layout.latency_activity, null);
        setContentView(view);
        mTextView = (TextView) findViewById(R.id.histogramInfo);
        mHistogramView = (HistogramView) findViewById(R.id.viewHistogram);



    }
}
