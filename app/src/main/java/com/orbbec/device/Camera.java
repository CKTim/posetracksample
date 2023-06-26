package com.orbbec.device;

import android.content.Context;
import android.util.Log;

import com.orbbec.bean.FrameT;
import com.orbbec.obsensor.AlignMode;
import com.orbbec.obsensor.CameraParam;
import com.orbbec.obsensor.ColorFrame;
import com.orbbec.obsensor.DepthFrame;
import com.orbbec.obsensor.Device;
import com.orbbec.obsensor.DeviceProperty;
import com.orbbec.obsensor.FrameSetCallback;
import com.orbbec.obsensor.StreamProfileList;
import com.orbbec.obsensor.StreamType;
import com.orbbec.obsensor.VideoFrame;
import com.orbbec.obsensor.Config;
import com.orbbec.obsensor.DeviceChangedCallback;
import com.orbbec.obsensor.DeviceList;
import com.orbbec.obsensor.Format;
import com.orbbec.obsensor.FrameSet;
import com.orbbec.obsensor.OBContext;
import com.orbbec.obsensor.Pipeline;
import com.orbbec.obsensor.SensorType;
import com.orbbec.obsensor.StreamProfile;
import com.orbbec.obsensor.VideoStreamProfile;
import com.orbbec.utils.ByteBufferPool;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.orbbec.obsensor.PermissionType.OB_PERMISSION_WRITE;
import static com.orbbec.bean.FrameT.FRAME_COLOR;
import static com.orbbec.bean.FrameT.FRAME_DEPTH;
import static com.orbbec.utils.GlobalDef.FPS;
import static com.orbbec.utils.GlobalDef.RESOLUTION_H;
import static com.orbbec.utils.GlobalDef.RESOLUTION_W;

public class Camera {
    private static final String TAG = "Camera";
    public static final int ROTATE_DISABLE = 0;
    public static final int ROTATE_90_CLOCKWISE = 1;
    public static final int ROTATE_90_COUNTERCLOCKWISE = 2;

    private static final int INDEX_W = 0;
    private static final int INDEX_H = 1;
    private static final int[][] RESOLUTION_LIST = {
            {640, 480},
            {1280, 720},
            {1920, 1080}};
    private boolean mInit = false;

    private static final int FRAME_SET_QUEUE_CAPACITY = 1;
    private static final int QUEUE_POLL_TIMEOUT = 300; // ms
    private BlockingQueue<FrameSet> mFrameSetQueue = new ArrayBlockingQueue<>(FRAME_SET_QUEUE_CAPACITY);

    private volatile boolean mStreamStart = false;

    private Context mContext;
    private EventHandler mEventHandler;

    private OBContext mOBContext;
    private Pipeline mPipeline;

    private Config mConfig;

    private CameraParam mCameraParam;

    private Device mDevice;

    private int mRotateType = ROTATE_DISABLE;

    private int mResolutionIndex = 0;

    private float mFps = 0;
    private long mFrameCount = 0;
    private long mLastTime = System.nanoTime();

    private double mRotateTime = 0;

    private final Object mLock = new Object();

    private volatile boolean mIsSwitchingConfig;

