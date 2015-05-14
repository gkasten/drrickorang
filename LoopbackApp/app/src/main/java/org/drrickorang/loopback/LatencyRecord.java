package org.drrickorang.loopback;

/**
 * Created by ninatai on 5/12/15.
 */

import android.util.Log;

import org.drrickorang.loopback.LoopbackAudioThread.RecorderRunnable;

import java.util.HashMap;


public class LatencyRecord {
    private static long mPreviousTime = 0;
    private static long mCurrentTime;
    private static final int range = 1002;
    private static int mMaxLatency = 0;

    private static int[] mJavaLatency = new int[range];


    public static void collectLatency()  {
        mCurrentTime = System.nanoTime();
        // if = 0 it's the first time the thread runs, so don't record the interval
        if (mPreviousTime != 0 && mCurrentTime != 0) {
            long diffInNano = mCurrentTime - mPreviousTime;
            int diffInMilli = (int) Math.ceil(((double) (diffInNano / 1000))); // round up

            if (diffInMilli > mMaxLatency) {
                mMaxLatency = diffInMilli;
            }

            // from 0 ms to 1000 ms, plus a sum of all instances > 1000ms
            if (diffInMilli >= 0 && diffInMilli < (range - 1)) {
                mJavaLatency[diffInMilli] += 1;
            } else if (diffInMilli >= (range - 1)) {
                mJavaLatency[range-1] += 1;
            } else if (diffInMilli < 0) {
                // throw new IllegalLatencyException("Latency must be >= 0");
                errorLog("Having negative Latency.");
            }

        }

        mPreviousTime = mCurrentTime;
    }

    private static void errorLog(String msg) {
        Log.e("LatencyTracker", msg);
    }

    private static void log(String msg) {
        Log.v("LatencyTracker", msg);
    }

    public static class IllegalLatencyException extends Exception {

        public IllegalLatencyException(String message)
        {
            super(message);
        }
    }



}
