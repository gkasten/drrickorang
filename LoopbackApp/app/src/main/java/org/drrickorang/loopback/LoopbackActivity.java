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

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.util.Arrays;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Toast;
import android.widget.TextView;


/**
 * This is the main activity of the Loopback app. Two tests (latency test and buffer test) can be
 * initiated here. Note: buffer test and glitch detection is the same test, it's just that this test
 * has two parts of result.
 */

public class LoopbackActivity extends Activity {
    private static final String TAG = "LoopbackActivity";

    private static final int SAVE_TO_WAVE_REQUEST = 42;
    private static final int SAVE_TO_PNG_REQUEST = 43;
    private static final int SAVE_TO_TXT_REQUEST = 44;
    private static final int SAVE_RECORDER_BUFFER_PERIOD_TO_TXT_REQUEST = 45;
    private static final int SAVE_PLAYER_BUFFER_PERIOD_TO_TXT_REQUEST = 46;
    private static final int SETTINGS_ACTIVITY_REQUEST_CODE = 54;
    private static final int THREAD_SLEEP_DURATION_MS = 200;
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 201;

    LoopbackAudioThread  mAudioThread = null;
    NativeAudioThread    mNativeAudioThread = null;
    private WavePlotView mWavePlotView;
    private String       mCurrentTime = "IncorrectTime";  // The time the plot is acquired
    private String       mWaveFilePath; // path of the wave file

    private SeekBar  mBarMasterLevel; // drag the volume
    private TextView mTextInfo;
    private TextView mTextViewCurrentLevel;
    private TextView mTextViewEstimatedLatency;
    private Toast    mToast;

    private int          mTestType;
    private double []    mWaveData;    // this is where we store the data for the wave plot
    private Correlation  mCorrelation = new Correlation();
    private BufferPeriod mRecorderBufferPeriod = new BufferPeriod();
    private BufferPeriod mPlayerBufferPeriod = new BufferPeriod();

    // for native buffer period
    private int[] mNativeRecorderBufferPeriodArray;
    private int   mNativeRecorderMaxBufferPeriod;
    private int[] mNativePlayerBufferPeriodArray;
    private int   mNativePlayerMaxBufferPeriod;

    private static final String INTENT_SAMPLING_FREQUENCY = "SF";
    private static final String INTENT_FILENAME = "FileName";
    private static final String INTENT_RECORDER_BUFFER = "RecorderBuffer";
    private static final String INTENT_PLAYER_BUFFER = "PlayerBuffer";
    private static final String INTENT_AUDIO_THREAD = "AudioThread";
    private static final String INTENT_MIC_SOURCE = "MicSource";
    private static final String INTENT_AUDIO_LEVEL = "AudioLevel";
    private static final String INTENT_TEST_TYPE = "TestType";
    private static final String INTENT_BUFFER_TEST_DURATION = "BufferTestDuration";

    // for running the test using adb command
    private boolean mIntentRunning = false; // if it is running triggered by intent with parameters
    private String  mIntentFileName;
    private int     mIntentSamplingRate = 0;
    private int     mIntentPlayerBuffer = 0;
    private int     mIntentRecorderBuffer = 0;
    private int     mIntentMicSource = -1;
    private int     mIntentAudioThread = -1;
    private int     mIntentAudioLevel = -1;
    private int     mIntentTestType = -1;
    private int     mIntentBufferTestDuration = 0; // in second

    // Note: these four values should only be assigned in restartAudioSystem()
    private int   mAudioThreadType = Constant.UNKNOWN;
    private int   mSamplingRate;
    private int   mPlayerBufferSizeInBytes;
    private int   mRecorderBufferSizeInBytes;

    // for buffer test
    private int[]   mGlitchesData;
    private boolean mGlitchingIntervalTooLong;
    private int     mFFTSamplingSize;
    private int     mFFTOverlapSamples;
    private int     mBufferTestDuration; //in second

    // threads that load CPUs
    private static final int mNumberOfLoadThreads = 4;
    private LoadThread[]     mLoadThreads;

