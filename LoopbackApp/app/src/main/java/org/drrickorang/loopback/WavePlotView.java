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
import android.os.Vibrator;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Scroller;


/**
 * This view is the wave plot shows on the main activity.
 */

public class WavePlotView extends View  {
    private static final String TAG = "WavePlotView";

    private double [] mBigDataArray;
    private double [] mValuesArray;  //top points to plot
    private double [] mValuesArray2; //bottom

    private double[]  mInsetArray;
    private double[]  mInsetArray2;
    private int       mInsetSize = 20;

    private double mZoomFactorX = 1.0; //1:1  1 sample / point .  Note: Point != pixel.
    private int    mCurrentOffset = 0;
    private int    mArraySize = 100; //default size
    private int    mSamplingRate;

    private GestureDetector        mDetector;
    private ScaleGestureDetector   mSGDetector;
    private MyScaleGestureListener mSGDListener;
    private Scroller mScroller;

    private int mWidth;
    private int mHeight;
    private boolean mHasDimensions;

    private Paint mMyPaint;
    private Paint mPaintZoomBox;
    private Paint mPaintInsetBackground;
    private Paint mPaintInsetBorder;
    private Paint mPaintInset;
    private Paint mPaintGrid;
    private Paint mPaintGridText;

    // Default values used when we don't have a valid waveform to display.
    // This saves us having to add multiple special cases to handle null waveforms.
    private int mDefaultSampleRate = 48000; // chosen because it is common in real world devices
    private double[] mDefaultDataVector = new double[mDefaultSampleRate]; // 1 second of fake audio

    public WavePlotView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mSGDListener = new MyScaleGestureListener();
        mDetector = new GestureDetector(context, new MyGestureListener());
        mSGDetector = new ScaleGestureDetector(context, mSGDListener);
        mScroller = new Scroller(context, new LinearInterpolator(), true);
        initPaints();

        // Initialize the value array to 1s silence
        mSamplingRate = mDefaultSampleRate;
        mBigDataArray = new double[mSamplingRate];
        Arrays.fill(mDefaultDataVector, 0);
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
        mHasDimensions = true;
        initView();
        refreshView();
    }


    private void initView() {
        //re init graphical elements
        mArraySize = mWidth;
        mInsetSize = mWidth / 5;
        mValuesArray = new double[mArraySize];
        mValuesArray2 = new double[mArraySize];
        Arrays.fill(mValuesArray, 0);
        Arrays.fill(mValuesArray2, 0);

        //inset
        mInsetArray = new double[mInsetSize];
        mInsetArray2 = new double[mInsetSize];
        Arrays.fill(mInsetArray, (double) 0);
        Arrays.fill(mInsetArray2, (double) 0);
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
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
            if (getZoom() >= 2) {
                for (i = 0; i < mArraySize; ++i) {
                    float top = (float) ((valueMax - mValuesArray[i]) / valueRange) * h;
                    float bottom = (float) ((valueMax - mValuesArray2[i]) / valueRange) * h + 1;
                    float left = i * deltaX;
                    canvas.drawRect(left, top, left + deltaX, bottom, mMyPaint);
                }
            } else {
                for (i = 0; i < (mArraySize - 1); ++i) {
                    float first = (float) ((valueMax - mValuesArray[i]) / valueRange) * h;
                    float second = (float) ((valueMax - mValuesArray[i + 1]) / valueRange) * h;
                    float left = i * deltaX;
                    canvas.drawLine(left, first, left + deltaX, second, mMyPaint);
                }
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

                for (i = 0; i < mInsetSize; ++i) {
                    float top = iY + (float) ((valueMax - mInsetArray[i]) / valueRange) * iH;
                    float bottom = iY +
                            (float) ((valueMax - mInsetArray2[i]) / valueRange) * iH + 1;
                    float left = iX + i * iDeltaX;
                    canvas.drawRect(left, top, left + deltaX, bottom, mPaintInset);
                }

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
        if (mScroller.computeScrollOffset()) {
            setOffset(mScroller.getCurrX(), false);
            refreshGraph();
        }
    }


    private void resetArray() {
        Arrays.fill(mValuesArray, 0);
        Arrays.fill(mValuesArray2, 0);
    }

    private void refreshView() {
        double maxZoom = getMaxZoomOut();
        setZoom(maxZoom);
        setOffset(0, false);
        computeInset();
        refreshGraph();
    }

    private void computeInset() {
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


    private void computeViewArray(double zoomFactorX, int sampleOffset) {
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


    // FIXME why not public?
    void setData(double[] dataVector, int sampleRate) {
        if (sampleRate < 1)
            throw new IllegalArgumentException("sampleRate must be a positive integer");

        mSamplingRate = sampleRate;
        mBigDataArray = (dataVector != null ? dataVector : mDefaultDataVector);

        if (mHasDimensions) { // only refresh the view if it has been initialized already
            refreshView();
        }
    }

    // also called in LoopbackActivity
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
        private boolean mInDrag = false;

        @Override
        public boolean onDown(MotionEvent event) {
            Log.d(DEBUG_TAG, "onDown: " + event.toString() + " " + TAG);
            if (!mScroller.isFinished()) {
                mScroller.forceFinished(true);
                refreshGraph();
            }
            return true;
        }


        @Override
        public boolean onFling(MotionEvent event1, MotionEvent event2,
                               float velocityX, float velocityY) {
            Log.d(DEBUG_TAG, "onFling: VelocityX: " + velocityX + "  velocityY:  " + velocityY);

            mScroller.fling(mCurrentOffset, 0,
                    (int) (-velocityX * getZoom()),
                    0, 0, mBigDataArray.length, 0, 0);
            refreshGraph();
            return true;
        }


        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            setOffset((int) (distanceX * getZoom()), true);
            refreshGraph();
            return super.onScroll(e1, e2, distanceX, distanceY);
        }

        @Override
        public boolean onDoubleTap(MotionEvent event) {
            Log.d(DEBUG_TAG, "onDoubleTap: " + event.toString());

            int tappedSample = (int) (event.getX() * getZoom());
            setZoom(getZoom() / 2);
            setOffset(tappedSample / 2, true);

            refreshGraph();
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            Vibrator vibe = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (vibe.hasVibrator()) {
                vibe.vibrate(20);
            }
            setZoom(getMaxZoomOut());
            setOffset(0, false);
            refreshGraph();
        }

    }   // MyGestureListener

    private class MyScaleGestureListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        //private static final String DEBUG_TAG = "MyScaleGestureListener";
        int focusSample = 0;

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            focusSample = (int) (detector.getFocusX() * getZoom()) + mCurrentOffset;
            return super.onScaleBegin(detector);
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            setZoom(getZoom() / detector.getScaleFactor());

            int newFocusSample = (int) (detector.getFocusX() * getZoom()) + mCurrentOffset;
            int sampleDelta = (int) (focusSample - newFocusSample);
            setOffset(sampleDelta, true);
            refreshGraph();
            return true;
        }

    }   // MyScaleGestureListener

    private static void log(String msg) {
        Log.v(TAG, msg);
    }

}
