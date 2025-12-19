#pragma once

#include <vector>
#include <queue>
#include <tuple>
#include <optional>
#include <iostream>
#include <iomanip>
#include <sstream>
#include <string>

#include "struct.hpp"

namespace vitals {

namespace measure {

struct MeasureConfig
{
  double hr_high = 130.0;
  double hr_low = 50.0;
  double rr_high = 24.0;
  double rr_low = 9.0;
};

struct MeasureResult {
  double hr = 0;
  double hrv = 0;
  double rr = 0;
  double spo2 = 0;

  double stress = 0;

  double hbp = 0;
  double lbp = 0;

  double ratio = 0;

  long long timestamp = 0;

  std::string string() const {
    std::ostringstream oss;
    oss << std::setprecision(17);
    oss << "MeasureResult: { "
      << "hr: " << hr << ", "
      << "hrv: " << hrv << ", "
      << "rr: " << rr << ", "
      << "spo2: " << spo2 << ", "
      << "stress: " << stress << ", "
      << "hbp: " << hbp << ", "
      << "lbp: " << lbp << ", "
      << "ratio: " << ratio << ", "
      << "timestamp: " << timestamp
      << " }";
    return oss.str();
  }

  friend std::ostream& operator<<(std::ostream& os, const MeasureResult& res) {
    os << res.string();
    return os;
  }
};

std::tuple<double, double> predict_hr_v2(const std::vector<std::vector<double>>& p, double fps, double low, double high);
std::tuple<double, double> predict_hrv_v2(const std::vector<double>& p, double fps, double hr_pred);
double predict_spo2_v2(const std::vector<double>& sig_r, const std::vector<double>& sig_b);
double predict_rr_v2(const std::vector<std::vector<double>>& p, double fps, int age);

MeasureResult processPixelsV2(
        const std::vector<std::vector<double>>& p, double fps,
        MeasureConfig config,
        std::optional<BaseFeature> base_fea = std::nullopt
);

} // namespace measure

} // namespace vitals
