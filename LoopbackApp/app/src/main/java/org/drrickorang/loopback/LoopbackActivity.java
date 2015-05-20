/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.database.Cursor;
import android.provider.MediaStore;
import android.os.ParcelFileDescriptor;


import java.io.FileDescriptor;

import android.media.AudioManager;
//import android.media.MediaPlayer;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Toast;
import android.widget.TextView;
import android.content.Context;
import android.os.Handler;
import android.os.Message;

import java.io.FileOutputStream;
import java.io.File;

import android.os.Build;

public class LoopbackActivity extends Activity {
    /**
     * Member Vars
     */

    public final static String SETTINGS_OBJECT = "org.drrickorang.loopback.SETTINGS_OBJECT";

    private static final int SAVE_TO_WAVE_REQUEST = 42;
    private static final int SAVE_TO_PNG_REQUEST = 43;

    private static final int SETTINGS_ACTIVITY_REQUEST_CODE = 44;
    private static final int ABOUT_ACTIVITY_REQUEST_CODE = 45;
    LoopbackAudioThread audioThread = null;
    NativeAudioThread nativeAudioThread = null;
    private WavePlotView mWavePlotView;
    private String mCurrentTime = "IncorrectTime";  // The time the plot is acquired
    private String mFilePathWav;


    SeekBar  mBarMasterLevel; //drag the volumn
    TextView mTextInfo;
    TextView mTextViewCurrentLevel;
    TextView mTextViewEstimatedLatency;
    private double [] mWaveData;
    private Correlation mCorrelation = new Correlation();
    int mSamplingRate;

    Toast toast;

