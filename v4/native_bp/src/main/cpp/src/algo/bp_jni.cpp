#include <jni.h>
#include <string>

//
// Created by TheZz on 2025/10/22.
//

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_vitals_sdk_bp_Native_verifyCredential(JNIEnv *env, jobject thiz, jlong timestamp,
                                               jstring sign) {
    std::string app_secret_hash = "3d44d922058396773f131a9070f8b88f8fb6b7f2733fadae888be7bf26577c37";

    return JNI_TRUE;
}