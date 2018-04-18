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
 * This class is a pipe that allows one writer and one reader.
 */

public abstract class Pipe {
    public static final int OVERRUN = -2;   // when there's an overrun, return this value

    protected int       mSamplesOverrun;
    protected int       mOverruns;
    protected final int mMaxValues;   // always a power of two

    /** maxSamples must be >= 2. */
    public Pipe(int maxSamples) {
        mMaxValues = Utilities.roundup(maxSamples); // round up to the nearest power of 2
    }

    /**
     * Read at most "count" number of samples into array "buffer", starting from index "offset".
     * If the available samples to read is smaller than count, just read as much as it can and
     * return the amount of samples read (non-blocking). offset + count must be <= buffer.length.
     */
    public abstract int read(short[] buffer, int offset, int count);

    /** Return the amount of samples available to read. */
    public abstract int availableToRead();

    /** Clear the pipe. */
    public abstract void flush();

}
