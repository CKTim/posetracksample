package com.orbbec.utils;

import com.orbbec.obt.Body;
import com.orbbec.obt.Calibration;
import com.orbbec.obt.Image;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class RenderUtils {
    static {
        System.loadLibrary("RenderUtils");
    }

    /**
     * 绘制2D骨骼
     *
     * @param img    rgb格式的图像
     * @param bodies 骨骼数据
     */
    public static void draw2DBodyList(Image img, ArrayList<Body> bodies) {
        nDraw2DBodyList(img.getWidth(), img.getHeight(), img.getBuffer(), img.getStride(), bodies);
    }

    /**
     * 绘制3D骨骼
     *
     * @param img         rgb格式的图像
     * @param bodies      骨骼数据
     * @param calibration 相机内参
     */
    public static void draw3DBodyList(Image img, ArrayList<Body> bodies, Calibration calibration) {
        nDraw3DBodyList(img.getWidth(), img.getHeight(), img.getBuffer(), img.getStride(), bodies, calibration);
    }

    private static native void nDraw2DBodyList(int imgW, int imgH, ByteBuffer imgBuffer, int imgStride, ArrayList<Body> bodies);

    private static native void nDraw3DBodyList(int imgW, int imgH, ByteBuffer imgBuffer, int imgStride, ArrayList<Body> bodies, Calibration calibration);
}
