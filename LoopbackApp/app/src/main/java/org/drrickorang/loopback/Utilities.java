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
 * This class contains functions that can be reused in different classes.
 * TODO move to audio_utils
 */

public class Utilities {


    /** Multiply the input array with a hanning window. */
    public static void hanningWindow(double[] samples) {
        int length = samples.length;
        final double alpha = 0.5;
        final double beta = 0.5;
        double coefficient;
        for (int i = 0; i < length; i++) {
            coefficient = (Constant.TWO_PI * i) / (length - 1);
            samples[i] *= alpha - beta * Math.cos(coefficient);
        }

    }


    /** Round up to the nearest power of 2. */
    public static int roundup(int size)
    {
        // Integer.numberOfLeadingZeros() returns 32 for zero input
        if (size == 0) {
            size = 1;
        }

        int lz = Integer.numberOfLeadingZeros(size);
        int rounded = 0x80000000 >>> lz;
        // 0x800000001 and higher are actually rounded _down_ to prevent overflow
        if (size > rounded && lz > 0) {
            rounded <<= 1;
        }
        return rounded;
    }


    /**
     * Returns value if value is within inclusive bounds min through max
     * otherwise returns min or max according to if value is less than or greater than the range
     */
    public static int clamp(int value, int min, int max) {

        if (max < min) throw new UnsupportedOperationException("min must be <= max");

        if (value < min) return min;
        else if (value > max) return max;
        else return value;
    }
}