    private Handler mMessageHandler = new Handler() {
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch(msg.what) {
                case LoopbackAudioThread.FUN_PLUG_AUDIO_THREAD_MESSAGE_REC_STARTED:
                    log("got message java rec started!!");
                    showToast("Java Recording Started");
                    refreshState();
                    break;
                case LoopbackAudioThread.FUN_PLUG_AUDIO_THREAD_MESSAGE_REC_ERROR:
                    log("got message java rec can't start!!");
                    showToast("Java Recording Error. Please try again");
                    refreshState();
                    stopAudioThread();
                    break;
                case LoopbackAudioThread.FUN_PLUG_AUDIO_THREAD_MESSAGE_REC_COMPLETE:
                    if(audioThread != null) {
                        mWaveData = audioThread.getWaveData();
                        mSamplingRate = audioThread.mSamplingRate;
                        mCorrelation.computeCorrelation(mWaveData,mSamplingRate);
                        log("got message java rec complete!!");
                        refreshPlots();
                        refreshState();
                        mCurrentTime = (String) DateFormat.format("MMddkkmmss", System.currentTimeMillis());
                        showToast("Java Recording Completed");
                        stopAudioThread();
                    }
                    break;
                case NativeAudioThread.FUN_PLUG_NATIVE_AUDIO_THREAD_MESSAGE_REC_STARTED:
                    log("got message native rec started!!");
                    showToast("Native Recording Started");
                    refreshState();
                    break;
                case NativeAudioThread.FUN_PLUG_NATIVE_AUDIO_THREAD_MESSAGE_REC_ERROR:
                    log("got message native rec can't start!!");
                    showToast("Native Recording Error. Please try again");
                    refreshState();
                    break;
                case NativeAudioThread.FUN_PLUG_NATIVE_AUDIO_THREAD_MESSAGE_REC_COMPLETE:
                case NativeAudioThread.FUN_PLUG_NATIVE_AUDIO_THREAD_MESSAGE_REC_COMPLETE_ERRORS:
                    if(nativeAudioThread != null) {
                        mWaveData = nativeAudioThread.getWaveData();
                        mSamplingRate = nativeAudioThread.mSamplingRate;
                        mCorrelation.computeCorrelation(mWaveData, mSamplingRate);
                        log("got message native rec complete!!");
                        refreshPlots();
                        refreshState();
                        if(msg.what == NativeAudioThread.FUN_PLUG_NATIVE_AUDIO_THREAD_MESSAGE_REC_COMPLETE_ERRORS) {
                            mCurrentTime = (String) DateFormat.format("MMddkkmmss", System.currentTimeMillis());
                            showToast("Native Recording Completed with ERRORS");
                        } else {
                            mCurrentTime = (String) DateFormat.format("MMddkkmmss", System.currentTimeMillis());
                            showToast("Native Recording Completed");
                        }
                        stopAudioThread();
                    }
                    break;
                default:
                    log("Got message:"+msg.what);
                    break;
            }
        }
    };


    // Thread thread;

    /**
     * Called with the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the layout for this activity. You can find it
        View view = getLayoutInflater().inflate(R.layout.main_activity, null);
        setContentView(view);

        mTextInfo = (TextView) findViewById(R.id.textInfo);
        mBarMasterLevel = (SeekBar) findViewById(R.id.BarMasterLevel);

        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        mBarMasterLevel.setMax(maxVolume);

        mBarMasterLevel.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                am.setStreamVolume(AudioManager.STREAM_MUSIC,
                        progress, 0);
                refreshState();
                log("Changed stream volume to: " + progress);
            }
        });
        mWavePlotView = (WavePlotView) findViewById(R.id.viewWavePlot);

        mTextViewCurrentLevel = (TextView) findViewById(R.id.textViewCurrentLevel);

        mTextViewEstimatedLatency = (TextView) findViewById(R.id.textViewEstimatedLatency);
        refreshState();
    }

    private void stopAudioThread() {
        log("stopping audio threads");
        if (audioThread != null) {
            audioThread.isRunning = false;
            // isRunning = false;
            try {
                audioThread.finish();
                audioThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            audioThread = null;
        }
        if (nativeAudioThread != null) {
            nativeAudioThread.isRunning = false;
            // isRunning = false;
            try {
                nativeAudioThread.finish();
                nativeAudioThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            nativeAudioThread = null;
        }
        System.gc();
    }

    public void onDestroy() {
        stopAudioThread();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        // TODO Auto-generated method stub
        super.onResume();
        //web.loadUrl(stream);
        log("on resume called");

        //restartAudioSystem();
    }

    @Override
    protected void onPause () {
        super.onPause();
        //stop audio system
        stopAudioThread();
    }

    public boolean isBusy() {

        boolean busy = false;

        if( audioThread != null) {
            if(audioThread.isRunning)
                busy = true;
        }

        if( nativeAudioThread != null) {
            if(nativeAudioThread.isRunning)
                busy = true;
        }

        return busy;
     }

    private void restartAudioSystem() {

        log("restart audio system...");

        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int sessionId = 0; /* FIXME runtime test for am.generateAudioSessionId() in API 21 */

        int samplingRate = getApp().getSamplingRate();
        int playbackBuffer = getApp().getPlayBufferSizeInBytes();
        int recordBuffer = getApp().getRecordBufferSizeInBytes();
        int micSource = getApp().getMicSource();

        log(" current sampling rate: " + samplingRate);
        stopAudioThread();

        //select if java or native audio thread
        if (getApp().getAudioThreadType() == LoopbackApplication.AUDIO_THREAD_TYPE_JAVA ) {
            int micSourceMapped = getApp().mapMicSource(LoopbackApplication.AUDIO_THREAD_TYPE_JAVA ,micSource);
            audioThread = new LoopbackAudioThread();
            audioThread.setMessageHandler(mMessageHandler);
            audioThread.mSessionId = sessionId;
            audioThread.setParams(samplingRate, playbackBuffer, recordBuffer,micSourceMapped);
            audioThread.start();
        } else {
            int micSourceMapped = getApp().mapMicSource(LoopbackApplication.AUDIO_THREAD_TYPE_NATIVE ,micSource);
            nativeAudioThread = new NativeAudioThread();
            nativeAudioThread.setMessageHandler(mMessageHandler);
            nativeAudioThread.mSessionId = sessionId;
            nativeAudioThread.setParams(samplingRate, playbackBuffer, recordBuffer,micSourceMapped);
            nativeAudioThread.start();
        }
        mWavePlotView.setSamplingRate( samplingRate);


        //first refresh
        refreshState();
    }
    private void resetBufferPeriodRecord() {
        BufferPeriod.resetRecord();
    }

    /** Called when the user clicks the button */
    public void onButtonTest(View view) {
        int samplingRate = getApp().getSamplingRate();
        int playbackBuffer = getApp().getPlayBufferSizeInBytes()/getApp().BYTES_PER_FRAME;
        int recordBuffer = getApp().getRecordBufferSizeInBytes()/getApp().BYTES_PER_FRAME;
        int micSource = getApp().getMicSource();
        String micSourceName = getApp().getMicSourceString(micSource);
        int audioThreadType = getApp().getAudioThreadType();
        log("On button test sampling rate: " + samplingRate);
        log("On button test playbackBuffer: " + playbackBuffer);
        log("On button test recordBuffer: " + recordBuffer);
        log("On button test micSource Name: " + micSourceName);
        log("On button test thread type: " + audioThreadType);  //java =0, native = 1

        if( !isBusy()) {
            restartAudioSystem();
            resetBufferPeriodRecord();
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (getApp().getAudioThreadType() == LoopbackApplication.AUDIO_THREAD_TYPE_JAVA) {
                if (audioThread != null) {
                    audioThread.runTest();
                }
            } else {
                if (nativeAudioThread != null) {
                    nativeAudioThread.runTest();
                }
            }

        } else {
            //please wait, or restart application.
//            Toast.makeText(getApplicationContext(), "Test in progress... please wait",
//                    Toast.LENGTH_SHORT).show();

            showToast("Test in progress... please wait");
        }

    }

    /** Called when the user clicks the button */
    public void onButtonSave(View view) {

        //create filename with date
        String date = mCurrentTime;  // the time the plot is acquired
        String micSource = getApp().getMicSourceString(getApp().getMicSource());
        String fileName = "loopback_"+/*micSource+"_"+*/date;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {


            Intent intent2 = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent2.addCategory(Intent.CATEGORY_OPENABLE);
            intent2.setType("image/png");

            intent2.putExtra(Intent.EXTRA_TITLE, fileName + ".png"); //suggested filename
            startActivityForResult(intent2, SAVE_TO_PNG_REQUEST);

            // browser.
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("audio/wav");

            // sometimes ".wav" will be added automatically, sometimes not
            intent.putExtra(Intent.EXTRA_TITLE, fileName + ".wav"); //suggested filename
            startActivityForResult(intent, SAVE_TO_WAVE_REQUEST);

        } else {
            showToast("Saving Wave to: "+fileName+".wav");

            //save to a given uri... local file?
            Uri uri = Uri.parse("file://mnt/sdcard/"+fileName+".wav");

            // for some devices it cannot find the path
            String temp = getPath1(uri);
            if (temp != null) {
                File file = new File(temp);
                mFilePathWav = file.getAbsolutePath();
            } else {
                mFilePathWav = "";

            }

            saveToWavefile(uri);
            Uri uri2 = Uri.parse("file://mnt/sdcard/"+fileName+".png");
            saveScreenShot(uri2);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode,
            Intent resultData) {
        log("ActivityResult request: " + requestCode + "  result:" + resultCode);
        if (requestCode == SAVE_TO_WAVE_REQUEST && resultCode == Activity.RESULT_OK) {
            log("got SAVE TO WAV intent back!");
            Uri uri = null;
            if (resultData != null) {
                uri = resultData.getData();

                String temp = getPath1(uri);
                if (temp != null) {
                    File file = new File(temp);
                    mFilePathWav = file.getAbsolutePath();
                } else {
                    mFilePathWav = "";
                }

                saveToWavefile(uri);
            }
        } else if( requestCode == SAVE_TO_PNG_REQUEST && resultCode == Activity.RESULT_OK)  {

            log("got SAVE TO PNG intent back!");
            Uri uri = null;
            if (resultData != null) {
                uri = resultData.getData();
                saveScreenShot(uri);
            }

        } else if (requestCode == SETTINGS_ACTIVITY_REQUEST_CODE &&
                resultCode == Activity.RESULT_OK) {
            //new settings!
            log("return from settings!");
            refreshState();
        }
    }

    // method to get the file path from uri. Doesn't work for all devices
    public String getPath1(Uri uri)
    {
        String[] projection = {MediaStore.Images.Media.DATA};
        Cursor cursor1 = getContentResolver().query(uri, projection, null, null, null);
        if (cursor1 == null) {
            // cursor1.close();
            return uri.getPath();
        }

        int ColumnIndex = cursor1.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor1.moveToFirst();
        String path = cursor1.getString(ColumnIndex);
        cursor1.close();
        return path;
    }



    /** Called when the user clicks the button */
    public void onButtonZoomOutFull(View view) {

        double fullZoomOut = mWavePlotView.getMaxZoomOut();

        mWavePlotView.setZoom(fullZoomOut);
        mWavePlotView.refreshGraph();
    }

    public void onButtonZoomOut(View view) {

        double zoom = mWavePlotView.getZoom();

        zoom = 2.0 *zoom;
        mWavePlotView.setZoom(zoom);
        mWavePlotView.refreshGraph();
    }



    /** Called when the user clicks the button */
    public void onButtonZoomIn(View view) {

        double zoom = mWavePlotView.getZoom();

        zoom = zoom/2.0;
        mWavePlotView.setZoom(zoom);
        mWavePlotView.refreshGraph();
    }

/*
    public void onButtonZoomInFull(View view) {

        double minZoom = mWavePlotView.getMinZoomOut();

        mWavePlotView.setZoom(minZoom);
        mWavePlotView.refreshGraph();
    }
*/

    public void onButtonAbout(View view) {
        if(!isBusy()) {
            Intent aboutIntent = new Intent(this, AboutActivity.class);
            startActivity(aboutIntent);
        } else
            showToast("Test in progress... please wait");
    }


    public void onButtonBufferPeriod(View view) {
        if(!isBusy()) {
            HistogramView.setBufferPeriodArray(BufferPeriod.getBufferPeriodArray());
            HistogramView.setMaxBufferPeriod(BufferPeriod.getMaxBufferPeriod());

            Intent aboutIntent = new Intent(this, BufferPeriodActivity.class);
            startActivity(aboutIntent);
        } else
            showToast("Test in progress... please wait");
    }

    /** Called when the user clicks the button */
    public void onButtonSettings(View view) {

        if(!isBusy()) {
            Intent mySettingsIntent = new Intent(this, SettingsActivity.class);
            //send settings
            startActivityForResult(mySettingsIntent, SETTINGS_ACTIVITY_REQUEST_CODE);
        } else {
            showToast("Test in progress... please wait");
//            Toast.makeText(getApplicationContext(), "Test in progress... please wait",
//                    Toast.LENGTH_SHORT).show();
        }
    }

    void refreshPlots() {
        mWavePlotView.setData(mWaveData);
        mWavePlotView.redraw();
    }

    void refreshState() {

        log("refreshState!");

        Button buttonTest = (Button) findViewById(R.id.buttonTest);

        //get current audio level
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        int currentVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        mBarMasterLevel.setProgress(currentVolume);

        mTextViewCurrentLevel.setText(String.format("Level: %d/%d", currentVolume,
                mBarMasterLevel.getMax()));

        log("refreshState 2b");

        //get info
        int samplingRate = getApp().getSamplingRate();
        int playbackBuffer = getApp().getPlayBufferSizeInBytes()/getApp().BYTES_PER_FRAME;
        int recordBuffer = getApp().getRecordBufferSizeInBytes()/getApp().BYTES_PER_FRAME;
        StringBuilder s = new StringBuilder(200);
        s.append("SR: " + samplingRate + " Hz");
        int audioThreadType = getApp().getAudioThreadType();
        switch(audioThreadType) {
            case LoopbackApplication.AUDIO_THREAD_TYPE_JAVA:
                s.append(" Play Frames: " + playbackBuffer);
                s.append(" Record Frames: " + recordBuffer);
                s.append(" Audio: JAVA");
            break;
            case LoopbackApplication.AUDIO_THREAD_TYPE_NATIVE:
                s.append(" Frames: " + playbackBuffer);
                s.append(" Audio: NATIVE");
                break;
        }

        //mic source
        int micSource = getApp().getMicSource();
        String micSourceName = getApp().getMicSourceString(micSource);
        if(micSourceName != null) {
            s.append(String.format(" Mic: %s", micSourceName));
        }
/*
        s.append(" App");
*/

        String info = getApp().getSystemInfo();
        s.append(" " + info);

        mTextInfo.setText(s.toString());

/*
        if(mCorrelation.mEstimatedLatencyMs>0.0001) {
            mTextViewEstimatedLatency.setText(String.format("Latency: %.2f ms", mCorrelation.mEstimatedLatencyMs));
        } else {
            mTextViewEstimatedLatency.setText(String.format("Latency: ----"));
        }
*/

    }

    private static void log(String msg) {
        Log.v("Recorder", msg);
    }

    public void showToast(String msg) {

        if(toast == null) {
            toast = Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT);
        } else {
            toast.setText(msg);

        }



        {
//            toast.setText(msg);
            toast.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL, 10, 10);
            toast.show();
        }
    }

    private LoopbackApplication getApp() {
        return (LoopbackApplication) this.getApplication();
    }

    void saveToWavefile(Uri uri) {

       // double [] data = audioThread.getWaveData();
        if (mWaveData != null && mWaveData.length > 0 ) {
            AudioFileOutput audioFileOutput = new AudioFileOutput(getApplicationContext(), uri,
                    mSamplingRate);
            boolean status = audioFileOutput.writeData(mWaveData);

            if (status) {
                showToast("Finished exporting wave File " + mFilePathWav);
//                Toast.makeText(getApplicationContext(), "Finished exporting wave File",
//                        Toast.LENGTH_SHORT).show();
            } else {
                showToast("Something failed saving wave file");
//                Toast.makeText(getApplicationContext(), "Something failed saving wave file",
//                        Toast.LENGTH_SHORT).show();
            }
        }

    }

    void saveScreenShot(Uri uri) {

        boolean status = false;
        ParcelFileDescriptor parcelFileDescriptor = null;
        FileOutputStream outputStream = null;
        try {
            parcelFileDescriptor = getApplicationContext().getContentResolver().openFileDescriptor(uri, "w");

            FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
            outputStream= new FileOutputStream(fileDescriptor);

            log("Done creating output stream");

            LinearLayout LL = (LinearLayout) findViewById(R.id.linearLayoutMain);

            View v = LL.getRootView();
            v.setDrawingCacheEnabled(true);
            Bitmap b = v.getDrawingCache();
            //BitmapDrawable bitmapDrawable = new BitmapDrawable(b);

            //save
            b.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
//            int sampleCount = data.length;
//            writeHeader(sampleCount);
//            writeDataBufer(data);
//            mOutputStream.close();
            status = true;
            parcelFileDescriptor.close();
            v.setDrawingCacheEnabled(false);
        } catch (Exception e) {
            outputStream = null;
            log("Failed to open png" +e);
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

    }

}
