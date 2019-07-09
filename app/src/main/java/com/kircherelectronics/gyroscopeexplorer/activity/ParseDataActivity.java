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

import com.kircherelectronics.fsensor.filter.gyroscope.OrientationGyroscope;
import com.kircherelectronics.fsensor.filter.gyroscope.fusion.complimentary.OrientationFusedComplimentary;
import com.kircherelectronics.fsensor.filter.gyroscope.fusion.kalman.OrientationFusedKalman;
import com.kircherelectronics.gyroscopeexplorer.R;

import org.apache.commons.math3.complex.Quaternion;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ParseDataActivity extends AppCompatActivity {
    private static final String TAG = ParseDataActivity.class.getSimpleName();
    private final static int WRITE_EXTERNAL_STORAGE_REQUEST = 1000;

    private String IMU_FILE = "/sdcard/ZZZ_q8h_Area_3_1562552173878.txt";
    private FileInputStream is;
    private BufferedReader reader;
    private BufferedInputStream inputStream;
    private long baseTimestamp;
    private List<Float> timeDiff = new ArrayList<Float>();

    private Button parseBtn;

    private enum Mode {
        GYROSCOPE_ONLY,
        COMPLIMENTARY_FILTER,
        KALMAN_FILTER;
    }
    private Mode mode = Mode.GYROSCOPE_ONLY;
    private OrientationGyroscope orientationGyroscope;
    private OrientationFusedComplimentary orientationComplimentaryFusion;
    private OrientationFusedKalman orientationKalmanFusion;
    private float[] fusedOrientation = new float[3];
    private double[] degree = new double[3];

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

        switch (mode) {
            case GYROSCOPE_ONLY:
                orientationGyroscope = new OrientationGyroscope();
                break;
            case COMPLIMENTARY_FILTER:
                orientationComplimentaryFusion = new OrientationFusedComplimentary();
                break;
            case KALMAN_FILTER:
                orientationKalmanFusion = new OrientationFusedKalman();
                break;
        }
        reset();
    }

    public void onClickView(View v) {
        switch (v.getId()) {
            case R.id.parse_btn:
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        getTimeInverval(IMU_FILE);
                        parseFile(IMU_FILE);
                    }
                }).start();

                break;
        }
    }

    private void reset() {
        switch (mode) {
            case GYROSCOPE_ONLY:
                orientationGyroscope.reset();
                break;
            case COMPLIMENTARY_FILTER:
                orientationComplimentaryFusion.reset();
                break;
            case KALMAN_FILTER:
                orientationKalmanFusion.reset();
                break;
        }
    }

    private void getTimeInverval(String filename) {
        File file = new File(filename);
        byte[] bytes = new byte[18];
        int read = 0;
        int imu_count = 0;
        long tv_sec, tv_usec, timestamp, last_timestamp = 0;

        try {
            if (file.exists()) {
                is = new FileInputStream(file);
                while ((read = is.read(bytes)) != -1) {
//                    Log.i(TAG, byteArrayToHex(bytes));
                    if ((char) bytes[0] == 'T' && (char) bytes[1] == 'I' && (char) bytes[2] == 'M' && (char) bytes[3] == 'E') {
                        tv_sec = (bytes[7] & 0xFF) << 24 | (bytes[6] & 0xFF) << 16 | (bytes[5] & 0xFF) << 8 | (bytes[4] & 0xFF);
                        tv_usec = (bytes[11] & 0xFF) << 24 | (bytes[10] & 0xFF) << 16 | (bytes[9] & 0xFF) << 8 | (bytes[8] & 0xFF);
                        timestamp = tv_sec * 1000000000 + tv_usec * 1000;
//                        Log.i(TAG, "timestamp = " + timestamp);
                        if (baseTimestamp == 0)
                            baseTimestamp = timestamp;

                        if (imu_count != 0) {
                            timeDiff.add((Float) ((timestamp - last_timestamp) / (float)imu_count));
                            imu_count = 0;
                        }

                        last_timestamp = timestamp;
                    } else {
                        imu_count += 1;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException ignored) {
            }
        }

//        for (Float i : timeDiff) {
//            Log.i(TAG, "" + i);
//        }
    }

    private void parseFile(String filename) {
        File file = new File(filename);
        byte[] bytes = new byte[18];
        int read = 0;
        float to_rps = 0.0174532925f;
//        float gyro_x, gyro_y, gyro_z, acc_x, acc_y, acc_z;

        int last_imu_ts = 0;
        int imu_ts;
        long tv_sec, tv_usec, timestamp;
        int timestamp_count = 0;
        long base_timestamp = 0;
        int imu_count = 0;

        try {
            if (file.exists()) {
                is = new FileInputStream(file);
                while((read = is.read(bytes)) != -1) {
//                    Log.i(TAG, byteArrayToHex(bytes));
                    if ((char)bytes[0] == 'T' && (char)bytes[1] == 'I' && (char)bytes[2] == 'M' && (char)bytes[3] == 'E') {
                        tv_sec = (bytes[7] & 0xFF) << 24 | (bytes[6] & 0xFF) << 16 | (bytes[5] & 0xFF) << 8 | (bytes[4] & 0xFF);
                        tv_usec = (bytes[11] & 0xFF) << 24 | (bytes[10] & 0xFF) << 16 | (bytes[9] & 0xFF) << 8 | (bytes[8] & 0xFF);
                        timestamp = tv_sec * 1000000000 + tv_usec * 1000;
                        Log.i(TAG, "timestamp = " + timestamp);
                        base_timestamp = timestamp;
                        timestamp_count += 1;
                        imu_count = 0;
                    } else {
                        float[] gyro = new float[3];
                        float[] acc = new float[3];
                        gyro[0] = getGyro(bytes[1], bytes[0]) * to_rps;
                        gyro[1] = getGyro(bytes[3], bytes[2]) * to_rps;
                        gyro[2] = getGyro(bytes[5], bytes[4]) * to_rps;
                        acc[0] = getAcc(bytes[7], bytes[6]);
                        acc[1] = getAcc(bytes[9], bytes[8]);
                        acc[2] = getAcc(bytes[11], bytes[10]);
                        long measured_ts = base_timestamp + (long)(imu_count * timeDiff.get(timestamp_count-1));
//                        Log.i(TAG, "measured_ts = " + measured_ts);
                        imu_count += 1;

                        imu_ts = (bytes[13] & 0xFF) << 16 | (bytes[12] & 0xFF) << 8 | (bytes[15] & 0xFF);

                        if (imu_ts == 0 || imu_ts - last_imu_ts < 3) {
                            last_imu_ts = imu_ts;
                        } else {
                            Log.e(TAG, "IMU DATA TIMESTAMP ERROR!!! diff = " + (imu_ts - last_imu_ts));
                            break;
                        }

                        getOrientation(gyro, measured_ts);
                        logText(fusedOrientation, measured_ts);
                    }
//                    Log.i(TAG, "gyro_x = " + gyro_x);
                }
            } else {
                Log.e(TAG, "File not found: " + filename);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException ignored) {
            }
        }
    }

    private void getOrientation(float[] rotation, long timestamp) {
        switch (mode) {
            case GYROSCOPE_ONLY:
                if (!orientationGyroscope.isBaseOrientationSet()) {
                    orientationGyroscope.setBaseOrientation(Quaternion.IDENTITY);
                } else {
                    fusedOrientation = orientationGyroscope.calculateOrientation(rotation, timestamp);
                }
                break;
        }
    }

    private void logText(float[] rotation, long timestamp) {

        String strX = String.format(Locale.getDefault(),"%.1f", (Math.toDegrees(rotation[0]) + 360) % 360);
        String strY = String.format(Locale.getDefault(),"%.1f", (Math.toDegrees(rotation[1]) + 360) % 360);
        String strZ = String.format(Locale.getDefault(),"%.1f", (Math.toDegrees(rotation[2]) + 360) % 360);

        Log.i(TAG, "updateText = " + strX + " " + strY + " " + strZ + " " + timestamp);
    }

    private static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for(byte b: a)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private void requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, WRITE_EXTERNAL_STORAGE_REQUEST);
        }
    }

    private float getGyro(byte msb, byte lsb) {
//        Log.i(TAG, "" + String.format("%02x", msb) + " " + String.format("%02x", lsb));
        int value = (msb & 0xFF) << 8 | (lsb & 0xFF);
        if (value > 32767)
            value = (65536 - value) * (-1);

//        Log.i(TAG, "raw value 2 = " + value);

//        float gyro = (value / 32768.0f) * 250.0f;
        float gyro = value * 0.00875f;

        return gyro;
    }

    private float getAcc(byte msb, byte lsb) {
        int value = (msb & 0xFF) << 8 | (lsb & 0xFF);
        if (value > 32767)
            value = (65536 - value) * (-1);

//        Log.i(TAG, "raw value 2 = " + value);

        float acc = (value / 32768.0f) * 2.0f;

        return acc;
    }
}
