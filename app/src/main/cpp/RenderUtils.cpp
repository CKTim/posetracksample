#include <jni.h>
#include <android/log.h>
#include <vector>
#include <sstream>
#include <iomanip>
#include "opencv2/opencv.hpp"

#define REGISTER_CLASS "com/orbbec/utils/RenderUtils"

#define LOG_TAG "RenderUtils-Jni"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

#define LINE_THICK_INDEX 0
#define POINT_THICK_INDEX 1
#define FONT_SCALE_INDEX 2
#define FONT_THICK_INDEX 3

// 用于判断绘制骨架时需要连接哪些骨骼点
const std::vector<std::pair<int, int>> gLimbKeyPointsIds = {{1,  3},
                                                            {1,  4},
                                                            {3,  5},
                                                            {5,  7},
                                                            {4,  6},
                                                            {6,  8},
                                                            {1,  2},
                                                            {2,  9},
                                                            {2,  10},
                                                            {9,  11},
                                                            {11, 13},
                                                            {10, 12},
                                                            {12, 14},
                                                            {1,  0},
                                                            {0,  15},
                                                            {15, 17},
                                                            {0,  16},
                                                            {16, 18},
                                                            {13, 19},
                                                            {14, 20}};

// 骨骼线条绘制颜色表
const std::array<cv::Scalar, 5> gLinesColors{
        cv::Scalar(0, 96, 255), cv::Scalar(0, 255, 96), cv::Scalar(255, 72, 0),
        cv::Scalar(102, 0, 255), cv::Scalar(247, 50, 182)};

// 不同分辨率下需要绘制不同粗细的线和点
const std::array<std::array<float, 4>, 3> gSkeletonDrawParam{
        std::array<float, 4>{2, -1, 0.5, 1.5},
        std::array<float, 4>{4, 5.5, 1, 2},
        std::array<float, 4>{6, 9, 1.5, 4.5}};

template<typename T>
std::string toString(T val) {
    std::ostringstream out;
    out << std::fixed << std::setprecision(2) << val;
    return out.str();
}

/** Model fitting joint definition
 */
typedef enum {
    /** nose */
    OBT_JOINT_NOSE = 0,
    /** neck */
    OBT_JOINT_NECK = 1,
    /** middle hip */
    OBT_JOINT_MIDDLE_HIP = 2,
    /** right shoulder */
    OBT_JOINT_SHOULDER_RIGHT = 3,
    /** left shoulder */
    OBT_JOINT_SHOULDER_LEFT = 4,
    /** right relbow */
    OBT_JOINT_RELBOW_RIGHT = 5,
    /** left elbow */
    OBT_JOINT_ELBOW_LEFT = 6,
    /** right wrist */
    OBT_JOINT_RWRIST_RIGHT = 7,
    /** left wrist */
    OBT_JOINT_WRIST_LEFT = 8,
    /** right hip */
    OBT_JOINT_HIP_RIGHT = 9,
    /** left hip */
    OBT_JOINT_HIP_LEFT = 10,
    /** right knee */
    OBT_JOINT_KNEE_RIGHT = 11,
    /** left knee */
    OBT_JOINT_KNEE_LEFT = 12,
    /** right ankle */
    OBT_JOINT_ANKLE_RIGHT = 13,
    /** left ankle */
    OBT_JOINT_ANKLE_LEFT = 14,
    /** right eye */
    OBT_JOINT_EYE_RIGHT = 15,
    /** left eye */
    OBT_JOINT_EYE_LEFT = 16,
    /** right ear */
    OBT_JOINT_EAR_RIGHT = 17,
    /** left ear */
    OBT_JOINT_EAR_LEFT = 18,
    /** right toe */
    OBT_JOINT_TOE_RIGHT = 19,
    /** left toe */
    OBT_JOINT_TOE_LEFT = 20,
    /** Unknown */
    OBT_JOINT_MAX
} OBT_JOINT_TYPE;

/**
 * 用于选择不同分辨率下绘制骨架的线型粗细
 *
 * @param imgW 宽
 * @param imgH 高
 * @return 线型参数
 */
std::array<float, 4> selectDrawParam(int imgW, int imgH) {
    if (1280 * 720 == imgW * imgH) {
        return gSkeletonDrawParam[1];
    } else if (1920 * 1080 == imgW * imgH) {
        return gSkeletonDrawParam[2];
    } else {
        return gSkeletonDrawParam[0];
    }
}

