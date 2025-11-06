#ifndef VITALS_V4_BP_PRED_V3_TEST_HPP
#define VITALS_V4_BP_PRED_V3_TEST_HPP

#include <string>
#include <iostream>

#include "bp_pred_v3.hpp"
#include "rapidcsv.h"
#include "log.hpp"

class BPPredV3Test {
public:
  static void run_tests(const std::string &models_dir) {
    rapidcsv::Document df_sig2(
      models_dir + "/sig2_34_20240325204004925_tmp_28ccd766fed319ae2b93c441647597ad_2.csv",
      rapidcsv::LabelParams(0, -1));
    std::vector<double> sig_oval_pos_0 = df_sig2.GetColumn<double>("oval_pos_0");

    // vitals::measure::BPPredV3 bpPredV3(models_dir + "/bp.pt");
    vitals::measure::BPPredV3 bpPredV3("bp.pt");
    int age = 59;
    int gender = 1;
    double bmi = 25;
    double hr_pred = 106.66117866634494;
    double peak_ratio = 0.1605085064777376;
    double hbp_refer = 0;
    double lbp_refer = 0;
    // hbp_refer = 137.38;
    // lbp_refer = 83.38666666666667;
    double fps = 24.906865353911655;
    auto pred = bpPredV3.predict_bp_v3(sig_oval_pos_0, fps,
                                       age, gender, bmi, hr_pred, peak_ratio,
                                       hbp_refer, lbp_refer);
    LOGD("hbp: %.2f, lbp: %.2f", pred.first, pred.second);

  }
};

#endif //VITALS_V4_BP_PRED_V3_TEST_HPP
