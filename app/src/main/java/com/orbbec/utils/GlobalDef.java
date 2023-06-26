package com.orbbec.utils;

import com.orbbec.obt.Calibration;
import com.orbbec.obt.DepthUnit;

public class GlobalDef {

    public static final int RESOLUTION_W = 640;
    public static final int RESOLUTION_H = 480;
    public static final int FPS = 30;

    public static final int SKELETON_2D = 0; // 2D骨架
    public static final int SKELETON_3D = 1; // 3D骨架

    public static final String APP_KEY = "202112152843702";
    public static final String APP_SECRET = "ba129a5569870625c403221f4f2219160dc6cca3";
    public static final String AUTH_CODE = "5e08506b-de9a-4a28-a295-e606fdfbf17b";

    public static Calibration CALIBRATION_ROTATE = new Calibration(575.66128f, 572.08369f, 240.0f, 320.0f, 480, 640, 480, 640, DepthUnit.DEPTH_1_MM);
    public static Calibration CALIBRATION = new Calibration(572.08369f, 575.66128f, 320.0f, 240.0f, 640, 480, 640, 480, DepthUnit.DEPTH_1_MM);
}
