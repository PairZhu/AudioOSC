package com.example.myapplication;

import static android.content.Context.MODE_PRIVATE;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

public class SGFragment extends Fragment {

    private enum WaveType {
        SINE,
        SQUARE,
        SAWTOOTH,
        TRIANGLE,
    }

    private final int sampleRate = 44100;                                                                                                 // 采样率

    private WaveType waveType; // 波形类型

    private volatile boolean isPlaying = false; // 是否正在播放

    private Activity activity;

    private ImageButton playButton;
    private SeekWithEdit freqView;
    private SeekBar volumeView;

    final private int bufferSize = AudioTrack.getMinBufferSize(sampleRate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
    final short[] buffer = new short[bufferSize];


    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        activity = (Activity) context;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopSignalGenerator();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.activity_sg, container, false);

        SharedPreferences sharedPreferences = activity.getSharedPreferences("settings", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        Spinner waveTypeSpinner = view.findViewById(R.id.wave_type_spinner);
        waveTypeSpinner.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, RecordConstant.WAVE_TYPE_NAMES));
        
        waveTypeSpinner.setSelection(sharedPreferences.getInt("wave_type", RecordConstant.DEFAULT_WAVE_TYPE_ID));
        waveTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View _view, int position, long id) {
                waveType = WaveType.values()[position];
                editor.putInt("wave_type", position).apply();
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        playButton = view.findViewById(R.id.sg_play);
        playButton.setOnClickListener(v -> {
            if (!isPlaying) {
                startSignalGenerator();
            } else {
                stopSignalGenerator();
            }
        });

        freqView = view.findViewById(R.id.sg_freq);
        // 计算最小频率和最大频率
        double minFreq = sampleRate / (double) bufferSize;
        double maxFreq = sampleRate / 2.0 - minFreq;
        // minFreq向上取整，maxFreq向下取整
        freqView.setMinValue((int) Math.ceil(minFreq));
        freqView.setMaxValue((int) Math.floor(maxFreq));
        freqView.setValue(sharedPreferences.getInt("sg_freq", RecordConstant.DEFAULT_SG_FREQ));
        freqView.setOnValueChangedListener(value -> editor.putInt("sg_freq", value).apply());
        
        volumeView = view.findViewById(R.id.sg_volume);
        volumeView.setMax(Short.MAX_VALUE);
        volumeView.setProgress(sharedPreferences.getInt("sg_volume", RecordConstant.DEFAULT_SG_VOLUME));
        volumeView.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int value, boolean _fromUser) {
                editor.putInt("sg_volume", value).apply();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        return view;
    }

    private void startSignalGenerator() {
        isPlaying = true;
        playButton.setImageResource(R.drawable.ic_baseline_pause_24);
        final AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,sampleRate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,bufferSize,AudioTrack.MODE_STREAM);
        new Thread(() -> {
            double angle = 0.0;
            audioTrack.play();
            while (isPlaying) {
                double frequency = freqView.getValue();
                double volume = volumeView.getProgress();
                double deltaAngle = 2.0 * Math.PI * frequency / sampleRate;
                for (int i = 0; i < bufferSize; i++) {
                    buffer[i] = (short) (volume * waveFunction(angle));
                    angle = (angle + deltaAngle) % (2.0 * Math.PI);
                }
                audioTrack.write(buffer, 0, bufferSize);
            }
            audioTrack.stop();
            audioTrack.release();
            stopSignalGenerator();
        }).start();
    }

    private void stopSignalGenerator() {
        isPlaying = false;
        playButton.setImageResource(R.drawable.ic_baseline_play_arrow_24);
    }

    private double waveFunction(double angle) {
        switch (waveType) {
            case SINE:
                return Math.sin(angle);
            case SQUARE:
                return angle < Math.PI ? 1.0 : -1.0;
            case SAWTOOTH:
                return 2.0 * (angle / (2.0 * Math.PI) - 0.5);
            case TRIANGLE:
                return 2.0 * Math.abs(2.0 * (angle / (2.0 * Math.PI) - 0.5)) - 1.0;
            default:
                return 0.0;
        }
    }

}
