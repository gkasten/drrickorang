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
import android.util.Log;
import android.view.View;


/**
 * This is the histogram used to show recorder/player buffer period.
 */

public class HistogramView extends View {
    private static final String TAG = "HistogramView";


    private Paint mHistPaint;
    private Paint mTextPaint;
    private Paint mLinePaint;
    private Paint mXLabelPaint;

    private int[] mData; // data for buffer period
    private int[] mDisplayData; // modified data that is used to draw histogram
    private int mMaxBufferPeriod = 0;
    // number of x-axis labels excluding the last x-axis label
    private int mNumberOfXLabel = 5;  // mNumberOfXLabel must > 0

    private final int mYAxisBase = 10; // base of y-axis log scale
    private final int mYLabelSize = 40;
    private final int mXLabelSize = 40;
    private final int mLineWidth = 3;
    private final int mMaxNumberOfBeams = 202; // the max amount of beams to display on the screen

    // Note: if want to change this to base other than 10, must change the way x labels are
    // displayed. It's currently half-hardcoded.
    private final int mBucketBase = 10; // a bucket's range


    public HistogramView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaints();
    }


    /** Initiate all the Paint objects. */
    private void initPaints() {
        mHistPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mHistPaint.setStyle(Paint.Style.FILL);
        mHistPaint.setColor(Color.BLUE);

        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setColor(Color.BLACK);
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
        fillCanvas(canvas, this.getRight(), this.getBottom());
    }

    // also called in LoopbackActivity.java
    void fillCanvas(Canvas canvas, int right, int bottom) {
        canvas.drawColor(Color.GRAY);

        if (mData == null || mData.length == 0) {
            return;
        }

        int arrayLength = mData.length;
        boolean exceedBufferPeriodRange;
        if (mMaxBufferPeriod != 0) {
            final int extraYMargin = 5; // the extra margin between y labels and y-axis
            final int beamInterval = 2; // separate each beam in the histogram by such amount
            int range; // the number of beams that's going to be displayed on histogram
            if (mMaxBufferPeriod > arrayLength - 1) {
                range = arrayLength;
                exceedBufferPeriodRange = true;
            } else {
                range = mMaxBufferPeriod + 1;
                exceedBufferPeriodRange = false;
            }

            if (range == 0) {
                return;
            }

            boolean isUsingDisplayData = false;
            int oldRange = range;
            int interval = 1;

            // if there are more beams than allowed to be displayed on screen,
            // put beams into buckets
            if (range > mMaxNumberOfBeams) {
                isUsingDisplayData = true;
                int bucketOrder = 0;
                if (exceedBufferPeriodRange) { // there should be one extra beam for 101+
                    range -= 2;
                    while (range > mMaxNumberOfBeams - 2) {
                        range /= mBucketBase;
                        bucketOrder++;
                    }
                    range += 2; // assuming always XXX1+, not something like 0~473, 474+.

                } else {
                    range--;
                    int temp = range;
                    while (range > mMaxNumberOfBeams - 2) {
                        range /= mBucketBase;
                        bucketOrder++;
                    }

                    if ((temp % mBucketBase) != 0) {
                        range += 2;
                    } else {
                        range++;
                    }
                }

                interval = (int) Math.pow(mBucketBase, bucketOrder);
                mDisplayData = new int[mMaxNumberOfBeams];
                mDisplayData[0] = mData[0];

                // putting data into buckets.
                for (int i = 1; i < (range - 1); i++) {
                    for (int j = (((i - 1) * interval) + 1); (j <= (i * interval)); j++) {
                        mDisplayData[i] += mData[j];
                    }
                }

                if (exceedBufferPeriodRange) {
                    mDisplayData[range - 1] = mData[oldRange - 1];
                } else {
                    for (int i = (((range - 2) * interval) + 1); i < oldRange; i++) {
                        mDisplayData[range - 1] += mData[i];
                    }
                }
            } else {
                mDisplayData = mData;
            }

            // calculate the max frequency among all latencies
            int maxBufferPeriodFreq = 0;
            for (int i = 1; i < range; i++) {
                if (mDisplayData[i] > maxBufferPeriodFreq) {
                    maxBufferPeriodFreq = mDisplayData[i];
                }
            }

            if (maxBufferPeriodFreq == 0) {
                return;
            }

            // find the closest order of "mYAxisBase" according to maxBufferPeriodFreq
            int order = (int) Math.ceil((Math.log10(maxBufferPeriodFreq)) /
                        (Math.log10(mYAxisBase)));
            float height = ((float) (bottom - mXLabelSize - mLineWidth) / (order + 1));

            // y labels
            String[] yLabels = new String[order + 2]; // store {"0", "1", "10", ...} for base = 10
            yLabels[0] = "0";
            int yStartPoint = bottom - mXLabelSize - mLineWidth;
            canvas.drawText(yLabels[0], 0, yStartPoint, mTextPaint);
            int currentValue = 1;
            for (int i = 1; i < yLabels.length; i++) {
                yLabels[i] = Integer.toString(currentValue);
                // Label is displayed at lower than it should be by the amount of "mYLabelSize"
                canvas.drawText(yLabels[i], 0, yStartPoint - (i * height) + mYLabelSize,
                        mTextPaint);
                currentValue *= mYAxisBase;
            }

            // draw x axis
            canvas.drawLine(0, bottom - mXLabelSize, right, bottom - mXLabelSize, mLinePaint);

            // draw y axis
            int yMargin = getTextWidth(yLabels[order + 1], mTextPaint);
            canvas.drawLine(yMargin + extraYMargin, bottom, yMargin + extraYMargin,
                    0, mLinePaint);

            // width of each beam in the histogram
            float width = ((float) (right - yMargin - extraYMargin - mLineWidth -
                          (range * beamInterval)) / range);

            // draw x labels
            String lastXLabel;
            int xLabelInterval;
            int xStartPoint = yMargin + extraYMargin + mLineWidth;  // position of first beam
            String[] xLabels;

            // mNumberOfXLabel includes "0" but excludes the last label, which will be at last beam
            // if mNumberOfXLabel exceeds the total beams that's going to have, reduce its value
            if (mNumberOfXLabel - 1 > range - 2) {
                mNumberOfXLabel = range - 1;
            }

            //
            if (!isUsingDisplayData) { // in this case each beam represent one buffer period
                if ((range - 2) < mNumberOfXLabel) {
                    xLabelInterval = 1;
                } else {
                    xLabelInterval = (range - 2) / mNumberOfXLabel;
                }

                xLabels = new String[mNumberOfXLabel];
                xLabels[0] = "0";       // first label is for 0
                canvas.drawText(xLabels[0], yMargin + extraYMargin + mLineWidth, bottom,
                        mXLabelPaint);

                float xLabelLineStartX;
                float xLabelLineStartY;
                int xLabelLineLength = 10;
                for (int i = 1; i < mNumberOfXLabel; i++) {
                    xLabelLineStartX = xStartPoint +
                                       (xLabelInterval * i * (width + beamInterval));
                    xLabels[i] = Integer.toString(i * xLabelInterval);
                    canvas.drawText(xLabels[i], xLabelLineStartX, bottom, mXLabelPaint);

                    //add a vertical line to indicate label's corresponding beams
                    xLabelLineStartY = bottom - mXLabelSize;
                    canvas.drawLine(xLabelLineStartX, xLabelLineStartY, xLabelLineStartX,
                                    xLabelLineStartY - xLabelLineLength, mLinePaint);
                }

                // last label is for the last beam
                if (exceedBufferPeriodRange) {
                    lastXLabel = Integer.toString(range - 1) + "+";
                } else {
                    lastXLabel = Integer.toString(range - 1);
                }

                canvas.drawText(lastXLabel, right - getTextWidth(lastXLabel, mXLabelPaint) - 1,
                        bottom, mXLabelPaint);

            } else {    // in this case each beam represent a range of buffer period
                // if mNumberOfXLabel exceeds amount of beams, decrease mNumberOfXLabel
                if ((range - 2) < mNumberOfXLabel) {
                    xLabelInterval = 1;
                } else {
                    xLabelInterval = (range - 2) / mNumberOfXLabel;
                }

                xLabels = new String[mNumberOfXLabel];
                xLabels[0] = "0";       // first label is for 0ms
                canvas.drawText(xLabels[0], yMargin + extraYMargin + mLineWidth, bottom,
                        mXLabelPaint);

                // draw all the middle labels
                for (int i = 1; i < mNumberOfXLabel; i++) {
                    xLabels[i] = Integer.toString((i * xLabelInterval) - 1) + "1-" +
                                 Integer.toString(i * xLabelInterval) + "0";
                    canvas.drawText(xLabels[i], xStartPoint + (xLabelInterval * i *
                            (width + beamInterval)), bottom, mXLabelPaint);
                }

                // draw the last label for the last beam
                if (exceedBufferPeriodRange) {
                    lastXLabel = Integer.toString(oldRange - 1) + "+";
                } else {
                    if ((((range - 2) * interval) + 1) == oldRange - 1) {
                        lastXLabel = Integer.toString(oldRange - 1);
                    } else {
                        lastXLabel = Integer.toString(range - 2) + "1-" +
                                Integer.toString(oldRange - 1);
                    }
                }

                canvas.drawText(lastXLabel, right - getTextWidth(lastXLabel, mXLabelPaint) - 1,
                        bottom, mXLabelPaint);
            }

            // draw the histogram
            float currentLeft = yMargin + extraYMargin + mLineWidth;
            float currentTop;
            float currentRight;
            int currentBottom = bottom - mXLabelSize - mLineWidth;
            for (int i = 0; i < range; i++) {
                currentRight = currentLeft + width;
                // calculate the height of the beam. Skip drawing if mDisplayData[i] = 0
                if (mDisplayData[i] != 0) {
                    float units = (float) (((Math.log10((double) mDisplayData[i])) /
                            Math.log10(mYAxisBase)) + 1.0);
                    currentTop = currentBottom - (height * units);
                    canvas.drawRect(currentLeft, currentTop, currentRight,
                            currentBottom, mHistPaint);
                }

                currentLeft = currentRight + beamInterval;
            }

        }
    }


    /** get the width of "text" when using "paint". */
    public int getTextWidth(String text, Paint paint) {
        int width;
        Rect bounds = new Rect();
        paint.getTextBounds(text, 0, text.length(), bounds);
        width = bounds.left + bounds.width();
        return width;
    }

    /** Copy buffer period data into "mData" */
    public void setBufferPeriodArray(int[] data) {
        if (data == null) {
            return;
        }

        if (mData == null || data.length != mData.length) {
            mData = new int[data.length];
        }

        System.arraycopy(data, 0, mData, 0, data.length);
    }


    public void setMaxBufferPeriod(int ReadBufferPeriod) {
        mMaxBufferPeriod = ReadBufferPeriod;
    }


    private static void log(String msg) {
        Log.v(TAG, msg);
    }

}
