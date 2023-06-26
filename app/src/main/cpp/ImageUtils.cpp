#include <jni.h>
#include <stdio.h>
#include <string.h>
#include "opencv2/core/core.hpp"
#include "opencv2/highgui/highgui.hpp"
#include "opencv2/imgproc/imgproc.hpp"
#include <android/log.h>

#define REGISTER_CLASS "com/orbbec/utils/ImageUtils"

#define LOG_TAG "ImageUtils-Jni"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

typedef unsigned char uint8_t;

typedef struct {
    uint8_t r;
    uint8_t g;
    uint8_t b;
} RGB888Pixel;

int ConventToRGBA(uint8_t *src, uint8_t *dst, int w, int h, int strideInBytes) {
    for (int y = 0; y < h; ++y) {
        uint8_t *pTexture = dst + (y * w * 4);
        const RGB888Pixel *pData = (const RGB888Pixel *) (src + y * strideInBytes);
        for (int x = 0; x < w; ++x, ++pData, pTexture += 4) {
            pTexture[0] = pData->r;
            pTexture[1] = pData->g;
            pTexture[2] = pData->b;
            pTexture[3] = 255;
        }

    }

    return 0;
}

jint nMJPGToRGB24(JNIEnv *env, jclass obj, jobject src, jobject dst, jint w, jint h,
                  jint dataSize) {
    if (src == nullptr || dst == nullptr) {
        return -1;
    }
    uint8_t *mjpg = (uint8_t *) env->GetDirectBufferAddress(src);

    uint8_t *rgb = (uint8_t *) env->GetDirectBufferAddress(dst);

    cv::Mat rawMat(1, dataSize, CV_8UC1, mjpg);
    cv::Mat bgrMat = cv::imdecode(rawMat, 1);

    cv::Mat rgbMat;
    cv::cvtColor(bgrMat, rgbMat, CV_BGR2RGB);

    memcpy(rgb, rgbMat.data, w * h * 3);
    return 0;
}

jint nRGB888ToRGBA(JNIEnv *env, jclass obj, jobject src, jobject dst, jint w, jint h,
                   jint strideInBytes) {

    if (src == nullptr || dst == nullptr) {
        return -1;
    }
    uint8_t *srcBuf = (uint8_t *) env->GetDirectBufferAddress(src);

    uint8_t *dstBuf = (uint8_t *) env->GetDirectBufferAddress(dst);

    ConventToRGBA((uint8_t *) srcBuf, dstBuf, w, h, strideInBytes);

    return 0;
}

jint nSaveColor(JNIEnv *env, jclass obj, jstring filename, jobject data, jint width, jint height,
                jint stride) {
    uint8_t *jdata = (uint8_t *) env->GetDirectBufferAddress(data);
    std::vector<int> compression_params;
    compression_params.push_back(CV_IMWRITE_PNG_COMPRESSION);
    compression_params.push_back(0);    // 无压缩png
    compression_params.push_back(cv::IMWRITE_PNG_STRATEGY);    // 无压缩png
    compression_params.push_back(cv::IMWRITE_PNG_STRATEGY_DEFAULT);    // 无压缩png
    const char *name = env->GetStringUTFChars(filename, JNI_FALSE);
    cv::String strName(name);
    cv::Mat bgr_color(height, width, CV_8UC3, jdata, stride);
    cv::Mat rgb_color;
    cv::cvtColor(bgr_color, rgb_color, CV_BGR2RGB);
    cv::imwrite(strName, rgb_color, compression_params);
    env->ReleaseStringUTFChars(filename, name);
    return 1;
}

jint nSaveDepth(JNIEnv *env, jclass obj, jstring filename, jobject data, jint width, jint height,
                jint stride) {
    uint8_t *jdata = (uint8_t *) env->GetDirectBufferAddress(data);
    std::vector<int> compression_params;
    compression_params.push_back(CV_IMWRITE_PNG_COMPRESSION);
    compression_params.push_back(0);    // 无压缩png
    compression_params.push_back(cv::IMWRITE_PNG_STRATEGY);    // 无压缩png
    compression_params.push_back(cv::IMWRITE_PNG_STRATEGY_DEFAULT);    // 无压缩png
    const char *name = env->GetStringUTFChars(filename, JNI_FALSE);
    cv::String strName(name);
    cv::Mat y16_depth(height, width, CV_16UC1, jdata, stride);
    cv::imwrite(strName, y16_depth, compression_params);
    env->ReleaseStringUTFChars(filename, name);
    return 1;
}

void nFilterDataByStride(JNIEnv *env, jclass obj,
                         jobject data, jint width, jint height, jint stride) {
    uint8_t *dataPtr = (uint8_t *) env->GetDirectBufferAddress(data);
    uint8_t *memCpyPtr = dataPtr;
    int copyStep = width * 3;
    for (int i = 0; i < height; i++) {
        memcpy(dataPtr, memCpyPtr, copyStep);
        dataPtr += copyStep;
        memCpyPtr += stride;
    }
}

int *m_histogram;
enum {
    HISTSIZE = 0xFFFF,
};

