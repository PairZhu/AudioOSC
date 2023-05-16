package com.example.myapplication;

import static android.content.Context.MODE_PRIVATE;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingFragment extends Fragment {
    private Activity activity;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        activity = (Activity) context;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.activity_setting, container, false);

        final SharedPreferences sharedPreferences = activity.getSharedPreferences("settings", MODE_PRIVATE);
        final SharedPreferences.Editor editor = sharedPreferences.edit();

        final Spinner displaySpinner = view.findViewById(R.id.display_spinner);
        displaySpinner.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, RecordConstant.DISPLAY_NAMES));

        displaySpinner.setSelection(sharedPreferences.getInt("display_type", RecordConstant.DEFAULT_DISPLAY_TYPE_ID));
        displaySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View _view, int position, long id) {
                // 将数据存储到 SharedPreferences 中
                editor.putInt("display_type", position);
                editor.apply();
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        final Spinner windowFunctionSpinner = view.findViewById(R.id.window_function_spinner);
        windowFunctionSpinner.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, RecordConstant.WINDOW_NAMES));

        windowFunctionSpinner.setSelection(sharedPreferences.getInt("window_id", RecordConstant.DEFAULT_WINDOW_ID));
        windowFunctionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View _view, int position, long id) {
                // 将数据存储到 SharedPreferences 中
                editor.putInt("window_id", position);
                editor.apply();
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        final SwitchMaterial fixPhaseSwitch = view.findViewById(R.id.fix_phase);
        fixPhaseSwitch.setChecked(sharedPreferences.getBoolean("fix_phase", RecordConstant.DEFAULT_FIX_PHASE));
        fixPhaseSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            editor.putBoolean("fix_phase", isChecked);
            editor.apply();
        });

        return view;
    }
}
