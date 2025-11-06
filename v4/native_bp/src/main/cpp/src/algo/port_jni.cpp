#include <jni.h>

#include "../log.hpp"

#include "bp_pred_v3_test.hpp"
#include "jni_utils.h"
#include "measure_vital.hpp"
#include "process.h"
#include "binary_data_manager.hpp"
#include "fea_pred.h"

#include "test_helper.hpp"

#include <sys/stat.h>

using namespace std;

using MeasureResult = vitals::measure::MeasureResult;
std::string debug_dir_path;

extern "C" __attribute__((used)) JNIEXPORT jlong JNICALL
Java_com_vitals_lib_Port_nativeProcessPixelsV2Fea___3D_3IDLjava_lang_String_2IIDD(
  JNIEnv *env, jobject thiz, jdoubleArray pixels, jintArray shape,
  jdouble fps, jstring modelsDir, jint age, jint gender, jdouble height,
  jdouble weight
) {
  try {
    jdouble *p = env->GetDoubleArrayElements(pixels, 0);
    jint *p_shape = env->GetIntArrayElements(shape, 0);
    const char *models_dir_c = env->GetStringUTFChars(modelsDir, 0);

    string models_dir(models_dir_c);

    // pixels shape is (n, 3), need transpose to (3, n)
    //    Eigen::MatrixXd mp = Eigen::MatrixXd::Map(p, p_shape[0],
    //    p_shape[1]).transpose(); MeasureResult res =
    //    vitals::signal::processPixelsV2(mp, fps);

    MeasureResult res = vitals::processPixelsV2(p, p_shape, fps, models_dir,
                                                age, gender, height, weight);

    env->ReleaseDoubleArrayElements(pixels, p, 0);
    env->ReleaseIntArrayElements(shape, p_shape, 0);
    env->ReleaseStringUTFChars(modelsDir, models_dir_c);

    auto *testHelper = dynamic_cast<vitals::TestHelperAndroid *>(
      vitals::TestHelperInstance.get());
    if (testHelper && !debug_dir_path.empty()) {
      const std::string test_dir = testHelper->test_dir;
      mkdir(test_dir.c_str(), 777);
      vitals::csv::to_csv(
        vitals::join_path(test_dir, "test.csv"),
        std::make_pair("sig2_oval_pos_0", testHelper->sig2_oval_pos_0),
        std::make_pair("fft_oval_pos_0", testHelper->fft_oval_pos_0),
        std::make_pair("ind", testHelper->ind),
        std::make_pair("freq", testHelper->freq),
        std::make_pair("hr_pred", testHelper->hr_pred),
        std::make_pair("p_tar", testHelper->p_tar),
        std::make_pair("peak_ind", testHelper->peak_ind),
        std::make_pair("rr_interval", testHelper->rr_interval),
        std::make_pair("rr_interval_filter", testHelper->rr_interval_filter));

      std::ofstream fos_hrv_bandpass(
        vitals::join_path(test_dir, "test_hrv_bandpass.csv"));
      fos_hrv_bandpass << testHelper->print_hrv_bandpass();
      fos_hrv_bandpass.close();

      std::ofstream fos_test(vitals::join_path(test_dir, "test.txt"));
      fos_test << testHelper->oss.str();
      fos_test << res;
    }

    // LOGD("processPixelsV2 MeasureResult res: %s", res.string().c_str());
    auto *p_res = new MeasureResult;
    *p_res = res;
    LOGD("processPixelsV2 MeasureResult p_res: %s", p_res->string().c_str());
    return reinterpret_cast<jlong>(p_res);
  } catch (std::exception &e) {
    LOGD("processPixelsV2 catch err std::exception");
    vitals::throwNativeException(env, "std::exception", e.what());
    return 0;
  } catch (...) {
    LOGD("processPixelsV2 catch err unknown");
    vitals::throwNativeException(env, "UNKNOWN", "unknown error");
    return 0;
  }
}

