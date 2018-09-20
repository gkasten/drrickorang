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


/**
 * This file stores constants that are used across multiple files.
 */

public class Constant {
    public static final double TWO_PI = 2.0 * Math.PI;
    public static final long   NANOS_PER_MILLI = 1000000;
    public static final int    MILLIS_PER_SECOND = 1000;
    public static final int    SECONDS_PER_HOUR = 3600;

    // Must match constants in jni/loopback.h
    public static final int LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_LATENCY = 222;
    public static final int LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_BUFFER_PERIOD = 223;
    public static final int LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_CALIBRATION = 224;

    // Keys for CTS Loopback invocation
    public static final String KEY_CTSINVOCATION = "CTS-Test";
    public static final String KEY_NUMITERATIONS = "NumIterations";

    public static final int AUDIO_THREAD_TYPE_JAVA = 0;
    public static final int AUDIO_THREAD_TYPE_NATIVE_SLES = 1;
    public static final int AUDIO_THREAD_TYPE_NATIVE_AAUDIO = 2;

    public static final int BYTES_PER_SHORT = 2;
    public static final int SHORTS_PER_INT = 2;
    // FIXME Assumes 16-bit and mono, will not work for other bit depths or multi-channel.
    public static final int BYTES_PER_FRAME = 2;    // bytes per sample

    // prime numbers that don't overlap with FFT frequencies
    public static final double PRIME_FREQUENCY_1 = 703.0;
    public static final double PRIME_FREQUENCY_2 = 719.0;

    // amplitude for ToneGeneration
    public static final double SINE_WAVE_AMPLITUDE = 0.8;
    public static final double TWO_SINE_WAVES_AMPLITUDE = 0.4;

    // the number used to configured PipeShort/PipeByteBuffer
    public static final int MAX_SHORTS = 65536;

    // used to identify a variable is currently unknown
    public static final int UNKNOWN = -1;

    // used when joining a thread
    public static final int JOIN_WAIT_TIME_MS = 1000;

    // Loopback on Java thread test audio tone constants
    public static final int LOOPBACK_SAMPLE_FRAMES = 300;
    public static final double LOOPBACK_AMPLITUDE = 0.95;
    public static final int LOOPBACK_FREQUENCY = 4000;

    // Settings Activity and ADB constants
    public static final int SAMPLING_RATE_MAX = 48000;
    public static final int SAMPLING_RATE_MIN = 8000;
    public static final int CORRELATION_BLOCK_SIZE_MAX = 8192;
    public static final int CORRELATION_BLOCK_SIZE_MIN = 2048;
    public static final int DEFAULT_CORRELATION_BLOCK_SIZE = 4096;
    public static final int PLAYER_BUFFER_FRAMES_MAX = 8000;
    public static final int PLAYER_BUFFER_FRAMES_MIN = 16;
    public static final int RECORDER_BUFFER_FRAMES_MAX = 8000;
    public static final int RECORDER_BUFFER_FRAMES_MIN = 16;
    public static final int BUFFER_TEST_DURATION_SECONDS_MAX = 36000;
    public static final int BUFFER_TEST_DURATION_SECONDS_MIN = 1;
    public static final int BUFFER_TEST_WAVE_PLOT_DURATION_SECONDS_MAX = 120;
    public static final int BUFFER_TEST_WAVE_PLOT_DURATION_SECONDS_MIN = 1;
    public static final int MAX_NUM_LOAD_THREADS = 20;
    public static final int MIN_NUM_LOAD_THREADS = 0;
    public static final int MIN_NUM_CAPTURES = 1;
    public static final int MAX_NUM_CAPTURES = 100;
    public static final int DEFAULT_NUM_CAPTURES = 5;
    public static final int MIN_IGNORE_FIRST_FRAMES = 0;
    // impulse happens after 300 ms and shouldn't be ignored
    public static final int MAX_IGNORE_FIRST_FRAMES = SAMPLING_RATE_MAX * 3 / 10;
    public static final int DEFAULT_IGNORE_FIRST_FRAMES = 0;

    // Controls size of pre allocated timestamp arrays
    public static final int MAX_RECORDED_LATE_CALLBACKS_PER_SECOND = 2;
    // Ignore first few buffer callback periods
    public static final int BUFFER_PERIOD_DISCARD = 10;
}
