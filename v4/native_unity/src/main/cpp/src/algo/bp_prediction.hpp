#pragma once

#include <iostream>
#include <vector>
#include <queue>
#include <random>
#include <optional>
#include <string>

#include "torch/script.h"

#include "struct.hpp"
#include "signal_processing.hpp"
#include "bp_knn_model.hpp"

namespace vitals {

namespace measure {

extern std::vector<std::string> model_dir_list;


struct BPEval {
  double hbp = 0;
  double lbp = 0;
  double HR_BP = 0;
  double clf = -1;

  friend std::ostream& operator<<(std::ostream& os, const BPEval& bpEval) {
    os << "{ hbp: " << bpEval.hbp << ", lbp: " << bpEval.lbp << ", clf: " << bpEval.clf << " }";
    return os;
  }
};




std::vector<double> merge_signal_clf(const std::vector<double>& pixels_g, double fps);

std::vector<double> merge_signal_base_fea(const std::vector<double>& pixels_g, double fps);

BPEval predict_bp_clf(const std::vector<double>& bvp, bool use_high_bp_med = false);
BPEval predict_bp_base_fea(const std::vector<double>& rppg, int age, int gender, double height, double weight);

BPEval predice_bp_v2(const std::vector<double>& pixels_g, double fps, std::optional<BaseFeature> base_fea = std::nullopt);

} // namespace measure

} // namespace vitels
