/*
 * Copyright (C) 2016 The Android Open Source Project
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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.widget.LinearLayout.LayoutParams;

/**
 * Creates a heat map graphic for glitches and callback durations over the time period of the test
 * Instantiated view is used for displaying heat map on android device,  static methods can be used
 * without an instantiated view to draw graph on a canvas for use in exporting an image file
 */
public class GlitchAndCallbackHeatMapView extends View {

    private final BufferCallbackTimes mPlayerCallbackTimes;
    private final BufferCallbackTimes mRecorderCallbackTimes;
    private final int[] mGlitchTimes;
    private boolean mGlitchesExceededCapacity;
    private final int mTestDurationSeconds;
    private final String mTitle;

    private static final int MILLIS_PER_SECOND = 1000;
    private static final int SECONDS_PER_MINUTE = 60;
    private static final int MINUTES_PER_HOUR = 60;
    private static final int SECONDS_PER_HOUR = 3600;

    private static final int LABEL_SIZE = 36;
    private static final int TITLE_SIZE = 80;
    private static final int LINE_WIDTH = 5;
    private static final int INNER_MARGIN = 20;
    private static final int OUTER_MARGIN = 60;
    private static final int COLOR_LEGEND_AREA_WIDTH = 250;
    private static final int COLOR_LEGEND_WIDTH = 75;
    private static final int EXCEEDED_LEGEND_WIDTH = 150;
    private static final int MAX_DURATION_FOR_SECONDS_BUCKET = 240;
    private static final int NUM_X_AXIS_TICKS = 9;
    private static final int NUM_LEGEND_LABELS = 5;
    private static final int TICK_SIZE = 30;

    private static final int MAX_COLOR = 0xFF0D47A1; // Dark Blue
    private static final int START_COLOR = Color.WHITE;
    private static final float LOG_FACTOR = 2.0f; // >=1 Higher value creates a more linear curve

    public GlitchAndCallbackHeatMapView(Context context, BufferCallbackTimes recorderCallbackTimes,
                                        BufferCallbackTimes playerCallbackTimes, int[] glitchTimes,
                                        boolean glitchesExceededCapacity, int testDurationSeconds,
                                        String title) {
        super(context);

        mRecorderCallbackTimes = recorderCallbackTimes;
        mPlayerCallbackTimes = playerCallbackTimes;
        mGlitchTimes = glitchTimes;
        mGlitchesExceededCapacity = glitchesExceededCapacity;
        mTestDurationSeconds = testDurationSeconds;
        mTitle = title;

        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        setWillNotDraw(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Bitmap bmpResult = Bitmap.createBitmap(canvas.getHeight(), canvas.getWidth(),
                Bitmap.Config.ARGB_8888);
        // Provide rotated canvas to FillCanvas method
        Canvas tmpCanvas = new Canvas(bmpResult);
        fillCanvas(tmpCanvas, mRecorderCallbackTimes, mPlayerCallbackTimes, mGlitchTimes,
                mGlitchesExceededCapacity, mTestDurationSeconds, mTitle);
        tmpCanvas.translate(-1 * tmpCanvas.getWidth(), 0);
        tmpCanvas.rotate(-90, tmpCanvas.getWidth(), 0);
        // Display landscape oriented image on android device
        canvas.drawBitmap(bmpResult, tmpCanvas.getMatrix(), new Paint(Paint.ANTI_ALIAS_FLAG));
    }

    /**
     * Draw a heat map of callbacks and glitches for display on Android device or for export as png
     */
    public static void fillCanvas(final Canvas canvas,
                                  final BufferCallbackTimes recorderCallbackTimes,
                                  final BufferCallbackTimes playerCallbackTimes,
                                  final int[] glitchTimes, final boolean glitchesExceededCapacity,
                                  final int testDurationSeconds, final String title) {

        final Paint heatPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        heatPaint.setStyle(Paint.Style.FILL);

        final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(LABEL_SIZE);
        textPaint.setTextAlign(Paint.Align.CENTER);

        final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.BLACK);
        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setTextSize(TITLE_SIZE);

        final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.BLACK);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(LINE_WIDTH);

        final Paint colorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        colorPaint.setStyle(Paint.Style.STROKE);

