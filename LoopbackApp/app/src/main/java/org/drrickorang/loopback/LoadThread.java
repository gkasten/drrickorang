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

import android.util.Log;


/**
 * This thread is used to add load to CPU, in order to test performance of audio under load.
 */

public class LoadThread extends Thread {
    private static final String TAG = "LoadThread";

    private volatile boolean mIsRunning;

    public LoadThread(String threadName) {
        super(threadName);
    }

    public void run() {
        log("Entering load thread");
        long count = 0;
        mIsRunning = true;
        while (mIsRunning) {
            count++;
        }

        log("exiting CPU load thread with count = " + count);
    }


    public void requestStop() {
        mIsRunning = false;
    }


    private static void log(String msg) {
        Log.v(TAG, msg);
    }

}
