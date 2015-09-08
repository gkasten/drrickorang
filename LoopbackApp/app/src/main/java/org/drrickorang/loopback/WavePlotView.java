/*
 * Copyright (C) 2014 The Android Open Source Project
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

import java.util.Arrays;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Paint.Style;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;


/**
 * This view is the wave plot shows on the main activity.
 */

public class WavePlotView extends View  {
    private static final String TAG = "WavePlotView";

    private double [] mBigDataArray;
    private double [] mValuesArray;  //top points to plot
    private double [] mValuesArray2; //bottom

    private double [] mInsetArray;
    private double [] mInsetArray2;
    private int       mInsetSize = 20;

    private double mZoomFactorX = 1.0; //1:1  1 sample / point .  Note: Point != pixel.
    private int    mCurrentOffset = 0;
    private int    mArraySize = 100; //default size
    private int    mSamplingRate;

    private GestureDetector        mDetector;
    private ScaleGestureDetector   mSGDetector;
    private MyScaleGestureListener mSGDListener;

    private int mWidth;
    private int mHeight;

    private Paint mMyPaint;
    private Paint mPaintZoomBox;
    private Paint mPaintInsetBackground;
    private Paint mPaintInsetBorder;
    private Paint mPaintInset;
    private Paint mPaintGrid;
    private Paint mPaintGridText;

