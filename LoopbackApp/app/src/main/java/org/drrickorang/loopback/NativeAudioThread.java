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

import java.nio.ByteBuffer;
import java.util.Arrays;

import android.content.Context;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.Log;


/**
 * A thread/audio track based audio synth.
 */

public class NativeAudioThread extends Thread {
    private static final String TAG = "NativeAudioThread";

    // for latency test
    static final int LOOPBACK_NATIVE_AUDIO_THREAD_MESSAGE_LATENCY_REC_STARTED = 891;
    static final int LOOPBACK_NATIVE_AUDIO_THREAD_MESSAGE_LATENCY_REC_ERROR = 892;
    static final int LOOPBACK_NATIVE_AUDIO_THREAD_MESSAGE_LATENCY_REC_COMPLETE = 893;
    static final int LOOPBACK_NATIVE_AUDIO_THREAD_MESSAGE_LATENCY_REC_COMPLETE_ERRORS = 894;
    static final int LOOPBACK_NATIVE_AUDIO_THREAD_MESSAGE_LATENCY_REC_STOP = 895;

    // for buffer test
    static final int LOOPBACK_NATIVE_AUDIO_THREAD_MESSAGE_BUFFER_REC_STARTED = 896;
    static final int LOOPBACK_NATIVE_AUDIO_THREAD_MESSAGE_BUFFER_REC_ERROR = 897;
    static final int LOOPBACK_NATIVE_AUDIO_THREAD_MESSAGE_BUFFER_REC_COMPLETE = 898;
    static final int LOOPBACK_NATIVE_AUDIO_THREAD_MESSAGE_BUFFER_REC_COMPLETE_ERRORS = 899;
    static final int LOOPBACK_NATIVE_AUDIO_THREAD_MESSAGE_BUFFER_REC_STOP = 900;

    public boolean  mIsRunning = false;
    public int      mSessionId;
    public double[] mSamples; // store samples that will be shown on WavePlotView
    int             mSamplesIndex;

    private int mThreadType;
    private int mTestType;
    private int mSamplingRate;
    private int mMinPlayerBufferSizeInBytes = 0;
    private int mMinRecorderBuffSizeInBytes = 0; // currently not used
    private int mMicSource;
    private int mPerformanceMode = -1;
    private int mIgnoreFirstFrames;

    private boolean mIsRequestStop = false;
    private Handler mMessageHandler;
    private boolean isDestroying = false;
    private boolean hasDestroyingErrors = false;

    // for buffer test
    private int[]   mRecorderBufferPeriod;
    private int     mRecorderMaxBufferPeriod;
    private double  mRecorderStdDevBufferPeriod;
    private int[]   mPlayerBufferPeriod;
    private int     mPlayerMaxBufferPeriod;
    private double  mPlayerStdDevBufferPeriod;
    private BufferCallbackTimes mPlayerCallbackTimes;
    private BufferCallbackTimes mRecorderCallbackTimes;
    private int     mBufferTestWavePlotDurationInSeconds;
    private double  mFrequency1 = Constant.PRIME_FREQUENCY_1;
    private double  mFrequency2 = Constant.PRIME_FREQUENCY_2; // not actually used
    private int     mBufferTestDurationInSeconds;
    private int     mFFTSamplingSize;
    private int     mFFTOverlapSamples;
    private int[]   mAllGlitches;
    private boolean mGlitchingIntervalTooLong;
    private final CaptureHolder mCaptureHolder;

    private PipeByteBuffer        mPipeByteBuffer;
    private GlitchDetectionThread mGlitchDetectionThread;

