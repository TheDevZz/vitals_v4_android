#include <jni.h>

#include "../log.hpp"

#include "jni_utils.h"
#include "measure_vital.hpp"
#include "process.h"

#include "test_helper.hpp"

#include <sys/stat.h>
#include <vector>
#include <string>
#include <fstream>
#include <sstream>
#include <cstdio>
#include <random>
#include "json.hpp"
#include "rapidcsv.h"

using namespace std;

using MeasureResult = vitals::measure::MeasureResult;
std::string debug_dir_path;

static JavaVM *g_vm = nullptr;

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
  g_vm = vm;
  return JNI_VERSION_1_6;
}

namespace vitals {

std::string historyDataDir;

// Helper function: update_range ensures new_range doesn't completely exceed old_range
// Returns a range that is constrained within or overlapping with old_range
std::pair<double, double> update_range(const std::pair<double, double>& old_range,
                                       const std::pair<double, double>& new_range,
                                       double min_gap) {
    double old_low = old_range.first;
    double old_high = old_range.second;
    double new_low = new_range.first;
    double new_high = new_range.second;

    if (old_low <= new_low && new_low <= new_high && new_high <= old_high) {
        // 新范围完全在旧范围内
        return std::make_pair(new_low, new_high);
    } else if (old_low <= new_low && new_low <= old_high && old_high <= new_high) {
        // 新范围向右扩展超出旧范围
        return std::make_pair(std::min(old_high - min_gap, new_low), old_high);
    } else if (new_low <= old_low && old_low <= new_high && new_high <= old_high) {
        // 新范围向左扩展超出旧范围
        return std::make_pair(old_low, std::max(old_low + min_gap, new_high));
    } else {
        // 其他情况：返回旧范围
        return old_range;
    }
}

// CSV file name constant
const char* ENCRYPTED_CSV_FILENAME = "cherry.bin";

// Encryption key for measure history
const char* MEASURE_HISTORY_KEY = "measure_history";

// Helper: call static Port.encryptImpl
std::vector<uint8_t> callJavaPortEncrypt(const std::vector<uint8_t> &input, const std::string &key) {
  std::vector<uint8_t> out;
  if (!g_vm) return out;
  JNIEnv *env = nullptr;
  bool attached = false;
  jclass portCls = nullptr;
  jmethodID mid = nullptr;
  jbyteArray inArr = nullptr;
  jstring jkey = nullptr;
  jbyteArray res = nullptr;
  if (g_vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
    if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return out;
    attached = true;
  }

  portCls = env->FindClass("com/vitals/lib/Port");
  if (!portCls) goto cleanup;

  mid = env->GetStaticMethodID(portCls, "encryptImpl", "([BLjava/lang/String;)[B");
  if (!mid) goto cleanup;

  inArr = env->NewByteArray(static_cast<jsize>(input.size()));
  if (inArr == nullptr) goto cleanup;
  if (!input.empty()) env->SetByteArrayRegion(inArr, 0, static_cast<jsize>(input.size()), reinterpret_cast<const jbyte *>(input.data()));

  jkey = env->NewStringUTF(key.c_str());
  if (jkey == nullptr) { env->DeleteLocalRef(inArr); goto cleanup; }

  res = static_cast<jbyteArray>(env->CallStaticObjectMethod(portCls, mid, inArr, jkey));
  if (env->ExceptionCheck()) {
    env->ExceptionClear();
    // cleanup local refs
    env->DeleteLocalRef(inArr);
    env->DeleteLocalRef(jkey);
    goto cleanup;
  }

  if (res != nullptr) {
    jsize len = env->GetArrayLength(res);
    out.resize(len);
    if (len > 0) env->GetByteArrayRegion(res, 0, len, reinterpret_cast<jbyte *>(out.data()));
    env->DeleteLocalRef(res);
  }

  env->DeleteLocalRef(inArr);
  env->DeleteLocalRef(jkey);
  env->DeleteLocalRef(portCls);

cleanup:
  if (attached) g_vm->DetachCurrentThread();
  return out;
}

// Helper: call static Port.decryptImpl
std::vector<uint8_t> callJavaPortDecrypt(const std::vector<uint8_t> &input, const std::string &key) {
  std::vector<uint8_t> out;
  if (!g_vm) return out;
  JNIEnv *env = nullptr;
  bool attached = false;
  jclass portCls = nullptr;
  jmethodID mid = nullptr;
  jbyteArray inArr = nullptr;
  jstring jkey = nullptr;
  jbyteArray res = nullptr;
  if (g_vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
    if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return out;
    attached = true;
  }

  portCls = env->FindClass("com/vitals/lib/Port");
  if (!portCls) goto cleanup;

  mid = env->GetStaticMethodID(portCls, "decryptImpl", "([BLjava/lang/String;)[B");
  if (!mid) goto cleanup;

  inArr = env->NewByteArray(static_cast<jsize>(input.size()));
  if (inArr == nullptr) goto cleanup;
  if (!input.empty()) env->SetByteArrayRegion(inArr, 0, static_cast<jsize>(input.size()), reinterpret_cast<const jbyte *>(input.data()));

  jkey = env->NewStringUTF(key.c_str());
  if (jkey == nullptr) { env->DeleteLocalRef(inArr); goto cleanup; }

  res = static_cast<jbyteArray>(env->CallStaticObjectMethod(portCls, mid, inArr, jkey));
  if (env->ExceptionCheck()) {
    env->ExceptionClear();
    env->DeleteLocalRef(inArr);
    env->DeleteLocalRef(jkey);
    goto cleanup;
  }

  if (res != nullptr) {
    jsize len = env->GetArrayLength(res);
    out.resize(len);
    if (len > 0) env->GetByteArrayRegion(res, 0, len, reinterpret_cast<jbyte *>(out.data()));
    env->DeleteLocalRef(res);
  }

  env->DeleteLocalRef(inArr);
  env->DeleteLocalRef(jkey);
  env->DeleteLocalRef(portCls);

cleanup:
  if (attached) g_vm->DetachCurrentThread();
  return out;
}

// Overload: call using existing JNIEnv* and Port jobject (thiz)
std::vector<uint8_t> callJavaPortEncrypt(JNIEnv *env, jobject portObj, const std::vector<uint8_t> &input, const std::string &key) {
  std::vector<uint8_t> out;
  if (env == nullptr || portObj == nullptr) return out;

  jclass cls = env->GetObjectClass(portObj);
  if (cls == nullptr) return out;

  // try instance method first, fallback to static
  jmethodID mid = env->GetMethodID(cls, "encryptImpl", "([BLjava/lang/String;)[B");
  bool isStatic = false;
  if (mid == nullptr) {
    env->ExceptionClear();
    mid = env->GetStaticMethodID(cls, "encryptImpl", "([BLjava/lang/String;)[B");
    isStatic = true;
  }
  if (mid == nullptr) { env->DeleteLocalRef(cls); return out; }

  jbyteArray inArr = env->NewByteArray(static_cast<jsize>(input.size()));
  if (inArr == nullptr) { env->DeleteLocalRef(cls); return out; }
  if (!input.empty()) env->SetByteArrayRegion(inArr, 0, static_cast<jsize>(input.size()), reinterpret_cast<const jbyte *>(input.data()));

  jstring jkey = env->NewStringUTF(key.c_str());
  if (jkey == nullptr) { env->DeleteLocalRef(inArr); env->DeleteLocalRef(cls); return out; }

  jbyteArray res;
  if (isStatic) {
    res = static_cast<jbyteArray>(env->CallStaticObjectMethod(cls, mid, inArr, jkey));
  } else {
    res = static_cast<jbyteArray>(env->CallObjectMethod(portObj, mid, inArr, jkey));
  }

  if (env->ExceptionCheck()) {
    env->ExceptionClear();
    env->DeleteLocalRef(inArr);
    env->DeleteLocalRef(jkey);
    env->DeleteLocalRef(cls);
    return out;
  }

  if (res != nullptr) {
    jsize len = env->GetArrayLength(res);
    out.resize(len);
    if (len > 0) env->GetByteArrayRegion(res, 0, len, reinterpret_cast<jbyte *>(out.data()));
    env->DeleteLocalRef(res);
  }

  env->DeleteLocalRef(inArr);
  env->DeleteLocalRef(jkey);
  env->DeleteLocalRef(cls);
  return out;
}

std::vector<uint8_t> callJavaPortDecrypt(JNIEnv *env, jobject portObj, const std::vector<uint8_t> &input, const std::string &key) {
  std::vector<uint8_t> out;
  if (env == nullptr || portObj == nullptr) return out;

  jclass cls = env->GetObjectClass(portObj);
  if (cls == nullptr) return out;

  jmethodID mid = env->GetMethodID(cls, "decryptImpl", "([BLjava/lang/String;)[B");
  bool isStatic = false;
  if (mid == nullptr) {
    env->ExceptionClear();
    mid = env->GetStaticMethodID(cls, "decryptImpl", "([BLjava/lang/String;)[B");
    isStatic = true;
  }
  if (mid == nullptr) { env->DeleteLocalRef(cls); return out; }

  jbyteArray inArr = env->NewByteArray(static_cast<jsize>(input.size()));
  if (inArr == nullptr) { env->DeleteLocalRef(cls); return out; }
  if (!input.empty()) env->SetByteArrayRegion(inArr, 0, static_cast<jsize>(input.size()), reinterpret_cast<const jbyte *>(input.data()));

  jstring jkey = env->NewStringUTF(key.c_str());
  if (jkey == nullptr) { env->DeleteLocalRef(inArr); env->DeleteLocalRef(cls); return out; }

  jbyteArray res;
  if (isStatic) {
    res = static_cast<jbyteArray>(env->CallStaticObjectMethod(cls, mid, inArr, jkey));
  } else {
    res = static_cast<jbyteArray>(env->CallObjectMethod(portObj, mid, inArr, jkey));
  }

  if (env->ExceptionCheck()) {
    env->ExceptionClear();
    env->DeleteLocalRef(inArr);
    env->DeleteLocalRef(jkey);
    env->DeleteLocalRef(cls);
    return out;
  }

  if (res != nullptr) {
    jsize len = env->GetArrayLength(res);
    out.resize(len);
    if (len > 0) env->GetByteArrayRegion(res, 0, len, reinterpret_cast<jbyte *>(out.data()));
    env->DeleteLocalRef(res);
  }

  env->DeleteLocalRef(inArr);
  env->DeleteLocalRef(jkey);
  env->DeleteLocalRef(cls);
  return out;
}

void saveMeasureResult(JNIEnv *env, jobject portObj, const MeasureResult &res) {
    if (vitals::historyDataDir.empty()) {
        return;
    }

    // 使用res中的时间戳，如果没有则获取当前时间戳（毫秒）
    long long timestamp = (res.timestamp > 0) ? res.timestamp : vitals::TimeUtils::getTimestampMs();

    // 定义CSV文件路径
    std::string encrypted_csv_path = vitals::join_path(vitals::historyDataDir, ENCRYPTED_CSV_FILENAME);

    // 读取现有数据
    std::vector<long long> timestamps;
    std::vector<double> hrs, hrvs, rrs, spo2s, stresses, hbps, lbps, ratios;

    if (vitals::is_exists(encrypted_csv_path)) {
        try {
            // 读取加密文件并解密
            std::ifstream ifs(encrypted_csv_path, std::ios::binary);
            if (!ifs) throw std::runtime_error("Failed to open cherry.bin");

            std::vector<uint8_t> encrypted_data((std::istreambuf_iterator<char>(ifs)),
                                                 std::istreambuf_iterator<char>());
            ifs.close();

            // 解密数据
            std::vector<uint8_t> decrypted_data = callJavaPortDecrypt(env, portObj, encrypted_data, MEASURE_HISTORY_KEY);
            if (decrypted_data.empty()) throw std::runtime_error("Decryption failed");

            // 从内存中解析CSV数据
            std::string csv_str(reinterpret_cast<const char*>(decrypted_data.data()), decrypted_data.size());
            std::istringstream ss(csv_str);
            rapidcsv::Document doc(ss);

            timestamps = doc.GetColumn<long long>("timestamp");
            hrs = doc.GetColumn<double>("hr");
            hrvs = doc.GetColumn<double>("hrv");
            rrs = doc.GetColumn<double>("rr");
            spo2s = doc.GetColumn<double>("spo2");
            stresses = doc.GetColumn<double>("stress");
            hbps = doc.GetColumn<double>("hbp");
            lbps = doc.GetColumn<double>("lbp");
            ratios = doc.GetColumn<double>("ratio");
        } catch (const std::exception& e) {
            LOGD("Failed to read cherry.bin: %s", e.what());
            // 如果读取失败，重新开始
            timestamps.clear();
            hrs.clear();
            hrvs.clear();
            rrs.clear();
            spo2s.clear();
            stresses.clear();
            hbps.clear();
            lbps.clear();
            ratios.clear();
        }
    }

    // 添加新记录
    timestamps.push_back(timestamp);
    hrs.push_back(res.hr);
    hrvs.push_back(res.hrv);
    rrs.push_back(res.rr);
    spo2s.push_back(res.spo2);
    stresses.push_back(res.stress);
    hbps.push_back(res.hbp);
    lbps.push_back(res.lbp);
    ratios.push_back(res.ratio);

    // 保留最新的5条记录
    if (timestamps.size() > 5) {
        size_t start_idx = timestamps.size() - 5;
        timestamps.erase(timestamps.begin(), timestamps.begin() + start_idx);
        hrs.erase(hrs.begin(), hrs.begin() + start_idx);
        hrvs.erase(hrvs.begin(), hrvs.begin() + start_idx);
        rrs.erase(rrs.begin(), rrs.begin() + start_idx);
        spo2s.erase(spo2s.begin(), spo2s.begin() + start_idx);
        stresses.erase(stresses.begin(), stresses.begin() + start_idx);
        hbps.erase(hbps.begin(), hbps.begin() + start_idx);
        lbps.erase(lbps.begin(), lbps.begin() + start_idx);
        ratios.erase(ratios.begin(), ratios.begin() + start_idx);
    }

    // 生成CSV数据到内存并加密
    try {
        // 生成CSV内容到字符串流（使用 rapidcsv）
        std::ostringstream csv_stream;
        vitals::csv::to_csv(csv_stream,
            std::make_pair("timestamp", timestamps),
            std::make_pair("hr", hrs),
            std::make_pair("hrv", hrvs),
            std::make_pair("rr", rrs),
            std::make_pair("spo2", spo2s),
            std::make_pair("stress", stresses),
            std::make_pair("hbp", hbps),
            std::make_pair("lbp", lbps),
            std::make_pair("ratio", ratios));

        std::string csv_str = csv_stream.str();
        std::vector<uint8_t> csv_data(csv_str.begin(), csv_str.end());

        // 加密数据
        std::vector<uint8_t> encrypted_data = callJavaPortEncrypt(env, portObj, csv_data, MEASURE_HISTORY_KEY);
        if (!encrypted_data.empty()) {
            // 写入加密文件
            std::ofstream ofs(encrypted_csv_path, std::ios::binary);
            ofs.write(reinterpret_cast<const char*>(encrypted_data.data()), encrypted_data.size());
            ofs.close();
        }
    } catch (const std::exception& e) {
        LOGD("Failed to write cherry.bin: %s", e.what());
    }
}

std::optional<MeasureResult> getLatestMeasureResult(JNIEnv *env, jobject portObj) {
    if (vitals::historyDataDir.empty()) {
        return std::nullopt;
    }

    // 定义CSV文件路径
    std::string encrypted_csv_path = vitals::join_path(vitals::historyDataDir, ENCRYPTED_CSV_FILENAME);

    // 检查加密文件是否存在
    if (!vitals::is_exists(encrypted_csv_path)) {
        return std::nullopt;
    }

    try {
        // 读取加密文件并解密
        std::ifstream ifs(encrypted_csv_path, std::ios::binary);
        if (!ifs) throw std::runtime_error("Failed to open cherry.bin");

        std::vector<uint8_t> encrypted_data((std::istreambuf_iterator<char>(ifs)),
                                             std::istreambuf_iterator<char>());
        ifs.close();

        // 解密数据
        std::vector<uint8_t> decrypted_data = callJavaPortDecrypt(env, portObj, encrypted_data, MEASURE_HISTORY_KEY);
        if (decrypted_data.empty()) throw std::runtime_error("Decryption failed");

        // 从内存中解析CSV数据
        std::string csv_str(reinterpret_cast<const char*>(decrypted_data.data()), decrypted_data.size());
        std::istringstream ss(csv_str);
        rapidcsv::Document doc(ss);

        // 获取行数
        size_t row_count = doc.GetRowCount();
        if (row_count == 0) {
            return std::nullopt;
        }

        // 获取最后一行的数据
        size_t last_row = row_count - 1;

        MeasureResult result;
        result.hr = doc.GetCell<double>("hr", last_row);
        result.hrv = doc.GetCell<double>("hrv", last_row);
        result.rr = doc.GetCell<double>("rr", last_row);
        result.spo2 = doc.GetCell<double>("spo2", last_row);
        result.stress = doc.GetCell<double>("stress", last_row);
        result.hbp = doc.GetCell<double>("hbp", last_row);
        result.lbp = doc.GetCell<double>("lbp", last_row);
        result.ratio = doc.GetCell<double>("ratio", last_row);
        result.timestamp = doc.GetCell<long long>("timestamp", last_row);

        return result;
    } catch (const std::exception& e) {
        LOGD("Failed to read cherry.bin: %s", e.what());
        return std::nullopt;
    }
}

} // namespace vitals

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
    vitals::historyDataDir = models_dir;

    // pixels shape is (n, 3), need transpose to (3, n)
    //    Eigen::MatrixXd mp = Eigen::MatrixXd::Map(p, p_shape[0],
    //    p_shape[1]).transpose(); MeasureResult res =
    //    vitals::signal::processPixelsV2(mp, fps);

    vitals::measure::MeasureConfig config;
    std::optional<MeasureResult> latest_res = vitals::getLatestMeasureResult(env, thiz);

    long long now = vitals::TimeUtils::getTimestampMs();
    bool use_latest_range = latest_res.has_value() && now - latest_res->timestamp < 5 * 60 * 1000; // 5分钟内
    if (use_latest_range) { // 使用新的范围
        // 检查 latest_res 是否有有效的 hr 值
        if (latest_res->hr > 0) {
            // 计算新的 HR 范围
            // hr四舍五入取整
            double hr = std::round(latest_res->hr);
            std::pair<double, double> old_hr_range(config.hr_low, config.hr_high);
            std::pair<double, double> new_hr_range(hr - 15, hr + 15);
            std::pair<double, double> updated_hr_range = vitals::update_range(old_hr_range, new_hr_range, 30.0);
            config.hr_low = updated_hr_range.first;
            config.hr_high = updated_hr_range.second;
        }

        // 对 RR 应用类似的逻辑
        if (latest_res->rr > 0) {
            // rr四舍五入取整
            double rr = std::round(latest_res->rr);
            std::pair<double, double> old_rr_range(config.rr_low, config.rr_high);
            std::pair<double, double> new_rr_range(rr - 5, rr + 5);
            std::pair<double, double> updated_rr_range = vitals::update_range(old_rr_range, new_rr_range, 10.0);
            config.rr_low = updated_rr_range.first;
            config.rr_high = updated_rr_range.second;
        }
    }

