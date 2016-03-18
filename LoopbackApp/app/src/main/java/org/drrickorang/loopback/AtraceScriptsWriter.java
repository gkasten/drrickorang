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
 * limitations under the License
 */

package org.drrickorang.loopback;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 *  Places loopback_listener shell script on device storage
 */
public class AtraceScriptsWriter {

    private static final String TAG = "AtraceScriptsWriter";
    private static final String LISTENER_SCRIPT_LOCATION =
            CaptureHolder.DIRECTORY + "/loopback_listener";

    /** Writes scripts to device storage, return true on successful write **/
    public static boolean writeScriptsToFile(Context ctx) {
        try {
            File file = new File(CaptureHolder.DIRECTORY);

            // Create a directory for script and signal file
            if (!file.exists()) {
                if (file.mkdir()) {
                    Log.d(TAG, "writeScriptsToFile: Loopback folder created");
                } else {
                    System.out.println("Failed to create folder!");
                    return false;
                }
            }
            // Check for writable directory that already existed or after creating
            if (!file.isDirectory() || !file.canWrite()) {
                Log.d(TAG, "writeScriptsToFile: " + CaptureHolder.DIRECTORY
                        + (!file.isDirectory() ? "is not a directory " : "")
                        + (!file.canWrite() ? "is not writable" : ""));
                return false;
            }
            copyResToFile(ctx, R.raw.loopback_listener, LISTENER_SCRIPT_LOCATION);
        } catch (IOException e) {
            Log.e(TAG, "Unable to write script to file", e);
            return false;
        }
        return true;
    }

    private static void copyResToFile(Context ctx, int resId, String targetFile)
            throws IOException {
        InputStream inputStream = ctx.getResources().openRawResource(resId);
        OutputStream outputStream = new FileOutputStream(targetFile);
        copy(inputStream, outputStream);
        outputStream.close();
        inputStream.close();
    }


    private static int copy(InputStream input, OutputStream output) throws IOException {
        final int BYTES_TO_READ = 2048;
        byte[] buffer = new byte[BYTES_TO_READ];
        int total = 0;
        int n;
        while ((n = input.read(buffer)) != -1) {
            output.write(buffer, 0, n);
            total = total + n;
        }
        return total;
    }

}