    /** Check if it's safe to use getProperty(). */
    static boolean isSafeToUseGetProperty() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1;
    }

    public static TestSettings computeDefaultSettings(Context context,
            int threadType, int performanceMode) {
        TestSettings nativeResult = nativeComputeDefaultSettings(
                Constant.BYTES_PER_FRAME, threadType, performanceMode);
        if (nativeResult != null) {
            return nativeResult;
        }

        int samplingRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
        int minBufferSizeInFrames = 1024;
        if (isSafeToUseGetProperty()) {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            String value = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
            minBufferSizeInFrames = Integer.parseInt(value);
        }
        int minBufferSizeInBytes = Constant.BYTES_PER_FRAME * minBufferSizeInFrames;
        return new TestSettings(samplingRate, minBufferSizeInBytes, minBufferSizeInBytes);
    }

    public NativeAudioThread(int threadType, int samplingRate, int playerBufferInBytes,
                             int recorderBufferInBytes, int micSource, int performanceMode,
                             int testType, int bufferTestDurationInSeconds,
                             int bufferTestWavePlotDurationInSeconds, int ignoreFirstFrames,
                             CaptureHolder captureHolder) {
        mThreadType = threadType;
        mSamplingRate = samplingRate;
        mMinPlayerBufferSizeInBytes = playerBufferInBytes;
        mMinRecorderBuffSizeInBytes = recorderBufferInBytes;
        mMicSource = micSource;
        mPerformanceMode = performanceMode;
        mTestType = testType;
        mBufferTestDurationInSeconds = bufferTestDurationInSeconds;
        mBufferTestWavePlotDurationInSeconds = bufferTestWavePlotDurationInSeconds;
        mIgnoreFirstFrames = ignoreFirstFrames;
        mCaptureHolder = captureHolder;
        setName("Loopback_NativeAudio");
    }

    public NativeAudioThread(NativeAudioThread old) {
        mThreadType = old.mThreadType;
        mSamplingRate = old.mSamplingRate;
        mMinPlayerBufferSizeInBytes = old.mMinPlayerBufferSizeInBytes;
        mMinRecorderBuffSizeInBytes = old.mMinRecorderBuffSizeInBytes;
        mMicSource = old.mMicSource;
        mPerformanceMode = old.mPerformanceMode;
        mTestType = old.mTestType;
        mBufferTestDurationInSeconds = old.mBufferTestDurationInSeconds;
        mBufferTestWavePlotDurationInSeconds = old.mBufferTestWavePlotDurationInSeconds;
        mIgnoreFirstFrames = old.mIgnoreFirstFrames;
        mCaptureHolder = old.mCaptureHolder;
        setName("Loopback_NativeAudio");
    }

    //JNI load
    static {
        try {
            System.loadLibrary("loopback");
        } catch (UnsatisfiedLinkError e) {
            log("Error loading loopback JNI library");
            e.printStackTrace();
        }
        /* TODO: gracefully fail/notify if the library can't be loaded */
    }


    //jni calls
    public static native TestSettings nativeComputeDefaultSettings(
            int bytesPerFrame, int threadType, int performanceMode);
    public native long  nativeInit(int threadType,
                                 int samplingRate, int frameCount, int micSource,
                                 int performanceMode,
                                 int testType, double frequency1, ByteBuffer byteBuffer,
                                 short[] sincTone, int maxRecordedLateCallbacks,
                                 int ignoreFirstFrames);
    public native int   nativeProcessNext(long nativeHandle, double[] samples, long offset);
    public native int   nativeDestroy(long nativeHandle);

    // to get buffer period data
    public native int[]  nativeGetRecorderBufferPeriod(long nativeHandle);
    public native int    nativeGetRecorderMaxBufferPeriod(long nativeHandle);
    public native double nativeGetRecorderVarianceBufferPeriod(long nativeHandle);
    public native int[]  nativeGetPlayerBufferPeriod(long nativeHandle);
    public native int    nativeGetPlayerMaxBufferPeriod(long nativeHandle);
    public native double nativeGetPlayerVarianceBufferPeriod(long nativeHandle);
    public native BufferCallbackTimes nativeGetPlayerCallbackTimeStamps(long nativeHandle);
    public native BufferCallbackTimes nativeGetRecorderCallbackTimeStamps(long nativeHandle);

    public native int nativeGetCaptureRank(long nativeHandle);


    public void run() {
        setPriority(Thread.MAX_PRIORITY);
        mIsRunning = true;

        //erase output buffer
        if (mSamples != null)
            mSamples = null;

        //start playing
        log(" Started capture test");
        if (mMessageHandler != null) {
            Message msg = Message.obtain();
            switch (mTestType) {
            case Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_LATENCY:
                msg.what = LOOPBACK_NATIVE_AUDIO_THREAD_MESSAGE_LATENCY_REC_STARTED;
                break;
            case Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_BUFFER_PERIOD:
                msg.what = LOOPBACK_NATIVE_AUDIO_THREAD_MESSAGE_BUFFER_REC_STARTED;
                break;
            }
            mMessageHandler.sendMessage(msg);
        }

        // generate windowed tone use for loopback test
        short loopbackTone[] = new short[mMinPlayerBufferSizeInBytes / Constant.BYTES_PER_FRAME];
        if (mTestType == Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_LATENCY) {
            ToneGeneration sincToneGen = new RampedSineTone(mSamplingRate,
                    Constant.LOOPBACK_FREQUENCY);
            int sincLength = Math.min(Constant.LOOPBACK_SAMPLE_FRAMES, loopbackTone.length);
            sincToneGen.generateTone(loopbackTone, sincLength);
        }

        log(String.format("about to init, sampling rate: %d, buffer:%d", mSamplingRate,
                mMinPlayerBufferSizeInBytes / Constant.BYTES_PER_FRAME));

        // mPipeByteBuffer is only used in buffer test
        mPipeByteBuffer = new PipeByteBuffer(Constant.MAX_SHORTS);
        long startTimeMs = System.currentTimeMillis();
        long nativeHandle = nativeInit(mThreadType, mSamplingRate,
                mMinPlayerBufferSizeInBytes / Constant.BYTES_PER_FRAME, mMicSource,
                mPerformanceMode, mTestType,
                mFrequency1, mPipeByteBuffer.getByteBuffer(), loopbackTone,
                mBufferTestDurationInSeconds * Constant.MAX_RECORDED_LATE_CALLBACKS_PER_SECOND,
                mIgnoreFirstFrames);
        log(String.format("nativeHandle = 0x%X", nativeHandle));

        if (nativeHandle == 0) {
            //notify error!!
            log(" ERROR at JNI initialization");
            if (mMessageHandler != null) {
                Message msg = Message.obtain();
                switch (mTestType) {
                case Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_LATENCY:
                    msg.what = LOOPBACK_NATIVE_AUDIO_THREAD_MESSAGE_LATENCY_REC_ERROR;
                    break;
                case Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_BUFFER_PERIOD:
                    msg.what = LOOPBACK_NATIVE_AUDIO_THREAD_MESSAGE_BUFFER_REC_ERROR;
                    break;
                }
                mMessageHandler.sendMessage(msg);
            }
        } else {
            // wait a little bit
            try {
                final int setUpTime = 10;
                sleep(setUpTime); //just to let it start properly
            } catch (InterruptedException e) {
                e.printStackTrace();
            }


            int totalSamplesRead = 0;
            switch (mTestType) {
            case Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_LATENCY:
                final int latencyTestDurationInSeconds = 2;
                int nNewSize = (int) (1.1 * mSamplingRate * latencyTestDurationInSeconds);
                mSamples = new double[nNewSize];
                mSamplesIndex = 0; //reset index
                Arrays.fill(mSamples, 0);

                //TODO use a ByteBuffer to retrieve recorded data instead
                long offset = 0;
                // retrieve native recorder's recorded data
                for (int ii = 0; ii < latencyTestDurationInSeconds; ii++) {
                    log(String.format("block %d...", ii));
                    int samplesRead = nativeProcessNext(nativeHandle, mSamples, offset);
                    totalSamplesRead += samplesRead;
                    offset += samplesRead;
                    log(" [" + ii + "] jni samples read:" + samplesRead +
                        "  currentOffset:" + offset);
                }

                log(String.format(" samplesRead: %d, sampleOffset:%d", totalSamplesRead, offset));
                log("about to destroy...");
                break;
            case Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_BUFFER_PERIOD:
                setUpGlitchDetectionThread();
                long testDurationMs = mBufferTestDurationInSeconds * Constant.MILLIS_PER_SECOND;
                long elapsedTimeMs = System.currentTimeMillis() - startTimeMs;
                while (elapsedTimeMs < testDurationMs) {
                    if (mIsRequestStop) {
                        break;
                    } else {
                        int rank = nativeGetCaptureRank(nativeHandle);
                        if (rank > 0) {
                            //log("Late callback detected");
                            mCaptureHolder.captureState(rank);
                        }
                        try {
                            final int setUpTime = 100;
                            sleep(setUpTime); //just to let it start properly
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        elapsedTimeMs = System.currentTimeMillis() - startTimeMs;
                    }

                }
                break;


            }

            // collect buffer period data
            mRecorderBufferPeriod = nativeGetRecorderBufferPeriod(nativeHandle);
            mRecorderMaxBufferPeriod = nativeGetRecorderMaxBufferPeriod(nativeHandle);
            mRecorderStdDevBufferPeriod = Math.sqrt(nativeGetRecorderVarianceBufferPeriod(
                    nativeHandle));
            mPlayerBufferPeriod = nativeGetPlayerBufferPeriod(nativeHandle);
            mPlayerMaxBufferPeriod = nativeGetPlayerMaxBufferPeriod(nativeHandle);
            mPlayerStdDevBufferPeriod = Math.sqrt(nativeGetPlayerVarianceBufferPeriod(
                    nativeHandle));

            mPlayerCallbackTimes = nativeGetPlayerCallbackTimeStamps(nativeHandle);
            mRecorderCallbackTimes = nativeGetRecorderCallbackTimeStamps(nativeHandle);

            // get glitches data only for buffer test
            if (mTestType == Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_BUFFER_PERIOD) {
                mAllGlitches = mGlitchDetectionThread.getGlitches();
                mSamples = mGlitchDetectionThread.getWaveData();
                mGlitchingIntervalTooLong = mGlitchDetectionThread.getGlitchingIntervalTooLong();
                endDetecting();
            }

            if (mTestType == Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_LATENCY) {
                mCaptureHolder.captureState(0);
            }

            runDestroy(nativeHandle);

            final int maxTry = 20;
            int tryCount = 0;
            while (isDestroying) {
                try {
                    sleep(40);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                tryCount++;
                log("destroy try: " + tryCount);

                if (tryCount >= maxTry) {
                    hasDestroyingErrors = true;
                    log("WARNING: waited for max time to properly destroy JNI.");
                    break;
                }
            }
            log(String.format("after destroying. TotalSamplesRead = %d", totalSamplesRead));

            // for buffer test samples won't be read into here
            if (mTestType == Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_LATENCY
                && totalSamplesRead == 0) {
                //hasDestroyingErrors = true;
                log("Warning: Latency test reads no sample from native recorder!");
            }

            endTest();
        }
    }


    public void requestStopTest() {
        mIsRequestStop = true;
    }


    /** Set up parameters needed for GlitchDetectionThread, then create and run this thread. */
    private void setUpGlitchDetectionThread() {
        final int targetFFTMs = 20; // we want each FFT to cover 20ms of samples
        mFFTSamplingSize = targetFFTMs * mSamplingRate / Constant.MILLIS_PER_SECOND;
        // round to the nearest power of 2
        mFFTSamplingSize = (int) Math.pow(2, Math.round(Math.log(mFFTSamplingSize) / Math.log(2)));

        if (mFFTSamplingSize < 2) {
            mFFTSamplingSize = 2; // mFFTSamplingSize should be at least 2
        }
        mFFTOverlapSamples = mFFTSamplingSize / 2; // mFFTOverlapSamples is half of mFFTSamplingSize

        mGlitchDetectionThread = new GlitchDetectionThread(mFrequency1, mFrequency2, mSamplingRate,
            mFFTSamplingSize, mFFTOverlapSamples, mBufferTestDurationInSeconds,
            mBufferTestWavePlotDurationInSeconds, mPipeByteBuffer, mCaptureHolder);
        mGlitchDetectionThread.start();
    }


    public void endDetecting() {
        mPipeByteBuffer.flush();
        mPipeByteBuffer = null;
        mGlitchDetectionThread.requestStop();
        GlitchDetectionThread tempThread = mGlitchDetectionThread;
        mGlitchDetectionThread = null;
        try {
            tempThread.join(Constant.JOIN_WAIT_TIME_MS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    public void setMessageHandler(Handler messageHandler) {
        mMessageHandler = messageHandler;
    }


    private void runDestroy(final long localNativeHandle) {
        isDestroying = true;

        //start thread
        Thread thread = new Thread(new Runnable() {
            public void run() {
                isDestroying = true;
                log("**Start runnable destroy");

                int status = nativeDestroy(localNativeHandle);
                log(String.format("**End runnable destroy native delete status: %d", status));
                isDestroying = false;
            }
        });

        thread.start();
        log("end of runDestroy()");
    }


    /** not doing real work, just to keep consistency with LoopbackAudioThread. */
    public void runTest() {

    }


    /** not doing real work, just to keep consistency with LoopbackAudioThread. */
    public void runBufferTest() {

    }


    public void endTest() {
       log("--Ending capture test--");
       if (mMessageHandler != null) {
           Message msg = Message.obtain();
           if (hasDestroyingErrors) {
               switch (mTestType) {
                   case Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_LATENCY:
                       msg.what = LOOPBACK_NATIVE_AUDIO_THREAD_MESSAGE_LATENCY_REC_COMPLETE_ERRORS;
                       break;
                   case Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_BUFFER_PERIOD:
                       msg.what = LOOPBACK_NATIVE_AUDIO_THREAD_MESSAGE_BUFFER_REC_COMPLETE_ERRORS;
                       break;
               }
           } else if (mIsRequestStop) {
               switch (mTestType) {
                   case Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_LATENCY:
                       msg.what = LOOPBACK_NATIVE_AUDIO_THREAD_MESSAGE_LATENCY_REC_STOP;
                       break;
                   case Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_BUFFER_PERIOD:
                       msg.what = LOOPBACK_NATIVE_AUDIO_THREAD_MESSAGE_BUFFER_REC_STOP;
                       break;
               }
           } else {
               switch (mTestType) {
               case Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_LATENCY:
                   msg.what = LOOPBACK_NATIVE_AUDIO_THREAD_MESSAGE_LATENCY_REC_COMPLETE;
                   break;
               case Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_BUFFER_PERIOD:
                   msg.what = LOOPBACK_NATIVE_AUDIO_THREAD_MESSAGE_BUFFER_REC_COMPLETE;
                   break;
               }
           }

           mMessageHandler.sendMessage(msg);
       }
    }


    public void finish() {
        mIsRunning = false;
    }


    private static void log(String msg) {
        Log.v(TAG, msg);
    }


    double[] getWaveData() {
        return mSamples;
    }


    public int[] getRecorderBufferPeriod() {
        return mRecorderBufferPeriod;
    }

    public int getRecorderMaxBufferPeriod() {
        return mRecorderMaxBufferPeriod;
    }

    public double getRecorderStdDevBufferPeriod() {
        return mRecorderStdDevBufferPeriod;
    }

    public int[] getPlayerBufferPeriod() {
        return mPlayerBufferPeriod;
    }

    public int getPlayerMaxBufferPeriod() {
        return mPlayerMaxBufferPeriod;
    }

    public double getPlayerStdDevBufferPeriod() {
        return mPlayerStdDevBufferPeriod;
    }

    public int[] getNativeAllGlitches() {
        return mAllGlitches;
    }


    public boolean getGlitchingIntervalTooLong() {
        return mGlitchingIntervalTooLong;
    }


    public int getNativeFFTSamplingSize() {
        return mFFTSamplingSize;
    }


    public int getNativeFFTOverlapSamples() {
        return mFFTOverlapSamples;
    }


    public int getDurationInSeconds() {
        return mBufferTestDurationInSeconds;
    }

    public BufferCallbackTimes getPlayerCallbackTimes() {
        return mPlayerCallbackTimes;
    }

    public BufferCallbackTimes getRecorderCallbackTimes() {
        return mRecorderCallbackTimes;
    }
}
