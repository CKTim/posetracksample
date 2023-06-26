package com.orbbec.ui.pose;

import android.content.Context;
import android.util.Pair;

import com.orbbec.bean.FrameT;
import com.orbbec.bean.TrackInfo;
import com.orbbec.obt.Calibration;
import com.orbbec.ui.base.BasePresenter;

public class PosePresenter extends BasePresenter<PoseContract.IPoseView>
        implements PoseContract.IPosePresenter, PoseContract.IPoseModel.OnCameraEventsListener {
    private PoseModel mPoseModel;

    public PosePresenter() {
        mPoseModel = new PoseModel();
    }

    @Override
    public void openCamera() {
        mPoseModel.openCamera((Context) mView, this);
    }

    @Override
    public void closeCamera() {
        mPoseModel.closeCamera();
    }

    @Override
    public void setRotation(int rotation) {
        mPoseModel.setRotation(rotation);
    }

    @Override
    public void switchConfig(int position) {
        mPoseModel.switchConfig(position);
    }

    @Override
    public Calibration loadCalibration() {
        return mPoseModel.loadCalibration();
    }

    @Override
    public void onNewFrames(Pair<FrameT, FrameT> frames) {
        mView.onNewData(frames);
    }

    @Override
    public void onOpenDeviceFailed(String errorMsg) {
        mView.showInfoDialog(errorMsg);
    }

    @Override
    public void onDeviceStatusChanged(boolean isConnected) {
        mView.updateDeviceStatusDlg(isConnected);
    }

    @Override
    public void onRefreshTrackInfo(TrackInfo info) {
        mView.updateTrackInfo(info);
    }
}
