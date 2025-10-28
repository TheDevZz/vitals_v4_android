#include <jni.h>
#include <string>

//
// Created by TheZz on 2025/10/22.
//

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_vitals_sdk_bp_Native_verifyCredential(JNIEnv *env, jobject thiz, jlong timestamp,
                                               jstring sign) {


    return JNI_TRUE;
}