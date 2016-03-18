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
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

/**
 * Captures systrace, bugreport, and wav snippets. Capable of relieving capture requests from
 * multiple threads and maintains queue of most interesting records
 */
public class CaptureHolder {

    private static final String TAG = "CAPTURE";
    public static final String STORAGE = "/sdcard/";
    public static final String DIRECTORY = STORAGE + "Loopback";
    private static final String SIGNAL_FILE = DIRECTORY + "/loopback_signal";
    private static final String SYSTRACE_SUFFIX = ".trace";
    private static final String BUGREPORT_SUFFIX = "_bugreport.txt.gz";
    private static final String WAV_SUFFIX = ".wav";
    private static final String TERMINATE_SIGNAL = "QUIT";

    // Status codes returned by captureState
    public static final int NEW_CAPTURE_IS_LEAST_INTERESTING = -1;
    public static final int CAPTURE_ALREADY_IN_PROGRESS = 0;
    public static final int STATE_CAPTURED = 1;
    public static final int CAPTURING_DISABLED = 2;

    private final String mFileNamePrefix;
    private final long mStartTimeMS;
    private final boolean mIsCapturingWavs;
    private final boolean mIsCapturingSystraces;
    private final int mCaptureCapacity;
    private Thread mCaptureThread;
    private volatile CapturedState mCapturedStates[];
    private WaveDataRingBuffer mWaveDataBuffer;

    //for creating AudioFileOutput objects
    private final Context mContext;
    private final int mSamplingRate;

    public CaptureHolder(int captureCapacity, String fileNamePrefix, boolean captureWavs,
                         boolean captureSystraces, Context context, int samplingRate) {
        mCaptureCapacity = captureCapacity;
        mFileNamePrefix = fileNamePrefix;
        mIsCapturingWavs = captureWavs;
        mIsCapturingSystraces = captureSystraces;
        mStartTimeMS = System.currentTimeMillis();
        mCapturedStates = new CapturedState[mCaptureCapacity];
        mContext = context;
        mSamplingRate = samplingRate;
    }

    public void setWaveDataBuffer(WaveDataRingBuffer waveDataBuffer) {
        mWaveDataBuffer = waveDataBuffer;
    }

