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
    private Paint mXLabelPaint;

    private static int[] mData;
    private static int mMaxBufferPeriod = 0;
    private static boolean mExceedRange = false;
    private int mBase = 10; //base of logarithm
    private int mNumberOfXLabel = 4;
    private int mYLabelSize = 30;
    private int mXLabelSize = 22;
    private int mLineWidth = 3;
    private int mHistogramInterval = 2; // separate each beam in the histogram by such amount
    int mExtraYMargin = 5; // the extra margin between y labels and y-axis

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
        mTextPaint.setTextSize(mYLabelSize);

        mXLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mXLabelPaint.setColor(Color.BLACK);
        mXLabelPaint.setTextSize(mXLabelSize);

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

        if (mMaxBufferPeriod != 0) {

            // the range of latencies that's going to be displayed on histogram
            int range;
            if (mMaxBufferPeriod > arrayLength - 1) {
                range = arrayLength;
                mExceedRange = true;
            } else {
                range = mMaxBufferPeriod + 1;
                mExceedRange = false;
            }

            if (range == 0) {
                return;
            }

            // coordinate starts at (0,0), up to (right, bottom)
            int right = this.getRight();
            int bottom = this.getBottom();

            // calculate the max frequency among all latencies
            int maxBufferPeriodFreq = 0;
            for (int i = 1; i < arrayLength; i++) {
                if (mData[i] > maxBufferPeriodFreq) {
                    maxBufferPeriodFreq = mData[i];
                }
            }

            if (maxBufferPeriodFreq == 0) {
                return;
            }

            // find the closest order of "mBase" according to maxBufferPeriodFreq
            int order = 0;
            while (Math.pow(mBase, order) < maxBufferPeriodFreq) {
                order += 1;
            }
            float height =( (float) (bottom - mXLabelSize - mLineWidth) / (order + 1)); // height for one decade


            // y labels
            String[] yLabels = new String[order+2]; // will store {"0", "1", "10", "100", ...} for base = 10
            yLabels[0] = "0";
            int yStartPoint = bottom - mXLabelSize - mLineWidth;
            canvas.drawText(yLabels[0], 0, yStartPoint, mTextPaint);
            int currentValue = 1;
            for (int i = 1; i <= (order + 1); i++)
            {
                yLabels[i] = Integer.toString(currentValue);
                // Label is displayed at a height that's lower than it should be by the amount of "mYLabelSize"
                canvas.drawText(yLabels[i], 0, yStartPoint - (i * height) + mYLabelSize, mTextPaint);
                currentValue *= mBase;

            }


            // draw x axis
            canvas.drawLine(0, bottom - mXLabelSize, right, bottom - mXLabelSize, mLinePaint);

            // draw y axis
            int yMargin = getTextWidth(yLabels[order+1], mTextPaint);
            canvas.drawLine(yMargin + mExtraYMargin, bottom, yMargin + mExtraYMargin, 0, mLinePaint);

            // width of each beam in the histogram
            float width =  ((float) (right - yMargin - mExtraYMargin - mLineWidth - range * mHistogramInterval) / range);

            // draw x labels
            String[] xLabels = new String[mNumberOfXLabel];
            int xLabelInterval = (range - 2) / mNumberOfXLabel;
            xLabels[0] = "0";       // first label is for 0
            canvas.drawText(xLabels[0], yMargin - getTextWidth(xLabels[0], mXLabelPaint), bottom, mXLabelPaint);

            int xStartPoint = yMargin + mExtraYMargin + mLineWidth;  // position where first beam is placed on x-axis
            for (int i = 1; i < mNumberOfXLabel; i++) {
                xLabels[i] = Integer.toString(i * xLabelInterval);
                canvas.drawText(xLabels[i], xStartPoint + (xLabelInterval * i * (width + mHistogramInterval)), bottom, mXLabelPaint);
            }

            String lastXLabel;      // last label is for the last beam
            if (mExceedRange) {
                lastXLabel = Integer.toString(range - 1) + "+";
            } else {
                lastXLabel = Integer.toString(range - 1);
            }
            canvas.drawText(lastXLabel, right - getTextWidth(lastXLabel, mXLabelPaint) - 1, bottom, mXLabelPaint);


            // draw the histogram
            float currentLeft = yMargin + mExtraYMargin + mLineWidth; // FIXME there's an extra 1 pixel split, not sure why
            float currentTop;
            float currentRight;
            int currentBottom = bottom - mXLabelSize - mLineWidth;

            for (int i = 0; i < range; i++) {
                currentRight = currentLeft + width;

                // calculate the height of the beam
                if (mData[i] == 0) {
                    currentTop = currentBottom;
                } else {
                    float units = (float) ((Math.log10((double) mData[i])) + 1.0); // FIXME change it to have "mBase" as the baset
                    currentTop = currentBottom - (height * units);
                }

                canvas.drawRect(currentLeft, currentTop, currentRight, currentBottom, mHistPaint);
                currentLeft = currentRight + mHistogramInterval;
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
    public static void setBufferPeriodArray(int[] pData) {
        if (mData == null || pData.length != mData.length) {
            mData = new int[pData.length];
        }
        System.arraycopy(pData, 0, mData, 0, pData.length);
        // postInvalidate();
    }

    public static void setMaxBufferPeriod(int BufferPeriod) {
        mMaxBufferPeriod = BufferPeriod;
    }

}

