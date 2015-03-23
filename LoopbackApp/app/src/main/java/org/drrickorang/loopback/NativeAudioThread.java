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

//import android.content.Context;
//import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
//import android.media.MediaPlayer;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import android.os.Handler;
import  android.os.Message;

/**
 * A thread/audio track based audio synth.
 */
public class NativeAudioThread extends Thread {

    public boolean isRunning = false;
    double twoPi = 6.28318530718;

    public int mSessionId;

    public double[] mvSamples; //captured samples
    int mSamplesIndex;

    private final int mSecondsToRun = 2;
    public int mSamplingRate = 48000;
    private int mChannelConfigIn = AudioFormat.CHANNEL_IN_MONO;
    private int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;

    int mMinPlayBufferSizeInBytes = 0;
    int mMinRecordBuffSizeInBytes = 0;
    private int mChannelConfigOut = AudioFormat.CHANNEL_OUT_MONO;

    boolean isPlaying = false;
    private Handler mMessageHandler;

    static final int FUN_PLUG_NATIVE_AUDIO_THREAD_MESSAGE_REC_STARTED = 892;
    static final int FUN_PLUG_NATIVE_AUDIO_THREAD_MESSAGE_REC_COMPLETE = 893;

    public void setParams(int samplingRate, int playBufferInBytes, int recBufferInBytes) {
        mSamplingRate = samplingRate;

        mMinPlayBufferSizeInBytes = playBufferInBytes;
        mMinRecordBuffSizeInBytes = recBufferInBytes;

    }

    //JNI load
    static {
        System.loadLibrary("loopback");
        /* TODO: gracefully fail/notify if the library can't be loaded */
    }

    //jni calls
    public native long slesInit(int samplingRate, int frameCount);
    public native int slesProcessNext(long sles_data, double[] samples);
    public native int slesDestroy(long sles_data);

    public void run() {

        setPriority(Thread.MAX_PRIORITY);

        log(String.format("about to init, sampling rate: %d, buffer:%d", mSamplingRate,
                mMinPlayBufferSizeInBytes/2 ));
        long sles_data = slesInit(mSamplingRate, mMinPlayBufferSizeInBytes/2);
        log(String.format("sles_data = 0x%d",sles_data));

        double [] samples = new double[80000];
        mSamplesIndex = 0;
        int totalSamplesRead = 0;
        for (int ii=0; ii<mSecondsToRun; ii++) {
            log(String.format("block %d...",ii));
            int samplesRead = slesProcessNext(sles_data, samples);
            totalSamplesRead += samplesRead;
            log(" jni samples read:" + samplesRead + "  currentSampleIndex:"+mSamplesIndex);
            {
                for (int jj=0; jj<samplesRead && mSamplesIndex< mvSamples.length; jj++) {
                    mvSamples[mSamplesIndex++] = samples[jj];
                }
            }
        }

        log(String.format(" samplesRead: %d, samplesIndex:%d", totalSamplesRead, mSamplesIndex));
        log(String.format("about to destroy..."));
        int status = slesDestroy(sles_data);
        log(String.format("sles delete status: %d", status));

        endTest();
    }

    public void setMessageHandler(Handler messageHandler) {
        mMessageHandler = messageHandler;
    }

    public void togglePlay() {

    }

    public void runTest() {

        //erase output buffer
        if (mvSamples != null)
            mvSamples = null;

        //resize
        int nNewSize = mSamplingRate * mSecondsToRun; //5 seconds!
        mvSamples = new double[nNewSize];
        mSamplesIndex = 0; //reset index

        //start playing
        isPlaying = true;

        log(" Started capture test");
        if (mMessageHandler != null) {
            Message msg = Message.obtain();
            msg.what = FUN_PLUG_NATIVE_AUDIO_THREAD_MESSAGE_REC_STARTED;
            mMessageHandler.sendMessage(msg);
        }
    }

   public void endTest() {
       log("--Ending capture test--");
       isPlaying = false;


       if (mMessageHandler != null) {
           Message msg = Message.obtain();
           msg.what = FUN_PLUG_NATIVE_AUDIO_THREAD_MESSAGE_REC_COMPLETE;
           mMessageHandler.sendMessage(msg);
       }

   }

    public void finish() {

        if (isRunning) {
            isRunning = false;
            try {
                sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static void log(String msg) {
        Log.v("Loopback", msg);
    }

    double [] getWaveData () {
        return mvSamples;
    }

}  //end thread.
