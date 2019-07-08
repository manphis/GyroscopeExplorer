package com.kircherelectronics.gyroscopeexplorer.activity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.kircherelectronics.gyroscopeexplorer.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

public class ParseDataActivity extends AppCompatActivity {
    private static final String TAG = ParseDataActivity.class.getSimpleName();
    private final static int WRITE_EXTERNAL_STORAGE_REQUEST = 1000;

    FileInputStream is;
    BufferedReader reader;

    private Button parseBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.parse_activity);

        parseBtn = (Button) findViewById(R.id.parse_btn);
    }

    @Override
    public void onResume() {
        super.onResume();

        requestPermissions();
    }

    public void onClickView(View v) {
        switch (v.getId()) {
            case R.id.parse_btn:
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        parseFile("/sdcard/ZZZ_q8h_Area_3_1562552173878.csv");
                    }
                }).start();

                break;
        }
    }

    private void parseFile(String filename) {
        File file = new File(filename);
        try {
            if (file.exists()) {
                is = new FileInputStream(file);
                reader = new BufferedReader(new InputStreamReader(is));
                String line = reader.readLine();
                while (line != null) {
                    Log.d(TAG, line);
                    line = reader.readLine();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, WRITE_EXTERNAL_STORAGE_REQUEST);
        }
    }
}
