package com.example.myapplication;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private Fragment fragment;
    private FragmentTransaction fragmentTransaction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnItemSelectedListener(menuItem -> {
            final int NAV_RECORD = R.id.menu_item_record;
            final int NAV_SETTINGS = R.id.menu_item_settings;
            switch (menuItem.getItemId()) {
                case NAV_RECORD:
                    if (!(fragment instanceof RecordFragment)) {
                        fragment = new RecordFragment();
                        fragmentTransaction = getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment);
                        fragmentTransaction.commit();
                    }
                    break;
                case NAV_SETTINGS:
                    if (!(fragment instanceof SettingFragment)) {
                        fragment = new SettingFragment();
                        fragmentTransaction = getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment);
                        fragmentTransaction.commit();
                    }
                    break;
            }
            return true;
        });

        // 默认展示第一个菜单选项对应的页面
        fragment = new RecordFragment();
        fragmentTransaction = getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment);
        fragmentTransaction.commit();
    }
}