extern "C" __attribute__((used)) JNIEXPORT jlong JNICALL
Java_com_vitals_lib_Port_nativeProcessPixelsV2___3D_3IDLjava_lang_String_2(
  JNIEnv *env, jobject thiz, jdoubleArray pixels, jintArray shape,
  jdouble fps, jstring modelsDir
) {
  return Java_com_vitals_lib_Port_nativeProcessPixelsV2Fea___3D_3IDLjava_lang_String_2IIDD(
    env, thiz, pixels, shape, fps, modelsDir, -1, -1, -1, -1);
}

extern "C" __attribute__((used)) JNIEXPORT jdouble JNICALL
Java_com_vitals_lib_Port_00024MeasureResult_00024Companion_getHr__J(
  JNIEnv *env, jobject thiz, jlong ptr
) {
  MeasureResult *p = reinterpret_cast<MeasureResult *>(ptr);
  return p->hr;
}
extern "C" __attribute__((used)) JNIEXPORT jdouble JNICALL
Java_com_vitals_lib_Port_00024MeasureResult_00024Companion_getHrv__J(
  JNIEnv *env, jobject thiz, jlong ptr
) {
  MeasureResult *p = reinterpret_cast<MeasureResult *>(ptr);
  return p->hrv;
}
extern "C" __attribute__((used)) JNIEXPORT jdouble JNICALL
Java_com_vitals_lib_Port_00024MeasureResult_00024Companion_getRR__J(
  JNIEnv *env, jobject thiz, jlong ptr
) {
  MeasureResult *p = reinterpret_cast<MeasureResult *>(ptr);
  return p->rr;
}
extern "C" __attribute__((used)) JNIEXPORT jdouble JNICALL
Java_com_vitals_lib_Port_00024MeasureResult_00024Companion_getSpo2__J(
  JNIEnv *env, jobject thiz, jlong ptr
) {
  MeasureResult *p = reinterpret_cast<MeasureResult *>(ptr);
  return p->spo2;
}
extern "C" __attribute__((used)) JNIEXPORT jdouble JNICALL
Java_com_vitals_lib_Port_00024MeasureResult_00024Companion_getStress__J(
  JNIEnv *env, jobject thiz, jlong ptr
) {
  MeasureResult *p = reinterpret_cast<MeasureResult *>(ptr);
  return p->stress;
}
extern "C" __attribute__((used)) JNIEXPORT jdouble JNICALL
Java_com_vitals_lib_Port_00024MeasureResult_00024Companion_getHBP__J(
  JNIEnv *env, jobject thiz, jlong ptr
) {
  MeasureResult *p = reinterpret_cast<MeasureResult *>(ptr);
  return p->hbp;
}
extern "C" __attribute__((used)) JNIEXPORT jdouble JNICALL
Java_com_vitals_lib_Port_00024MeasureResult_00024Companion_getLBP__J(
  JNIEnv *env, jobject thiz, jlong ptr
) {
  MeasureResult *p = reinterpret_cast<MeasureResult *>(ptr);
  return p->lbp;
}
extern "C" __attribute__((used)) JNIEXPORT jdouble JNICALL
Java_com_vitals_lib_Port_00024MeasureResult_00024Companion_getRatio__J(
  JNIEnv *env, jobject thiz, jlong ptr
) {
  MeasureResult *p = reinterpret_cast<MeasureResult *>(ptr);
  return p->ratio;
}
extern "C" __attribute__((used)) JNIEXPORT void JNICALL
Java_com_vitals_lib_Port_00024MeasureResult_00024Companion_release__J(
  JNIEnv *env, jobject thiz, jlong ptr
) {
  MeasureResult *p = reinterpret_cast<MeasureResult *>(ptr);
  delete p;
  //    delete ((MeasureResult *)ptr);
}

extern "C" JNIEXPORT void JNICALL
Java_com_vitals_lib_Port_nativeStoreBinaryData(
  JNIEnv *env, jobject thiz, jstring tag, jobject buffer, jint size
) {
  try {
    const char *tag_str = env->GetStringUTFChars(tag, 0);
    const uint8_t *data = static_cast<const uint8_t *>(env->GetDirectBufferAddress(buffer));

    if (data == nullptr) {
      vitals::throwNativeException(env, "IllegalArgumentException", "Failed to get buffer address");
      return;
    }

    vitals::BinaryDataManager::getInstance().storeData(tag_str, data, size);
    env->ReleaseStringUTFChars(tag, tag_str);
  } catch (std::exception &e) {
    vitals::throwNativeException(env, "std::exception", e.what());
  } catch (...) {
    vitals::throwNativeException(env, "UNKNOWN", "unknown error");
  }
}

