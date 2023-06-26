package com.orbbec.ui.pose;

import android.app.AlertDialog;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.orbbec.bean.FrameT;
import com.orbbec.bean.TrackInfo;
import com.orbbec.ui.R;
import com.orbbec.ui.base.BaseActivity;
import com.orbbec.widget.AlertDlg;
import com.orbbec.widget.OBPoseGLView;

import java.math.BigDecimal;

public class PoseActivity extends BaseActivity<PosePresenter, PoseContract.IPoseView>
        implements PoseContract.IPoseView, AdapterView.OnItemSelectedListener, SeekBar.OnSeekBarChangeListener, CompoundButton.OnCheckedChangeListener {
    private static final String TAG = "PoseActivity";

    private Spinner mTrackModeSpinner;
    private Spinner mSkeletonModeSpinner;
    private Spinner mRotateTypeSpinner;
    private Spinner mResolutionSpinner;
    private TextView mFilterParamValTv;
    private TextView mFrameRateTv;
    private TextView mTrackRateTv;
    private TextView mTrackTimeTv;
    private SeekBar mFilterParamSeekBar;
    private OBPoseGLView mColorGLView;
    private Switch mStartTrackSw;
    private Switch mImageRenderSw;

    private int mColorGLLayoutW;
    private int mColorGLLayoutH;

    private AlertDialog mDeviceStatusDlg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        if (!mColorGLView.initPoseTrack()) {
            showInitFailedDialog("Init pose track failed, try again?");
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        new Thread(() -> {
            mPresenter.openCamera();
        }).start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mPresenter.closeCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mColorGLView.stopTrack();
        mColorGLView.releasePoseTrack();
    }

    @Override
    protected void initViews() {
        mStartTrackSw = find(R.id.sw_start_track);
        mStartTrackSw.setOnCheckedChangeListener(this);

        mImageRenderSw = find(R.id.sw_render);
        mImageRenderSw.setChecked(true);
        mImageRenderSw.setOnCheckedChangeListener(this);

        mTrackModeSpinner = find(R.id.track_mode_spinner_type);
        mTrackModeSpinner.setOnItemSelectedListener(this);

        mSkeletonModeSpinner = find(R.id.skeleton_mode_spinner_type);
        mSkeletonModeSpinner.setOnItemSelectedListener(this);

        mRotateTypeSpinner = find(R.id.spinner_rotate_type);
        mRotateTypeSpinner.setOnItemSelectedListener(this);

        mResolutionSpinner = find(R.id.spinner_resolution);
        mResolutionSpinner.setOnItemSelectedListener(this);

        mFrameRateTv = find(R.id.tv_frame_rate);
        mFrameRateTv.setText(getString(R.string.frame_rate, 0f));

        mTrackRateTv = find(R.id.tv_track_rate);
        mTrackRateTv.setVisibility(View.GONE);
        mTrackRateTv.setText(getString(R.string.track_rate, 0f));

        mTrackTimeTv = find(R.id.tv_track_time);
        mTrackTimeTv.setVisibility(View.GONE);
        mTrackTimeTv.setText(getString(R.string.track_time, 0f));

        mColorGLView = find(R.id.glColorView);
        mFilterParamValTv = find(R.id.tv_filter_param_value);
        mFilterParamSeekBar = find(R.id.seekbar_filter_param);
        mFilterParamSeekBar.setOnSeekBarChangeListener(this);
        mFilterParamSeekBar.setEnabled(false);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_pose;
    }

    @Override
    protected PosePresenter createPresenter() {
        return new PosePresenter();
    }

    @Override
    public void onNewData(Pair<FrameT, FrameT> frames) {
        if (frames.first.getWidth() != mColorGLLayoutW || frames.first.getHeight() != mColorGLLayoutH) {
            runOnUiThread(() -> {
                updateGLSurfaceViewLayout(mColorGLView, frames.first);
            });

            mColorGLLayoutW = frames.first.getWidth();
            mColorGLLayoutH = frames.first.getHeight();
        } else {
            mColorGLView.update(frames);
        }
    }

    @Override
    public void updateTrackInfo(TrackInfo info) {
        info.setRenderRate(new BigDecimal(mColorGLView.getRenderRate()).setScale(2, BigDecimal.ROUND_HALF_UP).floatValue());
        info.setDrawSkeletonTime(mColorGLView.getDrawSkeletonTime());
        info.setTrackRate(mColorGLView.getTrackRate());
        info.setTrackTime(mColorGLView.getTrackTime());
        info.setPoseTrackTotalTime(mColorGLView.getPoseTrackTotalTime());
        info.setImgCreateTime(mColorGLView.getImgCreateTime());
        Log.i(TAG, "updateTrackInfo: " + info);
        runOnUiThread(() -> {
            mFrameRateTv.setText(getString(R.string.frame_rate, info.getFrameRate()));
            mTrackRateTv.setText(getString(R.string.track_rate, info.getTrackRate()));
            mTrackTimeTv.setText(getString(R.string.track_time, info.getTrackTime()));
        });
    }

    @Override
    public void showInfoDialog(String msg) {
        runOnUiThread(() -> {
            AlertDlg.showInfo(PoseActivity.this, msg);
        });
    }

    @Override
    public void updateDeviceStatusDlg(boolean isConnected) {
        if (null == mDeviceStatusDlg) {
            if (!isConnected) {
                mDeviceStatusDlg = new AlertDialog.Builder(PoseActivity.this)
                        .setTitle("Notification")
                        .setIcon(R.drawable.ic_info)
                        .setMessage("Device disconnected!")
                        .setNegativeButton("OK", (dialog, which) -> {
                            dialog.dismiss();
                        })
                        .show();
            }
        } else {
            if (!isConnected) {
                mDeviceStatusDlg.show();
            } else {
                mDeviceStatusDlg.dismiss();
            }
        }
    }

    private void showInitFailedDialog(String msg) {
        runOnUiThread(() -> {
            AlertDlg.showWarning(PoseActivity.this, msg, new AlertDlg.Response() {
                @Override
                public void negativeResponse() {
                    finish();
                }

                @Override
                public void positiveResponse() {
                    if (!mColorGLView.initPoseTrack()) {
                        showInitFailedDialog("Init pose track failed, try again?");
                    }
                }
            });
        });
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switch (buttonView.getId()) {
            case R.id.sw_start_track:
                if (isChecked) {
                    mColorGLView.setCalibration(mPresenter.loadCalibration());
                    mColorGLView.startTrack();
                    mFilterParamSeekBar.setEnabled(true);
                    mTrackRateTv.setVisibility(View.VISIBLE);
                    mTrackTimeTv.setVisibility(View.VISIBLE);
                } else {
                    mColorGLView.stopTrack();
                    mFilterParamSeekBar.setEnabled(false);
                    mTrackRateTv.setVisibility(View.GONE);
                    mTrackTimeTv.setVisibility(View.GONE);
                }
                break;
            case R.id.sw_render:
                mColorGLView.setRenderEnable(isChecked);
                break;
            default:
                break;
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        switch (parent.getId()) {
            case R.id.skeleton_mode_spinner_type:
                mColorGLView.setSkeletonMode(position);
                break;
            case R.id.track_mode_spinner_type:
                mColorGLView.setTrackMode(position);
                break;
            case R.id.spinner_rotate_type:
                mPresenter.setRotation(position);
                mColorGLView.setCalibration(mPresenter.loadCalibration());
                break;
            case R.id.spinner_resolution:
                mPresenter.switchConfig(position);
                mColorGLView.setCalibration(mPresenter.loadCalibration());
                break;
            default:
                break;
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        float value = progress * 1.0f / 10;
        mFilterParamValTv.setText(Float.toString(value));
        mColorGLView.setSmoothingFactor(value);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    private void updateGLSurfaceViewLayout(GLSurfaceView surfaceView, FrameT frameT) {
        ViewGroup parent = (ViewGroup) surfaceView.getParent();
        if (null == parent || parent.getWidth() <= 0 || parent.getHeight() <= 0
                || frameT.getHeight() <= 0 || frameT.getWidth() <= 0) {
            Log.w(TAG, "update playView layout fail. parent: " + parent + "(" + parent.getWidth()
                    + ", " + parent.getHeight() + "), Frame:" + frameT.getWidth() + "x" + frameT.getHeight());
            return;
        }

        // 宽度一致，高度比例拉伸
        int w1 = parent.getWidth();
        int h1 = parent.getWidth() * frameT.getHeight() / frameT.getWidth();

        // 高度一致，宽度比例拉伸
        int h2 = parent.getHeight();
        int w2 = parent.getHeight() * frameT.getWidth() / frameT.getHeight();

        int targetWidth, targetHeight;
        if (h1 <= parent.getHeight()) {
            targetWidth = w1;
            targetHeight = h1;
        } else {
            targetWidth = w2;
            targetHeight = h2;
        }

        ViewGroup.LayoutParams params = surfaceView.getLayoutParams();
        params.width = targetWidth;
        params.height = targetHeight;
        surfaceView.setLayoutParams(params);
    }
}