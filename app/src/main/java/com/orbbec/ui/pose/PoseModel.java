package com.orbbec.ui.pose;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import com.orbbec.bean.FrameT;
import com.orbbec.bean.TrackInfo;
import com.orbbec.device.Camera;
import com.orbbec.obsensor.CameraParam;
import com.orbbec.obsensor.datatype.CameraIntrinsic;
import com.orbbec.obt.Calibration;
import com.orbbec.obt.DepthUnit;
import com.orbbec.utils.GlobalDef;

public class PoseModel implements PoseContract.IPoseModel, Camera.EventHandler {
    private static final String TAG = "PoseModel";
    private Calibration mCalibration;
    private Camera mCamera;
    private OnCameraEventsListener mCameraEventsListener;
    private int mRotateType = Camera.ROTATE_DISABLE;
    private TrackInfo mTrackInfo = new TrackInfo();

    private boolean isRotate() {
        return mRotateType != Camera.ROTATE_DISABLE;
    }

    @Override
    public void openCamera(Context context, OnCameraEventsListener listener) {
        this.mCameraEventsListener = listener;
        if (null == mCamera) {
            mCamera = new Camera(context, this);
        }
        mCamera.open();
        setRotation(mRotateType);
    }

    @Override
    public void closeCamera() {
        if (null != mCamera) {
            mCamera.close();
        }
        mCameraEventsListener = null;
    }

    @Override
    public void setRotation(int rotation) {
        if (null != mCamera) {
            mRotateType = rotation;
            mCamera.setRotation(rotation);
            mCalibration = loadCalibration(mCamera.getCameraParam(), isRotate());
        }
    }

    @Override
    public void switchConfig(int position) {
        if (null != mCamera) {
            mCamera.switchConfig(position);
            mCalibration = loadCalibration(mCamera.getCameraParam(), isRotate());
        }
    }

    @Override
    public Calibration loadCalibration() {
        return mCalibration;
    }

    @Override
    public void onNewFrame(FrameT colorFrame, FrameT depthFrame) {
        if (null != mCameraEventsListener) {
            mCameraEventsListener.onNewFrames(new Pair<>(colorFrame, depthFrame));

            float frameRate = mCamera.getFrameRate();
            float rotateTime = mCamera.getRotateTime();
            if (frameRate != mTrackInfo.getFrameRate() || rotateTime != mTrackInfo.getRotateTime()) {
                mTrackInfo.setFrameRate(frameRate);
                mTrackInfo.setRotateTime(mCamera.getRotateTime());
                mCameraEventsListener.onRefreshTrackInfo(mTrackInfo);
            }
        }
    }

    @Override
    public void onOpenDeviceFailed(String errorMsg) {
        mCameraEventsListener.onOpenDeviceFailed(errorMsg);
    }

    @Override
    public void onDeviceStatusChanged(boolean isConnected) {
        mCameraEventsListener.onDeviceStatusChanged(isConnected);
    }

    /**
     * Load the calibration of camera
     *
     * @param cameraParam
     * @return calibration
     */
    private Calibration loadCalibration(CameraParam cameraParam, boolean isRotate) {
        if (null == cameraParam) {
            Log.w(TAG, "loadCalibration: CameraParam is null, use default calibration!");
            return (isRotate ? GlobalDef.CALIBRATION_ROTATE : GlobalDef.CALIBRATION);
        }
        CameraIntrinsic colorIntrinsic = cameraParam.getColorIntrinsic();
        CameraIntrinsic depthIntrinsic = cameraParam.getDepthIntrinsic();
        Calibration calibration = new Calibration();
        calibration.colorWidth = isRotate ? colorIntrinsic.getHeight() : colorIntrinsic.getWidth();
        calibration.colorHeight = isRotate ? colorIntrinsic.getWidth() : colorIntrinsic.getHeight();
        calibration.depthWidth = isRotate ? depthIntrinsic.getHeight() : depthIntrinsic.getWidth();
        calibration.depthHeight = isRotate ? depthIntrinsic.getWidth() : depthIntrinsic.getHeight();
        calibration.depthUnit = DepthUnit.DEPTH_1_MM.toNative();

        calibration.fx = isRotate ? depthIntrinsic.getFy() : depthIntrinsic.getFx();
        calibration.fy = isRotate ? depthIntrinsic.getFx() : depthIntrinsic.getFy();
        calibration.cx = isRotate ? depthIntrinsic.getCy() : depthIntrinsic.getCx();
        calibration.cy = isRotate ? depthIntrinsic.getCx() : depthIntrinsic.getCy();

        if (calibration.fx <= 0 || calibration.fy <= 0 || calibration.cx <= 0 || calibration.cy <= 0 ||
                Float.isNaN(calibration.fx) || Float.isNaN(calibration.fy) || Float.isNaN(calibration.cx) || Float.isNaN(calibration.cy)) {
            return (isRotate ? GlobalDef.CALIBRATION_ROTATE : GlobalDef.CALIBRATION);
        }

        Log.d(TAG, "loadCalibration: fx:" + calibration.fx + " fy:" + calibration.fy +
                " cx:" + calibration.cx + " cy:" + calibration.cy +
                " colorWidth:" + calibration.colorWidth + " colorHeight:" + calibration.colorHeight +
                " depthWidth:" + calibration.depthWidth + " depthHeight:" + calibration.depthHeight +
                " depthUnit:" + calibration.depthUnit);
        return calibration;
    }
}
