package com.orbbec.utils;

import java.nio.ByteBuffer;

public class ImageUtils {
    static {
        System.loadLibrary("ImageUtils");
    }

    public static void rotateRGB(ByteBuffer src, int rotateType, int w, int h) {
        nRotateAndFlipRGB(src, rotateType, w, h, false);
    }

    public static void rotateY16(ByteBuffer src, int rotateType, int w, int h) {
        nRotateAndFlipY16(src, rotateType, w, h, false);
    }

    public static void rotateAndFlipRGB(ByteBuffer src, int rotateType, int w, int h) {
        nRotateAndFlipRGB(src, rotateType, w, h, true);
    }

    public static void rotateAndFlipY16(ByteBuffer src, int rotateType, int w, int h) {
        nRotateAndFlipY16(src, rotateType, w, h, true);
    }

    public static int RGB888ToRGBA(ByteBuffer src, ByteBuffer dst, int w, int h, int strideInBytes) {
        return nRGB888ToRGBA(src, dst, w, h, strideInBytes);
    }

    public static int Y16ToRGBA(ByteBuffer src, ByteBuffer dst, int w, int h, int strideInBytes) {
        return nY16ToRGBA(src, dst, w, h, strideInBytes);
    }

    public static int MJPGToRGB(ByteBuffer src, ByteBuffer dst, int w, int h, int size) {
        return nMJPGToRGB24(src, dst, w, h, size);
    }

    public static int saveColor(String fileName, ByteBuffer data, int w, int h, int stride) {
        return nSaveColor(fileName, data, w, h, stride);
    }

    public static int saveDepth(String fileName, ByteBuffer data, int w, int h, int stride) {
        return nSaveDepth(fileName, data, w, h, stride);
    }

    public static void filterDataByStride(ByteBuffer data, int w, int h, int stride) {
        nFilterDataByStride(data, w, h, stride);
    }

    private native static int nRGB888ToRGBA(ByteBuffer src, ByteBuffer dst, int w, int h, int strideInBytes);

    private native static int nY16ToRGBA(ByteBuffer src, ByteBuffer dst, int w, int h, int strideInBytes);

    private native static int nRotateAndFlipRGB(ByteBuffer src, int rotateType, int w, int h, boolean isFlip);

    private native static int nRotateAndFlipY16(ByteBuffer src, int rotateType, int w, int h, boolean isFlip);

    private native static int nMJPGToRGB24(ByteBuffer src, ByteBuffer dst, int w, int h, int size);

    private native static int nSaveColor(String fileName, ByteBuffer data, int w, int h, int stride);

    private native static int nSaveDepth(String fileName, ByteBuffer data, int w, int h, int stride);

    private native static void nFilterDataByStride(ByteBuffer data, int w, int h, int stride);
}
