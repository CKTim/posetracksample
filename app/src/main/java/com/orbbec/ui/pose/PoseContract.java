package com.orbbec.ui.pose;

import android.content.Context;
import android.util.Pair;

import com.orbbec.bean.FrameT;
import com.orbbec.bean.TrackInfo;
import com.orbbec.obt.Calibration;
import com.orbbec.ui.base.IBaseView;

public interface PoseContract {
    interface IPoseView extends IBaseView<Pair<FrameT, FrameT>> {
        void updateTrackInfo(TrackInfo info);

        void showInfoDialog(String msg);

        void updateDeviceStatusDlg(boolean isConnected);
    }

    interface IPoseModel {

        void openCamera(Context context, OnCameraEventsListener listener);

        void closeCamera();

        void setRotation(int rotation);

        void switchConfig(int position);

        Calibration loadCalibration();

        interface OnCameraEventsListener {
            void onNewFrames(Pair<FrameT, FrameT> frames);

            void onOpenDeviceFailed(String errorMsg);

            void onDeviceStatusChanged(boolean isConnected);

            void onRefreshTrackInfo(TrackInfo info);
        }
    }

    interface IPosePresenter {

        void openCamera();

        void closeCamera();

        void setRotation(int rotation);

        void switchConfig(int position);

        Calibration loadCalibration();
    }
}