//    LOGD("latest_res: %s",
//         use_latest_range ? latest_res->string().c_str() : "none");
//    LOGD("ProcessPixelsV2 using hr range: [%.2f, %.2f], rr range: [%.2f, %.2f]",
//         config.hr_low, config.hr_high, config.rr_low, config.rr_high);

    MeasureResult res = vitals::processPixelsV2(p, p_shape, fps, models_dir,
                                                age, gender, height, weight,
                                                config);

    if (use_latest_range) {
        // [-0.5, 0.5) 之间的随机值
        static std::random_device rd;
        static std::mt19937 gen(rd());
        static std::uniform_real_distribution<double> dis(-0.5, 0.5);
        res.stress = latest_res->stress + dis(gen);
        // 2/3加权
        res.spo2 = (latest_res->spo2 + res.spo2 + res.spo2) / 3.0;
    }

    vitals::saveMeasureResult(env, thiz, res);

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

    //        LOGD("processPixelsV2 MeasureResult res: %s",
    //        res.string().c_str());
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
extern "C" __attribute__((used)) JNIEXPORT jlong JNICALL
Java_com_vitals_lib_Port_00024MeasureResult_00024Companion_getTimestamp__J(
  JNIEnv *env, jobject thiz, jlong ptr
) {
  MeasureResult *p = reinterpret_cast<MeasureResult *>(ptr);
  return p->timestamp;
}
extern "C" __attribute__((used)) JNIEXPORT void JNICALL
Java_com_vitals_lib_Port_00024MeasureResult_00024Companion_release__J(
  JNIEnv *env, jobject thiz, jlong ptr
) {
  MeasureResult *p = reinterpret_cast<MeasureResult *>(ptr);
  delete p;
  //    delete ((MeasureResult *)ptr);
}

