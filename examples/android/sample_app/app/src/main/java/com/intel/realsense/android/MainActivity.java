package com.intel.realsense.android;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int VIDEO_PERMISSIONS_REQUEST_CODE = 1;
    private static final String[] VIDEO_PERMISSIONS = {
            Manifest.permission.CAMERA
    };


    static {
        System.loadLibrary("android_librs_test");
    }

    final Handler mHandler = new Handler();
    private ByteBuffer depthBuffer;
    private ByteBuffer colorBuffer;

    private Button btnStart;
    private static final int DEPTH_HEIGHT = 480;
    private static final int DEPTH_WIDTH = 848;
    private static final int COLOR_HEIGHT = 1080;
    private static final int COLOR_WIDTH = 1920;
    private ColorConverter mDepthConverter;
    private ColorConverter mColorConverter;

    private RealsenseUsbHostManager mUsbHostManager;
    private boolean isStreaming = false;
    TextureView mTextureViewDepth;
    TextureView mTextureViewColor;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mUsbHostManager = new RealsenseUsbHostManager(this);
        setContentView(R.layout.activity_main);
        depthBuffer = ByteBuffer.allocateDirect(DEPTH_HEIGHT * DEPTH_WIDTH * 2);
        depthBuffer.order(ByteOrder.nativeOrder());
        colorBuffer = ByteBuffer.allocateDirect(COLOR_HEIGHT * COLOR_WIDTH * 4);
        colorBuffer.order(ByteOrder.nativeOrder());
        mDepthConverter = new ColorConverter(this, ConversionType.DEPTH, DEPTH_WIDTH, DEPTH_HEIGHT);
        mColorConverter = new ColorConverter(this, ConversionType.RGBA, COLOR_WIDTH, COLOR_HEIGHT);

        mTextureViewDepth = findViewById(R.id.outputDepth);
        mTextureViewDepth.setSurfaceTextureListener(mDepthConverter);
        mTextureViewColor = findViewById(R.id.outputColor);
        mTextureViewColor.setSurfaceTextureListener(mColorConverter);
        btnStart = (Button) findViewById(R.id.btnStart);
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isStreaming == true) {
                    btnStart.setText(R.string.stream_start);
                    stopRepeatingTask();
                    librsStopStreaming();
                    isStreaming = false;
                } else if (isStreaming == false) {
                    btnStart.setText(R.string.stream_stop);
                    startRepeatingTask();
                    librsStartStreaming(depthBuffer, colorBuffer, DEPTH_WIDTH, DEPTH_HEIGHT,COLOR_WIDTH,COLOR_HEIGHT);
                    isStreaming = true;
                }
            }
        });
        getVideoPermissions();

    }

    @Override
    public void onResume() {
        super.onResume();
        if (isStreaming) {
            startRepeatingTask();
            librsStartStreaming(depthBuffer, colorBuffer, DEPTH_WIDTH, DEPTH_HEIGHT,COLOR_WIDTH,COLOR_HEIGHT);
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isStreaming)
            closeDevice();
    }

    Runnable updateBitmap = new Runnable() {
        @Override
        public void run() {
            try {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mDepthConverter.process(depthBuffer);
                        mTextureViewDepth.invalidate();
                        mColorConverter.process(colorBuffer);
                        mTextureViewColor.invalidate();
                    }
                });
            } finally {
                // 100% guarantee that this always happens, even if
                // your update method throws an exception
                mHandler.postDelayed(updateBitmap, 1000 / 30);
            }
        }
    };

    private void getVideoPermissions() {
        if (hasPermissionsGranted(VIDEO_PERMISSIONS))
            getUsbPermissions();
        else
            ActivityCompat.requestPermissions(this, VIDEO_PERMISSIONS, VIDEO_PERMISSIONS_REQUEST_CODE);

    }

    private boolean hasPermissionsGranted(String[] permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void getUsbPermissions() {
        if (mUsbHostManager.findDevice()) {
            btnStart.setText(R.string.stream_start);
            btnStart.setEnabled(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult");
        if (requestCode == VIDEO_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length == VIDEO_PERMISSIONS.length) {
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        Log.e(TAG, "Error getting permission!");
                        return;
                    }
                }
                getUsbPermissions();
            } else {
                Log.e(TAG, "Didnt get all permissions...");
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }//end onRequestPermissionsResult


    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     *
     * @param
     */
    public native boolean librsStartStreaming(ByteBuffer depthBuffer, ByteBuffer colorBuffer, int dw, int dh,int cw,int ch);

    public native boolean librsStopStreaming();

    void startRepeatingTask() {
        updateBitmap.run();
    }

    void stopRepeatingTask() {
        mHandler.removeCallbacks(updateBitmap);
    }

    private void closeDevice() {
        stopRepeatingTask();
        librsStopStreaming();
    }

}
