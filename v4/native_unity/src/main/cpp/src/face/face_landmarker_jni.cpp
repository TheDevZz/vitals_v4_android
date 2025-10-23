#include <jni.h>

#include "tensorflow/lite/c/c_api.h"

#include "opencv2/core.hpp"
#include "opencv2/imgproc.hpp"

#include "face_landmarker.hpp"
#include "jni_utils.h"

class MultiFaceLandmarksHolder {
public:
    MultiFaceLandmarks data;
};

extern "C"
JNIEXPORT jlong JNICALL
Java_com_vitals_sdk_lib_FaceLandmarker_createFaceLandmarker(JNIEnv *env, jobject thiz,
                                    jstring face_detection_model_path,
                                    jstring face_landmarker_model_path)
{
    const char *face_detection_model_path_c = env->GetStringUTFChars(face_detection_model_path, 0);
    const char *face_landmarker_model_path_c = env->GetStringUTFChars(face_landmarker_model_path, 0);

    auto* faceLandmarker = new FaceLandmarker(
            face_detection_model_path_c,
            face_landmarker_model_path_c
    );

    env->ReleaseStringUTFChars(face_detection_model_path, face_detection_model_path_c);
    env->ReleaseStringUTFChars(face_landmarker_model_path, face_landmarker_model_path_c);
    return (long) faceLandmarker;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_vitals_sdk_lib_FaceLandmarker_releaseFaceLandmarker(JNIEnv *env, jobject thiz, jlong ptr) {
    delete (FaceLandmarker*) ptr;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_vitals_sdk_lib_FaceLandmarker_nativeSetNumFaces(JNIEnv *env, jobject thiz, jlong ptr,
                                                         jint num_faces) {
    ((FaceLandmarker*) ptr)->num_faces = num_faces;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_vitals_sdk_lib_FaceLandmarker_nativeDetect(JNIEnv *env, jobject thiz,
                                                    jlong ptr, jbyteArray data,
                                                    jint width, jint height)
{
    jbyte* byte = env->GetByteArrayElements(data, nullptr);
    cv::Mat img(height, width, CV_8UC3, byte);
    auto faceLandmarker = (FaceLandmarker*) ptr;
    MultiFaceLandmarks res = faceLandmarker->detect(img);
    auto holder = new MultiFaceLandmarksHolder();
    holder->data = std::move(res);
    return (jlong) holder;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_vitals_sdk_lib_FaceLandmarker_nativeDetectBitmap(JNIEnv *env, jobject thiz,
                                                          jlong ptr, jobject bitmap) {
    cv::Mat img;
    vitals::RGBABitmapToRGBMat(env, bitmap, img);
    auto faceLandmarker = (FaceLandmarker*) ptr;
    MultiFaceLandmarks res = faceLandmarker->detect(img);
    auto holder = new MultiFaceLandmarksHolder();
    holder->data = std::move(res);
    return (jlong) holder;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_vitals_sdk_lib_FaceLandmarker_nativeGetLandmarkArray(JNIEnv *env, jobject thiz, jlong ptr,
                                                               jint id) {
    auto holder = (MultiFaceLandmarksHolder*) ptr;
    if (id >= 0 && id < holder->data.size()) {
        const std::vector<NormalizedLandmark>& landmarks = holder->data[id];
        int size = landmarks.size();
        jfloatArray jarr = env->NewFloatArray(size * 2);
        jfloat* arr = env->GetFloatArrayElements(jarr, nullptr);
        for (int i = 0; i < size; ++i) {
            auto offset = i * 2;
            arr[offset] = landmarks[i].x;
            arr[offset + 1] = landmarks[i].y;
        }
        env->ReleaseFloatArrayElements(jarr, arr, 0);
        return jarr;
    } else {
        return nullptr;
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_com_vitals_sdk_lib_FaceLandmarker_nativeReleaseMultiFaceLandmarksHolder(JNIEnv *env,
                                                                             jobject thiz,
                                                                             jlong ptr) {
    delete (MultiFaceLandmarksHolder*) ptr;
}