/**
 * 获取所有骨骼点的坐标
 *
 * @param env
 * @param jJointArray 所有骨骼点
 * @return 所有骨骼点的坐标
 */
std::vector<std::vector<float>> get2DBodyJoints(JNIEnv *env, jobjectArray jJointArray) {
    std::vector<std::vector<float>> joints;
    jint jointArraySize = env->GetArrayLength(jJointArray);
    for (int idx = 0; idx < jointArraySize; idx++) {
        jobject jJoint = env->GetObjectArrayElement(jJointArray, idx);
        jclass jclJoint = env->FindClass("com/orbbec/obt/Joint");
        jfieldID jfJointPt = env->GetFieldID(jclJoint, "position", "Lcom/orbbec/obt/Point;");
        jobject jPoint = env->GetObjectField(jJoint, jfJointPt);
        jclass jclPoint = env->FindClass("com/orbbec/obt/Point");
        jfieldID jfPointX = env->GetFieldID(jclPoint, "x", "F");
        jfieldID jfPointY = env->GetFieldID(jclPoint, "y", "F");
        jfieldID jfPointZ = env->GetFieldID(jclPoint, "z", "F");
        jfloat ptX = env->GetFloatField(jPoint, jfPointX);
        jfloat ptY = env->GetFloatField(jPoint, jfPointY);
        jfloat ptZ = env->GetFloatField(jPoint, jfPointZ);
        std::vector<float> joint{ptX, ptY, ptZ};
        joints.push_back(joint);
    }
    return joints;
}

/**
 * @class com_orbbec_utils_RenderUtils
 * @method nDraw2DBodyList
 * @signature (IILjava/nio/ByteBuffer;ILjava/util/ArrayList;)V
 *
 * @param imgW the width of the image
 * @param imgH the height of the image
 * @param imgBuffer the data of the image
 * @param imgStride the stride of the image
 * @param bodies the pose result of the image
 */
JNIEXPORT void JNICALL
nDraw2DBodyList(JNIEnv *env, jclass instance,
                jint imgW,
                jint imgH,
                jobject imgBuffer,
                jint imgStride,
                jobject bodies) {
    uint8_t *data = (uint8_t *) env->GetDirectBufferAddress(imgBuffer);

    cv::Mat rgbMat(imgH, imgW, CV_8UC3, data, imgStride);

    jclass jclArrayList = env->GetObjectClass(bodies);
    jmethodID jmArrayListGet = env->GetMethodID(jclArrayList, "get", "(I)Ljava/lang/Object;");
    jmethodID jmArrayListSize = env->GetMethodID(jclArrayList, "size", "()I");
    jint bodiesSize = env->CallIntMethod(bodies, jmArrayListSize);

    float lineThick, pointThick, fontScale, fontThick = 0;
    auto param = selectDrawParam(imgW, imgH);
    lineThick = param[LINE_THICK_INDEX];
    pointThick = param[POINT_THICK_INDEX];
    fontScale = param[FONT_SCALE_INDEX];
    fontThick = param[FONT_THICK_INDEX];

    for (int i = 0; i < bodiesSize; i++) {
        jobject jBody = env->CallObjectMethod(bodies, jmArrayListGet, i);
        jclass jclBody = env->GetObjectClass(jBody);
        jfieldID jfBodyId = env->GetFieldID(jclBody, "id", "I");
        // Body ID
        jint id = env->GetIntField(jBody, jfBodyId);

        // Body Joints
        jfieldID jfBodyJoints = env->GetFieldID(jclBody, "joints", "[Lcom/orbbec/obt/Joint;");
        jobjectArray jJointArray = static_cast<jobjectArray>(env->GetObjectField(jBody,
                                                                                 jfBodyJoints));
        auto joints = get2DBodyJoints(env, jJointArray);

        cv::Scalar leftColor = gLinesColors[id % gLinesColors.size()];
        cv::Scalar rightColor = gLinesColors[(id + 1) % gLinesColors.size()];

        for (const auto &limb: gLimbKeyPointsIds) {
            auto firstJoint = joints[limb.first];
            auto secondJoint = joints[limb.second];
            float firstPtX = firstJoint[0];
            float firstPtY = firstJoint[1];
            float secondPtX = secondJoint[0];
            float secondPtY = secondJoint[1];

            if ((firstPtX < 0.1 && firstPtY < 0.1) || (secondPtX < 0.1 && secondPtY < 0.1)) {
                continue;
            }

            if (limb.second <= 2 || (limb.second % 2 == 0)) {
                cv::line(rgbMat, cv::Point2d(firstPtX, firstPtY),
                         cv::Point2d(secondPtX, secondPtY), leftColor, lineThick);
            } else {
                cv::line(rgbMat, cv::Point2d(firstPtX, firstPtY),
                         cv::Point2d(secondPtX, secondPtY), rightColor, lineThick);
            }
        }

        for (int idx = 0; idx < joints.size(); idx++) {
            float ptX = joints[idx][0];
            float ptY = joints[idx][1];

            cv::Point2d pt = cv::Point2d(ptX, ptY);
            if (pt.x > 0.1 && pt.y > 0.1) {
                cv::circle(rgbMat, pt, 4, cv::Scalar(255, 255, 255), pointThick);
            }
        }

        std::string str = "ID: " + toString(id);
        cv::putText(rgbMat, cv::String(str),
                    cv::Point(joints[OBT_JOINT_NOSE][0] - 10, joints[OBT_JOINT_NOSE][1] - 40),
                    CV_FONT_HERSHEY_SIMPLEX, fontScale, cv::Scalar(0, 255, 0), fontThick);
    }
}

