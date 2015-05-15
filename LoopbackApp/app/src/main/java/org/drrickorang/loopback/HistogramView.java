package org.drrickorang.loopback;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

/**
 * Created by ninatai on 5/14/15.
 */
public class HistogramView extends View {
    private Paint mPaint;
    private static int[] mData;
    private static int mMaxLatency = 0;
    private static boolean mExceedRange = false;

    public HistogramView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    // TODO when to call this? For optimization
    private void init() {
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(Color.BLUE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawColor(Color.GRAY);
        int arrayLength = mData.length;
        if (mData == null || arrayLength == 0) {
            return;
        }

        // coordinate starts at (0,0), up to (right, bottom)
        int right = this.getRight();
        int bottom = this.getBottom();

        // Rect rect1 = new Rect(0, 0, width, height);
        if (mMaxLatency != 0) {
            int range;
            if (mMaxLatency > arrayLength - 1) {
                range = arrayLength;
                mExceedRange = true;
            } else {
                range = mMaxLatency + 1;
                mExceedRange = false;
            }

            if (range == 0) {
                return;
            }
            float width =  ((float)right / range);

            int maxLatencyFreq = 0;
            for (int i = 1; i < arrayLength; i++) {
                if (mData[i] > maxLatencyFreq) {
                    maxLatencyFreq = mData[i];
                }
            }

            if (maxLatencyFreq == 0) {
                return;
            }
            float height =( (float)bottom / maxLatencyFreq);

            float currentLeft = 0;
            float currentTop = 0;
            float currentRight = 0;
            float currentBottom = bottom;
            for (int i = 0; i < arrayLength; i++) {
                currentRight = currentLeft + width;
                currentTop = bottom - (height * mData[i]);
                canvas.drawRect(currentLeft, currentTop , currentRight, currentBottom, mPaint);
                currentLeft = currentRight;
                //currentTop = currentBottom;
            }
        }
        int x = 1;
    }

    void redraw() {
        invalidate();
    }

    // Copy data into internal buffer
    public static void setLatencyArray(int[] pData) {
        if (mData == null || pData.length != mData.length) {
            mData = new int[pData.length];
        }
        System.arraycopy(pData, 0, mData, 0, pData.length);
        // postInvalidate();
    }

    public static void setMaxLatency(int latency) {
        mMaxLatency = latency;
    }

}