    public WavePlotView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mSGDListener = new MyScaleGestureListener();
        mDetector = new GestureDetector(context, new MyGestureListener());
        mSGDetector = new ScaleGestureDetector(context, mSGDListener);
        initPaints();
    }


    /** Initiate all the Paint objects. */
    private void initPaints() {
        final int COLOR_WAVE = 0xFF1E4A99;
        final int COLOR_ZOOM_BOX = 0X50E0E619;
        final int COLOR_INSET_BACKGROUND = 0xFFFFFFFF;
        final int COLOR_INSET_BORDER = 0xFF002260;
        final int COLOR_INSET_WAVE = 0xFF910000;
        final int COLOR_GRID = 0x7F002260;
        final int COLOR_GRID_TEXT = 0xFF002260;

        mMyPaint = new Paint();
        mMyPaint.setColor(COLOR_WAVE);
        mMyPaint.setAntiAlias(true);
        mMyPaint.setStyle(Style.FILL_AND_STROKE);
        mMyPaint.setStrokeWidth(1);

        mPaintZoomBox = new Paint();
        mPaintZoomBox.setColor(COLOR_ZOOM_BOX);
        mPaintZoomBox.setAntiAlias(true);
        mPaintZoomBox.setStyle(Style.FILL);

        mPaintInsetBackground = new Paint();
        mPaintInsetBackground.setColor(COLOR_INSET_BACKGROUND);
        mPaintInsetBackground.setAntiAlias(true);
        mPaintInsetBackground.setStyle(Style.FILL);

        mPaintInsetBorder = new Paint();
        mPaintInsetBorder.setColor(COLOR_INSET_BORDER);
        mPaintInsetBorder.setAntiAlias(true);
        mPaintInsetBorder.setStyle(Style.STROKE);
        mPaintInsetBorder.setStrokeWidth(1);

        mPaintInset = new Paint();
        mPaintInset.setColor(COLOR_INSET_WAVE);
        mPaintInset.setAntiAlias(true);
        mPaintInset.setStyle(Style.FILL_AND_STROKE);
        mPaintInset.setStrokeWidth(1);

        final int textSize = 25;
        mPaintGrid = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintGrid.setColor(COLOR_GRID); //gray
        mPaintGrid.setTextSize(textSize);

        mPaintGridText = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintGridText.setColor(COLOR_GRID_TEXT); //BLACKgray
        mPaintGridText.setTextSize(textSize);
    }


    /** Must call this function to set mSamplingRate before plotting the wave. */
    public void setSamplingRate(int samplingRate) {
        mSamplingRate = samplingRate;
    }


    public double getZoom() {
        return mZoomFactorX;
    }


    /** Return max zoom out value (> 1.0)/ */
    public double getMaxZoomOut() {
        double maxZoom = 1.0;

        if (mBigDataArray != null) {
            int n = mBigDataArray.length;
            maxZoom = ((double) n) / mArraySize;
        }

        return maxZoom;
    }


    public double getMinZoomOut() {
        double minZoom = 1.0;
        return minZoom;
    }


    public int getOffset() {
        return mCurrentOffset;
    }


    public void setZoom(double zoom) {
        double newZoom = zoom;
        double maxZoom = getMaxZoomOut();
        double minZoom = getMinZoomOut();

        //foolproof:
        if (newZoom < minZoom)
            newZoom = minZoom;

        if (newZoom > maxZoom)
            newZoom = maxZoom;

        mZoomFactorX = newZoom;
        //fix offset if this is the case
        setOffset(0, true); //just touch offset in case it needs to be fixed.
    }


    public void setOffset(int sampleOffset, boolean relative) {
        int newOffset = sampleOffset;

        if (relative) {
            newOffset = mCurrentOffset + sampleOffset;
        }

        if (mBigDataArray != null) {
            int n = mBigDataArray.length;
            //update offset if last sample is more than expected
            int lastSample = newOffset + (int)getWindowSamples();
            if (lastSample >= n) {
                int delta = lastSample - n;
                newOffset -= delta;
            }

            if (newOffset < 0)
                newOffset = 0;

            if (newOffset >= n)
                newOffset = n - 1;

            mCurrentOffset = newOffset;
        }
    }


    public double getWindowSamples() {
        //samples in current window
        double samples = 0;
        if (mBigDataArray != null) {
            double zoomFactor = getZoom();
            samples = mArraySize * zoomFactor;
        }

        return samples;
    }


    public void refreshGraph() {
        computeViewArray(mZoomFactorX, mCurrentOffset);
    }


    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mWidth = w;
        mHeight = h;
        log("New w: " + mWidth + " h: " + mHeight);
        initView();
    }


    private void initView() {
        //re init graphical elements
        mArraySize = mWidth;
        mInsetSize = mWidth / 5;
        mValuesArray = new double[mArraySize];
        mValuesArray2 = new double[mArraySize];
        int i;

        for (i = 0; i < mArraySize; i++) {
            mValuesArray[i] = 0;
            mValuesArray2[i] = 0;
        }

        //inset
        mInsetArray = new double[mInsetSize];
        mInsetArray2 = new double[mInsetSize];
        Arrays.fill(mInsetArray, (double) 0);
        Arrays.fill(mInsetArray2, (double) 0);
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        boolean showZoomBox = mSGDListener.mIsScaling;
        boolean showGrid = true;
        boolean showInset = true;

        int i;
        int w = getWidth();
        int h = getHeight();

        double valueMax = 1.0;
        double valueMin = -1.0;
        double valueRange = valueMax - valueMin;

        //print gridline time in ms/seconds, etc.
        if (showGrid) {
            //current number of samples in display
            double samples = getWindowSamples();
            if (samples > 0.0 && mSamplingRate > 0) {
                double windowMs = (1000.0 * samples) / mSamplingRate;

                //decide the best units: ms, 10ms, 100ms, 1 sec, 2 sec
                double msPerDivision = windowMs / 10;
                log(" windowMS: " + windowMs + " msPerdivision: " + msPerDivision);

                int divisionInMS = 1;
                //find the best level for markings:
                if (msPerDivision <= 5) {
                    divisionInMS = 1;
                } else if (msPerDivision < 15) {
                    divisionInMS = 10;
                } else if (msPerDivision < 30) {
                    divisionInMS = 20;
                } else if (msPerDivision < 60) {
                    divisionInMS = 40;
                } else if (msPerDivision < 150) {
                    divisionInMS = 100;
                } else if (msPerDivision < 400) {
                    divisionInMS = 200;
                } else if (msPerDivision < 750) {
                    divisionInMS = 500;
                } else {
                    divisionInMS = 1000;
                }
                log(" chosen Division in MS: " + divisionInMS);

                //current offset in samples
                int currentOffsetSamples = getOffset();
                double currentOffsetMs = (1000.0 * currentOffsetSamples) / mSamplingRate;
                int gridCount = (int) ((currentOffsetMs + divisionInMS) / divisionInMS);
                double startGridCountFrac = ((currentOffsetMs) % divisionInMS);
                log(" gridCount:" + gridCount + " fraction: " + startGridCountFrac +
                    "  firstDivision: " + gridCount * divisionInMS);

                double currentGridMs = divisionInMS - startGridCountFrac; //in mS
                while (currentGridMs <= windowMs) {
                    float newX = (float) (w * currentGridMs / windowMs);
                    canvas.drawLine(newX, 0, newX, h, mPaintGrid);

                    double currentGridValueMS = gridCount * divisionInMS;
                    String label = String.format("%.0f ms", (float) currentGridValueMS);

                    //path
                    Path myPath = new Path();
                    myPath.moveTo(newX, h);
                    myPath.lineTo(newX, h / 2);

                    canvas.drawTextOnPath(label, myPath, 10, -3, mPaintGridText);

                    //advance
                    currentGridMs += divisionInMS;
                    gridCount++;
                }

                //horizontal line
                canvas.drawLine(0, h / 2, w, h / 2, mPaintGrid);
            }
        }

        float deltaX = (float) w / mArraySize;

        //top
        Path myPath = new Path();
        myPath.moveTo(0, h / 2); //start

        if (mBigDataArray != null) {
            for (i = 0; i < mArraySize; i++) {
                double value = mValuesArray[i];
                double valueScaled = (valueMax - value) / valueRange;
                float newX = i * deltaX;
                float newY = (float) (valueScaled * h);
                myPath.lineTo(newX, newY);
            }

            //bottom
            for (i = mArraySize - 1; i >= 0; i--) {
                double value = mValuesArray2[i];
                double valueScaled = (valueMax - value) / valueRange;
                float newX = i * deltaX;
                float newY = (float) (valueScaled * h);
                myPath.lineTo(newX, newY);
            }
            //close
            myPath.close();
            canvas.drawPath(myPath, mMyPaint);


            if (showZoomBox) {
                float x1 = (float) mSGDListener.mX1;
                float x2 = (float) mSGDListener.mX2;
                canvas.drawRect(x1, 0, x2, h, mPaintZoomBox);
            }

            if (showInset) {
                float iW = (float) (w * 0.2);
                float iH = (float) (h * 0.2);
                float iX = (float) (w * 0.7);
                float iY = (float) (h * 0.1);
                //x, y of inset
                canvas.drawRect(iX, iY, iX + iW, iY + iH, mPaintInsetBackground);
                canvas.drawRect(iX - 1, iY - 1, iX + iW + 2, iY + iH + 2, mPaintInsetBorder);
                //paintInset
                float iDeltaX = (float) iW / mInsetSize;

                //top
                Path iPath = new Path();
                iPath.moveTo(iX, iY + (iH / 2)); //start

                for (i = 0; i < mInsetSize; i++) {
                    double value = mInsetArray[i];
                    double valueScaled = (valueMax - value) / valueRange;
                    float newX = iX + (i * iDeltaX);
                    float newY = iY + (float) (valueScaled * iH);
                    iPath.lineTo(newX, newY);
                }

                //bottom
                for (i = mInsetSize - 1; i >= 0; i--) {
                    double value = mInsetArray2[i];
                    double valueScaled = (valueMax - value) / valueRange;
                    float newX = iX + i * iDeltaX;
                    float newY = iY + (float) (valueScaled * iH);
                    iPath.lineTo(newX, newY);
                }

                //close
                iPath.close();
                canvas.drawPath(iPath, mPaintInset);

                if (mBigDataArray != null) {
                    //paint current region of zoom
                    int offsetSamples = getOffset();
                    double windowSamples = getWindowSamples();
                    int samples = mBigDataArray.length;

                    if (samples > 0) {
                        float x1 = (float) (iW * offsetSamples / samples);
                        float x2 = (float) (iW * (offsetSamples + windowSamples) / samples);

                        canvas.drawRect(iX + x1, iY, iX + x2, iY + iH, mPaintZoomBox);
                    }
                }
            }
        }
    }


    void resetArray() {
        Arrays.fill(mValuesArray, 0);
        Arrays.fill(mValuesArray2, 0);
    }


    void computeInset() {
        if (mBigDataArray != null) {
            int sampleCount = mBigDataArray.length;
            double pointsPerSample = (double) mInsetSize / sampleCount;

            Arrays.fill(mInsetArray, 0);
            Arrays.fill(mInsetArray2, 0);

            double currentIndex = 0; //points.
            double max = -1.0;
            double min = 1.0;
            double maxAbs = 0.0;
            int index = 0;

            for (int i = 0; i < sampleCount; i++) {
                double value = mBigDataArray[i];
                if (value > max) {
                    max = value;
                }

                if (value < min) {
                    min = value;
                }

                int prevIndexInt = (int) currentIndex;
                currentIndex += pointsPerSample;
                if ((int) currentIndex > prevIndexInt) { //it switched, time to decide
                    mInsetArray[index] = max;
                    mInsetArray2[index] = min;

                    if (Math.abs(max) > maxAbs) maxAbs = Math.abs(max);
                    if (Math.abs(min) > maxAbs) maxAbs = Math.abs(min);

                    max = -1.0;
                    min = 1.0;
                    index++;
                }

                if (index >= mInsetSize)
                    break;
            }

            //now, normalize
            if (maxAbs > 0) {
                for (int i = 0; i < mInsetSize; i++) {
                    mInsetArray[i] /= maxAbs;
                    mInsetArray2[i] /= maxAbs;

                }
            }

        }
    }


    void computeViewArray(double zoomFactorX, int sampleOffset) {
        //zoom factor: how many samples per point. 1.0 = 1.0 samples per point
        // sample offset in samples.
        if (zoomFactorX < 1.0)
            zoomFactorX = 1.0;

        if (mBigDataArray != null) {
            int sampleCount = mBigDataArray.length;
            double samplesPerPoint = zoomFactorX;
            double pointsPerSample = 1.0 / samplesPerPoint;

            resetArray();

            double currentIndex = 0; //points.
            double max = -1.0;
            double min = 1.0;
            int index = 0;

            for (int i = sampleOffset; i < sampleCount; i++) {

                double value = mBigDataArray[i];
                if (value > max) {
                    max = value;
                }

                if (value < min) {
                    min = value;
                }

                int prevIndexInt = (int) currentIndex;
                currentIndex += pointsPerSample;
                if ((int) currentIndex > prevIndexInt) { //it switched, time to decide
                    mValuesArray[index] = max;
                    mValuesArray2[index] = min;

                    max = -1.0;
                    min = 1.0;
                    index++;
                }

                if (index >= mArraySize)
                    break;
            }
        } //big data array not null

        redraw();
    }


    void setData(double [] dataVector) {
        mBigDataArray = dataVector;
        double maxZoom = getMaxZoomOut();
        setZoom(maxZoom);
        setOffset(0, false);
        computeInset();
        refreshGraph();
    }


    void redraw() {
        invalidate();
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mDetector.onTouchEvent(event);
        mSGDetector.onTouchEvent(event);
        //return super.onTouchEvent(event);
        return true;
    }


    class MyGestureListener extends GestureDetector.SimpleOnGestureListener {
        private static final String DEBUG_TAG = "MyGestureListener";


        @Override
        public boolean onDown(MotionEvent event) {
            Log.d(DEBUG_TAG, "onDown: " + event.toString() + " " + TAG);
            return true;
        }


        @Override
        public boolean onFling(MotionEvent event1, MotionEvent event2,
                               float velocityX, float velocityY) {
            Log.d(DEBUG_TAG, "onFling: VelocityX: " + velocityX + "  velocityY:  " + velocityY);

            //velocityX positive left to right
            // negative: right to left
            //double offset = getZoom()

            double samplesPerWindow = mArraySize * getZoom();
            int maxPixelsPerWindow = 8000;
            double offsetFactor = -(double) (velocityX / maxPixelsPerWindow);
            double offset = (samplesPerWindow * offsetFactor / 3.0);
            Log.d(DEBUG_TAG, " VELOCITY: " + velocityX + " samples/window = " + samplesPerWindow +
                    " offsetFactor = " + offsetFactor + "  offset: " + offset);

            setOffset((int) offset, true);
            refreshGraph();
            return true;
        }


        @Override
        public boolean onDoubleTap(MotionEvent event) {
            Log.d(DEBUG_TAG, "onDoubleTap: " + event.toString());

            setZoom(100000);
            setOffset(0, false);
            refreshGraph();
            return true;
        }
    }


    private class MyScaleGestureListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        private static final String DEBUG_TAG = "MyScaleGestureListener";
        public boolean mIsScaling = false;
        public double mX1 = 0;
        public double mX2 = 0;


        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            mIsScaling = true;
            return super.onScaleBegin(detector);
        }


        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            mIsScaling = false;
            //now zoom
            {
                int w = getWidth();
                //int h = getHeight();

                //double currentSpan = detector.getCurrentSpan();
                double currentSpanX = detector.getCurrentSpanX();
                //double currentSpanY = detector.getCurrentSpanY();
                double focusX = detector.getFocusX();
                //double focusY = detector.getFocusY();
                //double scaleFactor = detector.getScaleFactor();

                //estimated X1, X2
                double x1 = focusX - (currentSpanX / 2);
                double x2 = focusX + (currentSpanX / 2);
                //double x1clip = x1 < 0 ? 0 : (x1 > w ? w : x1);
                //double x2clip = x2 < 0 ? 0 : (x2 > w ? w : x2);

                //int originalOffset = getOffset();
                double windowSamplesOriginal = getWindowSamples(); //samples in current window
                double currentZoom = getZoom();
                double windowFactor = Math.abs(mX2 - mX1) / w;

                double newZoom = currentZoom * windowFactor;
                setZoom(newZoom);
                int newOffset = (int) (windowSamplesOriginal * mX1 / w);
                setOffset(newOffset, true); //relative

            }
            refreshGraph();
        }


        @Override
        public boolean onScale(ScaleGestureDetector detector) {

            int w = getWidth();
            //int h = getHeight();
            //double currentSpan = detector.getCurrentSpan();
            double currentSpanX = detector.getCurrentSpanX();
            //double currentSpanY = detector.getCurrentSpanY();
            double focusX = detector.getFocusX();
            //double focusY = detector.getFocusY();
            //double scaleFactor = detector.getScaleFactor();

            //estimated X1, X2
            double x1 = focusX - (currentSpanX / 2);
            double x2 = focusX + (currentSpanX / 2);
            double x1clip = x1 < 0 ? 0 : (x1 > w ? w : x1);
            double x2clip = x2 < 0 ? 0 : (x2 > w ? w : x2);
            mX1 = x1clip;
            mX2 = x2clip;
            refreshGraph();
            return true;
        }
    }


    private static void log(String msg) {
        Log.v(TAG, msg);
    }

}
