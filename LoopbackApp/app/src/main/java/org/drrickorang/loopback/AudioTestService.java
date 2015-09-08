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

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.Binder;
import android.util.Log;


/**
 * This is the Service being created during the first onStart() in the activity.
 * Threads that are needed for the test will be created under this Service.
 * At the end of the test, this Service will pass the test results back to LoopbackActivity.
 */

public class AudioTestService extends Service {
    private static final String TAG = "AudioTestService";

    private final IBinder mBinder = new AudioTestBinder();


    @Override
    public void onCreate() {
        runAsForegroundService();
        log("Audio Test Service created!");
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        log("Service onStartCommand: " + startId);
        //runAsForegroundService();
        return Service.START_NOT_STICKY;
    }


    /**
     * This method will run the Service as Foreground Service, so the Service won't be killed
     * and restarted after a while.
     */
    private void runAsForegroundService() {
        int notificationId = 1400;
        Notification.Builder builder = new Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_launcher).setContentTitle("Loopback App")
                .setContentText("Please disregard me.");
        Notification notification;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            notification = builder.getNotification();
        } else {
            notification = builder.build();
        }

        startForeground(notificationId, notification);
    }


    @Override
    public IBinder onBind(Intent intent) {
        log("Service onBind");
        return mBinder;
    }


    @Override
    public void onDestroy() {
        log("Service onDestroy");
    }


    private static void log(String msg) {
        Log.v(TAG, msg);
    }


    /**
     * This class is only used by AudioTestService to create a binder that passes the
     * AudioTestService back to LoopbackActivity.
     */
    public class AudioTestBinder extends Binder {
        AudioTestService getService() {
            return AudioTestService.this;
        }
    }

}
