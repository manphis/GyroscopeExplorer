package com.kircherelectronics.gyroscopeexplorer.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.kircherelectronics.fsensor.filter.gyroscope.OrientationGyroscope;
import com.kircherelectronics.fsensor.filter.gyroscope.fusion.complimentary.OrientationFusedComplimentary;
import com.kircherelectronics.fsensor.filter.gyroscope.fusion.kalman.OrientationFusedKalman;
import com.kircherelectronics.gyroscopeexplorer.R;

import org.apache.commons.math3.complex.Quaternion;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ParseDataActivity extends AppCompatActivity {
    private static final String TAG = ParseDataActivity.class.getSimpleName();
    private final static int WRITE_EXTERNAL_STORAGE_REQUEST = 1000;
    private final static int FILE_CHOOSE_CODE = 7;

//    private String IMU_FILE = "/sdcard/ZZZ_q8h_Area_3_1562552173878.txt";
    private String IMU_FILE = "/sdcard/imu_rawdata_3";
    private String timestampFilePath = "";
    private String imuFilePath = "";
    private FileInputStream is;
    private BufferedReader reader;
    private BufferedInputStream inputStream;
    private long baseTimestamp;
    private List<Float> timeDiff = new ArrayList<Float>();
    private List<Long> timestampList = new ArrayList<Long>();
    private List<IMUData> imuDataList = new ArrayList<IMUData>();

    private Button parseBtn;
    private TextView filepathView;
    private String pathHolder = null;
    private RadioGroup rateGroup, deviceGroup, dataGroup;

    private enum Mode {
        GYROSCOPE_ONLY,
        COMPLIMENTARY_FILTER,
        KALMAN_FILTER;
    }
    private enum Rate {
        RATE1(0.00875f), RATE2(0.035f), RATE3(0.07f);

        private float value;
        private Rate(float value) {
            this.value = value;
        }

        public float getValue() {
            return value;
        }
    }
    private enum DeviceType {
        INSULIN,
        Q8H
    }
    private enum DataType {
        RAW_HEX,
        RAW_INT
    }
    private Mode mode = Mode.GYROSCOPE_ONLY;
    private Rate rate = Rate.RATE1;
    private DeviceType deviceType = DeviceType.INSULIN;
    private DataType dataType = DataType.RAW_HEX;

    private OrientationGyroscope orientationGyroscope;
    private OrientationFusedComplimentary orientationComplimentaryFusion;
    private OrientationFusedKalman orientationKalmanFusion;
    private float[] fusedOrientation = new float[3];
    private double[] degree = new double[3];
    private static final float to_rps = 0.0174532925f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.parse_activity);

        parseBtn = (Button) findViewById(R.id.parse_btn);
        filepathView = (TextView) findViewById(R.id.path_tv);
        rateGroup = (RadioGroup) findViewById(R.id.rate_rg);
        deviceGroup = (RadioGroup) findViewById(R.id.device_rg);
        dataGroup = (RadioGroup) findViewById(R.id.data_rg);
        rateGroup.setOnCheckedChangeListener(radioListener);
        deviceGroup.setOnCheckedChangeListener(radioListener);
        dataGroup.setOnCheckedChangeListener(radioListener);
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO Auto-generated method stub
        switch (requestCode) {
            case FILE_CHOOSE_CODE:
                if (resultCode == RESULT_OK) {
                    pathHolder = data.getData().getPath();
                    Log.i(TAG, "choose file path = " + pathHolder);
//                    Toast.makeText(MainActivity.this, PathHolder, Toast.LENGTH_LONG).show();
                    filepathView.setText(pathHolder);
                }
                break;
        }
    }

    public void onClickView(View v) {
        switch (v.getId()) {
            case R.id.parse_btn:
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        reset();
                        Log.i(TAG, "start parsing; rate = " + rate.getValue() + "; device type = " + deviceType + "; data type = " + dataType);

                        if (null != pathHolder) {
                            IMU_FILE = "/sdcard/" + pathHolder.split(":")[1];

                            switch (deviceType) {
                                case Q8H:
                                    getTimeInverval(IMU_FILE);
                                    parseFile(IMU_FILE);
                                    break;

                                case INSULIN:
                                    switch (dataType) {
                                        case RAW_HEX:
                                            parseRawHexFile(IMU_FILE);
                                            break;

                                        case RAW_INT:
                                            parseRawData(IMU_FILE);
                                            break;
                                    }
                                    break;
                            }
                        }
                    }
                }).start();

                break;

            case R.id.file_btn:
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                startActivityForResult(intent, FILE_CHOOSE_CODE);
                break;

            case R.id.parse_mp4_btn:
                if (pathHolder.contains(".mp4")) {
                    //read ts file
                    timestampFilePath = "/sdcard/" + pathHolder.split(":")[1];
                    timestampFilePath = timestampFilePath.replace(".mp4", ".ts");
                    readTimestamp(timestampFilePath);
                    Log.i(TAG, "timestamp from " + timestampList.get(0) + " to " + timestampList.get(timestampList.size()-1) + "; length = " + timestampList.size());

                    imuFilePath = "/sdcard/" + pathHolder.split(":")[1];
                    imuFilePath = imuFilePath.replace(".mp4", ".txt");
                    createIMUAndTSFile(imuFilePath);
                }
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

    private void readTimestamp(String filename) {
        File f = new File(filename);
        if (!f.exists()) {
            Log.e(TAG, "file not exist: " + filename);
            return;
        }
        timestampList.clear();
        FileReader fr = null;
        BufferedReader br = null;

        try {
            fr = new FileReader(filename);
            br = new BufferedReader(fr);
            String line = br.readLine().replace("\n", "").replace(" ", "");
            while (line != null && !line.equals("")){
                timestampList.add(Long.parseLong(line));

                line = br.readLine();
                if (null != line)
                    line = line.replace("\n", "").replace(" ", "");
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (IOException ignored) {
            }
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
                        Log.i(TAG, "timestamp = " + timestamp);
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

    private void parseRawData(String filename) {
        float sensitivity = rate.getValue();
        Log.i(TAG, "parse file = " + filename + " sensitivity = " + sensitivity);

        FileReader fr = null;
        BufferedReader br = null;
        long base_timestamp = 0;
        int imu_count = 0;

        try {
            fr = new FileReader(filename);
            br = new BufferedReader(fr);
            String line = br.readLine().replace("\n", "").replace(" ", ""); //readLine()讀取一整行
            while (line != null && !line.equals("")){
//                Log.i(TAG, line);
                String[] items = line.split(",");
                float[] gyro = new float[3];
                gyro[0] = Integer.valueOf(items[0]) * sensitivity * to_rps;
                gyro[1] = Integer.valueOf(items[1]) * sensitivity * to_rps;
                gyro[2] = Integer.valueOf(items[2]) * sensitivity * to_rps;
//                Log.i(TAG, "" + gyro[0] + " " + gyro[1] + " " + gyro[2]);

                long measured_ts = base_timestamp + (long)((long)imu_count*5000000L);

                getOrientation(gyro, measured_ts);
                logText(fusedOrientation, measured_ts);

                imu_count++;

                line = br.readLine();
                if (null != line)
                    line = line.replace("\n", "").replace(" ", "");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (IOException ignored) {
            }
        }


    }

    private void parseRawHexFile(String filename) {
        Log.i(TAG, "parse raw hex file = " + filename);
        FileReader fr = null;
        BufferedReader br = null;

        String output_speed = "/sdcard/" + pathHolder.split(":")[1] + "_outspeed";
        String output_degree = "/sdcard/" + pathHolder.split(":")[1] + "_outdegree";
        FileOutputStream speedOutStream = null, degreeOutStream = null;
        OutputStreamWriter speedWriter = null, imuWriter = null;

        long base_timestamp = 0;
        int imu_count = 0;

        float total_degree = 0f;

        try {
            speedOutStream = new FileOutputStream(new File(output_speed));
            speedWriter = new OutputStreamWriter(speedOutStream);
            degreeOutStream = new FileOutputStream(new File(output_degree));
            imuWriter = new OutputStreamWriter(degreeOutStream);

            fr = new FileReader(filename);
            br = new BufferedReader(fr);
            String line = br.readLine().replace("\n", ""); //readLine()讀取一整行
            while (line != null && !line.equals("")){
                Log.i(TAG, "parseRawHexFile " + line);
                String[] items = line.split(" ");
                float[] gyro = new float[3];
                String rawStr = getRawGyroInt(hexStringToByte(items[0]), hexStringToByte(items[1])) + " "+
                        getRawGyroInt(hexStringToByte(items[2]), hexStringToByte(items[3])) + " " +
                        getRawGyroInt(hexStringToByte(items[4]), hexStringToByte(items[5])) + "\n";
                Log.i(TAG, "hextostring = " + rawStr);
                speedWriter.append(rawStr);

                total_degree += getRawGyroInt(hexStringToByte(items[2]), hexStringToByte(items[3])) * 0.005 * rate.getValue();
                Log.i(TAG, "total_degree = " + total_degree);



                gyro[0] = getGyro(hexStringToByte(items[0]), hexStringToByte(items[1])) * to_rps;
                gyro[1] = getGyro(hexStringToByte(items[2]), hexStringToByte(items[3])) * to_rps;
                gyro[2] = getGyro(hexStringToByte(items[4]), hexStringToByte(items[5])) * to_rps;

                long measured_ts = base_timestamp + (long)((long)imu_count*5000000L);

                getOrientation(gyro, measured_ts);

                logText(fusedOrientation, measured_ts);
                String strX = String.format(Locale.getDefault(),"%.1f", (Math.toDegrees(fusedOrientation[0]) + 360) % 360);
                String strY = String.format(Locale.getDefault(),"%.1f", (Math.toDegrees(fusedOrientation[1]) + 360) % 360);
                String strZ = String.format(Locale.getDefault(),"%.1f", (Math.toDegrees(fusedOrientation[2]) + 360) % 360);
                String degreeStr = strX + " " + strY + " " + strZ + " " + measured_ts + "\n";
                Log.i(TAG, "" + degreeStr);
                imuWriter.append(degreeStr);

                imu_count++;

                line = br.readLine();
                if (null != line)
                    line = line.replace("\n", "");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (br != null) { br.close(); }
                if (speedWriter != null) { speedWriter.close(); }
                if (imuWriter != null) { imuWriter.close(); }
                if (speedOutStream != null) {
                    speedOutStream.flush();
                    speedOutStream.close();
                }
                if (degreeOutStream != null) {
                    degreeOutStream.flush();
                    degreeOutStream.close();
                }
            } catch (IOException ignored) {
            }
        }
        Log.i(TAG, "imu count = " + imu_count);
    }

    public static byte hexStringToByte(String s) {
        byte data = 0;
        data = (byte) ((Character.digit(s.charAt(0), 16) << 4) + Character.digit(s.charAt(1), 16));

        return data;
    }

    private void parseFile(String filename) {
        File file = new File(filename);
        byte[] bytes = new byte[18];
        int read = 0;
//        float gyro_x, gyro_y, gyro_z, acc_x, acc_y, acc_z;
        String output_speed = "/sdcard/" + pathHolder.split(":")[1] + "_outspeed";
        String output_degree = "/sdcard/" + pathHolder.split(":")[1] + "_outdegree";
        FileOutputStream speedOutStream = null, degreeOutStream = null;
        OutputStreamWriter speedWriter = null, degreeWriter = null;

        int last_imu_ts = 0;
        int imu_ts;
        long tv_sec, tv_usec, timestamp, rtp_timestamp;
        int timestamp_count = 0;
        long base_timestamp = 0;
        int imu_count = 0;
        int samplerate = 90000;

        try {
            if (file.exists()) {
                speedOutStream = new FileOutputStream(new File(output_speed));
                speedWriter = new OutputStreamWriter(speedOutStream);
                degreeOutStream = new FileOutputStream(new File(output_degree));
                degreeWriter = new OutputStreamWriter(degreeOutStream);

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

                        rtp_timestamp = (long) ((tv_sec * samplerate) % Math.pow(256.0, 4.0) + (tv_usec * (samplerate * 1.0e-6)));
                        Log.i(TAG+"kiky", "rtp timestamp = " + rtp_timestamp);
                    } else {
                        String rawStr = getRawGyroInt(bytes[1], bytes[0]) + " "+
                                getRawGyroInt(bytes[3], bytes[2]) + " " +
                                getRawGyroInt(bytes[5], bytes[4]) + "\n";
                        Log.i(TAG, "hextostring = " + rawStr);
                        speedWriter.append(rawStr);

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
                            Log.e(TAG+"kiky", "IMU DATA TIMESTAMP ERROR!!! diff = " + (imu_ts - last_imu_ts));
                            break;
                        }

                        getOrientation(gyro, measured_ts);
                        String strX = String.format(Locale.getDefault(),"%.1f", (Math.toDegrees(fusedOrientation[0]) + 360) % 360);
                        String strY = String.format(Locale.getDefault(),"%.1f", (Math.toDegrees(fusedOrientation[1]) + 360) % 360);
                        String strZ = String.format(Locale.getDefault(),"%.1f", (Math.toDegrees(fusedOrientation[2]) + 360) % 360);
                        String degreeStr = strX + " " + strY + " " + strZ + " " + measured_ts + "\n";
                        Log.i(TAG, "" + degreeStr);
                        degreeWriter.append(degreeStr);
//                        logText(fusedOrientation, measured_ts);
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
                if (speedWriter != null) { speedWriter.close(); }
                if (degreeWriter != null) { degreeWriter.close(); }
                if (speedOutStream != null) {
                    speedOutStream.flush();
                    speedOutStream.close();
                }
                if (degreeOutStream != null) {
                    degreeOutStream.flush();
                    degreeOutStream.close();
                }
            } catch (IOException ignored) {
            }
        }
    }

    private void createIMUAndTSFile(String filename) {
        Log.i(TAG, "createIMUAndTSFile: " + filename);
        File file = new File(filename);

        byte[] bytes = new byte[18];
        int read = 0;
//        float gyro_x, gyro_y, gyro_z, acc_x, acc_y, acc_z;
        String output_imu = filename.replace(".txt", "_imu.csv");
        FileOutputStream imuOutStream = null;
        OutputStreamWriter imuWriter = null;

        int file_lines = 0;
        int last_imu_ts = 0;
        int imu_ts;
        long tv_sec, tv_usec, start_rtp_timestamp = 0, end_rtp_timestamp = 0;
        int imu_count = 0;
        int total_imu_count = 0, total_ts_count = 0, total_ts_checked = 0;
        int samplerate = 90000;
        int tsListIndex = 0;
        List<IMUData> imuList = new ArrayList<IMUData>();

        imuDataList.clear();

        try {
            if (file.exists()) {
                file_lines = (int)(file.length()/18);
                Log.i(TAG, "imu file lines should be: " + file_lines);

                imuOutStream = new FileOutputStream(new File(output_imu));
                imuWriter = new OutputStreamWriter(imuOutStream);

                is = new FileInputStream(file);
                while((read = is.read(bytes)) != -1) {
//                    Log.i(TAG, byteArrayToHex(bytes));
                    if ((char)bytes[0] == 'T' && (char)bytes[1] == 'I' && (char)bytes[2] == 'M' && (char)bytes[3] == 'E') {
                        tv_sec = (bytes[7] & 0xFF) << 24 | (bytes[6] & 0xFF) << 16 | (bytes[5] & 0xFF) << 8 | (bytes[4] & 0xFF);
                        tv_usec = (bytes[11] & 0xFF) << 24 | (bytes[10] & 0xFF) << 16 | (bytes[9] & 0xFF) << 8 | (bytes[8] & 0xFF);

                        if (start_rtp_timestamp == 0)
                            start_rtp_timestamp = (long) ((tv_sec * samplerate) % Math.pow(256.0, 4.0) + (tv_usec * (samplerate * 1.0e-6)));
                        else {
                            end_rtp_timestamp = (long) ((tv_sec * samplerate) % Math.pow(256.0, 4.0) + (tv_usec * (samplerate * 1.0e-6)));

                            long imuTickTime = (end_rtp_timestamp - start_rtp_timestamp) / imu_count;

                            if (tsListIndex >= timestampList.size())
                                continue;

                            long ts = timestampList.get(tsListIndex);
                            while (ts >= start_rtp_timestamp && ts <= end_rtp_timestamp) {
                                int offset = (int) ((ts - start_rtp_timestamp) / imuTickTime);
                                imuList.get(offset).timestamp = ts;
//                                String ts_imu_str = String.valueOf(ts) + ", " + String.valueOf(imuList.get(offset));
//                                Log.i(TAG+"kiky", "ts_imu_str = " + ts_imu_str);
//                                tsWriter.append(ts_imu_str);
                                total_ts_checked ++;

                                tsListIndex ++;
                                if (tsListIndex >= timestampList.size())
                                    break;

                                ts = timestampList.get(tsListIndex);
                            }

                            // copy imuList to imuDataList
                            for (int i = 0; i < imuList.size(); i++) {
//                                imuDataList.add(imuList.get(i));
                                IMUData imuData = imuList.get(i);
                                String imu_str = String.valueOf(imuData.timestamp) + ", " + String.valueOf(imuData.imuString);
                                imuWriter.append(imu_str);
                            }

                            start_rtp_timestamp = end_rtp_timestamp;
                        }

                        Log.i(TAG+"kiky", "rtp timestamp = " + start_rtp_timestamp);

                        total_imu_count += imu_count;
                        total_ts_count ++;
                        imu_count = 0;
                        imuList.clear();
                    } else {
//                        String rawStr = getRawGyroInt(bytes[1], bytes[0]) + " "+
//                                getRawGyroInt(bytes[3], bytes[2]) + " " +
//                                getRawGyroInt(bytes[5], bytes[4]) + "\n";
//                        Log.i(TAG, "hextostring = " + rawStr);
//                        tsWriter.append(rawStr);

                        float[] gyro = new float[3];
                        float[] acc = new float[3];
                        gyro[0] = getGyro(bytes[1], bytes[0]);
                        gyro[1] = getGyro(bytes[3], bytes[2]);
                        gyro[2] = getGyro(bytes[5], bytes[4]);
                        acc[0] = getAcc(bytes[7], bytes[6]);
                        acc[1] = getAcc(bytes[9], bytes[8]);
                        acc[2] = getAcc(bytes[11], bytes[10]);
                        String imu_str = String.valueOf(gyro[0]) + ", " +
                                        String.valueOf(gyro[1]) + ", " +
                                        String.valueOf(gyro[2]) + ", " +
                                        String.valueOf(acc[0]) + ", " +
                                        String.valueOf(acc[1]) + ", " +
                                        String.valueOf(acc[2]) + "\n";
//                        Log.i(TAG, "measured_ts = " + measured_ts);
                        if (imu_count == 0 && total_imu_count == 0)
                            Log.i(TAG, "first imu data = " + imu_str);
                        imu_count += 1;

                        imu_ts = (bytes[13] & 0xFF) << 16 | (bytes[12] & 0xFF) << 8 | (bytes[15] & 0xFF);

                        if (imu_ts == 0 || imu_ts - last_imu_ts < 3) {
                            last_imu_ts = imu_ts;
                        } else {
                            Log.e(TAG+"kiky", "IMU DATA TIMESTAMP ERROR!!! diff = " + (imu_ts - last_imu_ts));
                            break;
                        }

                        imuList.add(new IMUData(imu_str));
                    }
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
                if (imuWriter != null) { imuWriter.close(); }
                if (imuOutStream != null) {
                    imuOutStream.flush();
                    imuOutStream.close();
                }
            } catch (IOException ignored) {
            }
        }
        if (total_ts_checked == timestampList.size()) {
            Log.i(TAG, "timestamp check PASS " + total_ts_checked + ":" + timestampList.size());
        } else {
            Log.i(TAG, "timestamp check FAILED " + total_ts_checked + ":" + timestampList.size());
        }
        Log.i(TAG+"kiky", "total imu count = " + total_imu_count +
                                " total ts count = " + total_ts_count +
                                " total data count = " + (total_imu_count + total_ts_count) + "\n\n");
        if ((total_imu_count+total_ts_count) != file_lines)
            Log.e(TAG, "parsing not match !!! file lines = " + file_lines + ", total data count = " + (total_imu_count+total_ts_count));
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

//        String strX = String.format(Locale.getDefault(),"%.1f", (Math.toDegrees(rotation[0]) + 360));
//        String strY = String.format(Locale.getDefault(),"%.1f", (Math.toDegrees(rotation[1]) + 360));
//        String strZ = String.format(Locale.getDefault(),"%.1f", (Math.toDegrees(rotation[2]) + 360));

//        String strX = String.format(Locale.getDefault(),"%.1f", (Math.toDegrees(rotation[0])));
//        String strY = String.format(Locale.getDefault(),"%.1f", (Math.toDegrees(rotation[1])));
//        String strZ = String.format(Locale.getDefault(),"%.1f", (Math.toDegrees(rotation[2])));

//        String strX = String.format(Locale.getDefault(),"%.3f", rotation[0]);
//        String strY = String.format(Locale.getDefault(),"%.3f", rotation[1]);
//        String strZ = String.format(Locale.getDefault(),"%.3f", rotation[2]);

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

    private int getRawGyroInt(byte msb, byte lsb) {
//        Log.i(TAG, "" + String.format("%02x", msb) + " " + String.format("%02x", lsb));
        int value = (msb & 0xFF) << 8 | (lsb & 0xFF);
        if (value > 32767)
            value = (65536 - value) * (-1);

        return value;
    }

    private float getGyro(byte msb, byte lsb) {
//        Log.i(TAG, "" + String.format("%02x", msb) + " " + String.format("%02x", lsb));
        int value = (msb & 0xFF) << 8 | (lsb & 0xFF);
        if (value > 32767)
            value = (65536 - value) * (-1);

//        Log.i(TAG, "raw value = " + value);

//        float gyro = (value / 32768.0f) * 250.0f;
        float gyro = value * rate.getValue();

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

    RadioGroup.OnCheckedChangeListener radioListener = new RadioGroup.OnCheckedChangeListener() {
        public void onCheckedChanged(RadioGroup group, int checkedId) {
            switch(checkedId){
                case R.id.radioButton1:
                    rate = Rate.RATE1;
                    break;
                case R.id.radioButton2:
                    rate = Rate.RATE2;
                    break;
                case R.id.radioButton3:
                    rate = Rate.RATE3;
                    break;


                case R.id.radioButton4:
                    deviceType = DeviceType.INSULIN;
                    break;

                case R.id.radioButton5:
                    deviceType = DeviceType.Q8H;
                    break;


                case R.id.radioButton6:
                    dataType = DataType.RAW_HEX;
                    break;

                case R.id.radioButton7:
                    dataType = DataType.RAW_INT;
                    break;
            }
        }
    };

    class IMUData{
        public IMUData(String imuString) {
            this.imuString = imuString;
            this.timestamp = 0;
        }
        public long timestamp;
        public String imuString;
    }
}