    private Thread mStreamThread;
    private Runnable mStreamRunnable = () -> {
        Log.d(TAG, "camera thread started");
        long rotateTimeSub = 0;
        int frameCount = 0;
        while (mStreamStart) {
            try {
                FrameSet frameSet = mFrameSetQueue.poll(QUEUE_POLL_TIMEOUT, TimeUnit.MILLISECONDS);

                if (null == frameSet) {
                    Log.d(TAG, "run: mFrameSet is null !!!");
                    continue;
                }

                ColorFrame colorFrame = frameSet.getColorFrame();
                DepthFrame depthFrame = frameSet.getDepthFrame();

                if (null != colorFrame && null != depthFrame && colorFrame.getTimeStamp() != 0 && depthFrame.getTimeStamp() != 0) {
                    long FAstp = System.currentTimeMillis();
                    calculateFps();
                    int colorW = colorFrame.getWidth();
                    int colorH = colorFrame.getHeight();
                    int colorSize = colorFrame.getDataSize();
                    ByteBuffer colorBuffer = ByteBufferPool.getInstance().acquireColorBuffer(colorSize);
                    colorFrame.getData(colorBuffer);
                    FrameT colorFrameT = new FrameT(colorW, colorH, FRAME_COLOR, colorBuffer);
                    colorFrameT.setSystemTimestamp(FAstp);

                    int depthW = depthFrame.getWidth();
                    int depthH = depthFrame.getHeight();
                    int depthSize = depthFrame.getDataSize();
                    ByteBuffer depthBuffer = ByteBufferPool.getInstance().acquireDepthBuffer(depthSize);
                    depthFrame.getData(depthBuffer);
                    FrameT depthFrameT = new FrameT(depthW, depthH, FRAME_DEPTH, depthBuffer);

                    long startTime = System.nanoTime();
                    synchronized (mLock) {
                        if (ROTATE_DISABLE != mRotateType) {
                            colorFrameT.rotate(mRotateType);
                            depthFrameT.rotate(mRotateType);
                        }
                    }
                    rotateTimeSub += (System.nanoTime() - startTime);
                    frameCount++;
                    if (30 == frameCount) {
                        mRotateTime = rotateTimeSub / (1e6 * frameCount);
                        rotateTimeSub = 0;
                        frameCount = 0;
                    }

                    if (null != mEventHandler) {
                        mEventHandler.onNewFrame(colorFrameT, depthFrameT);
                    }
                } else {
                    Log.i(TAG, "ColorFrame:" + colorFrame + " DepthFrame:" + depthFrame);
                }

                frameSet.close();
                if (null != colorFrame) {
                    colorFrame.close();
                }
                if (null != depthFrame) {
                    depthFrame.close();
                }
            } catch (InterruptedException e) {
                Log.w(TAG, e.getMessage());
            }
        }
    };

    private FrameSetCallback mFrameSetCallback = (frameSet) -> {
        // Drop the oldest frameSet
        if (!mIsSwitchingConfig) {
            if (mFrameSetQueue.size() == FRAME_SET_QUEUE_CAPACITY) {
                FrameSet tmp = mFrameSetQueue.poll();
                if (null != tmp) {
                    tmp.close();
                }
            }
            mFrameSetQueue.offer(frameSet);
        } else {
            frameSet.close();
        }
    };

    public Camera(Context context, EventHandler handler) {
        mContext = context;
        mEventHandler = handler;
    }

    private void calculateFps() {
        // calculate fps
        mFrameCount++;
        if (mFrameCount == 30) {
            long now = System.nanoTime();
            long diff = now - mLastTime;
            mFps = (float) (1e9 * mFrameCount / diff);
            mFrameCount = 0;
            mLastTime = now;
        }
    }

    public float getFrameRate() {
        return new BigDecimal(mFps).setScale(2, BigDecimal.ROUND_HALF_UP).floatValue();
    }

    public float getRotateTime() {
        return new BigDecimal(mRotateTime).setScale(2, BigDecimal.ROUND_HALF_UP).floatValue();
    }

    public void setRotation(int rotateType) {
        synchronized (mLock) {
            mRotateType = rotateType;
        }
    }

    private void updateCameraParam() {
        // Get camera parameter
        if (null != mPipeline) {
            mCameraParam = mPipeline.getCameraParam();
            Log.i(TAG, "updateCameraParam: " + mCameraParam);
        }
    }

    public CameraParam getCameraParam() {
        return this.mCameraParam;
    }

