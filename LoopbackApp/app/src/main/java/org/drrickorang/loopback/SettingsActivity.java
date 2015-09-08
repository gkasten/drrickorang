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

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.AdapterView;
import android.widget.NumberPicker;
import android.widget.NumberPicker.OnValueChangeListener;
import android.widget.TextView;


/**
 * This activity displays all settings that can be adjusted by the user.
 */

public class SettingsActivity extends Activity implements OnItemSelectedListener,
                                                          OnValueChangeListener {
    private static final String TAG = "SettingsActivity";

    private Spinner      mSpinnerMicSource;
    private Spinner      mSpinnerSamplingRate;
    private Spinner      mSpinnerAudioThreadType;
    private NumberPicker mNumberPickerPlayerBuffer;
    private NumberPicker mNumberPickerRecorderBuffer;
    private NumberPicker mNumberPickerBufferTestDuration;   // in seconds
    private NumberPicker mNumberPickerBufferTestWavePlotDuration; //in seconds
    private TextView     mTextSettingsInfo;

    ArrayAdapter<CharSequence> mAdapterSamplingRate;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Set the layout for this activity. You can find it
        View view = getLayoutInflater().inflate(R.layout.settings_activity, null);
        setContentView(view);
        mTextSettingsInfo = (TextView) findViewById(R.id.textSettingsInfo);

        int micSource = getApp().getMicSource();
        mSpinnerMicSource = (Spinner) findViewById(R.id.spinnerMicSource);
        ArrayAdapter<CharSequence> adapterMicSource = ArrayAdapter.createFromResource(this,
                R.array.mic_source_array, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        adapterMicSource.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        mSpinnerMicSource.setAdapter(adapterMicSource);
        //set current value
        mSpinnerMicSource.setSelection(micSource, false);
        mSpinnerMicSource.setOnItemSelectedListener(this);

        int samplingRate = getApp().getSamplingRate();
        //init spinner, etc
        mSpinnerSamplingRate = (Spinner) findViewById(R.id.spinnerSamplingRate);
        mAdapterSamplingRate = ArrayAdapter.createFromResource(this,
                R.array.samplingRate_array, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        mAdapterSamplingRate.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        mSpinnerSamplingRate.setAdapter(mAdapterSamplingRate);
        //set current value
        String currentValue = String.valueOf(samplingRate);
        int nPosition = mAdapterSamplingRate.getPosition(currentValue);
        mSpinnerSamplingRate.setSelection(nPosition, false);

        mSpinnerSamplingRate.setOnItemSelectedListener(this);
        //spinner native
        int audioThreadType = getApp().getAudioThreadType();
        mSpinnerAudioThreadType = (Spinner) findViewById(R.id.spinnerAudioThreadType);
        ArrayAdapter<CharSequence> adapter2 = ArrayAdapter.createFromResource(this,
                R.array.audioThreadType_array, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        adapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        mSpinnerAudioThreadType.setAdapter(adapter2);
        //set current value
        mSpinnerAudioThreadType.setSelection(audioThreadType, false);
        if (!getApp().isSafeToUseSles())
            mSpinnerAudioThreadType.setEnabled(false);

        mSpinnerAudioThreadType.setOnItemSelectedListener(this);

        // buffer test duration in seconds
        int bufferTestDurationMax = 36000;
        int bufferTestDurationMin = 1;
        mNumberPickerBufferTestDuration = (NumberPicker)
                                          findViewById(R.id.numberpickerBufferTestDuration);
        mNumberPickerBufferTestDuration.setMaxValue(bufferTestDurationMax);
        mNumberPickerBufferTestDuration.setMinValue(bufferTestDurationMin);
        mNumberPickerBufferTestDuration.setWrapSelectorWheel(false);
        mNumberPickerBufferTestDuration.setOnValueChangedListener(this);
        int bufferTestDuration = getApp().getBufferTestDuration();
        mNumberPickerBufferTestDuration.setValue(bufferTestDuration);

        // set the string to display bufferTestDurationMax
        Resources res = getResources();
        String string1 = res.getString(R.string.labelBufferTestDuration, bufferTestDurationMax);
        TextView textView = (TextView) findViewById(R.id.textBufferTestDuration);
        textView.setText(string1);

        // wave plot duration for buffer test in seconds
        int bufferTestWavePlotDurationMax = 120;
        int bufferTestWavePlotDurationMin = 1;
        mNumberPickerBufferTestWavePlotDuration = (NumberPicker)
                                        findViewById(R.id.numberPickerBufferTestWavePlotDuration);
        mNumberPickerBufferTestWavePlotDuration.setMaxValue(bufferTestWavePlotDurationMax);
        mNumberPickerBufferTestWavePlotDuration.setMinValue(bufferTestWavePlotDurationMin);
        mNumberPickerBufferTestWavePlotDuration.setWrapSelectorWheel(false);
        mNumberPickerBufferTestWavePlotDuration.setOnValueChangedListener(this);
        int bufferTestWavePlotDuration = getApp().getBufferTestWavePlotDuration();
        mNumberPickerBufferTestWavePlotDuration.setValue(bufferTestWavePlotDuration);

        // set the string to display bufferTestWavePlotDurationMax
        string1 = res.getString(R.string.labelBufferTestWavePlotDuration,
                bufferTestWavePlotDurationMax);
        textView = (TextView) findViewById(R.id.textBufferTestWavePlotDuration);
        textView.setText(string1);

        //player buffer
        int playerBufferMax = 8000;
        int playerBufferMin = 16;
        mNumberPickerPlayerBuffer = (NumberPicker) findViewById(R.id.numberpickerPlayerBuffer);
        mNumberPickerPlayerBuffer.setMaxValue(playerBufferMax);
        mNumberPickerPlayerBuffer.setMinValue(playerBufferMin);
        mNumberPickerPlayerBuffer.setWrapSelectorWheel(false);
        mNumberPickerPlayerBuffer.setOnValueChangedListener(this);
        int playerBuffer = getApp().getPlayerBufferSizeInBytes()/ Constant.BYTES_PER_FRAME;
        mNumberPickerPlayerBuffer.setValue(playerBuffer);
        log("playerbuffer = " + playerBuffer);

        // set the string to display playerBufferMax
        string1 = res.getString(R.string.labelPlayerBuffer, playerBufferMax);
        textView = (TextView) findViewById(R.id.textPlayerBuffer);
        textView.setText(string1);

        //record buffer
        int recorderBufferMax = 8000;
        int recorderBufferMin = 16;
        mNumberPickerRecorderBuffer = (NumberPicker) findViewById(R.id.numberpickerRecorderBuffer);
        mNumberPickerRecorderBuffer.setMaxValue(recorderBufferMax);
        mNumberPickerRecorderBuffer.setMinValue(recorderBufferMin);
        mNumberPickerRecorderBuffer.setWrapSelectorWheel(false);
        mNumberPickerRecorderBuffer.setOnValueChangedListener(this);
        int recorderBuffer = getApp().getRecorderBufferSizeInBytes()/ Constant.BYTES_PER_FRAME;
        mNumberPickerRecorderBuffer.setValue(recorderBuffer);
        log("recorderBuffer = " + recorderBuffer);

        // set the string to display playerBufferMax
        string1 = res.getString(R.string.labelRecorderBuffer, recorderBufferMax);
        textView = (TextView) findViewById(R.id.textRecorderBuffer);
        textView.setText(string1);

        refresh();
    }


    public void onDestroy() {
        super.onDestroy();
    }


    @Override
    public void onBackPressed() {
        log("on back pressed");
        settingsChanged();
        finish();
    }


    private void refresh() {
        int bufferTestDuration = getApp().getBufferTestDuration();
        mNumberPickerBufferTestDuration.setValue(bufferTestDuration);

        int bufferTestWavePlotDuration = getApp().getBufferTestWavePlotDuration();
        mNumberPickerBufferTestWavePlotDuration.setValue(bufferTestWavePlotDuration);

        int playerBuffer = getApp().getPlayerBufferSizeInBytes() / Constant.BYTES_PER_FRAME;
        mNumberPickerPlayerBuffer.setValue(playerBuffer);
        int recorderBuffer = getApp().getRecorderBufferSizeInBytes() / Constant.BYTES_PER_FRAME;
        mNumberPickerRecorderBuffer.setValue(recorderBuffer);

        if (getApp().getAudioThreadType() == Constant.AUDIO_THREAD_TYPE_JAVA) {
            mNumberPickerRecorderBuffer.setEnabled(true);
        } else {
            mNumberPickerRecorderBuffer.setEnabled(false);
        }

        int samplingRate = getApp().getSamplingRate();
        String currentValue = String.valueOf(samplingRate);
        int nPosition = mAdapterSamplingRate.getPosition(currentValue);
        mSpinnerSamplingRate.setSelection(nPosition);

        String info = getApp().getSystemInfo();
        mTextSettingsInfo.setText("SETTINGS - " + info);
    }


    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        // An item was selected. You can retrieve the selected item using
        // parent.getItemAtPosition(pos)
        log("item selected!");

        switch (parent.getId()) {
        case R.id.spinnerSamplingRate:
            String stringValue = mSpinnerSamplingRate.getSelectedItem().toString();
            int samplingRate = Integer.parseInt(stringValue);
            getApp().setSamplingRate(samplingRate);
            settingsChanged();
            log("Sampling Rate: " + stringValue);
            refresh();
            break;
        case R.id.spinnerAudioThreadType:
            int audioThreadType = mSpinnerAudioThreadType.getSelectedItemPosition();
            getApp().setAudioThreadType(audioThreadType);
            getApp().computeDefaults();
            settingsChanged();
            log("AudioThreadType:" + audioThreadType);
            refresh();
            break;
        case R.id.spinnerMicSource:
            int micSource = mSpinnerMicSource.getSelectedItemPosition();
            getApp().setMicSource(micSource);
            settingsChanged();
            log("mic Source:" + micSource);
            refresh();
            break;
        }
    }


    public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
        if (picker == mNumberPickerPlayerBuffer) {
            log("player buffer new size " + oldVal + " -> " + newVal);
            getApp().setPlayerBufferSizeInBytes(newVal * Constant.BYTES_PER_FRAME);
            int audioThreadType = mSpinnerAudioThreadType.getSelectedItemPosition();
            // in native mode, recorder buffer size = player buffer size
            if (audioThreadType == Constant.AUDIO_THREAD_TYPE_NATIVE){
                getApp().setRecorderBufferSizeInBytes(newVal * Constant.BYTES_PER_FRAME);
            }
        } else if (picker == mNumberPickerRecorderBuffer) {
            log("recorder buffer new size " + oldVal + " -> " + newVal);
            getApp().setRecorderBufferSizeInBytes(newVal * Constant.BYTES_PER_FRAME);
        } else if (picker == mNumberPickerBufferTestDuration) {
            log("buffer test new duration: " + oldVal + " -> " + newVal);
            getApp().setBufferTestDuration(newVal);
        } else if (picker == mNumberPickerBufferTestWavePlotDuration) {
            log("buffer test's wave plot new duration: " + oldVal + " -> " + newVal);
            getApp().setBufferTestWavePlotDuration(newVal);
        }
        settingsChanged();
        refresh();
    }


    private void settingsChanged() {
        Intent intent = new Intent();
        setResult(RESULT_OK, intent);
    }


    public void onNothingSelected(AdapterView<?> parent) {
        // Another interface callback
    }


    /** Called when the user clicks the button */
    public void onButtonClick(View view) {
        getApp().computeDefaults();
        refresh();
    }

// Below is work in progress by Ricardo
//    public void onButtonRecordDefault(View view) {
//        int samplingRate = getApp().getSamplingRate();
//
//        int minRecorderBufferSizeInBytes =  AudioRecord.getMinBufferSize(samplingRate,
//                AudioFormat.CHANNEL_IN_MONO,
//                AudioFormat.ENCODING_PCM_16BIT);
//        getApp().setRecorderBufferSizeInBytes(minRecorderBufferSizeInBytes);
//
//        refresh();
//    }

//    private void computeDefaults() {
//
////        if (getApp().getAudioThreadType() == LoopbackApplication.AUDIO_THREAD_TYPE_JAVA) {
////            mNumberPickerRecorderBuffer.setEnabled(true);
////        else
////            mNumberPickerRecorderBuffer.setEnabled(false);
//
//        int samplingRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
//        getApp().setSamplingRate(samplingRate);
//        int minPlayerBufferSizeInBytes = AudioTrack.getMinBufferSize(samplingRate,
//                AudioFormat.CHANNEL_OUT_MONO,
//                AudioFormat.ENCODING_PCM_16BIT);
//        getApp().setPlayerBufferSizeInBytes(minPlayerBufferSizeInBytes);
//
//        int minRecorderBufferSizeInBytes =  AudioRecord.getMinBufferSize(samplingRate,
//                AudioFormat.CHANNEL_IN_MONO,
//                AudioFormat.ENCODING_PCM_16BIT);
//        getApp().setRecorderBufferSizeInBytes(minRecorderBufferSizeInBytes);
//        getApp().setRecorderBufferSizeInBytes(minRecorderBufferSizeInBytes);
//
//        log("computed defaults");
//
//    }


    private LoopbackApplication getApp() {
        return (LoopbackApplication) this.getApplication();
    }


    private static void log(String msg) {
        Log.v(TAG, msg);
    }

}
