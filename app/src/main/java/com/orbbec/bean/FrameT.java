package com.orbbec.bean;

import com.orbbec.utils.ImageUtils;

import java.nio.ByteBuffer;

public class FrameT {
    public static final int FRAME_COLOR = 0x01;
    public static final int FRAME_DEPTH = 0x02;

    private int mWidth;
    private int mHeight;
    private int mFrameType;
    private int mStride;
    private ByteBuffer mBuffer;

    private long mSystemTimestamp;

    public FrameT(int w, int h, int frameType, ByteBuffer buffer) {
        this.mWidth = w;
        this.mHeight = h;
        this.mFrameType = frameType;
        this.mBuffer = buffer;
        if (frameType == FRAME_COLOR) {
            this.mStride = w * 3;
        } else if (frameType == FRAME_DEPTH) {
            this.mStride = w * 2;
        }
    }

    public long getSystemTimestamp() {
        return mSystemTimestamp;
    }

    public void setSystemTimestamp(long timestamp) {
        this.mSystemTimestamp = timestamp;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public ByteBuffer getBuffer() {
        return mBuffer;
    }

    public int getStride() {
        return mStride;
    }

    public int getFrameType() {
        return mFrameType;
    }

    public void rotate(int rotateType) {
        int temp = 0;
        if (null != mBuffer) {
            switch (mFrameType) {
                case FRAME_COLOR:
                    ImageUtils.rotateRGB(mBuffer, rotateType, mWidth, mHeight);
                    temp = mWidth;
                    mWidth = mHeight;
                    mHeight = temp;
                    mStride = mWidth * 3;
                    break;
                case FRAME_DEPTH:
                    ImageUtils.rotateY16(mBuffer, rotateType, mWidth, mHeight);
                    temp = mWidth;
                    mWidth = mHeight;
                    mHeight = temp;
                    mStride = mWidth * 2;
                    break;
                default:
                    break;
            }
        }
    }
}
