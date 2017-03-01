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
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

class SoundLevelCalibration {
    private static final int SECONDS_PER_LEVEL = 1;
    private static final int MAX_STEPS = 15; // The maximum number of levels that should be tried
    private static final double CRITICAL_RATIO = 0.4; // Ratio of input over output amplitude at
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

    SoundLevelCalibration(int samplingRate, int playerBufferSizeInBytes,
                                 int recorderBufferSizeInBytes, int micSource, int performanceMode, Context context) {

        // TODO: Allow capturing wave data without doing glitch detection.
        CaptureHolder captureHolder = new CaptureHolder(0, "", false, false, false, context,
                samplingRate);
        // TODO: Run for less than 1 second.
        mNativeAudioThread = new NativeAudioThread(samplingRate, playerBufferSizeInBytes,
                recorderBufferSizeInBytes, micSource, performanceMode,
                Constant.LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_BUFFER_PERIOD, SECONDS_PER_LEVEL,
                SECONDS_PER_LEVEL, 0, captureHolder);
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    // TODO: Allow stopping in the middle of calibration
    int calibrate() {
        final int maxLevel = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int delta = (maxLevel + MAX_STEPS - 1) / MAX_STEPS; // round up
        int level;
        // TODO: Use a better algorithm such as binary search.
        for(level = maxLevel; level >= 0; level -= delta) {
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0);
            if (mChangeListener != null) {
                mChangeListener.go(level);
            }

            mNativeAudioThread.start();
            try {
                mNativeAudioThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            double[] data = mNativeAudioThread.getWaveData();
            mNativeAudioThread = new NativeAudioThread(mNativeAudioThread); // generate fresh thread
            double amplitude = averageAmplitude(data);
            Log.d(TAG, "calibrate: at sound level " + level + " volume was " + amplitude);

            if (amplitude < Constant.SINE_WAVE_AMPLITUDE * CRITICAL_RATIO) {
                Log.d(TAG, "calibrate: chose sound level " + level);
                break;
            }
        }

        // Return the maximum level if we can't find a proper one
        return level != 0 ? level : maxLevel;
    }

    // TODO: Only gives accurate results for an undistorted sine wave. Check for distortion.
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

    void setChangeListener(SoundLevelChangeListener changeListener) {
        mChangeListener = changeListener;
    }
}
