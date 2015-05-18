package org.drrickorang.loopback;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

/**
 * Created by ninatai on 5/13/15.
 */
public class BufferPeriodActivity extends Activity {

    private HistogramView mHistogramView;
    private TextView mTextView;

    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        View view = getLayoutInflater().inflate(R.layout.buffer_period_activity, null);
        setContentView(view);
        mTextView = (TextView) findViewById(R.id.histogramInfo);
        mHistogramView = (HistogramView) findViewById(R.id.viewHistogram);



    }
}
