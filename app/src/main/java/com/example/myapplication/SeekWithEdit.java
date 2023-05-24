package com.example.myapplication;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

public class SeekWithEdit extends LinearLayout {

    private SeekBar seekBar;
    private EditText editText;
    private int minValue;
    private int maxValue;

    @RequiresApi(api = Build.VERSION_CODES.O)
    public SeekWithEdit(Context context) {
        super(context);
        initializeViews(context, null);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public SeekWithEdit(Context context, AttributeSet attrs) {
        super(context, attrs);
        initializeViews(context, attrs);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void initializeViews(Context context, AttributeSet attrs) {
        LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.seek_with_edit, this);

        seekBar = findViewById(R.id.seekBar);
        editText = findViewById(R.id.editText);

        // 获取自定义属性的值
        if (attrs != null) {    
            TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.SeekWithEdit);
            minValue = typedArray.getInt(R.styleable.SeekWithEdit_minValue, 0);
            maxValue = typedArray.getInt(R.styleable.SeekWithEdit_maxValue, 100);
            typedArray.recycle();
        } else {
            minValue = 0;
            maxValue = 100;
        }

        // 设置滑动条的最小值和最大值
        seekBar.setMin(minValue);
        seekBar.setMax(maxValue);
        editText.setText(String.valueOf(minValue));

        // 设置数字输入框的文本改变监听器
        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                // 用户点击了数字输入框的完成按钮或关闭软键盘时，将数字输入框的值设置到滑动条上
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_UNSPECIFIED) {
                    setValue(Integer.parseInt(editText.getText().toString()));
                    return true;
                }
                return false;
            }
        });
        editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    // 输入框失去焦点，将输入框的值设置恢复为滑动条
                    editText.setText(String.valueOf(seekBar.getProgress()));
                }
            }
        });


        // 设置滑动条的值改变监听器
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // 当滑动条的值改变时，将其值设置到数字输入框上
                editText.setText(String.valueOf(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // 滑动条开始拖动时的操作
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // 滑动条停止拖动时的操作
            }
        });

    }

    // 提供公共方法获取滑动条的值
    public int getValue() {
        return seekBar.getProgress();
    }

    // 提供公共方法设置滑动条的值
    public void setValue(int value) {
        if (value < minValue) {
            value = minValue;
        } else if (value > maxValue) {
            value = maxValue;
        }
        seekBar.setProgress(value);
        // 修改数字输入框的文本
        editText.setText(String.valueOf(value));
    }

    // 提供公共方法设置最小值
    public void setMinValue(int minValue) {
        this.minValue = minValue;
        seekBar.setMin(minValue);
        if (seekBar.getProgress() < minValue) {
            setValue(minValue);
        }
    }

    // 提供公共方法设置最大值
    public void setMaxValue(int maxValue) {
        this.maxValue = maxValue;
        seekBar.setMax(maxValue);
        if (seekBar.getProgress() > maxValue) {
            setValue(maxValue);
        }
    }

    // 提供公共方法设置值改变监听器
    public void setOnValueChangedListener(SeekBar.OnSeekBarChangeListener listener) {
        seekBar.setOnSeekBarChangeListener(listener);
    }

    // 提供公共方法设置文本改变监听器
    public void setOnTextChangedListener(TextWatcher watcher) {
        editText.addTextChangedListener(watcher);
    }

    // 提供公共方法获取最小值
    public int getMinValue() {
        return minValue;
    }

    // 提供公共方法获取最大值
    public int getMaxValue() {
        return maxValue;
    }

    // 提供公共方法获取数字输入框
    public EditText getEditText() {
        return editText;
    }

    // 提供公共方法获取滑动条
    public SeekBar getSeekBar() {
        return seekBar;
    }
}
