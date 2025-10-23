#ifndef VITALS_JNI_UTILS_H
#define VITALS_JNI_UTILS_H

#include <jni.h>
#include "opencv2/imgproc.hpp"
#include "opencv2/core.hpp"
#include <android/bitmap.h>
#include "../log.hpp"

namespace vitals {

void RGBABitmapToRGBMat(JNIEnv * env, jobject& bitmap, cv::Mat& dst);
void BitmapToMat(JNIEnv * env, jclass, jobject bitmap, jlong m_addr, jboolean needUnPremultiplyAlpha);

} // namespace vitals

#endif //VITALS_JNI_UTILS_H