        ColorInterpolator colorInter = new ColorInterpolator(START_COLOR, MAX_COLOR);

        Rect textBounds = new Rect();
        titlePaint.getTextBounds(title, 0, title.length(), textBounds);
        Rect titleArea = new Rect(0, OUTER_MARGIN, canvas.getWidth(),
                OUTER_MARGIN + textBounds.height());

        Rect bottomLegendArea = new Rect(0, canvas.getHeight() - LABEL_SIZE - OUTER_MARGIN,
                canvas.getWidth(), canvas.getHeight() - OUTER_MARGIN);

        int graphWidth = canvas.getWidth() - COLOR_LEGEND_AREA_WIDTH - OUTER_MARGIN * 3;
        int graphHeight = (bottomLegendArea.top - titleArea.bottom - OUTER_MARGIN * 3) / 2;

        Rect callbackHeatArea = new Rect(0, 0, graphWidth, graphHeight);
        callbackHeatArea.offsetTo(OUTER_MARGIN, titleArea.bottom + OUTER_MARGIN);

        Rect glitchHeatArea = new Rect(0, 0, graphWidth, graphHeight);
        glitchHeatArea.offsetTo(OUTER_MARGIN, callbackHeatArea.bottom + OUTER_MARGIN);

        final int bucketSize =
                testDurationSeconds < MAX_DURATION_FOR_SECONDS_BUCKET ? 1 : SECONDS_PER_MINUTE;

        String units = testDurationSeconds < MAX_DURATION_FOR_SECONDS_BUCKET ? "Second" : "Minute";
        String glitchLabel = "Glitches Per " + units;
        String callbackLabel = "Maximum Callback Duration(ms) Per " + units;

        // Create White background
        canvas.drawColor(Color.WHITE);

        // Label Graph
        canvas.drawText(title, titleArea.left + titleArea.width() / 2, titleArea.bottom,
                titlePaint);

        // Callback Graph /////////////
        // label callback graph
        Rect graphArea = new Rect(callbackHeatArea);
        graphArea.left += LABEL_SIZE + INNER_MARGIN;
        graphArea.bottom -= LABEL_SIZE;
        graphArea.top += LABEL_SIZE + INNER_MARGIN;
        canvas.drawText(callbackLabel, graphArea.left + graphArea.width() / 2,
                graphArea.top - INNER_MARGIN, textPaint);

        int labelX = graphArea.left - INNER_MARGIN;
        int labelY = graphArea.top + graphArea.height() / 4;
        canvas.save();
        canvas.rotate(-90, labelX, labelY);
        canvas.drawText("Recorder", labelX, labelY, textPaint);
        canvas.restore();
        labelY = graphArea.bottom - graphArea.height() / 4;
        canvas.save();
        canvas.rotate(-90, labelX, labelY);
        canvas.drawText("Player", labelX, labelY, textPaint);
        canvas.restore();

        // draw callback heat graph
        CallbackGraphData recorderData =
                new CallbackGraphData(recorderCallbackTimes, bucketSize, testDurationSeconds);
        CallbackGraphData playerData =
                new CallbackGraphData(playerCallbackTimes, bucketSize, testDurationSeconds);
        int maxCallbackValue = Math.max(recorderData.getMax(), playerData.getMax());

        drawHeatMap(canvas, recorderData.getBucketedCallbacks(), maxCallbackValue, colorInter,
                recorderCallbackTimes.isCapacityExceeded(), recorderData.getLastFilledIndex(),
                new Rect(graphArea.left + LINE_WIDTH, graphArea.top,
                        graphArea.right - LINE_WIDTH, graphArea.centerY()));
        drawHeatMap(canvas, playerData.getBucketedCallbacks(), maxCallbackValue, colorInter,
                playerCallbackTimes.isCapacityExceeded(), playerData.getLastFilledIndex(),
                new Rect(graphArea.left + LINE_WIDTH, graphArea.centerY(),
                        graphArea.right - LINE_WIDTH, graphArea.bottom));

        drawTimeTicks(canvas, testDurationSeconds, bucketSize, callbackHeatArea.bottom,
                graphArea.bottom, graphArea.left, graphArea.width(), textPaint, linePaint);

        // draw graph boarder
        canvas.drawRect(graphArea, linePaint);

