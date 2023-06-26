package com.orbbec.ui;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import com.orbbec.ui.pose.PoseActivity;
import com.orbbec.widget.AlertDlg;

import java.util.ArrayList;
import java.util.List;

public class SplashActivity extends AppCompatActivity {
    private static final String TAG = "SplashActivity";
    private static final int PERMISSIONS_REQUEST = 1001;

    private final String[] mPermissions = {
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE};
    private List<String> mPermissionList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_splash);
        checkPermission();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean allow = true;
        if (requestCode == PERMISSIONS_REQUEST) {
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "onRequestPermissionsResult: request " + permissions[i] + " failed!");
                    allow = false;
                }
            }
        }
        if (allow) {
            toPoseActivity();
        } else {
            try {
                AlertDlg.showWarning(SplashActivity.this, "Request permissions failed, try again?", new AlertDlg.Response() {
                    @Override
                    public void negativeResponse() {
                        finish();
                    }

                    @Override
                    public void positiveResponse() {
                        checkPermission();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String mPermission : mPermissions) {
                if (ContextCompat.checkSelfPermission(this, mPermission) != PackageManager.PERMISSION_GRANTED) {
                    mPermissionList.add(mPermission);
                }
            }
            if (!mPermissionList.isEmpty()) {
                String[] permissions = mPermissionList.toArray(new String[mPermissionList.size()]);
                ActivityCompat.requestPermissions(this, permissions, PERMISSIONS_REQUEST);
            } else {
                toPoseActivity();
            }
        } else {
            toPoseActivity();
        }
    }

    public void toPoseActivity() {
        Intent intent = new Intent(this, PoseActivity.class);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
    }
}