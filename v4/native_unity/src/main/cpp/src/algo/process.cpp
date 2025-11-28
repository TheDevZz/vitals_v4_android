#include <jni.h>
#include "process.h"
#include "log.hpp"
#include "test_helper.hpp"

#ifdef ENABLE_BP
#include "bp_prediction.hpp"
#endif

namespace vitals {

measure::MeasureResult processPixelsV2(
        const double* p, const int* p_shape,
        double fps,
        const string& models_dir,
        int age, int gender, double height, double weight,
        measure::MeasureConfig config
) {
#ifdef ENABLE_BP
    auto& model_dir_list = measure::model_dir_list;
    if (std::find(model_dir_list.begin(), model_dir_list.end(), models_dir) == model_dir_list.end()) {
        model_dir_list.push_back(models_dir);
        LOGD("append models_dir %s", models_dir.c_str());
    }
#endif

    // fc -> cf
    int n_frame = p_shape[0];
    int n_channel = p_shape[1];
    std::vector<std::vector<double>> pixels_cf(n_channel);
    for (int i = 0; i < n_channel; ++i) {
        std::vector<double> pixels_f(n_frame);
        for (int j = 0; j < n_frame; ++j) {
            int idx = j * n_channel + i;
            pixels_f[j] = p[idx];
        }
        pixels_cf[i] = pixels_f;
    }

    auto* testHelper = dynamic_cast<TestHelperAndroid*>(TestHelperInstance.get());
    if (testHelper) {
        testHelper->print_sig(pixels_cf, fps);
    }

    measure::BaseFeature baseFea(age, gender, height, weight);

    if (age <= 0) {
        return measure::processPixelsV2(pixels_cf, fps, config);
    } else {
        return measure::processPixelsV2(pixels_cf, fps, config, baseFea);
    }
}


} // namespace vitals
