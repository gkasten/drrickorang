/*
 * Copyright (C) 2017 The Android Open Source Project
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

// Object to store the settings of the test that can be computed
// automatically by the test threads.
public class TestSettings {
    public TestSettings(int samplingRate, int playerBufferSizeInBytes,
            int recorderBuffSizeInBytes) {
        mSamplingRate = samplingRate;
        mPlayerBufferSizeInBytes = playerBufferSizeInBytes;
        mRecorderBuffSizeInBytes = recorderBuffSizeInBytes;
    }

    public int getSamplingRate() {
        return mSamplingRate;
    }

    public int getPlayerBufferSizeInBytes() {
        return mPlayerBufferSizeInBytes;
    }

    public int getRecorderBufferSizeInBytes() {
        return mRecorderBuffSizeInBytes;
    }

    public void setSamplingRate(int samplingRate) {
        mSamplingRate = Utilities.clamp(samplingRate,
                Constant.SAMPLING_RATE_MIN, Constant.SAMPLING_RATE_MAX);
    }

    public void setPlayerBufferSizeInBytes(int playerBufferSizeInBytes) {
        mPlayerBufferSizeInBytes = Utilities.clamp(playerBufferSizeInBytes,
                Constant.PLAYER_BUFFER_FRAMES_MIN, Constant.PLAYER_BUFFER_FRAMES_MAX);
    }

    public void setRecorderBufferSizeInBytes(int recorderBufferSizeInBytes) {
        mRecorderBuffSizeInBytes = Utilities.clamp(recorderBufferSizeInBytes,
                Constant.RECORDER_BUFFER_FRAMES_MIN, Constant.RECORDER_BUFFER_FRAMES_MAX);
    }

    private int mSamplingRate;
    private int mPlayerBufferSizeInBytes;
    private int mRecorderBuffSizeInBytes;
}
