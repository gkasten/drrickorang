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

    public static final int LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_LATENCY = 222;
    public static final int LOOPBACK_PLUG_AUDIO_THREAD_TEST_TYPE_BUFFER_PERIOD = 223;

    public static final int AUDIO_THREAD_TYPE_JAVA = 0;
    public static final int AUDIO_THREAD_TYPE_NATIVE = 1;

    public static final int BYTES_PER_SHORT = 2;
    public static final int SHORTS_PER_INT = 2;
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
}
