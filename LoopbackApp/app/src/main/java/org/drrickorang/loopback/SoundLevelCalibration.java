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
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

class SoundLevelCalibration {
    private static final int SECONDS_PER_LEVEL = 1;
    private static final int MAX_STEPS = 15; // The maximum number of levels that should be tried
    private static final double CRITICAL_RATIO = 0.41; // Ratio of input over output amplitude at
                                                       // which the feedback loop neither decays nor
                                                       // grows (determined experimentally)
    private static final String TAG = "SoundLevelCalibration";

    private NativeAudioThread mNativeAudioThread = null;
    private AudioManager mAudioManager;

    private SoundLevelChangeListener mChangeListener;

    abstract static class SoundLevelChangeListener {
        // used to run the callback on the UI thread
        private Handler handler = new Handler(Looper.getMainLooper());

        abstract void onChange(int newLevel);

        private void go(final int newLevel) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    onChange(newLevel);
                }
            });
        }
    }

    SoundLevelCalibration(int threadType, int samplingRate, int playerBufferSizeInBytes,
            int recorderBufferSizeInBytes, int micSource, int performanceMode, Context context) {

        // TODO: Allow capturing wave data without doing glitch detection.
        CaptureHolder captureHolder = new CaptureHolder(0, "", false, false, false, context,
                samplingRate);
        // TODO: Run for less than 1 second.
        mNativeAudioThread = new NativeAudioThread(threadType, samplingRate,
                playerBufferSizeInBytes, recorderBufferSizeInBytes, micSource, performanceMode,
                Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_BUFFER_PERIOD, SECONDS_PER_LEVEL,
                SECONDS_PER_LEVEL, 0, captureHolder);
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    // TODO: Allow stopping in the middle of calibration
    int calibrate() {
        final int maxLevel = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int levelBottom = 0;
        int levelTop = maxLevel + 1;

        // The ratio of 0.36 seems to correctly calibrate with the Mir dongle on Taimen and Walleye,
        // but it does not work with the Mir dongle on devices with a 3.5mm jack. Using
        // CRITICAL_RATIO leads tp a correct calibration when plugging the loopback dongle into
        // a 3.5mm jack directly.
        // TODO: Find a better solution that, if possible, doesn't involve querying device names.
        final double ratio = (Build.DEVICE.equals("walleye")
                              || Build.DEVICE.equals("taimen")) ? 0.36 : CRITICAL_RATIO;

        while (levelTop - levelBottom > 1) {
            int level = (levelBottom + levelTop) / 2;
            Log.d(TAG, "setting level to " + level);
            setVolume(level);

            double amplitude = runAudioThread(mNativeAudioThread);
            mNativeAudioThread = new NativeAudioThread(mNativeAudioThread); // generate fresh thread
            Log.d(TAG, "calibrate: at sound level " + level + " volume was " + amplitude);

            if (amplitude < Constant.SINE_WAVE_AMPLITUDE * ratio) {
                levelBottom = level;
            } else {
                levelTop = level;
            }
        }
        // At this point, levelBottom has the highest proper value, if there is one (0 otherwise)
        Log.d(TAG, "Final level: " + levelBottom);
        setVolume(levelBottom);
        return levelBottom;
    }

    private double runAudioThread(NativeAudioThread thread) {
        // runs the native audio thread and returns the average amplitude
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        double[] data = thread.getWaveData();
        return averageAmplitude(data);
    }

    // TODO: Only gives accurate results for an undistorted sine wave. Check for distortion.
    // TODO move to audio_utils
    private static double averageAmplitude(double[] data) {
        if (data == null || data.length == 0) {
            return 0; // no data is present
        }
        double sumSquare = 0;
        for (double x : data) {
            sumSquare += x * x;
        }
        return Math.sqrt(2.0 * sumSquare / data.length); // amplitude of the sine wave
    }

    private void setVolume(int level) {
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0);
        if (mChangeListener != null) {
            mChangeListener.go(level);
        }
    }

    void setChangeListener(SoundLevelChangeListener changeListener) {
        mChangeListener = changeListener;
    }
}