    public void switchConfig(int position) {
        if (!mInit) {
            Log.w(TAG, "switchConfig: Device has not been initialized!");
            return;
        }

        if (!mStreamStart) {
            Log.w(TAG, "switchConfig: Stream has not been started!");
            return;
        }

        // 此标志位为了确保分辨率切换完成并且相机内参更新完成后再进行数据帧回调
        mIsSwitchingConfig = true;

        int colorW = RESOLUTION_LIST[position][INDEX_W];
        int colorH = RESOLUTION_LIST[position][INDEX_H];

        // Config stream profile
        StreamProfile colorStreamProfile = null;
        StreamProfile depthStreamProfile = null;
        StreamProfileList colorProfileList = mPipeline.getStreamProfileList(SensorType.COLOR);
        StreamProfileList depthProfileList = mPipeline.getStreamProfileList(SensorType.DEPTH);
        if (null != colorProfileList) {
            colorStreamProfile = colorProfileList.getVideoStreamProfile(colorW, colorH, Format.RGB888, FPS);
            colorProfileList.close();
        }
        if (null != colorStreamProfile) {
            mConfig.enableStream(colorStreamProfile);
            Log.i(TAG, "switchConfig: " + printStreamProfile(colorStreamProfile.as(StreamType.VIDEO)));
            colorStreamProfile.close();
        }

        if (null != depthProfileList) {
            depthStreamProfile = depthProfileList.getVideoStreamProfile(RESOLUTION_W, RESOLUTION_H, Format.Y16, FPS);
            depthProfileList.close();
        }
        if (null != depthStreamProfile) {
            mConfig.enableStream(depthStreamProfile);
            Log.i(TAG, "switchConfig: " + printStreamProfile(depthStreamProfile.as(StreamType.VIDEO)));
            depthStreamProfile.close();
        }

        mPipeline.switchConfig(mConfig);
        mResolutionIndex = position;

        updateCameraParam();

        mIsSwitchingConfig = false;
    }

    public void open() {
        if (mInit) {
            Log.w(TAG, "Device has already been opened!");
            return;
        }

        mOBContext = new OBContext(mContext, new DeviceChangedCallback() {
            @Override
            public void onDeviceAttach(DeviceList deviceList) {
                try {
                    if (null == mPipeline) {
                        mDevice = deviceList.getDevice(0);
                        mPipeline = new Pipeline(mDevice);
                    }
                    initCamera();
                    start();
                    mInit = true;
                    deviceList.close();
                    if (null != mEventHandler) {
                        mEventHandler.onDeviceStatusChanged(true);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "onDeviceAttach: " + e.getMessage());
                    if (null != mEventHandler) {
                        mEventHandler.onOpenDeviceFailed(e.getMessage());
                    }
                }
            }

            @Override
            public void onDeviceDetach(DeviceList deviceList) {
                Log.d(TAG, "onNoDevice: ");
                try {
                    stop();
                    deInitCamera();
                    deviceList.close();
                } catch (Exception e) {
                    Log.e(TAG, "onDeviceDetach: " + e.getMessage());
                }
                if (null != mEventHandler) {
                    mEventHandler.onDeviceStatusChanged(false);
                }
            }
        });
    }

    public void close() {
        if (!mInit) {
            Log.w(TAG, "Device has not been opened!");
            return;
        }

        stop();

        deInitCamera();

        if (null != mOBContext) {
            mOBContext.close();
            mOBContext = null;
        }

        mInit = false;

        Log.i(TAG, "Device closed!");
    }

    private void deInitCamera() {
        if (null != mConfig) {
            mConfig.close();
            mConfig = null;
        }

        if (null != mPipeline) {
            mPipeline.close();
            mPipeline = null;
        }

        if (null != mDevice) {
            mDevice.close();
            mDevice = null;
        }
    }