extern "C" __attribute__((used)) JNIEXPORT jbyteArray JNICALL
Java_com_vitals_lib_Port_nativeEncryptCall(
  JNIEnv *env, jclass clazz, jbyteArray input, jstring key
) {
  if (input == nullptr) return nullptr;
  // use the passed clazz (com/vitals/lib/Port) to find static method
  jmethodID mid = env->GetStaticMethodID(clazz, "encryptImpl", "([BLjava/lang/String;)[B");
  if (mid == nullptr) {
    if (env->ExceptionCheck()) env->ExceptionClear();
    return nullptr;
  }
  jbyteArray res = (jbyteArray)env->CallStaticObjectMethod(clazz, mid, input, key);
  if (env->ExceptionCheck()) {
    env->ExceptionClear();
    return nullptr;
  }
  return res;
}

extern "C" __attribute__((used)) JNIEXPORT jbyteArray JNICALL
Java_com_vitals_lib_Port_nativeDecryptCall(
  JNIEnv *env, jclass clazz, jbyteArray input, jstring key
) {
  if (input == nullptr) return nullptr;
  jmethodID mid = env->GetStaticMethodID(clazz, "decryptImpl", "([BLjava/lang/String;)[B");
  if (mid == nullptr) {
    if (env->ExceptionCheck()) env->ExceptionClear();
    return nullptr;
  }
  jbyteArray res = (jbyteArray)env->CallStaticObjectMethod(clazz, mid, input, key);
  if (env->ExceptionCheck()) {
    env->ExceptionClear();
    return nullptr;
  }
  return res;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_vitals_lib_Port_nativeSetPath(JNIEnv *env, jobject thiz, jstring tag, jstring path) {
    if (tag == nullptr || path == nullptr) return;

    const char *tag_c = env->GetStringUTFChars(tag, nullptr);
    const char *path_c = env->GetStringUTFChars(path, nullptr);

    if (tag_c != nullptr && path_c != nullptr) {
        std::string tag_s(tag_c);
        if (tag_s == "cherry") {
            vitals::historyDataDir = std::string(path_c);
        }
    }

    if (tag_c) env->ReleaseStringUTFChars(tag, tag_c);
    if (path_c) env->ReleaseStringUTFChars(path, path_c);
}
