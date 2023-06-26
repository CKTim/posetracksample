package com.orbbec.widget;

import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.util.Log;

import com.orbbec.bean.FrameT;
import com.orbbec.obt.Body;
import com.orbbec.obt.Calibration;
import com.orbbec.obt.Image;
import com.orbbec.utils.ByteBufferPool;
import com.orbbec.utils.GlUtil;
import com.orbbec.utils.ImageUtils;
import com.orbbec.utils.RenderUtils;

import java.lang.ref.WeakReference;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class OBPoseRender implements GLSurfaceView.Renderer {
    private static final String TAG = "OBPoseRender";

    private WeakReference<GLSurfaceView> mSurfaceView;

    private int mProgramHandle;
    private int maPositionLoc;
    private int maTextureCoordLoc;
    private int muTexture0Loc;

    private int mTextureColor;

    private int mWidth, mHeight;

    private float mRenderFps = 0;
    private long mFrameCount = 0;
    private long mLastTime = System.nanoTime();

    private Object mLock = new Object();

    private static final String VERTEX_SHADER =
            "attribute vec4 aPosition;\n" +
                    "attribute vec2 aTextureCoord;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "void main() {\n" +
                    "    gl_Position = aPosition;\n" +
                    "    vTextureCoord = aTextureCoord.xy;\n" +
                    "}\n";

    private static final String FRAGMENT_SHADER =
            "precision mediump float;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "uniform sampler2D uTexture0;\n" +
                    "void main(){\n" +
                    "    vec4 color = texture2D(uTexture0, vTextureCoord);\n" +
                    "    gl_FragColor = color;\n" +
                    "}\n";

    private static final float[] VERTEX = {
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f, 1.0f,
            1.0f, 1.0f,
    };

    private static final float[] TEXTURE = {
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f,
    };

    private static final FloatBuffer mVertexArray = GlUtil.createFloatBuffer(VERTEX);
    private static final FloatBuffer mTexCoordArray = GlUtil.createFloatBuffer(TEXTURE);

    public OBPoseRender(GLSurfaceView surfaceView) {
        mSurfaceView = new WeakReference<>(surfaceView);
    }

    public void drawFrame(Image image) {
        drawFrame(image, null, null);
    }

    public void drawFrame(Image image, ArrayList<Body> bodies) {
        drawFrame(image, bodies, null);
    }

    public void drawFrame(Image image, ArrayList<Body> bodies, Calibration calibration) {
        synchronized (mLock) {
            GLSurfaceView glSurfaceView = mSurfaceView.get();
            if (null == glSurfaceView) {
                Log.e(TAG, "drawFrame: the glSurfaceView is null!");
                return;
            }
            if (null != bodies && null == calibration) { // 2D模式
                RenderUtils.draw2DBodyList(image, bodies);
            } else if (null != bodies && null != calibration) { // 3D模式
                RenderUtils.draw3DBodyList(image, bodies, calibration);
            }
            mWidth = image.getWidth();
            mHeight = image.getHeight();
            int stride = image.getStride();
            if (stride != mWidth * 3) { // RGB格式
                // 由于Image返回的buffer内部是做了16字节对齐的，所以实际的stride可能不等于w * 3，需要将字节对齐补的字节数过滤掉
                ImageUtils.filterDataByStride(image.getBuffer(), mWidth, mHeight, stride);
            }
            glSurfaceView.queueEvent(() -> {
                calculateFps();
                loadTexture(image.getBuffer(), mWidth, mHeight, GLES30.GL_RGB, GLES30.GL_RGB, mTextureColor);
                image.release();
            });
            glSurfaceView.requestRender();
        }
    }

    public void drawFrame(FrameT frame) {
        synchronized (mLock) {
            GLSurfaceView glSurfaceView = mSurfaceView.get();
            if (null == glSurfaceView) {
                Log.e(TAG, "drawFrame: the glSurfaceView is null!");
                return;
            }
            mWidth = frame.getWidth();
            mHeight = frame.getHeight();
            glSurfaceView.queueEvent(() -> {
                calculateFps();
                loadTexture(frame.getBuffer(), mWidth, mHeight, GLES30.GL_RGB, GLES30.GL_RGB, mTextureColor);
                // 回收buffer
                ByteBufferPool.getInstance().recycleColorBuffer(frame.getBuffer());
            });
            glSurfaceView.requestRender();
        }
    }

    public void clearWindow() {
        synchronized (mLock) {
            GLSurfaceView glSurfaceView = mSurfaceView.get();
            if (null == glSurfaceView) {
                Log.e(TAG, "clearWindow: the glSurfaceView is null!");
                return;
            }
            glSurfaceView.queueEvent(() -> {
                ByteBuffer buffer = ByteBuffer.allocateDirect(mWidth * mHeight * 3);
                loadTexture(buffer, mWidth, mHeight, GLES30.GL_RGB, GLES30.GL_RGB, mTextureColor);
            });
            glSurfaceView.requestRender();
            mRenderFps = 0;
        }
    }

    public float getRenderRate() {
        return mRenderFps;
    }

    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
        mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (mProgramHandle == 0) {
            Log.d(TAG, "createProgram failed");
            return;
        }

        maPositionLoc = GLES30.glGetAttribLocation(mProgramHandle, "aPosition");
        GlUtil.checkLocation(maPositionLoc, "aPosition");

        maTextureCoordLoc = GLES30.glGetAttribLocation(mProgramHandle, "aTextureCoord");
        GlUtil.checkLocation(maTextureCoordLoc, "aTextureCoord");
        muTexture0Loc = GLES30.glGetUniformLocation(mProgramHandle, "uTexture0");
        GlUtil.checkLocation(muTexture0Loc, "uTexture0");

        mTextureColor = createTexture();
    }

    @Override
    public void onSurfaceChanged(GL10 gl10, int width, int height) {
        GLES30.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl10) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

        GLES30.glUseProgram(mProgramHandle);

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mTextureColor);
        GLES30.glUniform1i(muTexture0Loc, 0);

        GLES30.glEnableVertexAttribArray(maPositionLoc);
        GLES30.glVertexAttribPointer(maPositionLoc, 2, GLES30.GL_FLOAT, false, 0, mVertexArray);

        GLES30.glEnableVertexAttribArray(maTextureCoordLoc);
        GLES30.glVertexAttribPointer(maTextureCoordLoc, 2, GLES30.GL_FLOAT, false, 0, mTexCoordArray);

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);

        GLES30.glDisableVertexAttribArray(maPositionLoc);
        GLES30.glDisableVertexAttribArray(maTextureCoordLoc);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
        GLES30.glUseProgram(0);
    }

    private static int createTexture() {
        int[] textures = new int[1];
        GLES30.glGenTextures(1, textures, 0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[0]);
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        return textures[0];
    }

    private static void loadTexture(Buffer data, int width, int height, int internalFormat, int format, int usedTexId) {
        try {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, usedTexId);
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, GLES30.GL_UNSIGNED_BYTE, data);
            GlUtil.checkGlError("glTexImage2D");
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void updateTexture(Buffer data, int width, int height, int format, int usedTexId) {
        try {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, usedTexId);
            GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, width, height, format, GLES30.GL_UNSIGNED_BYTE, data);
            GlUtil.checkGlError("glTexSubImage2D");
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void calculateFps() {
        // calculate fps
        mFrameCount++;
        if (mFrameCount == 30) {
            long now = System.nanoTime();
            long diff = now - mLastTime;
            mRenderFps = (float) (1e9 * mFrameCount / diff);
            mFrameCount = 0;
            mLastTime = now;
        }
    }
}
