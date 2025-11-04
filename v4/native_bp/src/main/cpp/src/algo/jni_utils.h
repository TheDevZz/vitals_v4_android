#ifndef VITALS_JNI_UTILS_H
#define VITALS_JNI_UTILS_H

#include <jni.h>
// #include <opencv2/imgproc.hpp>
// #include <opencv2/core.hpp>
#include <android/bitmap.h>
#include "log.hpp"

namespace vitals {

// void RGBABitmapToRGBMat(JNIEnv * env, jobject& bitmap, cv::Mat& dst);
// void BitmapToMat(JNIEnv * env, jclass, jobject bitmap, jlong m_addr, jboolean needUnPremultiplyAlpha);

inline void throwByName(JNIEnv *env, const char *name, const char *msg) {
    jclass cls = env->FindClass(name);
    if (cls != NULL) {
        env->ThrowNew(cls, msg);
    }
    env->DeleteLocalRef(cls);
}

inline void throwNativeException(JNIEnv *env, const char *errCode, const char *msg) {
    jclass cls = env->FindClass("com/vitals/lib/VitalsNativeException");
    if (cls != NULL) {
        jmethodID constructor = env->GetMethodID(cls, "<init>",
                                                 "(Ljava/lang/String;Ljava/lang/String;)V");

        jstring jerrCode = env->NewStringUTF(errCode);
        jstring jmsg = env->NewStringUTF(msg);

        jobject obj = env->NewObject(cls, constructor, jerrCode, jmsg);

        int throwResult = env->Throw(static_cast<jthrowable>(obj));
        if (throwResult == 0) {
            LOGD(">>>>> throwNativeException succ");
        } else {
            LOGD(">>>>> throwNativeException fail");
        }

//        env->DeleteLocalRef(jerrCode);
//        env->DeleteLocalRef(jmsg);
    }
    env->DeleteLocalRef(cls);
}

} // namespace vitals

#endif //VITALS_JNI_UTILS_H
