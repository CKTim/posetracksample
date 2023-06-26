package com.orbbec.widget;

import static com.orbbec.bean.FrameT.FRAME_COLOR;
import static com.orbbec.bean.FrameT.FRAME_DEPTH;
import static com.orbbec.utils.GlobalDef.APP_KEY;
import static com.orbbec.utils.GlobalDef.APP_SECRET;
import static com.orbbec.utils.GlobalDef.AUTH_CODE;
import static com.orbbec.utils.GlobalDef.SKELETON_2D;
import static com.orbbec.utils.GlobalDef.SKELETON_3D;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Process;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.SurfaceHolder;

import com.orbbec.bean.FrameT;
import com.orbbec.obt.Calibration;
import com.orbbec.obt.Frame;
import com.orbbec.obt.Image;
import com.orbbec.obt.ImageFormat;
import com.orbbec.obt.Obt;
import com.orbbec.obt.TrackMode;
import com.orbbec.obt.Tracker;
import com.orbbec.obt.android.ObtAndroidContext;
import com.orbbec.utils.ByteBufferPool;
import com.orbbec.utils.GlobalDef;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class OBPoseGLView extends GLSurfaceView {
    private static final String TAG = "OBPoseGLView";

    private static final int PROCESS_QUEUE_CAPACITY = 1;
    private static final int FRAME_PAIR_QUEUE_CAPACITY = 1;

    private OBPoseRender mOBPoseRender;
    private Context mContext;

    // ms
    private static final long QUEUE_TIMEOUT = 30;
    private static final long THREAD_WAIT_TIME = 100;
    private Tracker mTracker;
    private TrackMode mTrackMode = TrackMode.TRACK_SINGLE;
    private Calibration mCalibration;
    private ObtAndroidContext mObtContext;
    private volatile boolean mIsTracking = false;
    private volatile boolean mIsRenderEnable = true;
    private volatile boolean mIsRunning = true;
    // ms
    private double mTrackTime = 0;
    private double mImgCreateTime = 0;
    private double mDrawSkeletonTime = 0;
    private double mTotalTime = 0;

    private int mSkeletonMode = GlobalDef.SKELETON_2D;

    private float mSmoothingFactor = 0;

    // first:colorImage second:depthImage
    private BlockingQueue<Pair<Image, Image>> mProcessQueue = new ArrayBlockingQueue<>(PROCESS_QUEUE_CAPACITY);
    private BlockingQueue<Pair<FrameT, FrameT>> mFramePairQueue = new ArrayBlockingQueue<>(FRAME_PAIR_QUEUE_CAPACITY);

    private Thread mPoseTrackerThread;
    private Thread mDrawSkeletonThread;
    private Thread mImageCreateThread;
    private final Object mSync = new Object();

    private float mTrackRate = 0;
    private long mFrameCount = 0;
    private long mLastTime = System.nanoTime();

    private int mColorW = 640;
    private int mColorH = 480;
    private volatile Frame mFrame;

    private Runnable mPoseTrackerRunnable = () -> {
        int frameCount = 0;
        long processTimeSub = 0, totalTimeSub = 0;
        Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE);
        while (mIsTracking) {
            try {
                // first:colorImage second:depthImage
                Pair<Image, Image> imagePair = mProcessQueue.poll(QUEUE_TIMEOUT, TimeUnit.MILLISECONDS);
                if (null != imagePair) {
                    long totalStartTime = System.nanoTime();
                    Image colorImage = imagePair.first;
                    Image depthImage = imagePair.second;

                    int colorW = colorImage.getWidth();
                    int colorH = colorImage.getHeight();
                    // 分辨率变化时，需要重新创建Tracker，否则可能会有异常
                    Frame frame = null;
                    synchronized (mSync) {
                        if (null != mTracker) {
                            if (colorW != mColorW || colorH != mColorH) {
                                mColorW = colorW;
                                mColorH = colorH;
                                mTracker.release();
                                mTracker = Tracker.create();
                                mTracker.setCalibration(mCalibration);
                                printCalibration("mPoseTrackerRunnable");
                                mTracker.setMode(mTrackMode);
                                mTracker.setSmoothingFactor(mSmoothingFactor);
                            }

                            long startTime = System.nanoTime();
                            if (mSkeletonMode == GlobalDef.SKELETON_3D) {
                                frame = mTracker.process(colorImage, depthImage);
                            } else {
                                frame = mTracker.process(colorImage);
                            }
                            processTimeSub += (System.nanoTime() - startTime);

                            colorImage.release();
                            depthImage.release();
                        }
                    }

                    if (null != frame && frame.isValid()) {
                        calculateFps();
                        if (null == mFrame) {
                            mFrame = frame;
                        } else {
                            frame.release();
                        }
                    }

                    totalTimeSub += (System.nanoTime() - totalStartTime);

                    // 耗时统计
                    frameCount++;
                    if (30 == frameCount) {
                        mTrackTime = processTimeSub / (1e6 * frameCount);
                        processTimeSub = 0;

                        mTotalTime = totalTimeSub / (1e6 * frameCount);
                        totalTimeSub = 0;
                        frameCount = 0;
                    }
                }
            } catch (InterruptedException e) {
                Log.i(TAG, e.getMessage());
            }
        }
    };

    private Runnable mDrawSkeletonRunnable = () -> {
        int frameCount = 0;
        long drawSkeletonTimeSub = 0;
        while (mIsTracking) {
            if (null != mFrame) {
                long startTime = System.nanoTime();
                if (mIsRenderEnable) {
                    Image colorImage = mFrame.getColor();
                    if (null != colorImage) {
                        if (mSkeletonMode == SKELETON_2D) {
                            mOBPoseRender.drawFrame(colorImage, mFrame.getColorBodyList());
                        } else if (mSkeletonMode == SKELETON_3D) {
                            mOBPoseRender.drawFrame(colorImage, mFrame.getDepthBodyList(), mCalibration);
                        }
                    }
                }
                drawSkeletonTimeSub += (System.nanoTime() - startTime);
                frameCount++;
                if (30 == frameCount) {
                    mDrawSkeletonTime = drawSkeletonTimeSub / (1e6 * frameCount);
                    drawSkeletonTimeSub = 0;
                    frameCount = 0;
                }

                mFrame.release();
                mFrame = null;
            }
        }
    };

    private Runnable mImageCreateRunnable = () -> {
        int frameCount = 0;
        long imgCreateTimeSub = 0;
        while (mIsRunning) {
            try {
                Pair<FrameT, FrameT> data = mFramePairQueue.poll(QUEUE_TIMEOUT, TimeUnit.MILLISECONDS);
                if (null != data) {
                    if (mIsTracking) {
                        long startTime = System.currentTimeMillis();
                        Image colorImage = createImage(data.first);
                        Image depthImage = createImage(data.second);

                        imgCreateTimeSub += (System.currentTimeMillis() - startTime);
                        frameCount++;
                        if (30 == frameCount) {
                            mImgCreateTime = imgCreateTimeSub / (1e6 * frameCount);
                            imgCreateTimeSub = 0;
                            frameCount = 0;
                        }

                        // 回收DirectByteBuffer
                        ByteBufferPool.getInstance().recycleColorBuffer(data.first.getBuffer());
                        ByteBufferPool.getInstance().recycleDepthBuffer(data.second.getBuffer());

                        Pair<Image, Image> imagePair = new Pair<>(colorImage, depthImage);
                        // Drop the oldest frame pair when the queue is fulled
                        if (mProcessQueue.size() == PROCESS_QUEUE_CAPACITY) {
                            Pair<Image, Image> tmp = mProcessQueue.poll();
                            if (null != tmp) {
                                tmp.first.release();
                                tmp.second.release();
                            }
                        }
                        mProcessQueue.offer(imagePair);
                    } else {
                        if (mIsRenderEnable) {
                            mOBPoseRender.drawFrame(data.first);
                        } else {
                            ByteBufferPool.getInstance().recycleColorBuffer(data.first.getBuffer());
                        }
                        ByteBufferPool.getInstance().recycleDepthBuffer(data.second.getBuffer());
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    };

    public OBPoseGLView(Context context) {
        super(context);
        init(context);
    }

    public OBPoseGLView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        mContext = context;
        setEGLContextClientVersion(3);
        mOBPoseRender = new OBPoseRender(this);
        setRenderer(mOBPoseRender);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                mImageCreateThread = new Thread(mImageCreateRunnable);
                mImageCreateThread.setName("ImageCreateThread");
                mIsRunning = true;
                mImageCreateThread.start();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                mIsRunning = false;
                if (null != mImageCreateThread) {
                    try {
                        mImageCreateThread.join(THREAD_WAIT_TIME);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    mImageCreateThread = null;
                }
                mFramePairQueue.clear();
            }
        });
    }

    private void calculateFps() {
        // calculate fps
        mFrameCount++;
        if (mFrameCount == 30) {
            long now = System.nanoTime();
            long diff = now - mLastTime;
            mTrackRate = (float) (1e9 * mFrameCount / diff);
            mFrameCount = 0;
            mLastTime = now;
        }
    }

    private Image createImage(FrameT frame) {
        ByteBuffer buffer = frame.getBuffer();
        int w = frame.getWidth();
        int h = frame.getHeight();
        int stride = frame.getStride();
        int frameType = frame.getFrameType();
        switch (frameType) {
            case FRAME_COLOR:
                return Image.create(ImageFormat.RGB888, w, h, stride, buffer);
            case FRAME_DEPTH:
                return Image.create(ImageFormat.DEPTH16, w, h, stride, buffer);
            default:
                break;
        }
        return null;
    }

    public float getTrackTime() {
        return new BigDecimal(mTrackTime).setScale(2, BigDecimal.ROUND_HALF_UP).floatValue();
    }

    public float getImgCreateTime() {
        return new BigDecimal(mImgCreateTime).setScale(2, BigDecimal.ROUND_HALF_UP).floatValue();
    }

    public float getDrawSkeletonTime() {
        return new BigDecimal(mDrawSkeletonTime).setScale(2, BigDecimal.ROUND_HALF_UP).floatValue();
    }

    public float getPoseTrackTotalTime() {
        return new BigDecimal(mTotalTime).setScale(2, BigDecimal.ROUND_HALF_UP).floatValue();
    }

    public float getTrackRate() {
        return new BigDecimal(mTrackRate).setScale(2, BigDecimal.ROUND_HALF_UP).floatValue();
    }

    public void setTrackMode(int mode) {
        try {
            synchronized (mSync) {
                mTrackMode = TrackMode.fromNative(mode);
                if (null != mTracker) {
                    mTracker.setMode(mTrackMode);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "setTrackMode: " + e.getMessage());
        }
    }

    public void setSkeletonMode(int mode) {
        this.mSkeletonMode = mode;
    }

    public void setCalibration(Calibration calibration) {
        this.mCalibration = calibration;
    }

    public void setSmoothingFactor(float smoothingFactor) {
        if (null != mTracker) {
            mSmoothingFactor = smoothingFactor;
            mTracker.setSmoothingFactor(smoothingFactor);
        } else {
            Log.w(TAG, "setSmoothingFactor: Tracker is uninitialized!");
        }
    }

    public void setRenderEnable(boolean enable) {
        mIsRenderEnable = enable;
        if (!enable) {
            mOBPoseRender.clearWindow();
        }
    }

    public float getRenderRate() {
        return mOBPoseRender.getRenderRate();
    }

    public boolean initPoseTrack() {
        try {
            Obt.setLicense(APP_KEY, APP_SECRET, AUTH_CODE);
            mObtContext = new ObtAndroidContext(mContext);
            return mObtContext.initialize();
        } catch (Exception e) {
            Log.w(TAG, "init: " + e.getMessage());
        }
        return false;
    }

    public void releasePoseTrack() {
        stopTrack();
        if (null != mObtContext) {
            mObtContext.terminate();
            mObtContext = null;
        }
    }

    public void update(Pair<FrameT, FrameT> data) {
        if (mFramePairQueue.size() == FRAME_PAIR_QUEUE_CAPACITY) {
            Pair<FrameT, FrameT> tmp = mFramePairQueue.poll();
            if (null != tmp) {
                ByteBufferPool.getInstance().recycleColorBuffer(tmp.first.getBuffer());
                ByteBufferPool.getInstance().recycleDepthBuffer(tmp.second.getBuffer());
            }
        }
        mFramePairQueue.offer(data);
    }

    private void printCalibration(String label) {
        Log.i(TAG, label + " printCalibration: fx:" + mCalibration.fx + " fy:" + mCalibration.fy +
                " cx:" + mCalibration.cx + " cy:" + mCalibration.cy +
                " colorWidth:" + mCalibration.colorWidth + " colorHeight:" + mCalibration.colorHeight +
                " depthWidth:" + mCalibration.depthWidth + " depthHeight:" + mCalibration.depthHeight +
                " depthUnit:" + mCalibration.depthUnit);
    }

    public void startTrack() {
        mTracker = Tracker.create();
        mTracker.setCalibration(mCalibration);
        printCalibration("startTrack");
        mTracker.setMode(mTrackMode);
        mTracker.setSmoothingFactor(mSmoothingFactor);

        mPoseTrackerThread = new Thread(mPoseTrackerRunnable);
        mPoseTrackerThread.setName("PoseTrackerThread");
        mDrawSkeletonThread = new Thread(mDrawSkeletonRunnable);
        mDrawSkeletonThread.setName("DrawSkeletonThread");
        mIsTracking = true;
        mPoseTrackerThread.start();
        mDrawSkeletonThread.start();
    }

    public void stopTrack() {
        synchronized (mSync) {
            mIsTracking = false;

            mTrackRate = 0;
            mTrackTime = 0;
            mImgCreateTime = 0;
            mDrawSkeletonTime = 0;
            mTotalTime = 0;

            if (null != mPoseTrackerThread) {
                try {
                    mPoseTrackerThread.join(THREAD_WAIT_TIME);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                mPoseTrackerThread = null;
            }

            if (null != mDrawSkeletonThread) {
                try {
                    mDrawSkeletonThread.join(THREAD_WAIT_TIME);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                mDrawSkeletonThread = null;
            }

            if (null != mTracker) {
                mTracker.release();
                mTracker = null;
            }

            if (null != mFrame) {
                mFrame.release();
                mFrame = null;
            }

            while (mProcessQueue.size() > 0) {
                Pair<Image, Image> tmp = mProcessQueue.poll();
                if (null != tmp) {
                    tmp.first.release();
                    tmp.second.release();
                }
            }
        }
    }
}
