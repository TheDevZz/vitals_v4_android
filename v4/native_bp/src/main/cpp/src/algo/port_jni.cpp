#include <jni.h>

#include "../log.hpp"

#include "measure_vital.hpp"
#include "process.h"
#include "jni_utils.h"

#include "test_helper.hpp"

#include <sys/stat.h>

using namespace std;

using MeasureResult = vitals::measure::MeasureResult;
std::string debug_dir_path;

extern "C" __attribute__((used))
JNIEXPORT jlong JNICALL
Java_com_vitals_lib_Port_nativeProcessPixelsV2Fea___3D_3IDLjava_lang_String_2IIDD(JNIEnv *env, jobject thiz, jdoubleArray pixels,
                                                                                  jintArray shape, jdouble fps, jstring modelsDir,
                                                                                  jint age, jint gender, jdouble height,
                                                                                  jdouble weight) {
    try {
        jdouble *p = env->GetDoubleArrayElements(pixels, 0);
        jint *p_shape = env->GetIntArrayElements(shape, 0);
        const char *models_dir_c = env->GetStringUTFChars(modelsDir, 0);

        string models_dir(models_dir_c);

        // pixels shape is (n, 3), need transpose to (3, n)
        //    Eigen::MatrixXd mp = Eigen::MatrixXd::Map(p, p_shape[0], p_shape[1]).transpose();
        //    MeasureResult res = vitals::signal::processPixelsV2(mp, fps);

        MeasureResult res = vitals::processPixelsV2(p, p_shape, fps, models_dir, age, gender, height, weight);

        env->ReleaseDoubleArrayElements(pixels, p, 0);
        env->ReleaseIntArrayElements(shape, p_shape, 0);
        env->ReleaseStringUTFChars(modelsDir, models_dir_c);


        auto *testHelper = dynamic_cast<vitals::TestHelperAndroid*>(vitals::TestHelperInstance.get());
        if (testHelper && !debug_dir_path.empty()) {
            const std::string test_dir = testHelper->test_dir;
            mkdir(test_dir.c_str(), 777);
            vitals::csv::to_csv(vitals::join_path(test_dir, "test.csv"),
                                std::make_pair("sig2_oval_pos_0", testHelper->sig2_oval_pos_0),
                                std::make_pair("fft_oval_pos_0", testHelper->fft_oval_pos_0),
                                std::make_pair("ind", testHelper->ind),
                                std::make_pair("freq", testHelper->freq),
                                std::make_pair("hr_pred", testHelper->hr_pred),
                                std::make_pair("p_tar", testHelper->p_tar),
                                std::make_pair("peak_ind", testHelper->peak_ind),
                                std::make_pair("rr_interval", testHelper->rr_interval),
                                std::make_pair("rr_interval_filter", testHelper->rr_interval_filter)
            );

            std::ofstream fos_hrv_bandpass(vitals::join_path(test_dir, "test_hrv_bandpass.csv"));
            fos_hrv_bandpass << testHelper->print_hrv_bandpass();
            fos_hrv_bandpass.close();

            std::ofstream fos_test(vitals::join_path(test_dir, "test.txt"));
            fos_test << testHelper->oss.str();
            fos_test << res;
        }

//        LOGD("processPixelsV2 MeasureResult res: %s", res.string().c_str());
        auto *p_res = new MeasureResult;
        *p_res = res;
        LOGD("processPixelsV2 MeasureResult p_res: %s", p_res->string().c_str());
        return reinterpret_cast<jlong>(p_res);
    } catch(std::exception& e) {
        LOGD("processPixelsV2 catch err std::exception");
        vitals::throwNativeException(env, "std::exception", e.what());
        return 0;
    } catch(...) {
        LOGD("processPixelsV2 catch err unknown");
        vitals::throwNativeException(env, "UNKNOWN", "unknown error");
        return 0;
    }
}

extern "C" __attribute__((used))
JNIEXPORT jlong JNICALL
Java_com_vitals_lib_Port_nativeProcessPixelsV2___3D_3IDLjava_lang_String_2(JNIEnv *env, jobject thiz, jdoubleArray pixels,
                                                                           jintArray shape, jdouble fps, jstring modelsDir) {
    return Java_com_vitals_lib_Port_nativeProcessPixelsV2Fea___3D_3IDLjava_lang_String_2IIDD(
            env, thiz, pixels, shape, fps, modelsDir,
            -1, -1, -1, -1
    );
}

extern "C" __attribute__((used))
JNIEXPORT jdouble JNICALL
Java_com_vitals_lib_Port_00024MeasureResult_00024Companion_getHr__J(JNIEnv *env, jobject thiz,
                                                                    jlong ptr) {
    MeasureResult *p = reinterpret_cast<MeasureResult *>(ptr);
    return p->hr;
}
extern "C" __attribute__((used))
JNIEXPORT jdouble JNICALL
Java_com_vitals_lib_Port_00024MeasureResult_00024Companion_getHrv__J(JNIEnv *env, jobject thiz,
                                                                     jlong ptr) {
    MeasureResult *p = reinterpret_cast<MeasureResult *>(ptr);
    return p->hrv;
}
extern "C" __attribute__((used))
JNIEXPORT jdouble JNICALL
Java_com_vitals_lib_Port_00024MeasureResult_00024Companion_getRR__J(JNIEnv *env, jobject thiz,
                                                                    jlong ptr) {
    MeasureResult *p = reinterpret_cast<MeasureResult *>(ptr);
    return p->rr;
}
extern "C" __attribute__((used))
JNIEXPORT jdouble JNICALL
Java_com_vitals_lib_Port_00024MeasureResult_00024Companion_getSpo2__J(JNIEnv *env, jobject thiz,
                                                                      jlong ptr) {
    MeasureResult *p = reinterpret_cast<MeasureResult *>(ptr);
    return p->spo2;
}
extern "C" __attribute__((used))
JNIEXPORT jdouble JNICALL
Java_com_vitals_lib_Port_00024MeasureResult_00024Companion_getStress__J(JNIEnv *env, jobject thiz,
                                                                        jlong ptr) {
    MeasureResult *p = reinterpret_cast<MeasureResult *>(ptr);
    return p->stress;
}
extern "C" __attribute__((used))
JNIEXPORT jdouble JNICALL
Java_com_vitals_lib_Port_00024MeasureResult_00024Companion_getHBP__J(JNIEnv *env, jobject thiz,
                                                                     jlong ptr) {
    MeasureResult *p = reinterpret_cast<MeasureResult *>(ptr);
    return p->hbp;
}
extern "C" __attribute__((used))
JNIEXPORT jdouble JNICALL
Java_com_vitals_lib_Port_00024MeasureResult_00024Companion_getLBP__J(JNIEnv *env, jobject thiz,
                                                                     jlong ptr) {
    MeasureResult *p = reinterpret_cast<MeasureResult *>(ptr);
    return p->lbp;
}
extern "C" __attribute__((used))
JNIEXPORT jdouble JNICALL
Java_com_vitals_lib_Port_00024MeasureResult_00024Companion_getRatio__J(JNIEnv *env, jobject thiz,
                                                                       jlong ptr) {
    MeasureResult *p = reinterpret_cast<MeasureResult *>(ptr);
    return p->ratio;
}
extern "C" __attribute__((used))
JNIEXPORT void JNICALL
Java_com_vitals_lib_Port_00024MeasureResult_00024Companion_release__J(JNIEnv *env, jobject thiz,
                                                                      jlong ptr) {
    MeasureResult *p = reinterpret_cast<MeasureResult *>(ptr);
    delete p;
//    delete ((MeasureResult *)ptr);
}