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

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

public class LoopbackApplication extends Application {

    private int mSamplingRate = 48000;
    private int mPlayBufferSizeInBytes = 0;
    private int mRecordBuffSizeInBytes = 0;
    private int mAudioThreadType = 0; //0:Java, 1:Native (JNI)
    private int mMicSource = 3; //maps to MediaRecorder.AudioSource.VOICE_RECOGNITION;

    public static final int AUDIO_THREAD_TYPE_JAVA   = 0;
    public static final int AUDIO_THREAD_TYPE_NATIVE = 1;

    public static final int BYTES_PER_FRAME = 2;

    public void setDefaults () {

        if (isSafeToUseSles()) {
            mAudioThreadType = AUDIO_THREAD_TYPE_NATIVE;
        } else {

            mAudioThreadType = AUDIO_THREAD_TYPE_JAVA;
        }
        computeDefaults();
    }

    int getSamplingRate() {
        return mSamplingRate;
    }

    void setSamplingRate(int samplingRate) {
        mSamplingRate = samplingRate;
    }

    int getAudioThreadType() {
        return mAudioThreadType;
    }

    void setAudioThreadType(int audioThreadType) {
        mAudioThreadType = audioThreadType;
    }

    int getMicSource() { return mMicSource; }
    int mapMicSource(int threadType, int source) {
        int mappedSource = 0;
//        <item>DEFAULT</item>
//        <item>MIC</item>
//        <item>CAMCORDER</item>
//        <item>VOICE_RECOGNITION</item>
//        <item>VOICE_COMMUNICATION</item>

        if(threadType == AUDIO_THREAD_TYPE_JAVA) {

            switch (source) {
                default:
                case 0: //DEFAULT
                    mappedSource = MediaRecorder.AudioSource.DEFAULT;
                    break;
                case 1: //MIC
                    mappedSource = MediaRecorder.AudioSource.MIC;
                    break;
                case 2: //CAMCORDER
                    mappedSource = MediaRecorder.AudioSource.CAMCORDER;
                    break;
                case 3: //VOICE_RECOGNITION
                    mappedSource = MediaRecorder.AudioSource.VOICE_RECOGNITION;
                    break;
                case 4: //VOICE_COMMUNICATION
                    mappedSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION;
                    break;
            }
        } else if (threadType == AUDIO_THREAD_TYPE_NATIVE ) {

            //taken form OpenSLES_AndroidConfiguration.h
            switch (source) {
                default:
                case 0: //DEFAULT
                    mappedSource = 0x00; //SL_ANDROID_RECORDING_PRESET_NONE
                    break;
                case 1: //MIC
                    mappedSource = 0x01; //SL_ANDROID_RECORDING_PRESET_GENERIC
                    break;
                case 2: //CAMCORDER
                    mappedSource = 0x02; //SL_ANDROID_RECORDING_PRESET_CAMCORDER
                    break;
                case 3: //VOICE_RECOGNITION
                    mappedSource = 0x03; //SL_ANDROID_RECORDING_PRESET_VOICE_RECOGNITION
                    break;
                case 4: //VOICE_COMMUNICATION
                    mappedSource = 0x04; //SL_ANDROID_RECORDING_PRESET_VOICE_COMMUNICATION
                    break;
            }
        }

        return mappedSource;
    }

    String getMicSourceString(int source) {

        String name = null;

        String[] myArray = getResources().getStringArray(R.array.mic_source_array);
        if(myArray != null && source>=0 && source < myArray.length) {
            name = myArray[source];
        }
        return name;
    }

    void setMicSource(int micSource) { mMicSource = micSource; }

    int getPlayBufferSizeInBytes() {
        return mPlayBufferSizeInBytes;
    }

    void setPlayBufferSizeInBytes(int playBufferSizeInBytes) {
        mPlayBufferSizeInBytes = playBufferSizeInBytes;
    }

    int getRecordBufferSizeInBytes() {
        return mRecordBuffSizeInBytes;
    }

    void setRecordBufferSizeInBytes(int recordBufferSizeInBytes) {
        mRecordBuffSizeInBytes = recordBufferSizeInBytes;
    }

    public void computeDefaults() {

        int samplingRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
        setSamplingRate(samplingRate);



        if( mAudioThreadType == AUDIO_THREAD_TYPE_NATIVE) {

            int minBufferSizeInFrames;
            if (isSafeToUseGetProperty()) {
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                String value = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
                minBufferSizeInFrames = Integer.parseInt(value);
            } else {
                minBufferSizeInFrames = 1024;
                log("On button test micSource Name: " );
            }
            int minBufferSizeInBytes = BYTES_PER_FRAME * minBufferSizeInFrames;

            setPlayBufferSizeInBytes(minBufferSizeInBytes);
            setRecordBufferSizeInBytes(minBufferSizeInBytes);
        } else {

            int minPlayBufferSizeInBytes = AudioTrack.getMinBufferSize(samplingRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            setPlayBufferSizeInBytes(minPlayBufferSizeInBytes);

            int minRecBufferSizeInBytes =  AudioRecord.getMinBufferSize(samplingRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            setRecordBufferSizeInBytes(minRecBufferSizeInBytes);
        }

        //log("computed defaults");

    }

    String getSystemInfo() {

        String info = null;

        try {
            int versionCode = getApplicationContext().getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), 0).versionCode;
            String versionName = getApplicationContext().getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), 0).versionName;
            info = String.format("App ver. " +versionCode +"."+ versionName + " | " +Build.MODEL + " | " + Build.FINGERPRINT);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        return info;
    }

    boolean isSafeToUseSles() {
        return  Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD;
    }

    boolean isSafeToUseGetProperty() {
        return  Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        setDefaults();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
    }

    private static void log(String msg) {
        Log.v("Recorder", msg);
    }
}