    private void initCamera() {
        mConfig = new Config();

        // Open hardware d2c
        mConfig.setAlignMode(AlignMode.ALIGN_D2C_HW_ENABLE);

        int colorW = RESOLUTION_LIST[mResolutionIndex][INDEX_W];
        int colorH = RESOLUTION_LIST[mResolutionIndex][INDEX_H];

        // Config stream profile
        StreamProfile colorStreamProfile = null;
        StreamProfile depthStreamProfile = null;
        StreamProfileList colorProfileList = mPipeline.getStreamProfileList(SensorType.COLOR);
        StreamProfileList depthProfileList = mPipeline.getStreamProfileList(SensorType.DEPTH);
        if (null != colorProfileList) {
            colorStreamProfile = colorProfileList.getVideoStreamProfile(colorW, colorH, Format.RGB888, FPS);
            colorProfileList.close();
        }
        if (null != colorStreamProfile) {
            mConfig.enableStream(colorStreamProfile);
            Log.i(TAG, "initCamera: " + printStreamProfile(colorStreamProfile.as(StreamType.VIDEO)));
            colorStreamProfile.close();
        }

        if (null != depthProfileList) {
            depthStreamProfile = depthProfileList.getVideoStreamProfile(RESOLUTION_W, RESOLUTION_H, Format.Y16, FPS);
            depthProfileList.close();
        }
        if (null != depthStreamProfile) {
            mConfig.enableStream(depthStreamProfile);
            Log.i(TAG, "initCamera: " + printStreamProfile(depthStreamProfile.as(StreamType.VIDEO)));
            depthStreamProfile.close();
        }

        if (null != mDevice) {
            // 关闭自动曝光优先
            if (mDevice.isPropertySupported(DeviceProperty.OB_PROP_COLOR_AUTO_EXPOSURE_PRIORITY_INT, OB_PERMISSION_WRITE)) {
                mDevice.setPropertyValueI(DeviceProperty.OB_PROP_COLOR_AUTO_EXPOSURE_PRIORITY_INT, 0);
            }

            if (mDevice.isPropertySupported(DeviceProperty.OB_PROP_COLOR_MIRROR_BOOL, OB_PERMISSION_WRITE)
                    && mDevice.isPropertySupported(DeviceProperty.OB_PROP_DEPTH_MIRROR_BOOL, OB_PERMISSION_WRITE)) {
                mDevice.setPropertyValueB(DeviceProperty.OB_PROP_COLOR_MIRROR_BOOL, false);
                mDevice.setPropertyValueB(DeviceProperty.OB_PROP_DEPTH_MIRROR_BOOL, false);
            }
        }
        // 开启帧同步
        try {
            mPipeline.enableFrameSync();
        } catch (Exception e) {
            Log.w(TAG, "initCamera: " + e.getMessage());
        }
    }

    private String printStreamProfile(VideoStreamProfile vsp) {
        String ss = "format = " + vsp.getFormat() + ", width = " + vsp.getWidth()
                + ", height = " + vsp.getHeight() + ", fps = " + vsp.getFps()
                + ", type = " + vsp.getType();
        return ss;
    }

    private String printFrame(VideoFrame frame) {
        String ss = "index = " + frame.getFrameIndex() + ", format = " + frame.getFormat()
                + ",size = " + frame.getDataSize() + ", width = " + frame.getWidth()
                + ", height = " + frame.getHeight() + ", type = " + frame.getStreamType()
                + ", systemTimeStamp" + frame.getSystemTimeStamp() + ", timeStamp" + frame.getTimeStamp();
        return ss;
    }

    private void start() {
        mPipeline.start(mConfig, mFrameSetCallback);
        if (null == mStreamThread) {
            mStreamThread = new Thread(mStreamRunnable);
            mStreamThread.setName("StreamThread");
        }
        mStreamStart = true;
        mStreamThread.start();
        updateCameraParam();
    }

    private void stop() {
        mStreamStart = false;
        if (null != mStreamThread) {
            try {
                mStreamThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mStreamThread = null;
        }

        if (null != mPipeline) {
            mPipeline.stop();
        }
    }

    public interface EventHandler {
        void onNewFrame(FrameT colorFrame, FrameT depthFrame);

        void onOpenDeviceFailed(String errorMsg);

        void onDeviceStatusChanged(boolean isConnected);
    }
}