/**
 * 获取所有骨骼点的坐标，并将空间的点投影到2D平面
 *
 * @param env
 * @param jJointArray 所有骨骼点
 * @param fx 相机内参fx
 * @param fy 相机内参fy
 * @param cx 相机内参cx
 * @param cy 相机内参cy
 * @return 所有骨骼点投影到2D平面的坐标信息
 */
std::vector<std::vector<float>> get3DBodyJoints(JNIEnv *env, jobjectArray jJointArray,
                                                float fx, float fy, float cx, float cy) {
    std::vector<std::vector<float>> joints;
    jint jointArraySize = env->GetArrayLength(jJointArray);
    for (int idx = 0; idx < jointArraySize; idx++) {
        jobject jJoint = env->GetObjectArrayElement(jJointArray, idx);
        jclass jclJoint = env->FindClass("com/orbbec/obt/Joint");
        jfieldID jfJointScore = env->GetFieldID(jclJoint, "score", "F");
        jfloat score = env->GetFloatField(jJoint, jfJointScore);
        jfieldID jfJointPt = env->GetFieldID(jclJoint, "position", "Lcom/orbbec/obt/Point;");
        jobject jPoint = env->GetObjectField(jJoint, jfJointPt);
        jclass jclPoint = env->FindClass("com/orbbec/obt/Point");
        jfieldID jfPointX = env->GetFieldID(jclPoint, "x", "F");
        jfieldID jfPointY = env->GetFieldID(jclPoint, "y", "F");
        jfieldID jfPointZ = env->GetFieldID(jclPoint, "z", "F");
        jfloat ptX = env->GetFloatField(jPoint, jfPointX);
        jfloat ptY = env->GetFloatField(jPoint, jfPointY);
        jfloat ptZ = env->GetFloatField(jPoint, jfPointZ);

        // 将3D的点投影到2D平面
        float x = cx + fx * ptX / ptZ;
        float y = cy + fy * ptY / ptZ;
        float z = 0;
        std::vector<float> joint{x, y, z, score};
        joints.push_back(joint);
    }
    return joints;
}

/**
 * 绘制3D骨骼
 *
 * @class com_orbbec_utils_RenderUtils
 * @method nDraw3DBodyList
 * @signature (IILjava/nio/ByteBuffer;ILjava/util/ArrayList;Lcom/orbbec/obt/Calibration;)V
 *
 * @param imgW the width of the image
 * @param imgH the height of the image
 * @param imgBuffer the data of the image
 * @param imgStride the stride of the image
 * @param bodies the pose result of the image
 * @param calibration the param of the camera
 */
