package com.example.myapplication;

import static android.content.Context.MODE_PRIVATE;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

public class RecordFragment extends Fragment {

    private enum DisplayType {
        FFT, WAVE, ORIGIN,
    }

    private class Wave {
        public float amplitude;
        public float frequency;
        public float phase;

        public Wave(double amplitude, double frequency, double phase) {
            this.amplitude = (float) amplitude;
            this.frequency = (float) frequency;
            this.phase = (float) phase;
        }

        public float cal(double x) {
            x = 2*Math.PI*frequency*x+phase;
            int i = (int) (x/tableStep)%tableSize;
            if(i<0) i+=tableSize;
            return amplitude*cosTable[i];
        }
    }

    private volatile boolean recordFlag = false;

    private final FastFourierTransformer transformer = new FastFourierTransformer(DftNormalization.STANDARD);
    private final int sampleRate = 44100;   // ?????????
    private final int bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);   // ????????????
    private final int fftSize = getMinSize(bufferSize)*2;   // FFT???????????????
    private final float showCyclesNum = 10; // ????????????????????????????????????

    private DisplayType displayType = DisplayType.FFT;  // ??????????????????
    private boolean fixPhase = RecordConstant.DEFAULT_FIX_PHASE;    // ??????????????????
    private float thresholdFactor = RecordConstant.DEFAULT_THRESHOLD;   // ?????????????????????

    private LineChart chart;
    private Button recordBtn;
    private TextView dBText;
    private TextView freqText;
    private TextView amplitudeText;
    private TextView displayTypeText;

    private Activity activity;
    private AudioRecord audioRecord;

    private LineDataSet dataSet;    // ???????????????
    private final short[] buffer = new short[bufferSize];   // ????????????????????????
    private Wave maxWave;   // ??????????????????
    private final Wave[] waves = new Wave[fftSize/2];
    private double volume;  // ??????
    private double inferAmplitude;  // ???????????????

    private final double[] window = new double[bufferSize]; // ???????????????
    private final int tableSize = 1500;
    private final double tableStep = 2 * Math.PI / tableSize;
    private final float[] cosTable = new float[tableSize];   // ????????????

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        activity = (Activity) context;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        resetRecord();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.activity_record, container, false);
        chart = view.findViewById(R.id.chart);
        recordBtn = view.findViewById(R.id.record);
        dBText = view.findViewById(R.id.dB);
        freqText = view.findViewById(R.id.freq);
        amplitudeText = view.findViewById(R.id.amplitude);
        displayTypeText = view.findViewById(R.id.display);

        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }

        SharedPreferences sharedPreferences = activity.getSharedPreferences("settings", MODE_PRIVATE);

        displayType = DisplayType.values()[sharedPreferences.getInt("display_type",displayType.ordinal())];
        fixPhase = sharedPreferences.getBoolean("fix_phase",fixPhase);
        thresholdFactor = sharedPreferences.getFloat("threshold_factor",thresholdFactor);
        if(displayType == DisplayType.FFT) {
            // ???????????????
            switch (sharedPreferences.getInt("window_id",RecordConstant.DEFAULT_WINDOW_ID)) {
                case 0:     // ?????????
                    Arrays.fill(window, 1);
                    break;
                case 1:     // ?????????
                    for (int i = 0; i < window.length; i++) {
                        window[i] = 0.5 * (1 - Math.cos(2 * Math.PI * i / (window.length - 1)));
                    }
                    break;
                case 2:     // ?????????
                    for (int i = 0; i < window.length; i++) {
                        window[i] = 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (window.length - 1));
                    }
                    break;
                case 3:     // ???????????????
                    for (int i = 0; i < window.length; i++) {
                        window[i] = 0.42 - 0.5 * Math.cos(2 * Math.PI * i / (window.length - 1)) + 0.08 * Math.cos(4 * Math.PI * i / (window.length - 1));
                    }
                    break;
            }


        }

        if(displayType == DisplayType.WAVE) {
            // ????????????
            for (int i = 0; i < tableSize; i++) {
                cosTable[i] = (float) Math.cos(i*tableStep);
            }
        }

        initUI();

        view.findViewById(R.id.reset).setOnClickListener(_view -> resetChartView());

        recordBtn.setOnClickListener(_view -> {
            if(!recordFlag) {
                beginRecord();
            } else {
                resetRecord();
            }
        });
        return view;
    }

    public void readSound() {
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            Toast.makeText(activity,"???????????????????????????????????????????????????",Toast.LENGTH_SHORT).show();
            resetRecord();
            return;
        }
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        audioRecord.startRecording();
        final double[] timeDomainData = new double[fftSize];
        // ???????????????????????????????????????????????????????????????
        audioRecord.read(buffer, 0, bufferSize);
        // ????????????????????????????????????
        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            public void run() {
                if(!recordFlag) {
                    audioRecord.stop();
                    audioRecord.setRecordPositionUpdateListener(null);
                    audioRecord = null;
                    cancel();
                    return;
                }
                audioRecord.read(buffer, 0, bufferSize);
                for(int i=0;i<bufferSize;++i) {
                    timeDomainData[i] = buffer[i] * (displayType==DisplayType.FFT ? window[i] : 1);
                }
                // ??????FFT??????????????????????????????????????????
                Complex[] frequencyDomainData = transformer.transform(timeDomainData, TransformType.FORWARD);
                for(int i=0;i<waves.length;++i) {
                    double amplitude = frequencyDomainData[i].abs()/bufferSize;
                    double frequency = Math.max(i * sampleRate / (double)fftSize, 0.01 * sampleRate / (double)fftSize);
                    double phase = frequencyDomainData[i].getArgument();
                    waves[i] = new Wave(amplitude, frequency, phase);
                }
                // ??????0??????
                maxWave = waves[1];
                for (int i = 1; i < waves.length; i++) {
                    if(waves[i].amplitude>maxWave.amplitude) maxWave = waves[i];
                }
                // ????????????
                inferAmplitude = 0;
                double cyclePointNum = sampleRate/maxWave.frequency;
                int cycleNum = (int) (bufferSize/cyclePointNum);
                for (int i = 0; i < cycleNum; ++i) {
                    int begin = (int) (i*cyclePointNum);
                    short max=buffer[begin];
                    short min=buffer[begin];
                    for (int j=0;j<cyclePointNum;++j) {
                        if(buffer[begin+j]>max) max=buffer[begin+j];
                        if(buffer[begin+j]<min) min=buffer[begin+j];
                    }
                    inferAmplitude +=(max-min);
                }
                inferAmplitude /= cycleNum*2;
                if(fixPhase) {
                    // ??????x??????????????????????????????????????????
                    double deltaX = -maxWave.phase/maxWave.frequency;
                    for (Wave wave : waves) {
                        wave.phase += wave.frequency*deltaX;
                    }
                }
                volume = calculateVolume(buffer);
                updateUI();
            }
        }, 0, 100); // ??? 100 ??????????????????
    }

    private void updateUI() {
        List<Entry> data = new ArrayList<>();
        switch (displayType) {
            case FFT:
                data = Arrays.stream(waves)
                    .map(wave -> new Entry(wave.frequency, wave.amplitude))
                    .collect(Collectors.toList());
                break;
            case WAVE:
                final int curveSize = 500;
                final double step = showCyclesNum / maxWave.frequency / curveSize;
                data = new ArrayList<>(curveSize);
                float[] yData = new float[curveSize];
                for(Wave wave : waves) {
                    if(wave.amplitude>=maxWave.amplitude* thresholdFactor) {
                        for (int i = 0; i < curveSize; i++) {
                            yData[i]+=wave.cal(i*step);
                        }
                    }
                }
                for (int i = 0; i < curveSize; i++) {
                    data.add(new Entry((float) (i * step), yData[i]));
                }
                break;
            case ORIGIN:
                data = new ArrayList<>(bufferSize);
                for (int i = 0; i < bufferSize; i++) {
                    data.add(new Entry((float) i/sampleRate, buffer[i]));
                }
                break;
        }
        dataSet.setValues(data);
        LineData lineData = new LineData(dataSet);
        activity.runOnUiThread(() -> {
            chart.setData(lineData);
            chart.invalidate();
            dBText.setText(String.format(Locale.CHINA,"%05.2f", volume));
            freqText.setText(String.format(Locale.CHINA,"%05.2f", maxWave.frequency/1000));
            amplitudeText.setText(String.format(Locale.CHINA,"%.2E",inferAmplitude));
        });
    }

    private void initUI() {
        displayTypeText.setText(RecordConstant.DISPLAY_NAMES[displayType.ordinal()]);
        final YAxis yAxis = chart.getAxisLeft();
        final XAxis xAxis = chart.getXAxis();
        yAxis.setEnabled(false);
        chart.getAxisRight().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.getDescription().setEnabled(false);
        chart.setDrawBorders(true);
        xAxis.setDrawLabels(true);
        xAxis.setAxisMinimum(0);
        switch (displayType) {
            case FFT:
                xAxis.setValueFormatter((value, axis) -> String.format(Locale.CHINA,"%.0fHz",value));
                yAxis.setAxisMinimum(0);
                break;
            case WAVE:
            case ORIGIN:
                xAxis.setValueFormatter((value, axis) -> {
                    if(axis.mAxisMaximum>100) {
                        return String.format(Locale.CHINA,"%.2fs",value);
                    }
                    return String.format(Locale.CHINA,"%.2fms",value*1000);
                });
                break;
        }

        for(int i=0;i<fftSize/2;++i) {
            double frequency = Math.max(i * sampleRate / (double)fftSize, 0.01 * sampleRate / (double)fftSize);
            waves[i] = new Wave(0,frequency,0);
        }
        maxWave = waves[0];
        dataSet = new LineDataSet(new ArrayList<>(),"CURVE");
        dataSet.setDrawCircleHole(false);
        dataSet.setDrawCircles(false);
        updateUI();
        resetChartView();
    }

    private void beginRecord() {
        recordFlag = true;
        recordBtn.setText("??????");
        readSound();
    }

    private void resetRecord() {
        recordFlag = false;
        recordBtn.setText("??????");
    }

    private static int getMinSize(int size) {
        int res = 1;
        while(res<size) {
            res <<=1;
        }
        return res;
    }

    private void resetChartView() {
        chart.fitScreen();
        if(displayType==DisplayType.WAVE) {
            float cyclesShow = 2;
            final float scaleX = showCyclesNum / cyclesShow;
            chart.zoom(scaleX,1,0,0);
            // ????????????????????????????????????????????????,???????????????????????????????????????????????????
            float offset = (scaleX-1)/scaleX/2*chart.getData().getXMax();
            chart.moveViewToX(offset);
        }
        chart.invalidate();
    }

    private static double calculateVolume(@NonNull short[] buffer) {
        double sum = 0;
        for (short value : buffer) {
            sum += value * value;
        }
        double mean = sum / buffer.length;
        double root = Math.sqrt(mean);
        // ????????????????????????
        return  20 * Math.log10(root);
    }
}