        // Callback Legend //////////////
        if (maxCallbackValue > 0) {
            Rect legendArea = new Rect(graphArea);
            legendArea.left = graphArea.right + OUTER_MARGIN * 2;
            legendArea.right = legendArea.left + COLOR_LEGEND_WIDTH;
            drawColorLegend(canvas, maxCallbackValue, colorInter, linePaint, textPaint, legendArea);
        }


        // Glitch Graph /////////////
        // label Glitch graph
        graphArea.bottom = glitchHeatArea.bottom - LABEL_SIZE;
        graphArea.top = glitchHeatArea.top + LABEL_SIZE + INNER_MARGIN;
        canvas.drawText(glitchLabel, graphArea.left + graphArea.width() / 2,
                graphArea.top - INNER_MARGIN, textPaint);

        // draw glitch heat graph
        int[] bucketedGlitches = new int[(testDurationSeconds + bucketSize - 1) / bucketSize];
        int lastFilledGlitchBucket = bucketGlitches(glitchTimes, bucketSize * MILLIS_PER_SECOND,
                bucketedGlitches);
        int maxGlitchValue = 0;
        for (int totalGlitch : bucketedGlitches) {
            maxGlitchValue = Math.max(totalGlitch, maxGlitchValue);
        }
        drawHeatMap(canvas, bucketedGlitches, maxGlitchValue, colorInter,
                glitchesExceededCapacity, lastFilledGlitchBucket,
                new Rect(graphArea.left + LINE_WIDTH, graphArea.top,
                        graphArea.right - LINE_WIDTH, graphArea.bottom));

        drawTimeTicks(canvas, testDurationSeconds, bucketSize,
                graphArea.bottom + INNER_MARGIN + LABEL_SIZE, graphArea.bottom, graphArea.left,
                graphArea.width(), textPaint, linePaint);

        // draw graph border
        canvas.drawRect(graphArea, linePaint);

        // Callback Legend //////////////
        if (maxGlitchValue > 0) {
            Rect legendArea = new Rect(graphArea);
            legendArea.left = graphArea.right + OUTER_MARGIN * 2;
            legendArea.right = legendArea.left + COLOR_LEGEND_WIDTH;

            drawColorLegend(canvas, maxGlitchValue, colorInter, linePaint, textPaint, legendArea);
        }

        // Draw legend for exceeded capacity
        if (playerCallbackTimes.isCapacityExceeded() || recorderCallbackTimes.isCapacityExceeded()
                || glitchesExceededCapacity) {
            RectF exceededArea = new RectF(graphArea.left, bottomLegendArea.top,
                    graphArea.left + EXCEEDED_LEGEND_WIDTH, bottomLegendArea.bottom);
            drawExceededMarks(canvas, exceededArea);
            canvas.drawRect(exceededArea, linePaint);
            textPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(" = No Data Available, Recording Capacity Exceeded",
                    exceededArea.right + INNER_MARGIN, bottomLegendArea.bottom, textPaint);
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

    }

    /**
     * Find total number of glitches duration per minute or second
     * Returns index of last minute or second bucket with a recorded glitches
     */
    private static int bucketGlitches(int[] glitchTimes, int bucketSizeMS, int[] bucketedGlitches) {
        int bucketIndex = 0;

        for (int glitchMS : glitchTimes) {
            bucketIndex = glitchMS / bucketSizeMS;
            bucketedGlitches[bucketIndex]++;
        }

        return bucketIndex;
    }

    private static void drawHeatMap(Canvas canvas, int[] bucketedValues, int maxValue,
                                    ColorInterpolator colorInter, boolean capacityExceeded,
                                    int lastFilledIndex, Rect graphArea) {
        Paint colorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        colorPaint.setStyle(Paint.Style.FILL);
        float rectWidth = (float) graphArea.width() / bucketedValues.length;
        RectF colorRect = new RectF(graphArea.left, graphArea.top, graphArea.left + rectWidth,
                graphArea.bottom);

        // values are log scaled to a value between 0 and 1 using the following formula:
        // (log(value + 1 ) / log(max + 1))^2
        // Data is typically concentrated around the extreme high and low values,  This log scale
        // allows low values to still be visible and the exponent makes the curve slightly more
        // linear in order that the color gradients are still distinguishable

        float logMax = (float) Math.log(maxValue + 1);

        for (int i = 0; i <= lastFilledIndex; ++i) {
            colorPaint.setColor(colorInter.getInterColor(
                    (float) Math.pow((Math.log(bucketedValues[i] + 1) / logMax), LOG_FACTOR)));
            canvas.drawRect(colorRect, colorPaint);
            colorRect.offset(rectWidth, 0);
        }

        if (capacityExceeded) {
            colorRect.right = graphArea.right;
            drawExceededMarks(canvas, colorRect);
        }
    }