    /**
     * Launch thread to capture a systrace/bugreport and/or wav snippets and insert into collection
     * If capturing is not enabled or capture state thread is already running returns immediately
     * If newly requested capture is determined to be less interesting than all previous captures
     * returns without running capture thread
     *
     * Can be called from both GlitchDetectionThread and Sles/Java buffer callbacks.
     * Rank parameter and time of capture can be used by getIndexOfLeastInterestingCapture to
     * determine which records to delete when at capacity.
     * Therefore rank could represent glitchiness or callback behaviour and comparisons will need to
     * be adjusted based on testing priorities
     *
     * Please note if calling from audio thread could cause glitches to occur because of blocking on
     * this synchronized method.  Additionally capturing a systrace and bugreport and writing to
     * disk will likely have an affect on audio performance.
     */
    public synchronized int captureState(int rank) {

        if (!mIsCapturingWavs && !mIsCapturingSystraces) {
            Log.d(TAG, "captureState: Capturing wavs or systraces not enabled");
            return CAPTURING_DISABLED;
        }

        if (mCaptureThread != null && mCaptureThread.getState() != Thread.State.TERMINATED) {
            // Capture already in progress
            Log.d(TAG, "captureState: Capture thread already running");
            return CAPTURE_ALREADY_IN_PROGRESS;
        }

        long timeFromTestStartMS = System.currentTimeMillis() - mStartTimeMS;
        long hours = TimeUnit.MILLISECONDS.toHours(timeFromTestStartMS);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(timeFromTestStartMS) -
                TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(timeFromTestStartMS));
        long seconds = TimeUnit.MILLISECONDS.toSeconds(timeFromTestStartMS) -
                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(timeFromTestStartMS));
        String timeString = String.format("%02dh%02dm%02ds", hours, minutes, seconds);

        String fullFileNamePrefix = STORAGE + mFileNamePrefix + '_' + timeString;
        CapturedState cs = new CapturedState(fullFileNamePrefix, timeFromTestStartMS, rank);

        int indexOfLeastInteresting = getIndexOfLeastInterestingCapture(cs);
        if (indexOfLeastInteresting == NEW_CAPTURE_IS_LEAST_INTERESTING) {
            Log.d(TAG, "captureState: All Previously captured states were more interesting than" +
                    " requested capture");
            return NEW_CAPTURE_IS_LEAST_INTERESTING;
        }

        mCaptureThread = new CaptureThread(cs, indexOfLeastInteresting);
        mCaptureThread.start();

        return STATE_CAPTURED;
    }

    /**
     * Send signal to listener script to terminate and stop atrace
     **/
    public static void stopLoopbackListenerScript() {
        try {
            OutputStream outputStream = new FileOutputStream(SIGNAL_FILE);
            outputStream.write(TERMINATE_SIGNAL.getBytes());
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.d(TAG, "stopLoopbackListenerScript: Signaled Listener Script to exit");
    }

    /**
     * Currently returns recorded state with lowest Glitch count
     * Alternate criteria can be established here and in captureState rank parameter
     *
     * returns -1 (NEW_CAPTURE_IS_LEAST_INTERESTING) if candidate is least interesting, otherwise
     * returns index of record to replace
     */
    private int getIndexOfLeastInterestingCapture(CapturedState candidateCS) {
        CapturedState leastInteresting = candidateCS;
        int index = NEW_CAPTURE_IS_LEAST_INTERESTING;
        for (int i = 0; i < mCapturedStates.length; i++) {
            if (mCapturedStates[i] == null) {
                // Array is not yet at capacity, insert in next available position
                return i;
            }
            if (mCapturedStates[i].rank < leastInteresting.rank) {
                index = i;
                leastInteresting = mCapturedStates[i];
            }
        }
        return index;
    }

    public boolean isCapturingWavs() {
        return mIsCapturingWavs;
    }

    public boolean isCapturingSysTraces() {
        return mIsCapturingSystraces;
    }

    /**
     * Data struct for filenames of previously captured results. Rank and time captured can be used
     * for determining position in rolling queue
     */
    private class CapturedState {
        public final String fileNamePrefix;
        public final long timeFromStartOfTestMS;
        public final int rank;

        public CapturedState(String fileNamePrefix, long timeFromStartOfTestMS, int rank) {
            this.fileNamePrefix = fileNamePrefix;
            this.timeFromStartOfTestMS = timeFromStartOfTestMS;
            this.rank = rank;
        }

        @Override
        public String toString() {
            return "CapturedState { fileName:" + fileNamePrefix + ", Rank:" + rank + "}";
        }
    }

    private class CaptureThread extends Thread {

        private CapturedState mNewCapturedState;
        private int mIndexToPlace;

        /**
         * Create new thread with capture state sturct for captured systrace, bugreport and wav
         **/
        public CaptureThread(CapturedState cs, int indexToPlace) {
            mNewCapturedState = cs;
            mIndexToPlace = indexToPlace;
            setName("CaptureThread");
            setPriority(Thread.MIN_PRIORITY);
        }

        @Override
        public void run() {

            // Create two empty files and write that file name prefix to signal file, signalling
            // the listener script to write systrace and bugreport to those files
            if (mIsCapturingSystraces) {
                Log.d(TAG, "CaptureThread: signaling listener to write to:" +
                        mNewCapturedState.fileNamePrefix);
                try {
                    new File(mNewCapturedState.fileNamePrefix + SYSTRACE_SUFFIX).createNewFile();
                    new File(mNewCapturedState.fileNamePrefix + BUGREPORT_SUFFIX).createNewFile();
                    OutputStream outputStream = new FileOutputStream(SIGNAL_FILE);
                    outputStream.write(mNewCapturedState.fileNamePrefix.getBytes());
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            // Write wav if member mWaveDataBuffer has been set
            if (mIsCapturingWavs && mWaveDataBuffer != null) {
                Log.d(TAG, "CaptureThread: begin Writing wav data to file");
                WaveDataRingBuffer.ReadableWaveDeck deck = mWaveDataBuffer.getWaveDeck();
                if (deck != null) {
                    AudioFileOutput audioFile = new AudioFileOutput(mContext,
                            Uri.parse("file://mnt" + mNewCapturedState.fileNamePrefix
                                    + WAV_SUFFIX),
                            mSamplingRate);
                    boolean success = deck.writeToFile(audioFile);
                    Log.d(TAG, "CaptureThread: wav data written successfully: " + success);
                }
            }

            // Check for sys and bug finished
            // loopback listener script signals completion by deleting signal file
            if (mIsCapturingSystraces) {
                File signalFile = new File(SIGNAL_FILE);
                while (signalFile.exists()) {
                    try {
                        sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

            // Delete least interesting if necessary and insert new capture in list
            String suffixes[] = {SYSTRACE_SUFFIX, BUGREPORT_SUFFIX, WAV_SUFFIX};
            if (mCapturedStates[mIndexToPlace] != null) {
                Log.d(TAG, "Deleting capture: " + mCapturedStates[mIndexToPlace]);
                for (String suffix : suffixes) {
                    File oldFile = new File(mCapturedStates[mIndexToPlace].fileNamePrefix + suffix);
                    boolean deleted = oldFile.delete();
                    if (!deleted) {
                        Log.d(TAG, "Delete old capture: " + oldFile.toString() +
                                (oldFile.exists() ? " unable to delete" : " was not present"));
                    }
                }
            }
            Log.d(TAG, "Adding capture to list: " + mNewCapturedState);
            mCapturedStates[mIndexToPlace] = mNewCapturedState;

            // Log captured states
            String log = "Captured states:";
            for (CapturedState cs:mCapturedStates) log += "\n...." + cs;
            Log.d(TAG, log);

            Log.d(TAG, "Completed capture thread terminating");
        }
    }
}
