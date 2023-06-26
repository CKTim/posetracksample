package com.orbbec.utils;

import android.util.Log;

import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class ByteBufferPool {
    private static final String TAG = "ByteBufferPool";

    private static final int DEFAULT_BUFFER_QUEUE_SIZE = 5;

    private static ByteBufferPool mInstance;

    private int mColorBufferCapacity;
    private int mDepthBufferCapacity;

    private final BlockingQueue<ByteBuffer> mColorBufferQueue;
    private final BlockingQueue<ByteBuffer> mDepthBufferQueue;

    private ByteBufferPool() {
        mColorBufferQueue = new ArrayBlockingQueue<>(DEFAULT_BUFFER_QUEUE_SIZE);
        mDepthBufferQueue = new ArrayBlockingQueue<>(DEFAULT_BUFFER_QUEUE_SIZE);
    }

    /**
     * 获取DirectByteBuffer池
     *
     * @return 返回获取到的Buffer池的实例
     */
    public static ByteBufferPool getInstance() {
        if (null == mInstance) {
            synchronized (ByteBufferPool.class) {
                if (null == mInstance) {
                    mInstance = new ByteBufferPool();
                }
            }
        }
        return mInstance;
    }

    /**
     * 申请彩色Frame的buffer
     *
     * @param capacity 待申请的buffer的大小
     * @return 返回申请的buffer
     */
    public ByteBuffer acquireColorBuffer(int capacity) {
        if (capacity != mColorBufferCapacity) {
            mColorBufferQueue.clear();
            mColorBufferCapacity = capacity;
            return ByteBuffer.allocateDirect(mColorBufferCapacity);
        }
        if (mColorBufferQueue.size() > 0) {
            return mColorBufferQueue.poll();
        } else {
            return ByteBuffer.allocateDirect(mColorBufferCapacity);
        }
    }

    /**
     * 申请深度Frame的buffer
     *
     * @param capacity 待申请的buffer的大小
     * @return 返回申请的buffer
     */
    public ByteBuffer acquireDepthBuffer(int capacity) {
        if (capacity != mDepthBufferCapacity) {
            mDepthBufferQueue.clear();
            mDepthBufferCapacity = capacity;
            return ByteBuffer.allocateDirect(mDepthBufferCapacity);
        }
        if (mDepthBufferQueue.size() > 0) {
            return mDepthBufferQueue.poll();
        } else {
            return ByteBuffer.allocateDirect(mDepthBufferCapacity);
        }
    }

    /**
     * 归还使用完后的彩色Frame的buffer
     *
     * @param buffer 待归还的buffer
     */
    public void recycleColorBuffer(ByteBuffer buffer) {
        // buffer池中的buffer大小已经变化，因此不回收
        if (buffer.capacity() != mColorBufferCapacity) {
            Log.w(TAG, "recycleColorBuffer: the capacity of the buffer was changed,drop the oldest buffer!");
            return;
        }
        // buffer池已经满了，放弃该buffer的回收
        if (mColorBufferQueue.size() > DEFAULT_BUFFER_QUEUE_SIZE) {
            Log.w(TAG, "recycleColorBuffer: the byte buffer pool is fulled, drop the oldest buffer!");
            return;
        }
        // 复位buffer
        buffer.clear();
        mColorBufferQueue.offer(buffer);
    }

    /**
     * 归还使用完后的深度Frame的buffer
     *
     * @param buffer 待归还的buffer
     */
    public void recycleDepthBuffer(ByteBuffer buffer) {
        // buffer池中的buffer大小已经变化，因此不回收
        if (buffer.capacity() != mDepthBufferCapacity) {
            Log.w(TAG, "returnBuffer: the capacity of the buffer was changed,drop the oldest buffer!");
            return;
        }
        // buffer池已经满了，放弃该buffer的回收
        if (mDepthBufferQueue.size() > DEFAULT_BUFFER_QUEUE_SIZE) {
            Log.w(TAG, "returnBuffer: the byte buffer pool is fulled, drop the oldest buffer!");
            return;
        }
        // 复位buffer
        buffer.clear();
        mDepthBufferQueue.offer(buffer);
    }
}
