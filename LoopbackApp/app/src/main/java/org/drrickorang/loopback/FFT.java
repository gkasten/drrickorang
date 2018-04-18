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
 * This class computes FFT of inputting data.
 * Note: this part of code is originally from another project, so there's actually multiple copies
 * of this code. Should somehow merge these copies in the future. Also, no modification on
 * naming has been made, but naming should be changed once we merge all copies.
 */

public class FFT {
    private int       m;
    private double[]  cos;   // precomputed cosine tables for FFT
    private double[]  sin;   // precomputed sine tables for FFT
    private final int mFFTSamplingSize;


    FFT(int FFTSamplingSize) {
        mFFTSamplingSize = FFTSamplingSize;

        // set up variables needed for computing FFT
        m = (int) (Math.log(mFFTSamplingSize) / Math.log(2));

        // Make sure n is a power of 2
        if (mFFTSamplingSize != (1 << m))
            throw new RuntimeException("FFT sampling size must be power of 2");

        // precomputed tables
        cos = new double[mFFTSamplingSize / 2];
        sin = new double[mFFTSamplingSize / 2];

        for (int i = 0; i < mFFTSamplingSize / 2; i++) {
            cos[i] = Math.cos(-2 * Math.PI * i / mFFTSamplingSize);
            sin[i] = Math.sin(-2 * Math.PI * i / mFFTSamplingSize);
        }
    }


    /**
     * Do FFT, and store the result's real part to "x", imaginary part to "y".
     */
    public void fft(double[] x, double[] y, int sign) {
        int i, j, k, n1, n2, a;
        double c, s, t1, t2;

        // Bit-reverse
        j = 0;
        n2 = mFFTSamplingSize / 2;
        for (i = 1; i < mFFTSamplingSize - 1; i++) {
            n1 = n2;
            while (j >= n1) {
                j = j - n1;
                n1 = n1 / 2;
            }
            j = j + n1;

            if (i < j) {
                t1 = x[i];
                x[i] = x[j];
                x[j] = t1;
                t1 = y[i];
                y[i] = y[j];
                y[j] = t1;
            }
        }

        // FFT
        n1 = 0;
        n2 = 1;

        for (i = 0; i < m; i++) {
            n1 = n2;
            n2 = n2 + n2;
            a = 0;

            for (j = 0; j < n1; j++) {
                c = cos[a];
                s = sign * sin[a];
                a += 1 << (m - i - 1);

                for (k = j; k < mFFTSamplingSize; k = k + n2) {
                    t1 = c * x[k + n1] - s * y[k + n1];
                    t2 = s * x[k + n1] + c * y[k + n1];
                    x[k + n1] = x[k] - t1;
                    y[k + n1] = y[k] - t2;
                    x[k] = x[k] + t1;
                    y[k] = y[k] + t2;
                }
            }
        }
    }

}
