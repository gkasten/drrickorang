package org.drrickorang.loopback;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

/**
 * Created by ninatai on 5/14/15.
 */
public class HistogramView extends View {
    private Paint mHistPaint;
    private Paint mTextPaint;
    private Paint mLinePaint;

    private static int[] mData;
    private static int mMaxLatency = 0;
    private static boolean mExceedRange = false;
    private int mBase = 10; //base of logarithm
    private int mNumberOfXLabel = 4;
    private int mTextSize = 30;
    private int mLineWidth = 3;

    public HistogramView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    // initiate once for optimization
    private void init() {
        mHistPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mHistPaint.setStyle(Paint.Style.FILL);
        mHistPaint.setColor(Color.BLUE);

        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setColor(Color.RED);
        mTextPaint.setTextSize(mTextSize);

        mLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mLinePaint.setColor(Color.BLACK);
        mLinePaint.setStrokeWidth(mLineWidth);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawColor(Color.GRAY);
        int arrayLength = mData.length;
        if (mData == null || arrayLength == 0) {
            return;
        }

        if (mMaxLatency != 0) {


            // the range of latencies that's going to be displayed on histogram
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

            // coordinate starts at (0,0), up to (right, bottom)
            int right = this.getRight();
            int bottom = this.getBottom();


            // calculate the max frequency among all latencies
            int maxLatencyFreq = 0;
            for (int i = 1; i < arrayLength; i++) {
                if (mData[i] > maxLatencyFreq) {
                    maxLatencyFreq = mData[i];
                }
            }


            if (maxLatencyFreq == 0) {
                return;
            }

            // find the closest order of 10 according to maxLatencyFreq
            int order = 0;
            while (Math.pow(mBase, order) < maxLatencyFreq) {
                order += 1;
            }
            float height =( (float) (bottom - mTextSize) / (order + 1)); // height for one decade





            // TODO add x labels
            int totalXLabel = mNumberOfXLabel + 1; // last label is for the last beam

            // y labels
            String[] yLabels = new String[order+2]; // will store {"0", "1", "10", "100", ...} for base = 10
            yLabels[0] = "0";
            canvas.drawText(yLabels[0], 0, bottom - mTextSize - mLineWidth, mTextPaint);
            int currentValue = 1;
            for (int i = 1; i <= (order + 1); i++)
            {
                yLabels[i] = Integer.toString(currentValue);
                // FIXME since no margin added, can't show the top y label (100) -> fixed. now it display lower by amount of textsize
                canvas.drawText(yLabels[i], 0, bottom - (i * height), mTextPaint);  // for the third argument,  + mTextSize - mTextSize cancels out
                currentValue *= 10;

            }
            // draw x axis
            canvas.drawLine(0, bottom - mTextSize, right, bottom - mTextSize, mLinePaint);

            // draw y axis
            int yMargin = getTextWidth(yLabels[order+1], mTextPaint);
            int extraYMargin = 5;
            canvas.drawLine(yMargin + extraYMargin, bottom, yMargin + extraYMargin, 0, mLinePaint);


            float width =  ((float) (right - yMargin - extraYMargin - mLineWidth) / range); // width of each beam in the histogram

            float currentLeft = yMargin + extraYMargin + mLineWidth; // FIXME there's an extra 1 pixel split, not sure why
            float currentTop;
            float currentRight;
            // TODO separate each beam
            // draw the histogram
            mData[0] = 1;
            mData[range-1] = 1;
            int currentBottom = bottom - mTextSize - mLineWidth;
            for (int i = 0; i < range; i++) {
                currentRight = currentLeft + width;
                // calculate the height of the beam
                if (mData[i] == 0) {
                    currentTop = currentBottom;
                } else {
                    float units = (float) ((Math.log10((double) mData[i])) + 1.0);
                    currentTop = currentBottom - (height * units);
                }

                canvas.drawRect(currentLeft, currentTop, currentRight, currentBottom, mHistPaint);
                currentLeft = currentRight;
            }


            



        }

    }

    // get the width of a certain string, using a certain paint
    public int getTextWidth(String text, Paint paint) {
        Rect bounds = new Rect();
        paint.getTextBounds(text, 0, text.length(), bounds);
        int width = bounds.left + bounds.width();
        return width;
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

