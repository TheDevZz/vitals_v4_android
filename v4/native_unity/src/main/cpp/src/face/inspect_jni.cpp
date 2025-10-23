#include <jni.h>
#include <string>
#include <memory>

#include "../lib/inspect.h"

#include "opencv2/imgproc.hpp"
#include "opencv2/imgcodecs.hpp"

#include "../log.hpp"

//class InspectorImp : public Inspector {
//public:
//    const char* data_dir;
//    const char* external_dir;
//
//    cv::Mat* transformed;
//    std::vector<std::pair<std::string, cv::Mat*>> transformed_map;
//
//    InspectorImp(const char* data_dir, const char* external_dir)
//            : data_dir(data_dir), external_dir(external_dir) {}
//
//    ~InspectorImp() {
//        delete transformed;
//        for (auto& it : transformed_map) {
//            delete it.second;
//        }
//        transformed_map.clear();
//    }
//
//    void update_transformed_image(const cv::Mat& transformed_) override {
//        delete transformed;
////        transformed = new cv::Mat(transformed_.clone());
//        transformed = new cv::Mat();
//        transformed_.copyTo(*transformed);
//    };
//
//    void stage_transformed_image(const std::string& tag) override {
//        if (transformed != nullptr) {
////            transformed_map.emplace_back(tag, new cv::Mat(transformed->clone()));
//            auto copy = new cv::Mat();
//            transformed->copyTo(*copy);
//            transformed_map.emplace_back(tag, copy);
//            transformed = nullptr;
//        }
//    };
//
//    void dump_all_transformed_image() {
//        for (auto& it : transformed_map) {
//            auto filepath = std::string(external_dir) + "/out/" + it.first + ".png";
//            cv::Mat bgr;
//            cv::cvtColor(*it.second, bgr, cv::COLOR_BGR2RGB);
//            cv::imwrite(filepath, bgr);
//            delete it.second;
//        }
//        transformed_map.clear();
//    }
//};

#if __cplusplus < 201402L
// C++14 之前定义 make_unique
template<typename T, typename... Args>
std::unique_ptr<T> make_unique(Args&&... args) {
    return std::unique_ptr<T>(new T(std::forward<Args>(args)...));
}
#else
// C++14 及以后版本直接使用标准库的 make_unique
using std::make_unique;
#endif


class InspectorImp : public Inspector {
public:
    const std::string data_dir;
    const std::string external_dir;

    std::unique_ptr<cv::Mat> transformed;
    std::vector<std::pair<std::string, cv::Mat>> transformed_map;

    InspectorImp(const char* data_dir, const char* external_dir)
            : data_dir(data_dir), external_dir(external_dir) {}

    void update_transformed_image(const cv::Mat& transformed_) override {
        transformed = make_unique<cv::Mat>(transformed_.clone());
    };

    void stage_transformed_image(const std::string& tag) override {
        if (transformed) {
            transformed_map.emplace_back(tag, transformed->clone());
            transformed.reset();
        }
    };

    void dump_all_transformed_image() {
//        LOGD("dump_all_transformed_image");
        for (auto& it : transformed_map) {
            auto filepath = std::string(external_dir) + "/out/" + it.first + ".png";
            cv::Mat bgr;
            cv::cvtColor(it.second, bgr, cv::COLOR_RGB2BGR);
            cv::imwrite(filepath, bgr);
            LOGD("imwrite: %s", filepath.c_str());
        }
        transformed_map.clear();
    }
};


extern "C"
JNIEXPORT void JNICALL
Java_com_vitals_sdk_lib_FaceLandmarkerInspect_nativeSetupInspector(JNIEnv *env, jobject thiz,
                                                                   jstring data_dir,
                                                                   jstring external_dir) {
    auto c_data_dir = env->GetStringUTFChars(data_dir, JNI_FALSE);
    auto c_external_dir = env->GetStringUTFChars(external_dir, JNI_FALSE);

    setup_inspector(new InspectorImp(c_data_dir, c_external_dir));

    env->ReleaseStringUTFChars(data_dir, c_data_dir);
    env->ReleaseStringUTFChars(external_dir, c_external_dir);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_vitals_sdk_lib_FaceLandmarkerInspect_nativeReleaseInspector(JNIEnv *env, jobject thiz) {
    remove_inspector();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_vitals_sdk_lib_FaceLandmarkerInspect_nativeSetBatchTag(JNIEnv *env, jobject thiz,
                                                                jstring tag) {
    auto c_tag = env->GetStringUTFChars(tag, JNI_FALSE);
    get_inspector()->current_batch_tag = c_tag;
    env->ReleaseStringUTFChars(tag, c_tag);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_vitals_sdk_lib_FaceLandmarkerInspect_nativeDumpAllTransformedImage(JNIEnv *env,
                                                                            jobject thiz) {
    auto inspector = get_inspector();
    auto imp = dynamic_cast<InspectorImp*>(inspector);
    if (imp) {
        imp->dump_all_transformed_image();
    }
}