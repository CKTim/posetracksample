package com.orbbec.bean;

public class TrackInfo {
    private float mFrameRate; // 相机出流帧率
    private float mTrackRate; // 算法帧率
    private float mRenderRate; // 实际渲染帧率
    private float mTrackTime; // 算法处理耗时
    private float mImgCreateTime; // 算法数据封装耗时
    private float mDrawSkeletonTime; // 骨架与RGB图像合成耗时
    private float mPoseTrackTotalTime; // PoseTrackBlock线程处理总耗时
    private float mRotateTime; // 相机流旋转耗时

    public void setFrameRate(float frameRate) {
        this.mFrameRate = frameRate;
    }

    public void setTrackRate(float trackRate) {
        this.mTrackRate = trackRate;
    }

    public void setRenderRate(float renderRate) {
        this.mRenderRate = renderRate;
    }

    public void setTrackTime(float trackTime) {
        this.mTrackTime = trackTime;
    }

    public void setImgCreateTime(float imgCreateTime) {
        this.mImgCreateTime = imgCreateTime;
    }

    public void setDrawSkeletonTime(float drawSkeletonTime) {
        this.mDrawSkeletonTime = drawSkeletonTime;
    }

    public void setPoseTrackTotalTime(float poseTrackTotalTime) {
        this.mPoseTrackTotalTime = poseTrackTotalTime;
    }

    public void setRotateTime(float rotateTime) {
        this.mRotateTime = rotateTime;
    }

    public float getFrameRate() {
        return mFrameRate;
    }

    public float getTrackRate() {
        return mTrackRate;
    }

    public float getRenderRate() {
        return mRenderRate;
    }

    public float getTrackTime() {
        return mTrackTime;
    }

    public float getImgCreateTime() {
        return mImgCreateTime;
    }

    public float getDrawSkeletonTime() {
        return mDrawSkeletonTime;
    }

    public float getPoseTrackTotalTime() {
        return mPoseTrackTotalTime;
    }

    public float getRotateTime() {
        return mRotateTime;
    }

    @Override
    public String toString() {
        return "TrackInfo{FrameRate," +
                "TrackRate," +
                "RenderRate," +
                "TrackTime," +
                "ImageCreateTime," +
                "DrawSkeletonTime," +
                "PoseTrackTotalTime," +
                "RotateTime}##" + mFrameRate
                + "," + mTrackRate
                + "," + mRenderRate
                + "," + mTrackTime
                + "," + mImgCreateTime
                + "," + mDrawSkeletonTime
                + "," + mPoseTrackTotalTime
                + "," + mRotateTime;
    }
}
