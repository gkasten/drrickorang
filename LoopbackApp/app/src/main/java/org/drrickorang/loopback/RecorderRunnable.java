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
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.os.Build;
import android.util.Log;

/**
 * This thread records incoming sound samples (uses AudioRecord).
 */

public class RecorderRunnable implements Runnable {
    private static final String TAG = "RecorderRunnable";

    private AudioRecord         mRecorder;
    private boolean             mIsRunning;
    private boolean             mIsRecording = false;
    private static final Object sRecordingLock = new Object();

    private final LoopbackAudioThread mAudioThread;
    // This is the pipe that connects the player and the recorder in latency test.
    private final PipeShort           mLatencyTestPipeShort;
    // This is the pipe that is used in buffer test to send data to GlitchDetectionThread
    private PipeShort                 mBufferTestPipeShort;

    private boolean   mIsRequestStop = false;
    private final int mTestType;    // latency test or buffer test
    private final int mSelectedRecordSource;
    private final int mSamplingRate;

    private int       mChannelConfig = AudioFormat.CHANNEL_IN_MONO;
    private int       mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private int       mMinRecorderBuffSizeInBytes = 0;
    private int       mMinRecorderBuffSizeInSamples = 0;

    private short[] mAudioShortArray;   // this array stores values from mAudioTone in read()
    private short[] mBufferTestShortArray;
    private short[] mAudioTone;

    // for glitch detection (buffer test)
    private BufferPeriod          mRecorderBufferPeriodInRecorder;
    private final int             mBufferTestWavePlotDurationInSeconds;
    private final int             mChannelIndex;
    private final double          mFrequency1;
    private final double          mFrequency2; // not actually used
    private int[]                 mAllGlitches; // value = 1 means there's a glitch in that interval
    private boolean               mGlitchingIntervalTooLong;
    private int                   mFFTSamplingSize; // the amount of samples used per FFT.
    private int                   mFFTOverlapSamples; // overlap half the samples
    private long                  mStartTimeMs;
    private int                   mBufferTestDurationInSeconds;
    private long                  mBufferTestDurationMs;
    private final CaptureHolder   mCaptureHolder;
    private final Context         mContext;
    private AudioManager          mAudioManager;
    private GlitchDetectionThread mGlitchDetectionThread;

    // for adjusting sound level in buffer test
    private double[] mSoundLevelSamples;
    private int      mSoundLevelSamplesIndex = 0;
    private boolean  mIsAdjustingSoundLevel = true; // is true if still adjusting sound level
    private double   mSoundBotLimit = 0.6;    // we want to keep the sound level high
    private double   mSoundTopLimit = 0.8;    // but we also don't want to be close to saturation
    private int      mAdjustSoundLevelCount = 0;
    private int      mMaxVolume;   // max possible volume of the device

    private double[]  mSamples; // samples shown on WavePlotView
    private int       mSamplesIndex;

    RecorderRunnable(PipeShort latencyPipe, int samplingRate, int channelConfig, int audioFormat,
                     int recorderBufferInBytes, int micSource, LoopbackAudioThread audioThread,
                     BufferPeriod recorderBufferPeriod, int testType, double frequency1,
                     double frequency2, int bufferTestWavePlotDurationInSeconds,
                     Context context, int channelIndex, CaptureHolder captureHolder) {
        mLatencyTestPipeShort = latencyPipe;
        mSamplingRate = samplingRate;
        mChannelConfig = channelConfig;
        mAudioFormat = audioFormat;
        mMinRecorderBuffSizeInBytes = recorderBufferInBytes;
        mSelectedRecordSource = micSource;
        mAudioThread = audioThread;
        mRecorderBufferPeriodInRecorder = recorderBufferPeriod;
        mTestType = testType;
        mFrequency1 = frequency1;
        mFrequency2 = frequency2;
        mBufferTestWavePlotDurationInSeconds = bufferTestWavePlotDurationInSeconds;
        mContext = context;
        mChannelIndex = channelIndex;
        mCaptureHolder = captureHolder;
    }


