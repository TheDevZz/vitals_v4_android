
#include <tuple>
#include <random>

#include "measure_vital.hpp"
#include "signal_processing.hpp"
#include "test_helper.hpp"

#ifdef ENABLE_BP
#include "bp_prediction.hpp"
#endif

namespace vitals {

namespace measure {

using namespace vitals::signal;

std::tuple<Eigen::VectorXd, Eigen::VectorXcd> get_fft(const std::vector<std::vector<double>>& p, double fps) {
  Eigen::VectorXd ps = pos(p, fps);
  Eigen::VectorXcd fft_data = rfft(ps);
  return std::make_tuple(ps, fft_data);
}

std::pair<int, int> hr_get_peak_width(const std::vector<double>& d, int ind) {
  // 有越界风险
  double temp = d[ind];
  int i = ind - 1;
  while (d[i] < temp && std::abs(i - ind) <= 5) {
    temp = d[i];
    --i;
  }
  ++i;

  temp = d[ind];
  int j = ind + 1;
  while (d[j] < temp && std::abs(j - ind) <= 5) {
    temp = d[j];
    ++j;
  }
  --j;

  return std::make_pair(i, j);
}

double hr_get_energy_ratio(const std::vector<double>& d, int ind) {
  // TestHelperInstance->print_vector(d, "hr_get_energy_ratio");
  std::pair<int, int> range = hr_get_peak_width(d, ind);
  double rangeSquareSum = 0;
  double allSquareSum = 0;
  for (int i = range.first; i <= range.second; ++i) {
    double v = d[i];
    rangeSquareSum += v * v;
  }
  for (auto it : d) {
    allSquareSum += it * it;
  }
  double ratio = rangeSquareSum / (allSquareSum + 1e-10);
  return ratio;
}

std::tuple<double, double> predict_hr_v2(const Eigen::VectorXcd& fft_data, int frame_cnt, double fps, double low, double high) {
  double max_amplitude = 0;
  int max_ampl_ind = 0;
  double final_hr_pred = 0;
  auto fsize = fft_data.size();
  std::vector<double> df_fft_oval_pos_0(fsize, 0);
  for (int i = 0; i < fsize; ++i) {
    double hr_pred = (double)i / frame_cnt * fps * 60;
    if (hr_pred <= low) {
      continue;
    }
    if (hr_pred >= high) {
      break;
    }
    auto amplitude = std::abs(fft_data[i]);
    df_fft_oval_pos_0[i] = amplitude;
    if (amplitude > max_amplitude) {
      max_amplitude = amplitude;
      max_ampl_ind = i;
      final_hr_pred = hr_pred;
    }
  }

  double peak_ratio = hr_get_energy_ratio(df_fft_oval_pos_0, max_ampl_ind);

  return std::make_tuple(final_hr_pred, peak_ratio);
}

/*
p is pixels cf (one roi)
*/
std::tuple<double, double> predict_hr_v2(const std::vector<std::vector<double>>& p, double fps, double low, double high) {
  auto frame_cnt = p[0].size();
  Eigen::VectorXd ps = pos(p, fps);
  Eigen::VectorXcd fft_data = rfft(ps);
  return predict_hr_v2(fft_data, frame_cnt, fps, low, high);
}

std::vector<double> bandpass(const std::vector<double> &p, double fps, double low, double high) {
  int frame_cnt = p.size();
  double ind_low = low * frame_cnt / fps;
  double ind_high = high * frame_cnt / fps;

  Eigen::FFT<double> eigenFFT;
  Eigen::VectorXcd fft = eigenFFT.fwd(vector2vectorXcd(p));

   TestHelperInstance->update_hrv_bandpass(fft, 0);

  ind_low = (int)ind_low;
  ind_high = (int)ind_high;

  int rfft_size = p.size() / 2 + 1;
  int offset = fft.size() - rfft_size;

  for (int i = rfft_size - 1; i >= 0; --i) {
    if (ind_low <= i && i <= ind_high) {
      fft[i + offset] = fft[i] = std::complex<double>(std::abs(fft[i]), 0);
    } else {
      fft[i + offset] = fft[i] = std::complex<double>(0, 0);
    }
  }

   TestHelperInstance->update_hrv_bandpass(fft, 1);

  Eigen::VectorXd ifft = eigenFFT.inv(fft, frame_cnt / 2 * 2);
  std::vector<double> r(ifft.data(), ifft.data() + ifft.size());
  return r;
}


template<typename T>
std::vector<T> filter_data(const std::vector<T>& d) {
  auto stats = calculateStats(d);
  auto lower = stats.mean - 3 * stats.std_dev;
  auto upper = stats.mean + 3 * stats.std_dev;
  std::vector<T> filtered;
  std::copy_if(d.begin(), d.end(), std::back_inserter(filtered), [lower, upper](T it){
    return lower <= it && it <= upper;
  });
  return filtered;
}

double cal_sdnn(const std::vector<double>& rr_interval) {
  return std_dev(rr_interval) * 1000;
}

std::vector<double> get_rr_interval_hrvs_sdnn(const std::vector<double>& p, size_t win_len = 10) {
  size_t w = std::min(win_len, p.size());
  std::vector<double> result;
  double sum = 0, squareSum = 0;
  auto p_size = p.size();
  for (int i = 0; i < p_size; ++i) {
    if (i >= w) {
      double shift_elem = p[i - w];
      sum -= shift_elem;
      squareSum -= shift_elem * shift_elem;
    }

    double push_elem = p[i];
    sum += push_elem;
    squareSum += push_elem * push_elem;

    if (i >= w - 1) {
      double mean = sum / w;
      double stdev = std::sqrt(squareSum / w - mean * mean);
      double sdnn = stdev * 1000;
      result.push_back(sdnn);
      std::cout << "sdnn: " << std::setprecision(17) << sdnn << std::endl;
    }
  }

  return result;
}

double random_uniform(double lower_bound, double upper_bound) {
    std::random_device rd; // 用于生成随机数种子
    std::mt19937 gen(rd()); // 生成随机数引擎
    std::uniform_real_distribution<> dis(lower_bound, upper_bound); // 定义范围

    return dis(gen); // 生成并返回随机数
}

double predict_stress(const std::vector<double>& rr_interval) {
  const std::vector<double>& grad1 = rr_interval;
  const std::vector<double>& grad2 = diff(rr_interval);

  double grad1_std = std_dev(grad1);
  double grad2_std = std_dev(grad2);

  double a = std::sqrt(0.5) * grad2_std;
  double b = std::sqrt(std::abs(2 * std::pow(grad1_std, 2) - 0.5 * std::pow(grad2_std, 2)));
  double stress = std::min(a / b, 1.0);
  stress = std::max(0.625, stress);
//  8 * stress - 4 + np.random.uniform(-0.3, 0.3)
  stress = 8 * stress - 4 + random_uniform(-0.3, 0.3);

  return stress;
}

/*
p is sig[oval_pos_0]
*/
std::tuple<double, double> predict_hrv_v2(const std::vector<double>& p, double fps, double hr_pred) {
  // get_rr_interval start >>>
  double hr_pred_minute_to_second = hr_pred / 60;
  double low = std::max(0.0, hr_pred_minute_to_second - 0.2);
  double high = hr_pred_minute_to_second + 0.2;

  std::vector<double> p_tar = bandpass(p, fps, low, high);
  std::vector<int> peak_ind = find_peak(p_tar, 100, false);
  // std::vector<double> peaks(peak_ind.size());
  // std::transform(peak_ind.begin(), peak_ind.end(), peaks.begin(), [&fps](int ind) { return ind / fps; });
  std::vector<int> diff_ind = diff(peak_ind);
  std::vector<double> rr_interval(diff_ind.size());
  std::transform(diff_ind.begin(), diff_ind.end(), rr_interval.begin(), [&fps](int it) {
    return it / fps;
  });

  TestHelperInstance->update_hrv_data(p_tar, peak_ind, rr_interval);

  rr_interval = filter_data(rr_interval);
  TestHelperInstance->update_rr_interval_filter(rr_interval);
  // get_rr_interval end >>>

  std::vector<double> hrvs_sdnn = get_rr_interval_hrvs_sdnn(rr_interval, 15);
  TestHelperInstance->update_sdnns(hrvs_sdnn);
  double sdnn = median(hrvs_sdnn);

  // if sdnn >= 140:
  //     for i in range(2, 11):
  //         if sdnn / i <= 120:
  //             break
  //     sdnn /= i
  if (sdnn >= 140) {
    for (int i = 2; i < 11; ++i) {
      if (sdnn / i <= 120) {
        sdnn /= i;
        break;
      }
    }
  }

  double stress = predict_stress(rr_interval);
  return std::make_tuple(sdnn, stress);
}

/*
sig_r is pixels of red channel
sig_b is pixels of blue channel
*/
double predict_spo2_v2(const std::vector<double>& sig_r, const std::vector<double>& sig_b) {
  NumStats stats_r = calculateStats(sig_r);
  NumStats stats_b = calculateStats(sig_b);
  double num = stats_r.std_dev / stats_r.mean;
  double den = stats_b.std_dev / stats_b.mean;
  double spo2 = 1 - 0.03 * std::log(num) / std::log(den);
  spo2 *= 100;
  return spo2;
}

double rr_sig_predict_ind(const Eigen::VectorXd& sig, double ind_low, double ind_high) {
  Eigen::FFT<double> eigenFFT;
  Eigen::VectorXcd fft;
  eigenFFT.fwd(fft, sig);
  auto rfft = fft.segment(0, sig.size() / 2 + 1);
  // Eigen::VectorXd abs_fft(rfft.size());
  double maxAmplitude = 0;
  int maxInd = -1;
  for (int i = 0; i < rfft.size(); ++i) {
    if (ind_low <= i && i <= ind_high) {
      // abs_fft(i) = std::abs(rfft[i]);
      double amplitude = std::abs(rfft[i]);
      if (amplitude > maxAmplitude) {
        maxAmplitude = amplitude;
        maxInd = i;
      }
    } else {
      // abs_fft(i) = 0;
    }
  }
  // std::cout << "==========\n" << abs_fft << "\n============\n" << "\n";
  return maxInd;
}

// # dict
// def most_freq(m, cal_avg = False) :
//   cnt = max(m.values())
//   ks = [k for k, v in m.items() if v == cnt]
//   if cal_avg :
//     return sum(ks) / len(ks)
//     return ks
double rr_most_freq(const std::vector<double>& rr_preds) {
  std::vector<std::pair<double, int>> value_counts;
  for (auto& it : rr_preds) {
    int find = -1;
    for (int i = 0; i < value_counts.size(); ++i) {
      if (value_counts[i].first == it) {
        find = i;
        break;
      }
    }
    if (find >= 0) {
      value_counts[find].second += 1;
    } else {
      value_counts.emplace_back(it, 1);
    }
  }
  int maxCount = 0;
  std::vector<double> mostList;
  for (int i = 0; i < value_counts.size(); ++i) {
    auto value = value_counts[i].first;
    auto count = value_counts[i].second;
    if (count > maxCount) {
      maxCount = count;
      mostList.clear();
      mostList.push_back(value);
    } else if (count == maxCount) {
      mostList.push_back(value);
    }
  }
  return mean(mostList);
}
/*
p is pixels cf (one roi)
*/
double predict_rr_v2(
        const std::vector<std::vector<double>>& p, double fps,
        int age,
        double low, double high
) {
  int frame_cnt =  p[0].size();
  double ind_low = low / 60.0 * frame_cnt / fps;
  double ind_high = high / 60.0 * frame_cnt / fps;

  // std::cout << "ind_low: " << ind_low << "\n";
  // std::cout << "ind_hig: " << ind_high << "\n";

  Eigen::MatrixXd mp(p.size(), p[0].size());
  for (int i = 0; i < p.size(); ++i) {
    for (int j = 0; j < p[0].size(); ++j) {
      mp(i, j) = p[i][j];
    }
  }

  std::vector<Eigen::VectorXd> sigs;
  sigs.push_back(pbv2(p, fps));
  sigs.push_back(omit2(mp));
  sigs.push_back(lgi_1(mp));

  std::vector<double> rr_preds(sigs.size());
  std::transform(sigs.begin(), sigs.end(), rr_preds.begin(),
    [ind_low, ind_high, frame_cnt, fps](auto& sig) {
      int pred_ind = rr_sig_predict_ind(sig, ind_low, ind_high);
      double rr_pred = (double)pred_ind / frame_cnt * fps * 60;
      return rr_pred;
    });

  // std::cout << "rr_preds: ";
  // for (auto it : rr_preds) { std::cout << it << ", "; }
  // std::cout << std::endl;

  TestHelperInstance->update_rr_preds(rr_preds[0], rr_preds[1], rr_preds[2]);

  double rr_pred = rr_most_freq(rr_preds);

  return rr_pred;
}
/*
p is pixels cf (one roi)
*/
MeasureResult processPixelsV2(
  const std::vector<std::vector<double>>& p,
  double fps,
  MeasureConfig config,
  std::optional<BaseFeature> base_fea
) {
  auto frame_cnt = p[0].size();
  Eigen::VectorXd ps = pos(p, fps);
  Eigen::VectorXcd fft_data = rfft(ps);

  TestHelperInstance->update_sig(ps);
  TestHelperInstance->update_fft(fft_data, frame_cnt, fps);

  double hr_low = config.hr_low;
  double hr_high = config.hr_high;
  double hr, peak_ratio;
  std::tie(hr, peak_ratio) = predict_hr_v2(fft_data, frame_cnt, fps, hr_low, hr_high);
  if (hr <= 50 && peak_ratio <= 0.5) {
    hr_low = 50;
    std::tie(hr, peak_ratio) = predict_hr_v2(fft_data, frame_cnt, fps, hr_low, hr_high);
  } else if (50 < hr && hr <= 55 && peak_ratio <= 0.5) {
    hr_low = 55;
    std::tie(hr, peak_ratio) = predict_hr_v2(fft_data, frame_cnt, fps, hr_low, hr_high);
  } else if (55 < hr && hr <= 60 && peak_ratio <= 0.3) {
    hr_low = 60;
    std::tie(hr, peak_ratio) = predict_hr_v2(fft_data, frame_cnt, fps, hr_low, hr_high);
  }
  if (hr >= 140 && peak_ratio <= 0.4) {
    hr_high = 140;
    std::tie(hr, peak_ratio) = predict_hr_v2(fft_data, frame_cnt, fps, hr_low, hr_high);
  }


  // std::cout << "hr: " << hr << std::endl;
  // std::cout << "peak_ratio: " << peak_ratio << std::endl;

  std::vector<double> sig_pos = EigenVectorToStdVector(ps);
  double hrv, stress;
  std::tie(hrv, stress) = predict_hrv_v2(sig_pos, fps, hr);

  // std::cout << "hrv: " << hrv << std::endl;
  // std::cout << "stress: " << stress << std::endl;

  double spo2 = predict_spo2_v2(p[0], p[2]);
  // std::cout << "spo2: " << spo2 << std::endl;

  double rr = predict_rr_v2(p, fps, 30, config.rr_low, config.rr_high);
  // std::cout << "rr: " << rr << std::endl;

  MeasureResult res;
  res.hr = hr;
  res.ratio = peak_ratio;

  res.hrv = hrv;
  res.stress = stress;

  res.rr = rr;
  res.spo2 = spo2;

#ifdef ENABLE_BP
  BPEval bpEval = predice_bp_v2(p[1], fps, base_fea);
  res.hbp = bpEval.hbp;
  res.lbp = bpEval.lbp;
#else
  res.hbp = 0;
  res.lbp = 0;
#endif

  return res;
}

} // namespace measure

} // namespace vitels
