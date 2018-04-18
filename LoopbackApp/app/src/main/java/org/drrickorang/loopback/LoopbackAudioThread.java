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

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;
import android.os.Handler;
import android.os.Message;

/**
 * A thread/audio track based audio synth.
 */

public class LoopbackAudioThread extends Thread {
    private static final String TAG = "LoopbackAudioThread";

    private static final int THREAD_SLEEP_DURATION_MS = 1;

    // for latency test
    static final int LOOPBACK_AUDIO_THREAD_MESSAGE_LATENCY_REC_STARTED = 991;
    static final int LOOPBACK_AUDIO_THREAD_MESSAGE_LATENCY_REC_ERROR = 992;
    static final int LOOPBACK_AUDIO_THREAD_MESSAGE_LATENCY_REC_COMPLETE = 993;
    static final int LOOPBACK_AUDIO_THREAD_MESSAGE_LATENCY_REC_STOP = 994;

    // for buffer test
    static final int LOOPBACK_AUDIO_THREAD_MESSAGE_BUFFER_REC_STARTED = 996;
    static final int LOOPBACK_AUDIO_THREAD_MESSAGE_BUFFER_REC_ERROR = 997;
    static final int LOOPBACK_AUDIO_THREAD_MESSAGE_BUFFER_REC_COMPLETE = 998;
    static final int LOOPBACK_AUDIO_THREAD_MESSAGE_BUFFER_REC_STOP = 999;

    public boolean           mIsRunning = false;
    public AudioTrack        mAudioTrack;
    public int               mSessionId;
    private Thread           mRecorderThread;
    private RecorderRunnable mRecorderRunnable;

    private final int mSamplingRate;
    private final int mChannelIndex;
    private final int mChannelConfigIn = AudioFormat.CHANNEL_IN_MONO;
    private final int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private int       mMinPlayerBufferSizeInBytes = 0;
    private int       mMinRecorderBuffSizeInBytes = 0;
    private int       mMinPlayerBufferSizeSamples = 0;
    private final int mMicSource;
    private final int mChannelConfigOut = AudioFormat.CHANNEL_OUT_MONO;
    private boolean   mIsPlaying = false;
    private boolean   mIsRequestStop = false;
    private Handler   mMessageHandler;
    // This is the pipe that connects the player and the recorder in latency test.
    private PipeShort mLatencyTestPipe = new PipeShort(Constant.MAX_SHORTS);

    // for buffer test
    private BufferPeriod   mRecorderBufferPeriod; // used to collect recorder's buffer period
    private BufferPeriod   mPlayerBufferPeriod; // used to collect player's buffer period
    private int            mTestType; // latency test or buffer test
    private int            mBufferTestDurationInSeconds; // Duration of actual buffer test
    private Context        mContext;
    private int            mBufferTestWavePlotDurationInSeconds;
    private final CaptureHolder mCaptureHolder;
    private boolean        mIsAdjustingSoundLevel = true; // only used in buffer test