    /** Initialize the recording device for latency test. */
    public boolean initRecord() {
        log("Init Record");
        if (mMinRecorderBuffSizeInBytes <= 0) {
            mMinRecorderBuffSizeInBytes = AudioRecord.getMinBufferSize(mSamplingRate,
                                          mChannelConfig, mAudioFormat);
            log("RecorderRunnable: computing min buff size = " + mMinRecorderBuffSizeInBytes
                + " bytes");
        } else {
            log("RecorderRunnable: using min buff size = " + mMinRecorderBuffSizeInBytes +
                " bytes");
        }

        if (mMinRecorderBuffSizeInBytes <= 0) {
            return false;
        }

        mMinRecorderBuffSizeInSamples = mMinRecorderBuffSizeInBytes / Constant.BYTES_PER_FRAME;
        mAudioShortArray = new short[mMinRecorderBuffSizeInSamples];

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mRecorder = new AudioRecord.Builder()
                        .setAudioFormat((mChannelIndex < 0 ?
                                new AudioFormat.Builder()
                                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO) :
                                new AudioFormat
                                        .Builder().setChannelIndexMask(1 << mChannelIndex))
                                .setSampleRate(mSamplingRate)
                                .setEncoding(mAudioFormat)
                                .build())
                        .setAudioSource(mSelectedRecordSource)
                        .setBufferSizeInBytes(2 * mMinRecorderBuffSizeInBytes)
                        .build();
            } else {
                mRecorder = new AudioRecord(mSelectedRecordSource, mSamplingRate,
                        mChannelConfig, mAudioFormat, 2 * mMinRecorderBuffSizeInBytes);
            }
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (mRecorder == null) {
                return false;
            } else if (mRecorder.getState() != AudioRecord.STATE_INITIALIZED) {
                mRecorder.release();
                mRecorder = null;
                return false;
            }
        }

        //generate sinc wave for use in loopback test
        ToneGeneration sincTone = new RampedSineTone(mSamplingRate, Constant.LOOPBACK_FREQUENCY);
        mAudioTone = new short[Constant.LOOPBACK_SAMPLE_FRAMES];
        sincTone.generateTone(mAudioTone, Constant.LOOPBACK_SAMPLE_FRAMES);

        return true;
    }


    /** Initialize the recording device for buffer test. */
    boolean initBufferRecord() {
        log("Init Record");
        if (mMinRecorderBuffSizeInBytes <= 0) {

            mMinRecorderBuffSizeInBytes = AudioRecord.getMinBufferSize(mSamplingRate,
                                          mChannelConfig, mAudioFormat);
            log("RecorderRunnable: computing min buff size = " + mMinRecorderBuffSizeInBytes
                + " bytes");
        } else {
            log("RecorderRunnable: using min buff size = " + mMinRecorderBuffSizeInBytes +
                " bytes");
        }

        if (mMinRecorderBuffSizeInBytes <= 0) {
            return false;
        }

        mMinRecorderBuffSizeInSamples = mMinRecorderBuffSizeInBytes / Constant.BYTES_PER_FRAME;
        mBufferTestShortArray = new short[mMinRecorderBuffSizeInSamples];

        final int cycles = 100;
        int soundLevelSamples =  (mSamplingRate / (int) mFrequency1) * cycles;
        mSoundLevelSamples = new double[soundLevelSamples];
        mAudioManager = (AudioManager) mContext.getSystemService(mContext.AUDIO_SERVICE);
        mMaxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mRecorder = new AudioRecord.Builder()
                        .setAudioFormat((mChannelIndex < 0 ?
                                new AudioFormat.Builder()
                                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO) :
                                new AudioFormat
                                        .Builder().setChannelIndexMask(1 << mChannelIndex))
                                .setSampleRate(mSamplingRate)
                                .setEncoding(mAudioFormat)
                                .build())
                        .setAudioSource(mSelectedRecordSource)
                        .setBufferSizeInBytes(2 * mMinRecorderBuffSizeInBytes)
                        .build();
            } else {
                mRecorder = new AudioRecord(mSelectedRecordSource, mSamplingRate,
                        mChannelConfig, mAudioFormat, 2 * mMinRecorderBuffSizeInBytes);
            }
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (mRecorder == null) {
                return false;
            } else if (mRecorder.getState() != AudioRecord.STATE_INITIALIZED) {
                mRecorder.release();
                mRecorder = null;
                return false;
            }
        }

        final int targetFFTMs = 20; // we want each FFT to cover 20ms of samples
        mFFTSamplingSize = targetFFTMs * mSamplingRate / Constant.MILLIS_PER_SECOND;
        // round to the nearest power of 2
        mFFTSamplingSize = (int) Math.pow(2, Math.round(Math.log(mFFTSamplingSize) / Math.log(2)));

        if (mFFTSamplingSize < 2) {
            mFFTSamplingSize = 2; // mFFTSamplingSize should be at least 2
        }
        mFFTOverlapSamples = mFFTSamplingSize / 2; // mFFTOverlapSamples is half of mFFTSamplingSize

        return true;
    }


    boolean startRecording() {
        synchronized (sRecordingLock) {
            mIsRecording = true;
        }

        final int samplesDurationInSecond = 2;
        int nNewSize = mSamplingRate * samplesDurationInSecond; // 2 seconds!
        mSamples = new double[nNewSize];

        boolean status = initRecord();
        if (status) {
            log("Ready to go.");
            startRecordingForReal();
        } else {
            log("Recorder initialization error.");
            synchronized (sRecordingLock) {
                mIsRecording = false;
            }
        }

        return status;
    }


    boolean startBufferRecording() {
        synchronized (sRecordingLock) {
            mIsRecording = true;
        }

        boolean status = initBufferRecord();
        if (status) {
            log("Ready to go.");
            startBufferRecordingForReal();
        } else {
            log("Recorder initialization error.");
            synchronized (sRecordingLock) {
                mIsRecording = false;
            }
        }

        return status;
    }


    void startRecordingForReal() {
        mLatencyTestPipeShort.flush();
        mRecorder.startRecording();
    }


    void startBufferRecordingForReal() {
        mBufferTestPipeShort = new PipeShort(Constant.MAX_SHORTS);
        mGlitchDetectionThread = new GlitchDetectionThread(mFrequency1, mFrequency2, mSamplingRate,
                mFFTSamplingSize, mFFTOverlapSamples, mBufferTestDurationInSeconds,
                mBufferTestWavePlotDurationInSeconds, mBufferTestPipeShort, mCaptureHolder);
        mGlitchDetectionThread.start();
        mRecorder.startRecording();
    }


    void stopRecording() {
        log("stop recording A");
        synchronized (sRecordingLock) {
            log("stop recording B");
            mIsRecording = false;
        }
        stopRecordingForReal();
    }


    void stopRecordingForReal() {
        log("stop recording for real");
        if (mRecorder != null) {
            mRecorder.stop();
        }

        if (mRecorder != null) {
            mRecorder.release();
            mRecorder = null;
        }
    }


    public void run() {
        // keeps the total time elapsed since the start of the test. Only used in buffer test.
        long elapsedTimeMs;
        mIsRunning = true;
        while (mIsRunning) {
            boolean isRecording;

            synchronized (sRecordingLock) {
                isRecording = mIsRecording;
            }

            if (isRecording && mRecorder != null) {
                int nSamplesRead;
                switch (mTestType) {
                case Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_LATENCY:
                    nSamplesRead = mRecorder.read(mAudioShortArray, 0,
                                   mMinRecorderBuffSizeInSamples);

                    if (nSamplesRead > 0) {
                        mRecorderBufferPeriodInRecorder.collectBufferPeriod();
                        { // inject the tone that will be looped-back
                            int currentIndex = mSamplesIndex - 100; //offset
                            for (int i = 0; i < nSamplesRead; i++) {
                                if (currentIndex >= 0 && currentIndex < mAudioTone.length) {
                                    mAudioShortArray[i] = mAudioTone[currentIndex];
                                }
                                currentIndex++;
                            }
                        }

                        mLatencyTestPipeShort.write(mAudioShortArray, 0, nSamplesRead);
                        if (isStillRoomToRecord()) { //record to vector
                            for (int i = 0; i < nSamplesRead; i++) {
                                double value = mAudioShortArray[i];
                                value = value / Short.MAX_VALUE;
                                if (mSamplesIndex < mSamples.length) {
                                    mSamples[mSamplesIndex++] = value;
                                }

                            }
                        } else {
                            mIsRunning = false;
                        }
                    }
                    break;
                case Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_BUFFER_PERIOD:
                    if (mIsRequestStop) {
                        endBufferTest();
                    } else {
                        // before we start the test, first adjust sound level
                        if (mIsAdjustingSoundLevel) {
                            nSamplesRead = mRecorder.read(mBufferTestShortArray, 0,
                                    mMinRecorderBuffSizeInSamples);
                            if (nSamplesRead > 0) {
                                for (int i = 0; i < nSamplesRead; i++) {
                                    double value = mBufferTestShortArray[i];
                                    if (mSoundLevelSamplesIndex < mSoundLevelSamples.length) {
                                        mSoundLevelSamples[mSoundLevelSamplesIndex++] = value;
                                    } else {
                                        // adjust the sound level to appropriate level
                                        mIsAdjustingSoundLevel = AdjustSoundLevel();
                                        mAdjustSoundLevelCount++;
                                        mSoundLevelSamplesIndex = 0;
                                        if (!mIsAdjustingSoundLevel) {
                                            // end of sound level adjustment, notify AudioTrack
                                            mAudioThread.setIsAdjustingSoundLevel(false);
                                            mStartTimeMs = System.currentTimeMillis();
                                            break;
                                        }
                                    }
                                }
                            }
                        } else {
                            // the end of test is controlled here. Once we've run for the specified
                            // test duration, end the test
                            elapsedTimeMs = System.currentTimeMillis() - mStartTimeMs;
                            if (elapsedTimeMs >= mBufferTestDurationMs) {
                                endBufferTest();
                            } else {
                                nSamplesRead = mRecorder.read(mBufferTestShortArray, 0,
                                        mMinRecorderBuffSizeInSamples);
                                if (nSamplesRead > 0) {
                                    mRecorderBufferPeriodInRecorder.collectBufferPeriod();
                                    mBufferTestPipeShort.write(mBufferTestShortArray, 0,
                                            nSamplesRead);
                                }
                            }
                        }
                    }
                    break;
                }
            }
        } //synchronized
        stopRecording(); //close this
    }


    /** Someone is requesting to stop the test, will stop the test even if the test is not done. */
    public void requestStop() {
        switch (mTestType) {
        case Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_BUFFER_PERIOD:
            mIsRequestStop = true;
            break;
        case Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_LATENCY:
            mIsRunning = false;
            break;
        }
    }


    /** Collect data then clean things up.*/
    private void endBufferTest() {
        mIsRunning = false;
        mAllGlitches = mGlitchDetectionThread.getGlitches();
        mGlitchingIntervalTooLong = mGlitchDetectionThread.getGlitchingIntervalTooLong();
        mSamples = mGlitchDetectionThread.getWaveData();
        endDetecting();
    }


    /** Clean everything up. */
    public void endDetecting() {
        mBufferTestPipeShort.flush();
        mBufferTestPipeShort = null;
        mGlitchDetectionThread.requestStop();
        GlitchDetectionThread tempThread = mGlitchDetectionThread;
        mGlitchDetectionThread = null;
        try {
            tempThread.join(Constant.JOIN_WAIT_TIME_MS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    /**
     * Adjust the sound level such that the buffer test can run with small noise disturbance.
     * Return a boolean value to indicate whether or not the sound level has adjusted to an
     * appropriate level.
     */
    private boolean AdjustSoundLevel() {
        // if after adjusting 20 times, we still cannot get into the volume we want, increase the
        // limit range, so it's easier to get into the volume we want.
        if (mAdjustSoundLevelCount != 0 && mAdjustSoundLevelCount % 20 == 0) {
            mSoundTopLimit += 0.1;
            mSoundBotLimit -= 0.1;
        }

        double topThreshold = Short.MAX_VALUE * mSoundTopLimit;
        double botThreshold = Short.MAX_VALUE * mSoundBotLimit;
        double currentMax = mSoundLevelSamples[0];
        int currentVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

        // since it's a sine wave, we are only checking max positive value
        for (int i = 1; i < mSoundLevelSamples.length; i++) {
            if (mSoundLevelSamples[i] > topThreshold) { // once a sample exceed, return
                // adjust sound level down
                currentVolume--;
                mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0);
                return true;
            }

            if (mSoundLevelSamples[i] > currentMax) {
                currentMax = mSoundLevelSamples[i];
            }
        }

        if (currentMax < botThreshold) {
            // adjust sound level up
            if (currentVolume < mMaxVolume) {
                currentVolume++;
                mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                        currentVolume, 0);
                return true;
            } else {
                return false;
            }
        }

        return false;
    }


    /** Check if there's any room left in mSamples. */
    public boolean isStillRoomToRecord() {
        boolean result = false;
        if (mSamples != null) {
            if (mSamplesIndex < mSamples.length) {
                result = true;
            }
        }

        return result;
    }


    public void setBufferTestDurationInSeconds(int bufferTestDurationInSeconds) {
        mBufferTestDurationInSeconds = bufferTestDurationInSeconds;
        mBufferTestDurationMs = Constant.MILLIS_PER_SECOND * mBufferTestDurationInSeconds;
    }


    public int[] getAllGlitches() {
        return mAllGlitches;
    }


    public boolean getGlitchingIntervalTooLong() {
        return mGlitchingIntervalTooLong;
    }


    public double[] getWaveData() {
        return mSamples;
    }


    public int getFFTSamplingSize() {
        return mFFTSamplingSize;
    }


    public int getFFTOverlapSamples() {
        return mFFTOverlapSamples;
    }


    private static void log(String msg) {
        Log.v(TAG, msg);
    }

}