extern "C" JNIEXPORT void JNICALL
Java_com_vitals_lib_Port_nativeRemoveBinaryData(
  JNIEnv *env, jobject thiz, jstring tag
) {
  try {
    const char *tag_str = env->GetStringUTFChars(tag, 0);
    vitals::BinaryDataManager::getInstance().removeData(tag_str);
    env->ReleaseStringUTFChars(tag, tag_str);
  } catch (std::exception &e) {
    vitals::throwNativeException(env, "std::exception", e.what());
  } catch (...) {
    vitals::throwNativeException(env, "UNKNOWN", "unknown error");
  }
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_vitals_lib_Port_nativePredictBaseFea(
  JNIEnv *env, jobject thiz, jfloatArray landmarks, jintArray landmarks_shape
) {
  // 将Java float数组转换为Eigen矩阵
  jfloat *landmark_ptr = env->GetFloatArrayElements(landmarks, nullptr);
  jsize length = env->GetArrayLength(landmarks);

  // 从landmarks_shape中获取第二个元素作为num_points
  // jint *shape_ptr = env->GetIntArrayElements(landmarks_shape, nullptr);
  // int num_points = shape_ptr[1];  // 获取第二个元素
  // env->ReleaseIntArrayElements(landmarks_shape, shape_ptr, JNI_ABORT);  // 释放数组元素

  // 将landmark_ptr转换为Eigen矩阵，一维的，后面的方法入参是一维的，所以这里是1行length列
  Eigen::Map<Eigen::MatrixXf> landmark_matrix(landmark_ptr, 1, length);
  // Eigen::Map<Eigen::MatrixXf> landmark_matrix(landmark_ptr, num_points, 2);

  // 释放landmarks数组元素
  env->ReleaseFloatArrayElements(landmarks, landmark_ptr, JNI_ABORT);

  Landmark2Fea processor;
  Eigen::MatrixXd features = processor.gen_fea(landmark_matrix.cast<double>());

  Fea2Pred fea2Pred(".");
  std::tuple<int, double, double> fea = fea2Pred.predict(features);
  LOGD("predictFea fea: (%d, %f, %f)", std::get<0>(fea), std::get<1>(fea), std::get<2>(fea));
  jfloatArray result = env->NewFloatArray(3);
  jfloat result_ptr[3] = {static_cast<float>(std::get<0>(fea)),
                          static_cast<float>(std::get<1>(fea)),
                          static_cast<float>(std::get<2>(fea))};
  env->SetFloatArrayRegion(result, 0, 3, result_ptr);
  return result;
}

// #define ENABLE_TEST_CODE 1
#ifdef ENABLE_TEST_CODE
extern "C" JNIEXPORT jboolean JNICALL
Java_com_vitals_lib_Port_nativeSetupTest(
  JNIEnv *env, jobject thiz, jstring test_dir
) {
  bool enable_test = true;
  if (enable_test) {
    const char *test_dir_c = env->GetStringUTFChars(test_dir, 0);
    vitals::TestHelperInstance =
      std::make_unique<vitals::TestHelperAndroid>(test_dir_c);
    env->ReleaseStringUTFChars(test_dir, test_dir_c);
  }
  return enable_test;
}

extern "C" JNIEXPORT void JNICALL
Java_com_vitals_lib_Port_nativeRunTest(JNIEnv *env, jobject thiz, jstring models_dir) {
  const char* models_dir_c = env->GetStringUTFChars(models_dir, 0);
  try {
    BPPredV3Test::run_tests(models_dir_c);
  } catch(const std::exception& e) {
    // 打印所有可用的tag
    auto all_tags = vitals::BinaryDataManager::getInstance().getAllTags();
    LOGD("Available tags in BinaryDataManager:");
    for (const auto& tag : all_tags) {
      LOGD("  - %s", tag.c_str());
    }
  }
  env->ReleaseStringUTFChars(models_dir, models_dir_c);
}
#endif