    public static TestSettings computeDefaultSettings() {
        int samplingRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
        int minPlayerBufferSizeInBytes = AudioTrack.getMinBufferSize(samplingRate,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int minRecorderBufferSizeInBytes = AudioRecord.getMinBufferSize(samplingRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        return new TestSettings(samplingRate, minPlayerBufferSizeInBytes,
                minRecorderBufferSizeInBytes);
    }

    public LoopbackAudioThread(int samplingRate, int playerBufferInBytes, int recorderBufferInBytes,
                               int micSource, BufferPeriod recorderBufferPeriod,
                               BufferPeriod playerBufferPeriod, int testType,
                               int bufferTestDurationInSeconds,
                               int bufferTestWavePlotDurationInSeconds, Context context,
                               int channelIndex, CaptureHolder captureHolder) {
        mSamplingRate = samplingRate;
        mMinPlayerBufferSizeInBytes = playerBufferInBytes;
        mMinRecorderBuffSizeInBytes = recorderBufferInBytes;
        mMicSource = micSource;
        mRecorderBufferPeriod = recorderBufferPeriod;
        mPlayerBufferPeriod = playerBufferPeriod;
        mTestType = testType;
        mBufferTestDurationInSeconds = bufferTestDurationInSeconds;
        mBufferTestWavePlotDurationInSeconds = bufferTestWavePlotDurationInSeconds;
        mContext = context;
        mChannelIndex = channelIndex;
        mCaptureHolder = captureHolder;

        setName("Loopback_LoopbackAudio");
    }


    public void run() {
        setPriority(Thread.MAX_PRIORITY);

        if (mMinPlayerBufferSizeInBytes <= 0) {
            mMinPlayerBufferSizeInBytes = AudioTrack.getMinBufferSize(mSamplingRate,
                                        mChannelConfigOut, mAudioFormat);

            log("Player: computed min buff size = " + mMinPlayerBufferSizeInBytes + " bytes");
        } else {
            log("Player: using min buff size = " + mMinPlayerBufferSizeInBytes + " bytes");
        }

        mMinPlayerBufferSizeSamples = mMinPlayerBufferSizeInBytes / Constant.BYTES_PER_FRAME;
        short[] audioShortArrayOut = new short[mMinPlayerBufferSizeSamples];

        // we may want to adjust this to different multiplication of mMinPlayerBufferSizeSamples
        int audioTrackWriteDataSize = mMinPlayerBufferSizeSamples;

        // used for buffer test only
        final double frequency1 = Constant.PRIME_FREQUENCY_1;
        final double frequency2 = Constant.PRIME_FREQUENCY_2; // not actually used
        short[] bufferTestTone = new short[audioTrackWriteDataSize]; // used by AudioTrack.write()
        ToneGeneration toneGeneration = new SineWaveTone(mSamplingRate, frequency1);

        mRecorderRunnable = new RecorderRunnable(mLatencyTestPipe, mSamplingRate, mChannelConfigIn,
                mAudioFormat, mMinRecorderBuffSizeInBytes, MediaRecorder.AudioSource.MIC, this,
                mRecorderBufferPeriod, mTestType, frequency1, frequency2,
                mBufferTestWavePlotDurationInSeconds, mContext, mChannelIndex, mCaptureHolder);
        mRecorderRunnable.setBufferTestDurationInSeconds(mBufferTestDurationInSeconds);
        mRecorderThread = new Thread(mRecorderRunnable);
        mRecorderThread.setName("Loopback_RecorderRunnable");

        // both player and recorder run at max priority
        mRecorderThread.setPriority(Thread.MAX_PRIORITY);
        mRecorderThread.start();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mAudioTrack = new AudioTrack.Builder()
                    .setAudioFormat((mChannelIndex < 0 ?
                            new AudioFormat.Builder().setChannelMask(AudioFormat.CHANNEL_OUT_MONO) :
                            new AudioFormat.Builder().setChannelIndexMask(1 << mChannelIndex))
                            .setSampleRate(mSamplingRate)
                            .setEncoding(mAudioFormat)
                            .build())
                    .setBufferSizeInBytes(mMinPlayerBufferSizeInBytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
        } else {
            mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                    mSamplingRate,
                    mChannelConfigOut,
                    mAudioFormat,
                    mMinPlayerBufferSizeInBytes,
                    AudioTrack.MODE_STREAM /* FIXME runtime test for API level 9,
                    mSessionId */);
        }

        if (mRecorderRunnable != null && mAudioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
            mIsPlaying = false;
            mIsRunning = true;

            while (mIsRunning && mRecorderThread.isAlive()) {
                if (mIsPlaying) {
                    switch (mTestType) {
                    case Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_LATENCY:
                        // read from the pipe and plays it out
                        int samplesAvailable = mLatencyTestPipe.availableToRead();
                        if (samplesAvailable > 0) {
                            int samplesOfInterest = Math.min(samplesAvailable,
                                    mMinPlayerBufferSizeSamples);

                            int samplesRead = mLatencyTestPipe.read(audioShortArrayOut, 0,
                                                                    samplesOfInterest);
                            mAudioTrack.write(audioShortArrayOut, 0, samplesRead);
                            mPlayerBufferPeriod.collectBufferPeriod();
                        }
                        break;
                    case Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_BUFFER_PERIOD:
                        // don't collect buffer period when we are still adjusting the sound level
                        if (mIsAdjustingSoundLevel) {
                            toneGeneration.generateTone(bufferTestTone, bufferTestTone.length);
                            mAudioTrack.write(bufferTestTone, 0, audioTrackWriteDataSize);
                        } else {
                            mPlayerBufferPeriod.collectBufferPeriod();
                            toneGeneration.generateTone(bufferTestTone, bufferTestTone.length);
                            mAudioTrack.write(bufferTestTone, 0, audioTrackWriteDataSize);
                        }
                        break;
                    }
                } else {
                    // wait for a bit to allow AudioTrack to start playing
                    if (mIsRunning) {
                        try {
                            sleep(THREAD_SLEEP_DURATION_MS);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            endTest();

        } else {
            log("Loopback Audio Thread couldn't run!");
            mAudioTrack.release();
            mAudioTrack = null;
            if (mMessageHandler != null) {
                Message msg = Message.obtain();
                switch (mTestType) {
                case Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_LATENCY:
                    msg.what = LOOPBACK_AUDIO_THREAD_MESSAGE_LATENCY_REC_ERROR;
                    break;
                case Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_BUFFER_PERIOD:
                    msg.what = LOOPBACK_AUDIO_THREAD_MESSAGE_BUFFER_REC_ERROR;
                    break;
                }

                mMessageHandler.sendMessage(msg);
            }

        }
    }


    public void setMessageHandler(Handler messageHandler) {
        mMessageHandler = messageHandler;
    }


    public void setIsAdjustingSoundLevel(boolean isAdjustingSoundLevel) {
        mIsAdjustingSoundLevel = isAdjustingSoundLevel;
    }


    public void runTest() {
        if (mIsRunning) {
            // start test
            if (mAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                log("...run test, but still playing...");
                endTest();
            } else {
                // start playing
                mIsPlaying = true;
                mAudioTrack.play();
                boolean status = mRecorderRunnable.startRecording();

                log("Started capture test");
                if (mMessageHandler != null) {
                    Message msg = Message.obtain();
                    msg.what = LOOPBACK_AUDIO_THREAD_MESSAGE_LATENCY_REC_STARTED;
                    if (!status) {
                        msg.what = LOOPBACK_AUDIO_THREAD_MESSAGE_LATENCY_REC_ERROR;
                    }

                    mMessageHandler.sendMessage(msg);
                }
            }
        }
    }


    public void runBufferTest() {
        if (mIsRunning) {
            // start test
            if (mAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                log("...run test, but still playing...");
                endTest();
            } else {
                // start playing
                mIsPlaying = true;
                mAudioTrack.play();
                boolean status = mRecorderRunnable.startBufferRecording();
                log(" Started capture test");
                if (mMessageHandler != null) {
                    Message msg = Message.obtain();
                    msg.what = LOOPBACK_AUDIO_THREAD_MESSAGE_BUFFER_REC_STARTED;

                    if (!status) {
                        msg.what = LOOPBACK_AUDIO_THREAD_MESSAGE_BUFFER_REC_ERROR;
                    }

                    mMessageHandler.sendMessage(msg);
                }
            }
        }
    }


    /** Clean some things up before sending out a message to LoopbackActivity. */
    public void endTest() {
        switch (mTestType) {
        case Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_LATENCY:
            log("--Ending latency test--");
            break;
        case Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_BUFFER_PERIOD:
            log("--Ending buffer test--");
            break;
        }

        mIsPlaying = false;
        mAudioTrack.pause();
        mLatencyTestPipe.flush();
        mAudioTrack.flush();

        if (mMessageHandler != null) {
            Message msg = Message.obtain();
            if (mIsRequestStop) {
                switch (mTestType) {
                case Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_LATENCY:
                    msg.what = LOOPBACK_AUDIO_THREAD_MESSAGE_LATENCY_REC_STOP;
                    break;
                case Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_BUFFER_PERIOD:
                    msg.what = LOOPBACK_AUDIO_THREAD_MESSAGE_BUFFER_REC_STOP;
                    break;
                }
            } else {
                switch (mTestType) {
                case Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_LATENCY:
                    msg.what = LOOPBACK_AUDIO_THREAD_MESSAGE_LATENCY_REC_COMPLETE;
                    break;
                case Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_BUFFER_PERIOD:
                    msg.what = LOOPBACK_AUDIO_THREAD_MESSAGE_BUFFER_REC_COMPLETE;
                    break;
                }
            }

            mMessageHandler.sendMessage(msg);
        }
    }


    /**
     * This is called only when the user requests to stop the test through
     * pressing a button in the LoopbackActivity.
     */
    public void requestStopTest() throws InterruptedException {
        mIsRequestStop = true;
        mRecorderRunnable.requestStop();
    }


    /** Release mAudioTrack and mRecorderThread. */
    public void finish() throws InterruptedException {
        mIsRunning = false;

        final AudioTrack at = mAudioTrack;
        if (at != null) {
            at.release();
            mAudioTrack = null;
        }

        Thread zeThread = mRecorderThread;
        mRecorderThread = null;
        if (zeThread != null) {
            zeThread.interrupt();
            zeThread.join(Constant.JOIN_WAIT_TIME_MS);
        }
    }


    private static void log(String msg) {
        Log.v(TAG, msg);
    }


    public double[] getWaveData() {
        return mRecorderRunnable.getWaveData();
    }


    public int[] getAllGlitches() {
        return mRecorderRunnable.getAllGlitches();
    }


    public boolean getGlitchingIntervalTooLong() {
        return mRecorderRunnable.getGlitchingIntervalTooLong();
    }


    public int getFFTSamplingSize() {
        return mRecorderRunnable.getFFTSamplingSize();
    }


    public int getFFTOverlapSamples() {
        return mRecorderRunnable.getFFTOverlapSamples();
    }


    int getDurationInSeconds() {
        return mBufferTestDurationInSeconds;
    }

}