    // for getting the Service
    boolean mBound = false;
    private AudioTestService mAudioTestService;
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mAudioTestService = ((AudioTestService.AudioTestBinder) service).getService();
            mBound = true;
        }

        public void onServiceDisconnected(ComponentName className) {
            mAudioTestService = null;
            mBound = false;
        }
    };

    private Handler mMessageHandler = new Handler() {
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
            case LoopbackAudioThread.LOOPBACK_AUDIO_THREAD_MESSAGE_LATENCY_REC_STARTED:
                log("got message java latency test started!!");
                showToast("Java Latency Test Started");
                resetResults();
                refreshState();
                refreshPlots();
                break;
            case LoopbackAudioThread.LOOPBACK_AUDIO_THREAD_MESSAGE_LATENCY_REC_ERROR:
                log("got message java latency test rec can't start!!");
                showToast("Java Latency Test Recording Error. Please try again");
                refreshState();
                stopAudioTestThreads();
                mIntentRunning = false;
                refreshSoundLevelBar();
                break;
            case LoopbackAudioThread.LOOPBACK_AUDIO_THREAD_MESSAGE_LATENCY_REC_STOP:
            case LoopbackAudioThread.LOOPBACK_AUDIO_THREAD_MESSAGE_LATENCY_REC_COMPLETE:
                if (mAudioThread != null) {
                    mWaveData = mAudioThread.getWaveData();
                    mCorrelation.computeCorrelation(mWaveData, mSamplingRate);
                    log("got message java latency rec complete!!");
                    refreshPlots();
                    refreshState();
                    mCurrentTime = (String) DateFormat.format("MMddkkmmss",
                                            System.currentTimeMillis());
                    mBufferTestDuration = mAudioThread.getDurationInSeconds();

                    switch (msg.what) {
                    case LoopbackAudioThread.LOOPBACK_AUDIO_THREAD_MESSAGE_LATENCY_REC_STOP:
                        showToast("Java Latency Test Stopped");
                        break;
                    case LoopbackAudioThread.LOOPBACK_AUDIO_THREAD_MESSAGE_LATENCY_REC_COMPLETE:
                        showToast("Java Latency Test Completed");
                        break;
                    }

                    stopAudioTestThreads();
                    if (mIntentRunning && mIntentFileName != null && mIntentFileName.length() > 0) {
                        saveAllTo(mIntentFileName);
                    }
                    mIntentRunning = false;
                }
                refreshSoundLevelBar();
                break;
            case LoopbackAudioThread.LOOPBACK_AUDIO_THREAD_MESSAGE_BUFFER_REC_STARTED:
                log("got message java buffer test rec started!!");
                showToast("Java Buffer Test Started");
                resetResults();
                refreshState();
                refreshPlots();
                break;
            case LoopbackAudioThread.LOOPBACK_AUDIO_THREAD_MESSAGE_BUFFER_REC_ERROR:
                log("got message java buffer test rec can't start!!");
                showToast("Java Buffer Test Recording Error. Please try again");
                refreshState();
                stopAudioTestThreads();
                mIntentRunning = false;
                refreshSoundLevelBar();
                break;
            case LoopbackAudioThread.LOOPBACK_AUDIO_THREAD_MESSAGE_BUFFER_REC_STOP:
            case LoopbackAudioThread.LOOPBACK_AUDIO_THREAD_MESSAGE_BUFFER_REC_COMPLETE:
                if (mAudioThread != null) {
                    mWaveData = mAudioThread.getWaveData();
                    mGlitchesData = mAudioThread.getAllGlitches();
                    mGlitchingIntervalTooLong = mAudioThread.getGlitchingIntervalTooLong();
                    mFFTSamplingSize = mAudioThread.getFFTSamplingSize();
                    mFFTOverlapSamples = mAudioThread.getFFTOverlapSamples();
                    refreshPlots();  // only plot that last few seconds
                    refreshState();
                    mCurrentTime = (String) DateFormat.format("MMddkkmmss",
                                            System.currentTimeMillis());
                    switch (msg.what) {
                    case LoopbackAudioThread.LOOPBACK_AUDIO_THREAD_MESSAGE_BUFFER_REC_STOP:
                        showToast("Java Buffer Test Stopped");
                        break;
                    case LoopbackAudioThread.LOOPBACK_AUDIO_THREAD_MESSAGE_BUFFER_REC_COMPLETE:
                        showToast("Java Buffer Test Completed");
                        break;
                    }

                    stopAudioTestThreads();
                    if (mIntentRunning && mIntentFileName != null && mIntentFileName.length() > 0) {
                        saveAllTo(mIntentFileName);
                    }
                    mIntentRunning = false;
                }
                refreshSoundLevelBar();
                break;
            case NativeAudioThread.LOOPBACK_NATIVE_AUDIO_THREAD_MESSAGE_LATENCY_REC_STARTED:
                log("got message native latency test rec started!!");
                showToast("Native Latency Test Started");
                resetResults();
                refreshState();
                refreshPlots();
                break;
            case NativeAudioThread.LOOPBACK_NATIVE_AUDIO_THREAD_MESSAGE_BUFFER_REC_STARTED:
                log("got message native buffer test rec started!!");
                showToast("Native Buffer Test Started");
                resetResults();
                refreshState();
                refreshPlots();
                break;
            case NativeAudioThread.LOOPBACK_NATIVE_AUDIO_THREAD_MESSAGE_LATENCY_REC_ERROR:
                log("got message native latency test rec can't start!!");
                showToast("Native Latency Test Recording Error. Please try again");
                refreshState();
                mIntentRunning = false;
                refreshSoundLevelBar();
                break;
            case NativeAudioThread.LOOPBACK_NATIVE_AUDIO_THREAD_MESSAGE_BUFFER_REC_ERROR:
                log("got message native buffer test rec can't start!!");
                showToast("Native Buffer Test Recording Error. Please try again");
                refreshState();
                mIntentRunning = false;
                refreshSoundLevelBar();
                break;
            case NativeAudioThread.LOOPBACK_NATIVE_AUDIO_THREAD_MESSAGE_REC_STOP:
            case NativeAudioThread.
                    LOOPBACK_NATIVE_AUDIO_THREAD_MESSAGE_BUFFER_REC_COMPLETE:
            case NativeAudioThread.LOOPBACK_NATIVE_AUDIO_THREAD_MESSAGE_LATENCY_REC_COMPLETE:
            case NativeAudioThread.
                    LOOPBACK_NATIVE_AUDIO_THREAD_MESSAGE_REC_COMPLETE_ERRORS:
                if (mNativeAudioThread != null) {
                    mGlitchesData = mNativeAudioThread.getNativeAllGlitches();
                    mGlitchingIntervalTooLong = mNativeAudioThread.getGlitchingIntervalTooLong();
                    mFFTSamplingSize = mNativeAudioThread.getNativeFFTSamplingSize();
                    mFFTOverlapSamples = mNativeAudioThread.getNativeFFTOverlapSamples();
                    mBufferTestDuration = mNativeAudioThread.getDurationInSeconds();
                    mWaveData = mNativeAudioThread.getWaveData();
                    mNativeRecorderBufferPeriodArray = mNativeAudioThread.getRecorderBufferPeriod();
                    mNativeRecorderMaxBufferPeriod = mNativeAudioThread.
                            getRecorderMaxBufferPeriod();
                    mNativePlayerBufferPeriodArray = mNativeAudioThread.getPlayerBufferPeriod();
                    mNativePlayerMaxBufferPeriod = mNativeAudioThread.getPlayerMaxBufferPeriod();

                    if (msg.what != NativeAudioThread.
                            LOOPBACK_NATIVE_AUDIO_THREAD_MESSAGE_BUFFER_REC_COMPLETE) {
                        mCorrelation.computeCorrelation(mWaveData, mSamplingRate);
                    }

                    log("got message native buffer test rec complete!!");
                    refreshPlots();
                    refreshState();
                    mCurrentTime = (String) DateFormat.format("MMddkkmmss",
                                                              System.currentTimeMillis());
                    switch (msg.what) {
                    case NativeAudioThread.
                            LOOPBACK_NATIVE_AUDIO_THREAD_MESSAGE_REC_COMPLETE_ERRORS:
                        showToast("Native Test Completed with Destroying Errors");
                        break;
                    case NativeAudioThread.LOOPBACK_NATIVE_AUDIO_THREAD_MESSAGE_REC_STOP:
                        showToast("Native Test Stopped");
                        break;
                    default:
                        showToast("Native Test Completed");
                        break;
                    }


                    stopAudioTestThreads();
                    if (mIntentRunning && mIntentFileName != null && mIntentFileName.length() > 0) {
                        saveAllTo(mIntentFileName);
                    }
                    mIntentRunning = false;


                }
                refreshSoundLevelBar();
                break;
            default:
                log("Got message:" + msg.what);
                break;
            }
        }
    };


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the layout for this activity. You can find it
        View view = getLayoutInflater().inflate(R.layout.main_activity, null);
        setContentView(view);

        mTextInfo = (TextView) findViewById(R.id.textInfo);
        mBarMasterLevel = (SeekBar) findViewById(R.id.BarMasterLevel);

        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        mBarMasterLevel.setMax(maxVolume);

        mBarMasterLevel.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                am.setStreamVolume(AudioManager.STREAM_MUSIC,
                        progress, 0);
                refreshState();
                log("Changed stream volume to: " + progress);
            }
        });
        mWavePlotView = (WavePlotView) findViewById(R.id.viewWavePlot);

        mTextViewCurrentLevel = (TextView) findViewById(R.id.textViewCurrentLevel);
        mTextViewCurrentLevel.setTextSize(15);

        mTextViewEstimatedLatency = (TextView) findViewById(R.id.textViewEstimatedLatency);
        refreshState();

        applyIntent(getIntent());
    }


    @Override
    protected void onStart() {
        super.onStart();
        Intent audioTestIntent = new Intent(this, AudioTestService.class);
        startService(audioTestIntent);
        boolean bound = bindService(audioTestIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
        if (bound) {
            log("Successfully bound to service!");
        }
        else {
            log("Failed to bind service!");
        }
    }


    @Override
    protected void onStop() {
        super.onStop();
        log("Activity on stop!");
        // Unbind from the service
        if (mBound) {
            unbindService(mServiceConnection);
            mBound = false;
        }
    }


    @Override
    public void onNewIntent(Intent intent) {
        log("On New Intent called!");
        applyIntent(intent);
    }


    /**
     * This method will be called whenever the test starts running (either by operating on the
     * device or by adb command). In the case where the test is started through adb command,
     * adb parameters will be read into intermediate variables.
     */
    private void applyIntent(Intent intent) {
        Bundle b = intent.getExtras();
        if (b != null && !mIntentRunning) {
            // adb shell am start -n org.drrickorang.loopback/.LoopbackActivity
            // --ei SF 48000 --es FileName test1 --ei RecorderBuffer 512 --ei PlayerBuffer 512
            // --ei AudioThread 1 --ei MicSource 3 --ei AudioLevel 12
            // --ei TestType 223 --ei BufferTestDuration 60

            // Note: for native mode, player and recorder buffer sizes are the same, and can only be
            // set through player buffer size
            if (b.containsKey(INTENT_TEST_TYPE)) {
                mIntentTestType = b.getInt(INTENT_TEST_TYPE);
                mIntentRunning = true;
            }

            if (b.containsKey(INTENT_BUFFER_TEST_DURATION)) {
                mIntentBufferTestDuration = b.getInt(INTENT_BUFFER_TEST_DURATION);
                mIntentRunning = true;
            }

            if (b.containsKey(INTENT_SAMPLING_FREQUENCY)) {
                mIntentSamplingRate = b.getInt(INTENT_SAMPLING_FREQUENCY);
                mIntentRunning = true;
            }

            if (b.containsKey(INTENT_FILENAME)) {
                mIntentFileName = b.getString(INTENT_FILENAME);
                mIntentRunning = true;
            }

            if (b.containsKey(INTENT_RECORDER_BUFFER)) {
                mIntentRecorderBuffer = b.getInt(INTENT_RECORDER_BUFFER);
                mIntentRunning = true;
            }

            if (b.containsKey(INTENT_PLAYER_BUFFER)) {
                mIntentPlayerBuffer = b.getInt(INTENT_PLAYER_BUFFER);
                mIntentRunning = true;
            }

            if (b.containsKey(INTENT_AUDIO_THREAD)) {
                mIntentAudioThread = b.getInt(INTENT_AUDIO_THREAD);
                mIntentRunning = true;
            }

            if (b.containsKey(INTENT_MIC_SOURCE)) {
                mIntentMicSource = b.getInt(INTENT_MIC_SOURCE);
                mIntentRunning = true;
            }

            if (b.containsKey(INTENT_AUDIO_LEVEL)) {
                mIntentAudioLevel = b.getInt(INTENT_AUDIO_LEVEL);
                mIntentRunning = true;
            }

            log("Intent " + INTENT_TEST_TYPE + ": " + mIntentTestType);
            log("Intent " + INTENT_BUFFER_TEST_DURATION + ": " + mIntentBufferTestDuration);
            log("Intent " + INTENT_SAMPLING_FREQUENCY + ": " + mIntentSamplingRate);
            log("Intent " + INTENT_FILENAME + ": " + mIntentFileName);
            log("Intent " + INTENT_RECORDER_BUFFER + ": " + mIntentRecorderBuffer);
            log("Intent " + INTENT_PLAYER_BUFFER + ": " + mIntentPlayerBuffer);
            log("Intent " + INTENT_AUDIO_THREAD + ":" + mIntentAudioThread);
            log("Intent " + INTENT_MIC_SOURCE + ": " + mIntentMicSource);
            log("Intent " + INTENT_AUDIO_LEVEL + ": " + mIntentAudioLevel);

            if (!mIntentRunning) {
                log("No info to actually run intent.");
            }

            runIntentTest();
        } else {
            log("warning: can't run this intent, system busy");
            showToast("System Busy. Stop sending intents!");
        }
    }


    /**
     * In the case where the test is started through adb command, this method will change the
     * settings if any parameter is specified.
     */
    private void runIntentTest() {
        // mIntentRunning == true if test is started through adb command.
        if (mIntentRunning) {
            if (mIntentBufferTestDuration > 0) {
                getApp().setBufferTestDuration(mIntentBufferTestDuration);
            }

            if (mIntentAudioLevel >= 0) {
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                am.setStreamVolume(AudioManager.STREAM_MUSIC,
                        mIntentAudioLevel, 0);
            }

            if (mIntentSamplingRate != 0) {
                getApp().setSamplingRate(mIntentSamplingRate);
            }

            if (mIntentMicSource >= 0) {
                getApp().setMicSource(mIntentMicSource);
            }

            if (mIntentAudioThread >= 0) {
                getApp().setAudioThreadType(mIntentAudioThread);
                getApp().computeDefaults();
            }

            int bytesPerFrame = Constant.BYTES_PER_FRAME;

            if (mIntentRecorderBuffer > 0) {
                getApp().setRecorderBufferSizeInBytes(mIntentRecorderBuffer * bytesPerFrame);
            }

            if (mIntentPlayerBuffer > 0) {
                getApp().setPlayerBufferSizeInBytes(mIntentPlayerBuffer * bytesPerFrame);
            }

            refreshState();

            if (mIntentTestType >= 0) {
                switch (mIntentTestType) {
                case Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_LATENCY:
                    startLatencyTest();
                    break;
                case Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_BUFFER_PERIOD:
                    startBufferTest();
                    break;
                default:
                    assert(false);
                }
            } else {
                // if test type is not specified in command, just run latency test
                startLatencyTest();
            }

        }
    }


    /** Stop all currently running threads that are related to audio test. */
    private void stopAudioTestThreads() {
        log("stopping audio threads");
        if (mAudioThread != null) {
            try {
                mAudioThread.finish();
                mAudioThread.join(Constant.JOIN_WAIT_TIME_MS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mAudioThread = null;
        }

        if (mNativeAudioThread != null) {
            try {
                mNativeAudioThread.finish();
                mNativeAudioThread.join(Constant.JOIN_WAIT_TIME_MS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mNativeAudioThread = null;
        }

        stopLoadThreads();
        System.gc();
    }


    public void onDestroy() {
        stopAudioTestThreads();
        super.onDestroy();
        stopService(new Intent(this, AudioTestService.class));
    }


    @Override
    protected void onResume() {
        super.onResume();
        log("on resume called");
    }


    @Override
    protected void onPause() {
        super.onPause();
    }


    /** Check if the app is busy (running test). */
    public boolean isBusy() {
        boolean busy = false;

        if (mAudioThread != null && mAudioThread.mIsRunning) {
            busy = true;
        }

        if (mNativeAudioThread != null && mNativeAudioThread.mIsRunning) {
            busy = true;
        }

        return busy;
    }


    /** Create a new audio thread according to the settings. */
    private void restartAudioSystem() {
        log("restart audio system...");

        int sessionId = 0; /* FIXME runtime test for am.generateAudioSessionId() in API 21 */

        mAudioThreadType = getApp().getAudioThreadType();
        mSamplingRate = getApp().getSamplingRate();
        mPlayerBufferSizeInBytes = getApp().getPlayerBufferSizeInBytes();
        mRecorderBufferSizeInBytes = getApp().getRecorderBufferSizeInBytes();
        int micSource = getApp().getMicSource();
        int bufferTestDurationInSeconds = getApp().getBufferTestDuration();
        int bufferTestWavePlotDurationInSeconds = getApp().getBufferTestWavePlotDuration();

        log(" current sampling rate: " + mSamplingRate);
        stopAudioTestThreads();

        // select java or native audio thread
        int micSourceMapped;
        switch (mAudioThreadType) {
        case Constant.AUDIO_THREAD_TYPE_JAVA:
            micSourceMapped = getApp().mapMicSource(Constant.AUDIO_THREAD_TYPE_JAVA, micSource);
            mAudioThread = new LoopbackAudioThread(mSamplingRate, mPlayerBufferSizeInBytes,
                          mRecorderBufferSizeInBytes, micSourceMapped, mRecorderBufferPeriod,
                          mPlayerBufferPeriod, mTestType, bufferTestDurationInSeconds,
                          bufferTestWavePlotDurationInSeconds, getApplicationContext());
            mAudioThread.setMessageHandler(mMessageHandler);
            mAudioThread.mSessionId = sessionId;
            mAudioThread.start();
            break;
        case Constant.AUDIO_THREAD_TYPE_NATIVE:
            micSourceMapped = getApp().mapMicSource(Constant.AUDIO_THREAD_TYPE_NATIVE, micSource);
            // Note: mRecorderBufferSizeInBytes will not actually be used, since recorder buffer
            // size = player buffer size in native mode
            mNativeAudioThread = new NativeAudioThread(mSamplingRate, mPlayerBufferSizeInBytes,
                                mRecorderBufferSizeInBytes, micSourceMapped, mTestType,
                                bufferTestDurationInSeconds, bufferTestWavePlotDurationInSeconds);
            mNativeAudioThread.setMessageHandler(mMessageHandler);
            mNativeAudioThread.mSessionId = sessionId;
            mNativeAudioThread.start();
            break;
        }

        startLoadThreads();

        mWavePlotView.setSamplingRate(mSamplingRate);
        refreshState();
    }


    /** Start all LoadThread. */
    private void startLoadThreads() {
        mLoadThreads = new LoadThread[mNumberOfLoadThreads];

        for (int i = 0; i < mLoadThreads.length; i++) {
            mLoadThreads[i] = new LoadThread();
            mLoadThreads[i].start();
        }
    }


    /** Stop all LoadThread. */
    private void stopLoadThreads() {
        log("stopping load threads");
        if (mLoadThreads != null) {
            for (int i = 0; i < mLoadThreads.length; i++) {
                if (mLoadThreads[i] != null) {
                    try {
                        mLoadThreads[i].requestStop();
                        mLoadThreads[i].join(Constant.JOIN_WAIT_TIME_MS);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    mLoadThreads[i] = null;
                }
            }
        }
    }


    private void resetBufferPeriodRecord(BufferPeriod recorderBufferPeriod,
                                         BufferPeriod playerBufferPeriod) {
        recorderBufferPeriod.resetRecord();
        playerBufferPeriod.resetRecord();
    }


    /** Start the latency test. */
    public void onButtonLatencyTest(View view) {

        // Ensure we have RECORD_AUDIO permissions
        // On Android M (API 23) we must request dangerous permissions each time we use them
        if (hasRecordAudioPermission()){
            startLatencyTest();
        } else {
            requestRecordAudioPermission();
        }
    }

    private void startLatencyTest() {

        if (!isBusy()) {
            mBarMasterLevel.setEnabled(false);
            resetBufferPeriodRecord(mRecorderBufferPeriod, mPlayerBufferPeriod);
            mTestType = Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_LATENCY;
            restartAudioSystem();
            try {
                Thread.sleep(THREAD_SLEEP_DURATION_MS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            switch (mAudioThreadType) {
            case Constant.AUDIO_THREAD_TYPE_JAVA:
                if (mAudioThread != null) {
                    mAudioThread.runTest();
                }
                break;
            case Constant.AUDIO_THREAD_TYPE_NATIVE:
                if (mNativeAudioThread != null) {
                    mNativeAudioThread.runTest();
                }
                break;
            }
        } else {
            showToast("Test in progress... please wait");
        }
    }


    /** Start the Buffer (Glitch Detection) Test. */
    public void onButtonBufferTest(View view) {

        if (hasRecordAudioPermission()){
            startBufferTest();
        } else {
            requestRecordAudioPermission();
        }
    }


    private void startBufferTest() {

        if (!isBusy()) {
            mBarMasterLevel.setEnabled(false);
            resetBufferPeriodRecord(mRecorderBufferPeriod, mPlayerBufferPeriod);
            mTestType = Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_BUFFER_PERIOD;
            restartAudioSystem();   // in this function a audio thread is created
            try {
                Thread.sleep(THREAD_SLEEP_DURATION_MS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            switch (mAudioThreadType) {
            case Constant.AUDIO_THREAD_TYPE_JAVA:
                if (mAudioThread != null) {
                    mAudioThread.runBufferTest();
                }
                break;
            case Constant.AUDIO_THREAD_TYPE_NATIVE:
                if (mNativeAudioThread != null) {
                    mNativeAudioThread.runBufferTest();
                }
                break;
            }
        } else {
            int duration = 0;
            switch (mAudioThreadType) {
            case Constant.AUDIO_THREAD_TYPE_JAVA:
                duration = mAudioThread.getDurationInSeconds();
                break;
            case Constant.AUDIO_THREAD_TYPE_NATIVE:
                duration = mNativeAudioThread.getDurationInSeconds();
                break;
            }
            showToast("Long-run Test in progress, in total should take " +
                    Integer.toString(duration) + "s, please wait");
        }
    }


    /** Stop the ongoing test. */
    public void onButtonStopTest(View view) throws InterruptedException{
        if (mAudioThread != null) {
            mAudioThread.requestStopTest();
        }

        if (mNativeAudioThread != null) {
            mNativeAudioThread.requestStopTest();
        }
    }


    /**
     * Save five files: one .png file for a screenshot on the main activity, one .wav file for
     * the plot displayed on the main activity, one .txt file for storing various test results, one
     * .txt file for storing recorder buffer period data, and one .txt file for storing player
     * buffer period data.
     */
    public void onButtonSave(View view) {
        if (!isBusy()) {
            //create filename with date
            String date = mCurrentTime;  // the time the plot is acquired
            //String micSource = getApp().getMicSourceString(getApp().getMicSource());
            String fileName = "loopback_" + date;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TITLE, fileName + ".txt"); //suggested filename
                startActivityForResult(intent, SAVE_TO_TXT_REQUEST);

                Intent intent2 = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent2.addCategory(Intent.CATEGORY_OPENABLE);
                intent2.setType("image/png");
                intent2.putExtra(Intent.EXTRA_TITLE, fileName + ".png"); //suggested filename
                startActivityForResult(intent2, SAVE_TO_PNG_REQUEST);

                //sometimes ".wav" will be added automatically, sometimes not
                Intent intent3 = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent3.addCategory(Intent.CATEGORY_OPENABLE);
                intent3.setType("audio/wav");
                intent3.putExtra(Intent.EXTRA_TITLE, fileName + ".wav"); //suggested filename
                startActivityForResult(intent3, SAVE_TO_WAVE_REQUEST);

                fileName = "loopback_" + date + "_recorderBufferPeriod";
                Intent intent4 = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent4.addCategory(Intent.CATEGORY_OPENABLE);
                intent4.setType("text/plain");
                intent4.putExtra(Intent.EXTRA_TITLE, fileName + ".txt");
                startActivityForResult(intent4, SAVE_RECORDER_BUFFER_PERIOD_TO_TXT_REQUEST);

                fileName = "loopback_" + date + "_playerBufferPeriod";
                Intent intent5 = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent5.addCategory(Intent.CATEGORY_OPENABLE);
                intent5.setType("text/plain");
                intent5.putExtra(Intent.EXTRA_TITLE, fileName + ".txt");
                startActivityForResult(intent5, SAVE_PLAYER_BUFFER_PERIOD_TO_TXT_REQUEST);
            } else {
                saveAllTo(fileName);
            }
        } else {
            showToast("Test in progress... please wait");
        }
    }


    /** See the documentation on onButtonSave() */
    public void saveAllTo(String fileName) {
        showToast("Saving files to: " + fileName + ".(wav,png,txt)");

        //save to a given uri... local file?
        Uri uri = Uri.parse("file://mnt/sdcard/" + fileName + ".wav");
        String temp = getPath(uri);

        // for some devices it cannot find the path
        if (temp != null) {
            File file = new File(temp);
            mWaveFilePath = file.getAbsolutePath();
        } else {
            mWaveFilePath = "";
        }

        saveToWaveFile(uri);
        Uri uri2 = Uri.parse("file://mnt/sdcard/" + fileName + ".png");
        saveScreenShot(uri2);

        Uri uri3 = Uri.parse("file://mnt/sdcard/" + fileName + ".txt");
        saveReport(uri3);

        String fileName2 = fileName + "_recorderBufferPeriod";
        Uri uri4 = Uri.parse("file://mnt/sdcard/" + fileName2 + ".txt");
        int[] bufferPeriodArray = null;
        int maxBufferPeriod = Constant.UNKNOWN;
        switch (mAudioThreadType) {
        case Constant.AUDIO_THREAD_TYPE_JAVA:
            bufferPeriodArray = mRecorderBufferPeriod.getBufferPeriodArray();
            maxBufferPeriod = mRecorderBufferPeriod.getMaxBufferPeriod();
            break;
        case Constant.AUDIO_THREAD_TYPE_NATIVE:
            bufferPeriodArray = mNativeRecorderBufferPeriodArray;
            maxBufferPeriod = mNativeRecorderMaxBufferPeriod;
            break;
        }
        saveBufferPeriod(uri4, bufferPeriodArray, maxBufferPeriod);

        String fileName3 = fileName + "_playerBufferPeriod";
        Uri uri5 = Uri.parse("file://mnt/sdcard/" + fileName3 + ".txt");
        bufferPeriodArray = null;
        maxBufferPeriod = Constant.UNKNOWN;
        switch (mAudioThreadType) {
        case Constant.AUDIO_THREAD_TYPE_JAVA:
            bufferPeriodArray = mPlayerBufferPeriod.getBufferPeriodArray();
            maxBufferPeriod = mPlayerBufferPeriod.getMaxBufferPeriod();
            break;
        case Constant.AUDIO_THREAD_TYPE_NATIVE:
            bufferPeriodArray = mNativePlayerBufferPeriodArray;
            maxBufferPeriod = mNativePlayerMaxBufferPeriod;
            break;
        }
        saveBufferPeriod(uri5, bufferPeriodArray, maxBufferPeriod);
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        log("ActivityResult request: " + requestCode + "  result:" + resultCode);
        if (resultCode == Activity.RESULT_OK) {
            Uri uri;
            switch (requestCode) {
            case SAVE_TO_WAVE_REQUEST:
                log("got SAVE TO WAV intent back!");
                if (resultData != null) {
                    uri = resultData.getData();
                    String temp = getPath(uri);
                    if (temp != null) {
                        File file = new File(temp);
                        mWaveFilePath = file.getAbsolutePath();
                    } else {
                        mWaveFilePath = "";
                    }
                    saveToWaveFile(uri);
                }
                break;
            case SAVE_TO_PNG_REQUEST:
                log("got SAVE TO PNG intent back!");
                if (resultData != null) {
                    uri = resultData.getData();
                    saveScreenShot(uri);
                }
                break;
            case SAVE_TO_TXT_REQUEST:
                if (resultData != null) {
                    uri = resultData.getData();
                    saveReport(uri);
                }
                break;
            case SAVE_RECORDER_BUFFER_PERIOD_TO_TXT_REQUEST:
                if (resultData != null) {
                    uri = resultData.getData();
                    int[] bufferPeriodArray = null;
                    int maxBufferPeriod = Constant.UNKNOWN;
                    switch (mAudioThreadType) {
                    case Constant.AUDIO_THREAD_TYPE_JAVA:
                        bufferPeriodArray = mRecorderBufferPeriod.getBufferPeriodArray();
                        maxBufferPeriod = mRecorderBufferPeriod.getMaxBufferPeriod();
                        break;
                    case Constant.AUDIO_THREAD_TYPE_NATIVE:
                        bufferPeriodArray = mNativeRecorderBufferPeriodArray;
                        maxBufferPeriod = mNativeRecorderMaxBufferPeriod;
                        break;
                    }
                    saveBufferPeriod(uri, bufferPeriodArray, maxBufferPeriod);
                }
                break;
            case SAVE_PLAYER_BUFFER_PERIOD_TO_TXT_REQUEST:
                if (resultData != null) {
                    uri = resultData.getData();
                    int[] bufferPeriodArray = null;
                    int maxBufferPeriod = Constant.UNKNOWN;
                    switch (mAudioThreadType) {
                    case Constant.AUDIO_THREAD_TYPE_JAVA:
                        bufferPeriodArray = mPlayerBufferPeriod.getBufferPeriodArray();
                        maxBufferPeriod = mPlayerBufferPeriod.getMaxBufferPeriod();
                        break;
                    case Constant.AUDIO_THREAD_TYPE_NATIVE:
                        bufferPeriodArray = mNativePlayerBufferPeriodArray;
                        maxBufferPeriod = mNativePlayerMaxBufferPeriod;
                        break;
                    }
                    saveBufferPeriod(uri, bufferPeriodArray, maxBufferPeriod);
                }
                break;
            case SETTINGS_ACTIVITY_REQUEST_CODE:
                log("return from new settings!");

                // here we wipe out all previous results, in order to avoid the condition where
                // previous results does not match the new settings
                resetResults();
                refreshState();
                refreshPlots();
                break;
            }
        }
    }


    /**
     * Refresh the sound level bar on the main activity to reflect the current sound level
     * of the system.
     */
    private void refreshSoundLevelBar() {
        mBarMasterLevel.setEnabled(true);
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int currentVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        mBarMasterLevel.setProgress(currentVolume);
    }


    /** Reset all results gathered from previous round of test (if any). */
    private void resetResults() {
        mCorrelation.mEstimatedLatencyMs = 0;
        mCorrelation.mEstimatedLatencyConfidence = 0;
        mRecorderBufferPeriod.resetRecord();
        mPlayerBufferPeriod.resetRecord();
        mNativeRecorderBufferPeriodArray = null;
        mNativePlayerBufferPeriodArray = null;
        mGlitchesData = null;
        mWaveData = null;
    }


    /** Get the file path from uri. Doesn't work for all devices. */
    private String getPath(Uri uri) {
        String[] projection = {MediaStore.Images.Media.DATA};
        Cursor cursor1 = getContentResolver().query(uri, projection, null, null, null);
        if (cursor1 == null) {
            return uri.getPath();
        }

        int ColumnIndex = cursor1.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor1.moveToFirst();
        String path = cursor1.getString(ColumnIndex);
        cursor1.close();
        return path;
    }


    /** Zoom out the plot to its full size. */
    public void onButtonZoomOutFull(View view) {
        double fullZoomOut = mWavePlotView.getMaxZoomOut();
        mWavePlotView.setZoom(fullZoomOut);
        mWavePlotView.refreshGraph();
    }


    /** Zoom out the plot. */
    public void onButtonZoomOut(View view) {
        double zoom = mWavePlotView.getZoom();
        zoom = 2.0 * zoom;
        mWavePlotView.setZoom(zoom);
        mWavePlotView.refreshGraph();
    }


    /** Zoom in the plot. */
    public void onButtonZoomIn(View view) {
        double zoom = mWavePlotView.getZoom();
        zoom = zoom / 2.0;
        mWavePlotView.setZoom(zoom);
        mWavePlotView.refreshGraph();
    }


/*
    public void onButtonZoomInFull(View view) {

        double minZoom = mWavePlotView.getMinZoomOut();

        mWavePlotView.setZoom(minZoom);
        mWavePlotView.refreshGraph();
    }
*/


    /** Go to AboutActivity. */
    public void onButtonAbout(View view) {
        if (!isBusy()) {
            Intent aboutIntent = new Intent(this, AboutActivity.class);
            startActivity(aboutIntent);
        } else
            showToast("Test in progress... please wait");
    }


    /** Go to RecorderBufferPeriodActivity */
    public void onButtonRecorderBufferPeriod(View view) {
        if (!isBusy()) {
            Intent RecorderBufferPeriodIntent = new Intent(this,
                                                RecorderBufferPeriodActivity.class);
            int recorderBufferSizeInFrames = mRecorderBufferSizeInBytes / Constant.BYTES_PER_FRAME;
            log("recorderBufferSizeInFrames:" + recorderBufferSizeInFrames);

            switch (mAudioThreadType) {
            case Constant.AUDIO_THREAD_TYPE_JAVA:
                RecorderBufferPeriodIntent.putExtra("recorderBufferPeriodTimeStampArray",
                        mRecorderBufferPeriod.getBufferPeriodTimeStampArray());
                RecorderBufferPeriodIntent.putExtra("recorderBufferPeriodArray",
                        mRecorderBufferPeriod.getBufferPeriodArray());
                RecorderBufferPeriodIntent.putExtra("recorderBufferPeriodMax",
                        mRecorderBufferPeriod.getMaxBufferPeriod());
                break;
            case Constant.AUDIO_THREAD_TYPE_NATIVE:
                // TODO change code in sles.cpp to collect timeStamp in native mode as well
                RecorderBufferPeriodIntent.putExtra("recorderBufferPeriodArray",
                        mNativeRecorderBufferPeriodArray);
                RecorderBufferPeriodIntent.putExtra("recorderBufferPeriodMax",
                        mNativeRecorderMaxBufferPeriod);
                break;
            }

            RecorderBufferPeriodIntent.putExtra("recorderBufferSize", recorderBufferSizeInFrames);
            RecorderBufferPeriodIntent.putExtra("samplingRate", mSamplingRate);
            startActivity(RecorderBufferPeriodIntent);
        } else
            showToast("Test in progress... please wait");
    }


    /** Go to PlayerBufferPeriodActivity */
    public void onButtonPlayerBufferPeriod(View view) {
        if (!isBusy()) {
            Intent PlayerBufferPeriodIntent = new Intent(this, PlayerBufferPeriodActivity.class);
            int playerBufferSizeInFrames = mPlayerBufferSizeInBytes / Constant.BYTES_PER_FRAME;

            switch (mAudioThreadType) {
            case Constant.AUDIO_THREAD_TYPE_JAVA:
                PlayerBufferPeriodIntent.putExtra("playerBufferPeriodTimeStampArray",
                        mPlayerBufferPeriod.getBufferPeriodTimeStampArray());
                PlayerBufferPeriodIntent.putExtra("playerBufferPeriodArray",
                        mPlayerBufferPeriod.getBufferPeriodArray());
                PlayerBufferPeriodIntent.putExtra("playerBufferPeriodMax",
                        mPlayerBufferPeriod.getMaxBufferPeriod());
                break;
            case Constant.AUDIO_THREAD_TYPE_NATIVE:
                // TODO change code in sles.cpp to collect timeStamp in native mode as well
                PlayerBufferPeriodIntent.putExtra("playerBufferPeriodArray",
                        mNativePlayerBufferPeriodArray);
                PlayerBufferPeriodIntent.putExtra("playerBufferPeriodMax",
                        mNativePlayerMaxBufferPeriod);
                break;
            }

            PlayerBufferPeriodIntent.putExtra("playerBufferSize", playerBufferSizeInFrames);
            PlayerBufferPeriodIntent.putExtra("samplingRate", mSamplingRate);
            startActivity(PlayerBufferPeriodIntent);
        } else
            showToast("Test in progress... please wait");
    }


    /** Go to GlitchesActivity. */
    public void onButtonGlitches(View view) {
        if (!isBusy()) {
            if (mGlitchesData != null) {
                int numberOfGlitches = estimateNumberOfGlitches(mGlitchesData);
                Intent GlitchesIntent = new Intent(this, GlitchesActivity.class);
                GlitchesIntent.putExtra("glitchesArray", mGlitchesData);
                GlitchesIntent.putExtra("FFTSamplingSize", mFFTSamplingSize);
                GlitchesIntent.putExtra("FFTOverlapSamples", mFFTOverlapSamples);
                GlitchesIntent.putExtra("samplingRate", mSamplingRate);
                GlitchesIntent.putExtra("glitchingIntervalTooLong", mGlitchingIntervalTooLong);
                GlitchesIntent.putExtra("numberOfGlitches", numberOfGlitches);
                startActivity(GlitchesIntent);
            } else {
                showToast("Please run the buffer test to get data");
            }

        } else
            showToast("Test in progress... please wait");
    }


    /** Go to SettingsActivity. */
    public void onButtonSettings(View view) {
        if (!isBusy()) {
            Intent mySettingsIntent = new Intent(this, SettingsActivity.class);
            //send settings
            startActivityForResult(mySettingsIntent, SETTINGS_ACTIVITY_REQUEST_CODE);
        } else {
            showToast("Test in progress... please wait");
        }
    }


    /** Redraw the plot according to mWaveData */
    void refreshPlots() {
        mWavePlotView.setData(mWaveData);
        mWavePlotView.redraw();
    }


    /** Refresh the text on the main activity that shows the app states and audio settings. */
    void refreshState() {
        log("refreshState!");

        //get current audio level
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int currentVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        mBarMasterLevel.setProgress(currentVolume);

        mTextViewCurrentLevel.setText(String.format("Sound Level: %d/%d", currentVolume,
                mBarMasterLevel.getMax()));

        log("refreshState 2b");

        // get info
        int samplingRate = getApp().getSamplingRate();
        int playerBuffer = getApp().getPlayerBufferSizeInBytes() / Constant.BYTES_PER_FRAME;
        int recorderBuffer = getApp().getRecorderBufferSizeInBytes() / Constant.BYTES_PER_FRAME;
        StringBuilder s = new StringBuilder(200);
        s.append("SR: " + samplingRate + " Hz");
        int audioThreadType = getApp().getAudioThreadType();
        switch (audioThreadType) {
        case Constant.AUDIO_THREAD_TYPE_JAVA:
            s.append(" Play Frames: " + playerBuffer);
            s.append(" Record Frames: " + recorderBuffer);
            s.append(" Audio: JAVA");
            break;
        case Constant.AUDIO_THREAD_TYPE_NATIVE:
            s.append(" Frames: " + playerBuffer);
            s.append(" Audio: NATIVE");
            break;
        }

        // mic source
        int micSource = getApp().getMicSource();
        String micSourceName = getApp().getMicSourceString(micSource);
        if (micSourceName != null) {
            s.append(String.format(" Mic: %s", micSourceName));
        }

        String info = getApp().getSystemInfo();
        s.append(" " + info);

        // show buffer test duration
        int bufferTestDuration = getApp().getBufferTestDuration();
        s.append("\nBuffer Test Duration: " + bufferTestDuration + "s");

        // show buffer test wave plot duration
        int bufferTestWavePlotDuration = getApp().getBufferTestWavePlotDuration();
        s.append("   Buffer Test Wave Plot Duration: last " + bufferTestWavePlotDuration + "s");

        mTextInfo.setText(s.toString());

        String estimatedLatency = "----";

        if (mCorrelation.mEstimatedLatencyMs > 0.0001) {
            estimatedLatency = String.format("%.2f ms", mCorrelation.mEstimatedLatencyMs);
        }

        mTextViewEstimatedLatency.setText(String.format("Latency: %s Confidence: %.2f",
                                  estimatedLatency, mCorrelation.mEstimatedLatencyConfidence));
    }


    private static void log(String msg) {
        Log.v(TAG, msg);
    }


    public void showToast(String msg) {
        if (mToast == null) {
            mToast = Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT);
        } else {
            mToast.setText(msg);
        }

        {
            mToast.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL, 10, 10);
            mToast.show();
        }
    }


    /** Get the application that runs this activity. Wrapper for getApplication(). */
    private LoopbackApplication getApp() {
        return (LoopbackApplication) this.getApplication();
    }


    /** Save a .wav file of the wave plot on the main activity. */
    void saveToWaveFile(Uri uri) {
        if (mWaveData != null && mWaveData.length > 0) {
            AudioFileOutput audioFileOutput = new AudioFileOutput(getApplicationContext(), uri,
                                                                  mSamplingRate);
            boolean status = audioFileOutput.writeData(mWaveData);
            if (status) {
                showToast("Finished exporting wave File " + mWaveFilePath);
            } else {
                showToast("Something failed saving wave file");
            }

        }
    }


    /** Save a screenshot of the main activity. */
    void saveScreenShot(Uri uri) {
        ParcelFileDescriptor parcelFileDescriptor = null;
        FileOutputStream outputStream;
        try {
            parcelFileDescriptor = getApplicationContext().getContentResolver().
                                   openFileDescriptor(uri, "w");

            FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
            outputStream = new FileOutputStream(fileDescriptor);

            log("Done creating output stream");

            LinearLayout LL = (LinearLayout) findViewById(R.id.linearLayoutMain);

            View v = LL.getRootView();
            v.setDrawingCacheEnabled(true);
            Bitmap b = v.getDrawingCache();

            //save
            b.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            parcelFileDescriptor.close();
            v.setDrawingCacheEnabled(false);
        } catch (Exception e) {
            log("Failed to open png file " + e);
        } finally {
            try {
                if (parcelFileDescriptor != null) {
                    parcelFileDescriptor.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
                log("Error closing ParcelFile Descriptor");
            }
        }
    }


    /**
     * Save a .txt file of the given buffer period's data.
     * First column is time, second column is count.
     */
    void saveBufferPeriod(Uri uri, int[] bufferPeriodArray, int maxBufferPeriod) {
        ParcelFileDescriptor parcelFileDescriptor = null;
        FileOutputStream outputStream;
        if (bufferPeriodArray != null) {
            try {
                parcelFileDescriptor = getApplicationContext().getContentResolver().
                        openFileDescriptor(uri, "w");

                FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
                outputStream = new FileOutputStream(fileDescriptor);
                log("Done creating output stream for saving buffer period");

                int usefulDataRange = Math.min(maxBufferPeriod + 1, bufferPeriodArray.length);
                int[] usefulBufferData = Arrays.copyOfRange(bufferPeriodArray, 0, usefulDataRange);

                String endline = "\n";
                String tab = "\t";
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < usefulBufferData.length; i++) {
                    sb.append(i + tab + usefulBufferData[i] + endline);
                }

                outputStream.write(sb.toString().getBytes());
                parcelFileDescriptor.close();

            } catch (Exception e) {
                log("Failed to open text file " + e);
            } finally {
                try {
                    if (parcelFileDescriptor != null) {
                        parcelFileDescriptor.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    log("Error closing ParcelFile Descriptor");
                }
            }
        }

    }

    /** Save a .txt file of various test results. */
    void saveReport(Uri uri) {
        ParcelFileDescriptor parcelFileDescriptor = null;
        FileOutputStream outputStream;
        try {
            parcelFileDescriptor = getApplicationContext().getContentResolver().
                                   openFileDescriptor(uri, "w");

            FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
            outputStream = new FileOutputStream(fileDescriptor);

            log("Done creating output stream");

            String endline = "\n";
            final int stringLength = 300;
            StringBuilder sb = new StringBuilder(stringLength);
            sb.append("DateTime = " + mCurrentTime + endline);
            sb.append(INTENT_SAMPLING_FREQUENCY + " = " + getApp().getSamplingRate() + endline);
            sb.append(INTENT_RECORDER_BUFFER + " = " + getApp().getRecorderBufferSizeInBytes() /
                                                       Constant.BYTES_PER_FRAME + endline);
            sb.append(INTENT_PLAYER_BUFFER + " = "
                      + getApp().getPlayerBufferSizeInBytes() / Constant.BYTES_PER_FRAME + endline);
            sb.append(INTENT_AUDIO_THREAD + " = " + getApp().getAudioThreadType() + endline);
            int micSource = getApp().getMicSource();


            String audioType = "unknown";
            switch (getApp().getAudioThreadType()) {
            case Constant.AUDIO_THREAD_TYPE_JAVA:
                audioType = "JAVA";
                break;
            case Constant.AUDIO_THREAD_TYPE_NATIVE:
                audioType = "NATIVE";
                break;
            }
            sb.append(INTENT_AUDIO_THREAD + "_String = " + audioType + endline);

            sb.append(INTENT_MIC_SOURCE + " = " + micSource + endline);
            sb.append(INTENT_MIC_SOURCE + "_String = " + getApp().getMicSourceString(micSource)
                      + endline);
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

            int currentVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            sb.append(INTENT_AUDIO_LEVEL + " = " + currentVolume + endline);

            switch (mTestType) {
            case Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_LATENCY:
                if (mCorrelation.mEstimatedLatencyMs > 0.0001) {
                    sb.append(String.format("LatencyMs = %.2f", mCorrelation.mEstimatedLatencyMs)
                            + endline);
                } else {
                    sb.append(String.format("LatencyMs = unknown") + endline);
                }

                sb.append(String.format("LatencyConfidence = %.2f",
                        mCorrelation.mEstimatedLatencyConfidence) + endline);
                break;
            case Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_BUFFER_PERIOD:
                sb.append("Buffer Test Duration (s) = " + mBufferTestDuration + endline);

                // report expected recorder buffer period
                int expectedRecorderBufferPeriod = mRecorderBufferSizeInBytes /
                        Constant.BYTES_PER_FRAME * Constant.MILLIS_PER_SECOND / mSamplingRate;
                sb.append("Expected Recorder Buffer Period (ms) = " + expectedRecorderBufferPeriod +
                        endline);

                // report recorder results
                int recorderBufferSize = mRecorderBufferSizeInBytes / Constant.BYTES_PER_FRAME;
                int[] recorderBufferData = null;
                int recorderBufferDataMax = 0;
                switch (mAudioThreadType) {
                    case Constant.AUDIO_THREAD_TYPE_JAVA:
                        recorderBufferData = mRecorderBufferPeriod.getBufferPeriodArray();
                        recorderBufferDataMax = mRecorderBufferPeriod.getMaxBufferPeriod();
                        break;
                    case Constant.AUDIO_THREAD_TYPE_NATIVE:
                        recorderBufferData = mNativeRecorderBufferPeriodArray;
                        recorderBufferDataMax = mNativeRecorderMaxBufferPeriod;
                        break;
                }
                if (recorderBufferData != null) {
                    // this is the range of data that actually has values
                    int usefulDataRange = Math.min(recorderBufferDataMax + 1,
                                          recorderBufferData.length);
                    int[] usefulBufferData = Arrays.copyOfRange(recorderBufferData, 0,
                                             usefulDataRange);
                    PerformanceMeasurement measurement = new PerformanceMeasurement(
                            recorderBufferSize, mSamplingRate, usefulBufferData);
                    boolean isBufferSizesMismatch = measurement.determineIsBufferSizesMatch();
                    double benchmark = measurement.computeWeightedBenchmark();
                    int outliers = measurement.countOutliers();
                    sb.append("Recorder Buffer Sizes Mismatch = " + isBufferSizesMismatch +
                              endline);
                    sb.append("Recorder Benchmark = " + benchmark + endline);
                    sb.append("Recorder Number of Outliers = " + outliers + endline);
                } else {
                    sb.append("Cannot Find Recorder Buffer Period Data!" + endline);
                }

                // report player results
                int playerBufferSize = mPlayerBufferSizeInBytes / Constant.BYTES_PER_FRAME;
                int[] playerBufferData = null;
                int playerBufferDataMax = 0;
                switch (mAudioThreadType) {
                    case Constant.AUDIO_THREAD_TYPE_JAVA:
                        playerBufferData = mPlayerBufferPeriod.getBufferPeriodArray();
                        playerBufferDataMax = mPlayerBufferPeriod.getMaxBufferPeriod();
                        break;
                    case Constant.AUDIO_THREAD_TYPE_NATIVE:
                        playerBufferData = mNativePlayerBufferPeriodArray;
                        playerBufferDataMax = mNativePlayerMaxBufferPeriod;
                        break;
                }
                if (playerBufferData != null) {
                    // this is the range of data that actually has values
                    int usefulDataRange = Math.min(playerBufferDataMax + 1,
                                          playerBufferData.length);
                    int[] usefulBufferData = Arrays.copyOfRange(playerBufferData, 0,
                                             usefulDataRange);
                    PerformanceMeasurement measurement = new PerformanceMeasurement(
                            playerBufferSize, mSamplingRate, usefulBufferData);
                    boolean isBufferSizesMismatch = measurement.determineIsBufferSizesMatch();
                    double benchmark = measurement.computeWeightedBenchmark();
                    int outliers = measurement.countOutliers();
                    sb.append("Player Buffer Sizes Mismatch = " + isBufferSizesMismatch + endline);
                    sb.append("Player Benchmark = " + benchmark + endline);
                    sb.append("Player Number of Outliers = " + outliers + endline);

                } else {
                    sb.append("Cannot Find Player Buffer Period Data!" + endline);
                }

                // report expected player buffer period
                int expectedPlayerBufferPeriod = mPlayerBufferSizeInBytes / Constant.BYTES_PER_FRAME
                                                 * Constant.MILLIS_PER_SECOND / mSamplingRate;
                if (audioType.equals("JAVA")) {
                    // javaPlayerMultiple depends on the samples written per AudioTrack.write()
                    int javaPlayerMultiple = 2;
                    expectedPlayerBufferPeriod *= javaPlayerMultiple;
                }
                sb.append("Expected Player Buffer Period (ms) = " + expectedPlayerBufferPeriod +
                          endline);

                // report estimated number of glitches
                int numberOfGlitches = estimateNumberOfGlitches(mGlitchesData);
                sb.append("Estimated Number of Glitches = " + numberOfGlitches + endline);

                // report if the total glitching interval is too long
                sb.append("Total glitching interval too long: " +
                          mGlitchingIntervalTooLong + endline);
            }


            String info = getApp().getSystemInfo();
            sb.append("SystemInfo = " + info + endline);

            outputStream.write(sb.toString().getBytes());
            parcelFileDescriptor.close();
        } catch (Exception e) {
            log("Failed to open text file " + e);
        } finally {
            try {
                if (parcelFileDescriptor != null) {
                    parcelFileDescriptor.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
                log("Error closing ParcelFile Descriptor");
            }
        }

    }


    /**
     * Estimate the number of glitches. This version of estimation will count two consecutive
     * glitching intervals as one glitch. This is because two time intervals are partly overlapped.
     * Note: If the total glitching intervals exceed the length of glitchesData, this estimation
     * becomes incomplete. However, whether or not the total glitching interval is too long will
     * also be indicated, and in the case it's true, we know something went wrong.
     */
    private static int estimateNumberOfGlitches(int[] glitchesData) {
        final int discard = 10; // don't count glitches occurring at the first few FFT interval
        boolean isPreviousGlitch = false; // is there a glitch in previous interval or not
        int previousFFTInterval = -1;
        int count = 0;
        // if there are three consecutive glitches, the first two will be counted as one,
        // the third will be counted as another one
        for (int i = 0; i < glitchesData.length; i++) {
            if (glitchesData[i] > discard) {
                if (glitchesData[i] == previousFFTInterval + 1 && isPreviousGlitch) {
                    isPreviousGlitch = false;
                    previousFFTInterval = glitchesData[i];
                } else {
                    isPreviousGlitch = true;
                    previousFFTInterval = glitchesData[i];
                    count += 1;
                }
            }

        }

        return count;
    }


    /**
     * Estimate the number of glitches. This version of estimation will count the whole consecutive
     * intervals as one glitch. This version is not currently used.
     * Note: If the total glitching intervals exceed the length of glitchesData, this estimation
     * becomes incomplete. However, whether or not the total glitching interval is too long will
     * also be indicated, and in the case it's true, we know something went wrong.
     */
    private static int estimateNumberOfGlitches2(int[] glitchesData) {
        final int discard = 10; // don't count glitches occurring at the first few FFT interval
        int previousFFTInterval = -1;
        int count = 0;
        for (int i = 0; i < glitchesData.length; i++) {
            if (glitchesData[i] > discard) {
                if (glitchesData[i] != previousFFTInterval + 1) {
                    count += 1;
                }
                previousFFTInterval = glitchesData[i];
            }
        }
        return count;
    }

    /**
     * Check whether we have the RECORD_AUDIO permission
     * @return true if we do
     */
    private boolean hasRecordAudioPermission(){
        boolean hasPermission = (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);

        log("Has RECORD_AUDIO permission? " + hasPermission);
        return hasPermission;
    }

    /**
     * Requests the RECORD_AUDIO permission from the user
     */
    private void requestRecordAudioPermission(){

        String requiredPermission = Manifest.permission.RECORD_AUDIO;

        // If the user previously denied this permission then show a message explaining why
        // this permission is needed
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                requiredPermission)) {

            showToast("This app needs to record audio through the microphone to test the device's performance");
        }

        // request the permission.
        ActivityCompat.requestPermissions(this,
                new String[]{requiredPermission},
                PERMISSIONS_REQUEST_RECORD_AUDIO);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {

        // We can ignore this call since we'll check for RECORD_AUDIO each time the
        // user does anything which requires that permission. We can't, however, delete
        // this method as this will cause ActivityCompat.requestPermissions to fail.
    }

}
