/*
 * Copyright (C) 2012 The Android Open Source Project
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

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;


/**
 * This class is used to save the results to a .wav file.
 * FIXME Should save data in original resolution instead of converting to 16-bit PCM.
 */

public class AudioFileOutput {
    private static final String TAG = "AudioFileOutput";

    private Uri              mUri;
    private Context          mContext;
    private FileOutputStream mOutputStream;
    private final int        mSamplingRate;


    public AudioFileOutput(Context context, Uri uri, int samplingRate) {
        mContext = context;
        mUri = uri;
        mSamplingRate = samplingRate;
    }


    public boolean writeData(double[] data) {
        return writeRingBufferData(data, 0, data.length);
    }

    /**
     * Writes recorded wav data to file
     *  endIndex <= startIndex:  Writes [startIndex, data.length) then [0, endIndex)
     *  endIndex > startIndex :  Writes [startIndex, endIndex)
     * Returns true on successful write to file
     */
    public boolean writeRingBufferData(double[] data, int startIndex, int endIndex) {

        boolean status = false;
        ParcelFileDescriptor parcelFileDescriptor = null;
        try {
            parcelFileDescriptor =
                    mContext.getContentResolver().openFileDescriptor(mUri, "w");
            FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
            mOutputStream = new FileOutputStream(fileDescriptor);
            log("Done creating output stream");
            int sampleCount = endIndex - startIndex;
            if (sampleCount <= 0) {
                sampleCount += data.length;
            }
            writeHeader(sampleCount);

            if (endIndex > startIndex) {
                writeDataBuffer(data, startIndex, endIndex);
            } else {
                writeDataBuffer(data, startIndex, data.length);
                writeDataBuffer(data, 0, endIndex);
            }

            mOutputStream.close();
            status = true;
            parcelFileDescriptor.close();
        } catch (Exception e) {
            mOutputStream = null;
            log("Failed to open wavefile" + e);
        } finally {
            try {
                if (parcelFileDescriptor != null) {
                    parcelFileDescriptor.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
                log("Error closing ParcelFile Descriptor");
            }
        }
        return status;
    }

    private void writeHeader(int samples) {
        if (mOutputStream != null) {
            try {
                int channels = 1;
                int blockAlignment = 2;
                int bitsPerSample = 16;
                byte[] chunkSize = new byte[4];
                byte[] dataSize = new byte[4];
                int tempChunkSize =  (samples * 2) + 36;
                chunkSize[3] = (byte) (tempChunkSize >> 24);
                chunkSize[2] = (byte) (tempChunkSize >> 16);
                chunkSize[1] = (byte) (tempChunkSize >> 8);
                chunkSize[0] = (byte) tempChunkSize;
                int tempDataSize  = samples * 2;
                dataSize[3] = (byte) (tempDataSize >> 24);
                dataSize[2] = (byte) (tempDataSize >> 16);
                dataSize[1] = (byte) (tempDataSize >> 8);
                dataSize[0] = (byte) tempDataSize;

                byte[] header = new byte[] {
                    'R', 'I', 'F', 'F',
                    chunkSize[0], chunkSize[1], chunkSize[2], chunkSize[3],
                    'W', 'A', 'V', 'E',
                    'f', 'm', 't', ' ',
                    16, 0, 0, 0,
                    1, 0,   // PCM
                    (byte) channels, 0,   // number of channels
                    (byte) mSamplingRate, (byte) (mSamplingRate >> 8), 0, 0,    // sample rate
                    0, 0, 0, 0, // byte rate
                    (byte) (channels * blockAlignment),
                    0,   // block alignment
                    (byte) bitsPerSample,
                    0,  // bits per sample
                    'd', 'a', 't', 'a',
                    dataSize[0], dataSize[1], dataSize[2], dataSize[3],
                };
                mOutputStream.write(header);
                log("Done writing header");
            } catch (IOException e) {
                Log.e(TAG, "Error writing header " + e);
            }
        }
    }


    private void writeDataBuffer(double[] data, int startIndex, int end) {
        if (mOutputStream != null) {
            try {
                int bufferSize = 1024; //blocks of 1024 samples
                byte[] buffer = new byte[bufferSize * 2];

                for (int ii = startIndex; ii < end; ii += bufferSize) {
                    //clear buffer
                    Arrays.fill(buffer, (byte) 0);
                    int bytesUsed = 0;
                    for (int jj = 0; jj < bufferSize; jj++) {
                        int index = ii + jj;
                        if (index >= end)
                            break;
                        int value = (int) Math.round(data[index] * Short.MAX_VALUE);
                        byte ba = (byte) (0xFF & (value >> 8));  //little-endian
                        byte bb = (byte) (0xFF & (value));
                        buffer[(jj * 2) + 1] = ba;
                        buffer[jj * 2]   = bb;
                        bytesUsed += 2;
                    }
                    mOutputStream.write(buffer, 0, bytesUsed);
                }
                log("Done writing data");
            } catch (IOException e) {
                Log.e(TAG, "Error writing data " + e);
            }
        }
    }


    private static void log(String msg) {
        Log.v(TAG, msg);
    }

}