    private static void drawColorLegend(Canvas canvas, int maxValue, ColorInterpolator colorInter,
                                        Paint linePaint, Paint textPaint, Rect legendArea) {
        Paint colorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        colorPaint.setStyle(Paint.Style.STROKE);
        colorPaint.setStrokeWidth(1);
        textPaint.setTextAlign(Paint.Align.LEFT);

        float logMax = (float) Math.log(legendArea.height() + 1);
        for (int y = legendArea.bottom; y >= legendArea.top; --y) {
            float inter = (float) Math.pow(
                    (Math.log(legendArea.bottom - y + 1) / logMax), LOG_FACTOR);
            colorPaint.setColor(colorInter.getInterColor(inter));
            canvas.drawLine(legendArea.left, y, legendArea.right, y, colorPaint);
        }

        int tickSpacing = (maxValue + NUM_LEGEND_LABELS - 1) / NUM_LEGEND_LABELS;
        for (int i = 0; i < maxValue; i += tickSpacing) {
            float yPos = legendArea.bottom - (((float) i / maxValue) * legendArea.height());
            canvas.drawText(Integer.toString(i), legendArea.right + INNER_MARGIN,
                    yPos + LABEL_SIZE / 2, textPaint);
            canvas.drawLine(legendArea.right, yPos, legendArea.right - TICK_SIZE, yPos,
                    linePaint);
        }
        canvas.drawText(Integer.toString(maxValue), legendArea.right + INNER_MARGIN,
                legendArea.top + LABEL_SIZE / 2, textPaint);

        canvas.drawRect(legendArea, linePaint);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    private static void drawTimeTicks(Canvas canvas, int testDurationSeconds, int bucketSizeSeconds,
                                      int textYPos, int tickYPos, int startXPos, int width,
                                      Paint textPaint, Paint linePaint) {

        int secondsPerTick;

        if (bucketSizeSeconds == SECONDS_PER_MINUTE) {
            secondsPerTick = (((testDurationSeconds / SECONDS_PER_MINUTE) + NUM_X_AXIS_TICKS - 1) /
                    NUM_X_AXIS_TICKS) * SECONDS_PER_MINUTE;
        } else {
            secondsPerTick = (testDurationSeconds + NUM_X_AXIS_TICKS - 1) / NUM_X_AXIS_TICKS;
        }

        for (int seconds = 0; seconds <= testDurationSeconds - secondsPerTick;
             seconds += secondsPerTick) {
            float xPos = startXPos + (((float) seconds / testDurationSeconds) * width);

            if (bucketSizeSeconds == SECONDS_PER_MINUTE) {
                canvas.drawText(String.format("%dh:%02dm", seconds / SECONDS_PER_HOUR,
                                (seconds / SECONDS_PER_MINUTE) % MINUTES_PER_HOUR),
                        xPos, textYPos, textPaint);
            } else {
                canvas.drawText(String.format("%dm:%02ds", seconds / SECONDS_PER_MINUTE,
                                seconds % SECONDS_PER_MINUTE),
                        xPos, textYPos, textPaint);
            }

            canvas.drawLine(xPos, tickYPos, xPos, tickYPos - TICK_SIZE, linePaint);
        }

        //Draw total duration marking on right side of graph
        if (bucketSizeSeconds == SECONDS_PER_MINUTE) {
            canvas.drawText(
                    String.format("%dh:%02dm", testDurationSeconds / SECONDS_PER_HOUR,
                            (testDurationSeconds / SECONDS_PER_MINUTE) % MINUTES_PER_HOUR),
                    startXPos + width, textYPos, textPaint);
        } else {
            canvas.drawText(
                    String.format("%dm:%02ds", testDurationSeconds / SECONDS_PER_MINUTE,
                            testDurationSeconds % SECONDS_PER_MINUTE),
                    startXPos + width, textYPos, textPaint);
        }
    }

    /**
     * Draw hash marks across a given rectangle, used to indicate no data available for that
     * time period
     */
    private static void drawExceededMarks(Canvas canvas, RectF rect) {

        final float LINE_WIDTH = 8;
        final int STROKE_COLOR = Color.GRAY;
        final float STROKE_OFFSET = LINE_WIDTH * 3; //space between lines

        Paint strikePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strikePaint.setColor(STROKE_COLOR);
        strikePaint.setStyle(Paint.Style.STROKE);
        strikePaint.setStrokeWidth(LINE_WIDTH);

        canvas.save();
        canvas.clipRect(rect);

        float startY = rect.bottom + STROKE_OFFSET;
        float endY = rect.top - STROKE_OFFSET;
        float startX = rect.left - rect.height();  //creates a 45 degree angle
        float endX = rect.left;

        for (; startX < rect.right; startX += STROKE_OFFSET, endX += STROKE_OFFSET) {
            canvas.drawLine(startX, startY, endX, endY, strikePaint);
        }

        canvas.restore();
    }

    private static class CallbackGraphData {

        private int[] mBucketedCallbacks;
        private int mLastFilledIndex;

        /**
         * Fills buckets with maximum callback duration per minute or second
         */
        CallbackGraphData(BufferCallbackTimes callbackTimes, int bucketSizeSeconds,
                          int testDurationSeconds) {
            mBucketedCallbacks =
                    new int[(testDurationSeconds + bucketSizeSeconds - 1) / bucketSizeSeconds];
            int bucketSizeMS = bucketSizeSeconds * MILLIS_PER_SECOND;
            int bucketIndex = 0;
            for (BufferCallbackTimes.BufferCallback callback : callbackTimes) {

                bucketIndex = callback.timeStamp / bucketSizeMS;
                if (callback.callbackDuration > mBucketedCallbacks[bucketIndex]) {
                    mBucketedCallbacks[bucketIndex] = callback.callbackDuration;
                }

                // Original callback bucketing strategy, callbacks within a second/minute were added
                // together in attempt to capture total amount of lateness within a time period.
                // May become useful for debugging specific problems at some later date
                /*if (callback.callbackDuration > callbackTimes.getExpectedBufferPeriod()) {
                    bucketedCallbacks[bucketIndex] += callback.callbackDuration;
                }*/
            }
            mLastFilledIndex = bucketIndex;
        }

        public int getMax() {
            int maxCallbackValue = 0;
            for (int bucketValue : mBucketedCallbacks) {
                maxCallbackValue = Math.max(maxCallbackValue, bucketValue);
            }
            return maxCallbackValue;
        }

        public int[] getBucketedCallbacks() {
            return mBucketedCallbacks;
        }

        public int getLastFilledIndex() {
            return mLastFilledIndex;
        }
    }

    private static class ColorInterpolator {

        private final int mAlphaStart;
        private final int mAlphaRange;
        private final int mRedStart;
        private final int mRedRange;
        private final int mGreenStart;
        private final int mGreenRange;
        private final int mBlueStart;
        private final int mBlueRange;

        public ColorInterpolator(int startColor, int endColor) {
            mAlphaStart = Color.alpha(startColor);
            mAlphaRange = Color.alpha(endColor) - mAlphaStart;

            mRedStart = Color.red(startColor);
            mRedRange = Color.red(endColor) - mRedStart;

            mGreenStart = Color.green(startColor);
            mGreenRange = Color.green(endColor) - mGreenStart;

            mBlueStart = Color.blue(startColor);
            mBlueRange = Color.blue(endColor) - mBlueStart;
        }

        /**
         * Takes a float between 0 and 1 and returns a color int between mStartColor and mEndColor
         **/
        public int getInterColor(float input) {

            return Color.argb(
                    mAlphaStart + (int) (input * mAlphaRange),
                    mRedStart + (int) (input * mRedRange),
                    mGreenStart + (int) (input * mGreenRange),
                    mBlueStart + (int) (input * mBlueRange)
            );
        }
    }

}
