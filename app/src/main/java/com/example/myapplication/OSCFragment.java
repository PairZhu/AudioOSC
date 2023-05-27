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
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.commons.math3.complex.Complex;
import org.jtransforms.fft.DoubleFFT_1D;

public class OSCFragment extends Fragment {

    private enum DisplayType {
        FFT,
        WAVE,
        ORIGIN,
    }

    private volatile boolean recordFlag = false;

    private final int sampleRate = 44100;                                                                                                 // 采样率
    private final int bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT); // 采样点数
    private final int fftSize = getMinSize(bufferSize) * 2;                                                                               // FFT计算的点数

    private DisplayType displayType;           // 图标展示方式
    private boolean fixPhase; // 是否固定相位
    private boolean manualFreq; // 是否手动输入频率
    private float highPass;   // 高通滤波频率
    private float lowPass;     // 低通滤波频率

    private LineChart chart;
    private ImageButton recordBtn;
    private TextView dBText;
    private TextView freqText;
    private TextView amplitudeText;
    private TextView displayTypeText;
    private SeekWithEdit oscFreqView;

    private Activity activity;

    private LineDataSet dataSet;                          // 折线图对象
    private final short[] buffer = new short[bufferSize]; // 传感器的原始数据
    private final double[] fftData = new double[fftSize]; // FFT计算的数据
    // 合成曲线图表展示的周期数
    private final float showCyclesNum = 10;

    private double volume;         // 音量
    private double inferAmplitude; // 估算的振幅
    private double frequency;      // 频率
    private double phase;          // 相位
    private double maxAmpFreq;     // 最大振幅对应的频率

    private final double[] window = new double[bufferSize]; // 窗函数打表

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

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.activity_osc, container, false);
        chart = view.findViewById(R.id.chart);
        recordBtn = view.findViewById(R.id.record);
        dBText = view.findViewById(R.id.dB);
        freqText = view.findViewById(R.id.freq);
        amplitudeText = view.findViewById(R.id.amplitude);
        displayTypeText = view.findViewById(R.id.display);
        oscFreqView = view.findViewById(R.id.osc_freq);

        SharedPreferences sharedPreferences = activity.getSharedPreferences("settings", MODE_PRIVATE);

        displayType = DisplayType.values()[sharedPreferences.getInt("display_type", RecordConstant.DEFAULT_DISPLAY_TYPE_ID)];
        fixPhase = sharedPreferences.getBoolean("fix_phase", RecordConstant.DEFAULT_FIX_PHASE);
        if (displayType == DisplayType.FFT) {
            // 窗函数打表
            switch (sharedPreferences.getInt("window_id", RecordConstant.DEFAULT_WINDOW_ID)) {
                case 0: // 矩形窗
                    Arrays.fill(window, 1);
                    break;
                case 1: // 汉宁窗
                    for (int i = 0; i < window.length; i++) {
                        window[i] = 0.5 * (1 - Math.cos(2 * Math.PI * i / (window.length - 1)));
                    }
                    break;
                case 2: // 海明窗
                    for (int i = 0; i < window.length; i++) {
                        window[i] = 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (window.length - 1));
                    }
                    break;
                case 3: // 布莱克曼窗
                    for (int i = 0; i < window.length; i++) {
                        window[i] = 0.42 - 0.5 * Math.cos(2 * Math.PI * i / (window.length - 1)) + 0.08 * Math.cos(4 * Math.PI * i / (window.length - 1));
                    }
                    break;
            }
        }
        manualFreq = sharedPreferences.getBoolean("manual_freq", RecordConstant.DEFAULT_MANUAL_FREQ);
        if (!manualFreq || displayType != DisplayType.WAVE) {
            view.findViewById(R.id.osc_freq_container).setVisibility(View.GONE);
        }
        highPass = sharedPreferences.getFloat("high_pass", RecordConstant.DEFAULT_HIGH_PASS);
        lowPass = sharedPreferences.getFloat("low_pass", RecordConstant.DEFAULT_LOW_PASS);

        // 设置手动输入频率的范围
        // 根据采样率和采样点数，计算最小频率和最大频率
        final double minFreq = sampleRate / (double) bufferSize;
        final double maxFreq = sampleRate / 2.0 - minFreq;
        // minFreq向上取整，maxFreq向下取整
        oscFreqView.setMinValue((int) Math.ceil(minFreq));
        oscFreqView.setMaxValue((int) Math.floor(maxFreq));

        view.findViewById(R.id.reset).setOnClickListener(_view -> resetChartView());
        recordBtn.setOnClickListener(_view -> {
            if (!recordFlag) {
                beginRecord();
            } else {
                resetRecord();
            }
        });

        initUI();
        beginRecord();
        return view;
    }

    public void readSound() {
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            Toast.makeText(activity, "没有录音权限，请允许录音权限后重试", Toast.LENGTH_SHORT).show();
            resetRecord();
            return;
        }
        final AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        audioRecord.startRecording();
        final double[] timeDomainData = new double[fftSize];
        // 不断从麦克风采样声音数据
        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            public void run() {
                if (!recordFlag) {
                    audioRecord.stop();
                    audioRecord.setRecordPositionUpdateListener(null);
                    audioRecord.release();
                    cancel();
                    return;
                }
                audioRecord.read(buffer, 0, bufferSize);

                for (int i = 0; i < bufferSize; ++i) {
                    timeDomainData[i] = buffer[i] * (displayType == DisplayType.FFT ? window[i] : 1);
                }
                // 使用FFT变换将时域信号转换为频域信号
                final DoubleFFT_1D fft = new DoubleFFT_1D(fftSize);
                // 复制到另一个数组，防止在变换过程中被修改
                System.arraycopy(timeDomainData, 0, fftData, 0, fftSize);
                fft.realForward(fftData);
                // 零频率的复数部分为0，原算法用于存最大负频率的实部
                fftData[1] = 0;
                // 高通滤波
                if (highPass >= 0f) {
                    final int highPassIndex = (int) Math.ceil(highPass * fftSize / (double) sampleRate);
                    for (int i = 0; i < highPassIndex && i < fftSize / 2; ++i) {
                        fftData[2 * i] = 0;
                        fftData[2 * i + 1] = 0;
                    }
                }
                // 低通滤波
                if (lowPass >= 0f) {
                    final int lowPassIndex = (int) (lowPass * fftSize / (double) sampleRate);
                    for (int i = lowPassIndex + 1; i < fftSize / 2; ++i) {
                        fftData[2 * i] = 0;
                        fftData[2 * i + 1] = 0;
                    }
                }

                // 忽略零频率，计算最大振幅对应的下标
                int waveIndex = 1;
                double maxValue = 0;
                for (int i = 1; i < fftSize / 2; ++i) {
                    double newValue = fftData[2 * i] * fftData[2 * i] + fftData[2 * i + 1] * fftData[2 * i + 1];
                    if (newValue > maxValue) {
                        maxValue = newValue;
                        waveIndex = i;
                    }
                }
                maxAmpFreq = waveIndex * sampleRate / (double) fftSize;
                if (manualFreq) {
                    waveIndex = (int) (oscFreqView.getValue() * fftSize / (double) sampleRate);
                }
                frequency = waveIndex * sampleRate / (double) fftSize;
                phase = Math.atan2(fftData[2 * waveIndex + 1], fftData[2 * waveIndex]);

                // 估算振幅
                inferAmplitude = 0;
                double cyclePointNum = sampleRate / frequency;
                if (cyclePointNum >= bufferSize)
                    cyclePointNum = bufferSize;
                int cycleNum = (int) (bufferSize / cyclePointNum);
                for (int i = 0; i < cycleNum; ++i) {
                    int begin = (int) (i * cyclePointNum);
                    short max = buffer[begin];
                    short min = buffer[begin];
                    for (int j = 0; j < cyclePointNum; ++j) {
                        if (buffer[begin + j] > max)
                            max = buffer[begin + j];
                        if (buffer[begin + j] < min)
                            min = buffer[begin + j];
                    }
                    inferAmplitude += (max - min);
                }
                inferAmplitude /= cycleNum * 2;
                volume = calculateVolume(buffer);
                updateUI();
            }
        }, 0, 20); // 每 20 毫秒执行一次
    }

    private void updateUI() {
        List<Entry> data = new ArrayList<>();
        switch (displayType) {
            case FFT: {
                data = new ArrayList<>(fftSize / 2);
                for (int i = 0; i < fftSize / 2; ++i) {
                    double amplitude = Math.sqrt(fftData[2 * i] * fftData[2 * i] + fftData[2 * i + 1] * fftData[2 * i + 1]) / bufferSize;
                    double frequency = i * sampleRate / (double) fftSize;
                    data.add(new Entry((float) frequency, (float) amplitude));
                }
                break;
            }
            case WAVE: {
                // 不补零，原地fft
                final DoubleFFT_1D fft = new DoubleFFT_1D(bufferSize);
                final double[] originData = new double[bufferSize];
                for (int i = 0; i < bufferSize; i++) {
                    originData[i] = buffer[i];
                }
                fft.realForward(originData);
                originData[1] = 0;

                // 高通滤波
                if (highPass >= 0f) {
                    final int highPassIndex = (int) Math.ceil(highPass * bufferSize / (double) sampleRate);
                    for (int i = 0; i < highPassIndex && i < bufferSize / 2; ++i) {
                        originData[2 * i] = 0;
                        originData[2 * i + 1] = 0;
                    }
                }
                // 低通滤波
                if (lowPass >= 0f) {
                    final int lowPassIndex = (int) (lowPass * bufferSize / (double) sampleRate);
                    for (int i = lowPassIndex + 1; i < bufferSize / 2; ++i) {
                        originData[2 * i] = 0;
                        originData[2 * i + 1] = 0;
                    }
                }

                // 固定相位
                if (fixPhase && frequency > 0) {
                    final double deltaX = -phase / frequency;
                    for (int i = 1; i < bufferSize / 2; ++i) {
                        Complex complex = new Complex(originData[2 * i], originData[2 * i + 1]);
                        double waveFreq = i * sampleRate / (double) bufferSize;
                        double deltaPhase = waveFreq * deltaX;
                        complex = complex.multiply(new Complex(Math.cos(deltaPhase), Math.sin(deltaPhase)));
                        originData[2 * i] = complex.getReal();
                        originData[2 * i + 1] = complex.getImaginary();
                    }
                }

                // 计算有多少个周期
                final double cyclePointNum = sampleRate / frequency;
                final int cycleNum = (int) (bufferSize / cyclePointNum);
                // 根据周期数，计算补零后的ifft大小
                final int curveSize = 500;
                final int ifftSize = Math.max((int) (curveSize / showCyclesNum * cycleNum), bufferSize);
                final DoubleFFT_1D ifft = new DoubleFFT_1D(ifftSize);
                final double[] ifftData = new double[ifftSize];
                System.arraycopy(originData, 0, ifftData, 0, bufferSize);
                ifft.realInverse(ifftData, true);

                data = new ArrayList<>(curveSize);
                final double step = showCyclesNum / Math.max(frequency, 1e-6) / curveSize;
                for (int i = 0; i < curveSize; i++) {
                    data.add(new Entry((float) (i * step), (float) ifftData[i % ifftSize]));
                }
                break;
            }
            case ORIGIN: {
                data = new ArrayList<>(bufferSize);
                if (lowPass >= 0f || highPass >= 0f) {
                    final double[] originData = new double[bufferSize];
                    for (int i = 0; i < bufferSize; i++) {
                        originData[i] = buffer[i];
                    }
                    final DoubleFFT_1D fft = new DoubleFFT_1D(bufferSize);
                    fft.realForward(originData);
                    // 这个fft算法结果的第二个元素有特殊含义，不是复数的虚部，此处进行特判
                    final double tmp = originData[1];
                    // 高通滤波
                    if (highPass >= 0f) {
                        final int highPassIndex = (int) Math.ceil(highPass * bufferSize / (double) sampleRate);
                        for (int i = 0; i < highPassIndex && i < bufferSize / 2; ++i) {
                            originData[2 * i] = 0;
                            originData[2 * i + 1] = 0;
                        }
                    }
                    // 低通滤波
                    if (lowPass >= 0f) {
                        final int lowPassIndex = (int) (lowPass * bufferSize / (double) sampleRate);
                        for (int i = lowPassIndex + 1; i < bufferSize / 2; ++i) {
                            originData[2 * i] = 0;
                            originData[2 * i + 1] = 0;
                        }
                        if (lowPassIndex >= bufferSize / 2) {
                            originData[1] = tmp;
                        }
                    }

                    final DoubleFFT_1D ifft = new DoubleFFT_1D(bufferSize);
                    ifft.realInverse(originData, true);

                    for (int i = 0; i < bufferSize; i++) {
                        data.add(new Entry((float) i / sampleRate, (float) originData[i]));
                    }

                } else {
                    for (int i = 0; i < bufferSize; i++) {
                        data.add(new Entry((float) i / sampleRate, buffer[i]));
                    }
                }
                break;
            }
        }
        dataSet.setValues(data);
        LineData lineData = new LineData(dataSet);
        activity.runOnUiThread(() -> {
            chart.setData(lineData);
            chart.invalidate();
            dBText.setText(String.format(Locale.CHINA, "%05.2f", volume));
            freqText.setText(String.format(Locale.CHINA, "%05.2f", maxAmpFreq / 1000));
            amplitudeText.setText(String.format(Locale.CHINA, "%.2E", inferAmplitude));
        });
    }

    private void initUI() {
        chart.setViewPortOffsets(50, 10, 50, 50);
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
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        switch (displayType) {
            case FFT:
                xAxis.setValueFormatter((value, axis) -> String.format(Locale.CHINA, "%.0fHz", value));
                yAxis.setAxisMinimum(0);
                break;
            case WAVE:
            case ORIGIN:
                xAxis.setValueFormatter((value, axis) -> {
                    if (axis.mAxisMaximum > 100) {
                        return String.format(Locale.CHINA, "%.2fs", value);
                    }
                    return String.format(Locale.CHINA, "%.2fms", value * 1000);
                });
                break;
        }

        dataSet = new LineDataSet(new ArrayList<>(), "CURVE");
        dataSet.setDrawCircleHole(false);
        dataSet.setDrawCircles(false);
        updateUI();
        resetChartView();
    }

    private void beginRecord() {
        recordFlag = true;
        // 取消原有的十字线
        chart.highlightValue(null);
        // 禁用触摸的十字线
        chart.setHighlightPerTapEnabled(false);
        recordBtn.setImageResource(R.drawable.ic_baseline_pause_24);
        readSound();
    }

    private void resetRecord() {
        recordFlag = false;
        // 启用触摸的十字线
        chart.setHighlightPerTapEnabled(true);
        recordBtn.setImageResource(R.drawable.ic_baseline_play_arrow_24);
    }

    private static int getMinSize(int size) {
        int res = 1;
        while (res < size) {
            res <<= 1;
        }
        return res;
    }

    private void resetChartView() {
        chart.fitScreen();
        if (displayType == DisplayType.WAVE) {
            float cyclesShow = 2;
            final float scaleX = showCyclesNum / cyclesShow;
            chart.zoom(scaleX, 1, 0, 0);
            // 左移一定相位，展示波形中间的区域,使得大部分低频干扰看起来不过于明显
            float offset = (scaleX - 1) / scaleX / 2 * chart.getData().getXMax();
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
        // 将数值转换为分贝
        return 20 * Math.log10(root);
    }
}