JNIEXPORT void JNICALL
nDraw3DBodyList(JNIEnv *env, jclass instance,
                jint imgW,
                jint imgH,
                jobject imgBuffer,
                jint imgStride,
                jobject bodies,
                jobject calibration) {
    jclass jclCalibration = env->GetObjectClass(calibration);
    jfieldID jfIdFx = env->GetFieldID(jclCalibration, "fx", "F");
    jfieldID jfIdFy = env->GetFieldID(jclCalibration, "fy", "F");
    jfieldID jfIdCx = env->GetFieldID(jclCalibration, "cx", "F");
    jfieldID jfIdCy = env->GetFieldID(jclCalibration, "cy", "F");

    jfloat fx = env->GetFloatField(calibration, jfIdFx);
    jfloat fy = env->GetFloatField(calibration, jfIdFy);
    jfloat cx = env->GetFloatField(calibration, jfIdCx);
    jfloat cy = env->GetFloatField(calibration, jfIdCy);

    uint8_t *data = (uint8_t *) env->GetDirectBufferAddress(imgBuffer);

    cv::Mat rgbMat(imgH, imgW, CV_8UC3, data, imgStride);

    jclass jclArrayList = env->GetObjectClass(bodies);
    jmethodID jmArrayListGet = env->GetMethodID(jclArrayList, "get", "(I)Ljava/lang/Object;");
    jmethodID jmArrayListSize = env->GetMethodID(jclArrayList, "size", "()I");
    jint bodiesSize = env->CallIntMethod(bodies, jmArrayListSize);

    float lineThick, pointThick, fontScale, fontThick = 0;
    auto param = selectDrawParam(imgW, imgH);
    lineThick = param[LINE_THICK_INDEX];
    pointThick = param[POINT_THICK_INDEX];
    fontScale = param[FONT_SCALE_INDEX];
    fontThick = param[FONT_THICK_INDEX];

    for (int i = 0; i < bodiesSize; i++) {
        jobject jBody = env->CallObjectMethod(bodies, jmArrayListGet, i);
        jclass jclBody = env->GetObjectClass(jBody);
        jfieldID jfBodyId = env->GetFieldID(jclBody, "id", "I");
        // Body ID
        jint id = env->GetIntField(jBody, jfBodyId);

        // Body Joints
        jfieldID jfBodyJoints = env->GetFieldID(jclBody, "joints", "[Lcom/orbbec/obt/Joint;");
        jobjectArray jJointArray = static_cast<jobjectArray>(env->GetObjectField(jBody,
                                                                                 jfBodyJoints));
        auto joints = get3DBodyJoints(env, jJointArray, fx, fy, cx, cy);

        cv::Scalar leftColor = gLinesColors[id % gLinesColors.size()];
        cv::Scalar rightColor = gLinesColors[(id + 1) % gLinesColors.size()];

        for (const auto &limb: gLimbKeyPointsIds) {
            auto firstJoint = joints[limb.first];
            auto secondJoint = joints[limb.second];
            float firstPtX = firstJoint[0];
            float firstPtY = firstJoint[1];
            float firstJointScore = firstJoint[3];
            float secondPtX = secondJoint[0];
            float secondPtY = secondJoint[1];
            float secondJointScore = secondJoint[3];
            if (firstJointScore == 0 || secondJointScore == 0) {
                continue;
            }

            if (limb.second <= 2 || (limb.second % 2 == 0)) {
                cv::line(rgbMat, cv::Point2d(firstPtX, firstPtY),
                         cv::Point2d(secondPtX, secondPtY), leftColor, lineThick);
            } else {
                cv::line(rgbMat, cv::Point2d(firstPtX, firstPtY),
                         cv::Point2d(secondPtX, secondPtY), rightColor, lineThick);
            }
        }

        for (int idx = 0; idx < joints.size(); idx++) {
            float ptX = joints[idx][0];
            float ptY = joints[idx][1];
            float jointScore = joints[idx][3];

            cv::Point2d pt = cv::Point2d(ptX, ptY);
            if (jointScore != 0) {
                cv::circle(rgbMat, pt, 4,
                           cv::Scalar(255, 255, 255), pointThick);
            }
        }

        std::string str = "ID: " + toString(id);
        cv::putText(rgbMat, cv::String(str),
                    cv::Point(joints[OBT_JOINT_NOSE][0] - 10, joints[OBT_JOINT_NOSE][1] - 40),
                    CV_FONT_HERSHEY_SIMPLEX, fontScale, cv::Scalar(0, 255, 0), fontThick);
    }
}

JNINativeMethod jniMethods[] = {
        {"nDraw2DBodyList", "(IILjava/nio/ByteBuffer;ILjava/util/ArrayList;)V",                             (void *) &nDraw2DBodyList},
        {"nDraw3DBodyList", "(IILjava/nio/ByteBuffer;ILjava/util/ArrayList;Lcom/orbbec/obt/Calibration;)V", (void *) &nDraw3DBodyList},
};

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;

    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }

    jclass clz = env->FindClass(REGISTER_CLASS);
    env->RegisterNatives(clz, jniMethods, sizeof(jniMethods) / sizeof(JNINativeMethod));
    env->DeleteLocalRef(clz);
    LOGD("RenderUtils JNI_OnLoad");
    return JNI_VERSION_1_6;
}