int ConventFromDepthToRGBA(short *src, int *dst, int w, int h, int strideInBytes) {

    // Calculate the accumulative histogram (the yellow display...)
    if (m_histogram == NULL) {
        m_histogram = new int[HISTSIZE];
    }
    memset(m_histogram, 0, HISTSIZE * sizeof(int));

    int nNumberOfPoints = 0;
    unsigned int value;
    int Size = w * h;
    for (int i = 0; i < Size; ++i) {
        value = src[i];
        if (value != 0) {
            m_histogram[value]++;
            nNumberOfPoints++;
        }
    }

    int nIndex;
    for (nIndex = 1; nIndex < HISTSIZE; nIndex++) {
        m_histogram[nIndex] += m_histogram[nIndex - 1];
    }

    if (nNumberOfPoints != 0) {
        for (nIndex = 1; nIndex < HISTSIZE; nIndex++) {
            m_histogram[nIndex] = (unsigned int) (256 * (1.0f - ((float) m_histogram[nIndex] /
                                                                 nNumberOfPoints)));
        }
    }

    for (int y = 0; y < h; ++y) {
        uint8_t *rgb = (uint8_t *) (dst + y * w);
        short *pView = src + y * w;
        for (int x = 0; x < w; ++x, rgb += 4, pView++) {
            value = m_histogram[*pView];
            rgb[0] = value;
            rgb[1] = value;
            rgb[2] = 0x00;
            rgb[3] = 0xff;
        }
    }
    return 0;
}


jint nY16ToRGBA(JNIEnv *env, jclass obj, jobject src, jobject dst, jint w, jint h,
                jint strideInBytes) {

    if (src == nullptr || dst == nullptr) {
        return -1;
    }
    uint8_t *srcBuf = (uint8_t *) env->GetDirectBufferAddress(src);

    int *dstBuf = (int *) env->GetDirectBufferAddress(dst);

    ConventFromDepthToRGBA((short *) srcBuf, dstBuf, w, h, strideInBytes);

    return 0;
}

jint nRotateAndFlipRGB(JNIEnv *env, jclass obj, jobject src, jint rotateType, jint w, jint h,
                       jboolean isFlip) {

    if (src == nullptr) {
        return -1;
    }
    uint8_t *srcBuf = (uint8_t *) env->GetDirectBufferAddress(src);
    auto size = env->GetDirectBufferCapacity(src);

    cv::Mat colorMat(h, w, CV_8UC3, srcBuf);
    if (rotateType == 1) {
        cv::rotate(colorMat, colorMat, cv::ROTATE_90_CLOCKWISE);
    } else if (rotateType == 2) {
        cv::rotate(colorMat, colorMat, cv::ROTATE_90_COUNTERCLOCKWISE);
    }
    if (isFlip) {
        cv::flip(colorMat, colorMat, 1);
    }
    memcpy(srcBuf, colorMat.data, size);

    return 0;
}

jint RotateAndFlipY16(JNIEnv *env, jclass obj, jobject src, jint rotateType, jint w, jint h,
                      jboolean isFlip) {

    if (src == nullptr) {
        return -1;
    }
    uint8_t *srcBuf = (uint8_t *) env->GetDirectBufferAddress(src);
    auto size = env->GetDirectBufferCapacity(src);

    cv::Mat depthMat(h, w, CV_16UC1, srcBuf);
    if (rotateType == 1) {
        cv::rotate(depthMat, depthMat, cv::ROTATE_90_CLOCKWISE);
    } else if (rotateType == 2) {
        cv::rotate(depthMat, depthMat, cv::ROTATE_90_COUNTERCLOCKWISE);
    }
    if (isFlip) {
        cv::flip(depthMat, depthMat, 1);
    }
    memcpy(srcBuf, depthMat.data, size);

    return 0;
}

JNINativeMethod jniMethods[] = {
        {"nRGB888ToRGBA",       "(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;III)I", (void *) &nRGB888ToRGBA},
        {"nY16ToRGBA",          "(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;III)I", (void *) &nY16ToRGBA},
        {"nRotateAndFlipRGB",   "(Ljava/nio/ByteBuffer;IIIZ)I",                     (void *) &nRotateAndFlipRGB},
        {"nRotateAndFlipY16",   "(Ljava/nio/ByteBuffer;IIIZ)I",                     (void *) &RotateAndFlipY16},
        {"nMJPGToRGB24",        "(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;III)I", (void *) &nMJPGToRGB24},
        {"nSaveColor",          "(Ljava/lang/String;Ljava/nio/ByteBuffer;III)I",    (void *) &nSaveColor},
        {"nSaveDepth",          "(Ljava/lang/String;Ljava/nio/ByteBuffer;III)I",    (void *) &nSaveDepth},
        {"nFilterDataByStride", "(Ljava/nio/ByteBuffer;III)V",                      (void *) &nFilterDataByStride},
};

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;

    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }

    jclass clz = env->FindClass(REGISTER_CLASS);
    env->RegisterNatives(clz, jniMethods, sizeof(jniMethods) / sizeof(JNINativeMethod));
    env->DeleteLocalRef(clz);

    LOGD("ImageUtils JNI_OnLoad");

    return JNI_VERSION_1_6;
}
