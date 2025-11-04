#include <utility>

#include "bp_prediction.hpp"
#include "bp_refer.hpp"
#include "test_helper.hpp"
#include "utils.hpp"

#include "log.hpp"

//#include "torch/csrc/jit/mobile/import.h"

namespace vitals {

namespace measure {

using namespace vitals::signal;

#ifdef ENABLE_TEST
std::vector<std::string> model_dir_list = {
    "models/",             // <project>/
    "../models/",          // <project>/build/
    "../../models/",       // <project>/build/Debug
    "../../../../models/"  // <project>/build/test/catch2/Debug/
};
#else
std::vector<std::string> model_dir_list;
#endif

std::string find_model(const std::vector<std::string>& modelsDirs, const std::string& model_file) {
  for (auto& dir : modelsDirs) {
    auto file_path = join_path(dir, model_file);
    auto file_exist = is_exists(file_path);
    if (file_exist) {
      return file_path;
    }
  }
  return model_file;
}

std::string find_model(const std::string& model_file) {
  if (is_exists(model_file)) {
    return model_file;
  } else {
    return find_model(model_dir_list, model_file);
  }
}

inline torch::jit::script::Module loadModel(const std::string& model_file) {
//inline torch::jit::mobile::Module loadModel(const std::string& model_file) {
  auto model_path = find_model(model_file);
  std::cout << "load: " << model_path << std::endl;
  torch::jit::script::Module model = torch::jit::load(model_path, torch::kCPU);
//  torch::jit::mobile::Module model = torch::jit::_load_for_mobile(model_path, torch::kCPU);
  model.eval();
  return model;
}

std::vector<double> merge_signal_clf(const std::vector<double>& pixels_g, double fps) {
  std::vector<double> p = detrend_analysis(pixels_g, fps);
  auto p_size = p.size();
  while (p.size() < 420) {
    p.insert(p.end(), p.end() - p_size, p.end());
  }
  std::vector<double> bvp(p.begin(), p.begin() + 420);
  return bvp;
}

std::vector<double> merge_signal_base_fea(const std::vector<double>& pixels_g, double fps) {
  std::vector<double> p = detrend_analysis(pixels_g, 30); // 固定30，不用fps
  auto p_size = p.size();
  while (p.size() < 1200) {
    p.insert(p.end(), p.end() - p_size, p.end());
  }
  std::vector<double> bvp(p.begin(), p.begin() + 1200);
  return bvp;
}

BPEval predict_bp_clf(const std::vector<double>& bvp, bool use_high_bp_med) {
  auto bvp_prime = gradient(bvp);
  auto bvp_prime_prime = gradient(bvp_prime);

  auto t_bvp = torch::tensor(bvp).unsqueeze(0);
  auto t_bvp_prime = torch::tensor(bvp_prime).unsqueeze(0);
  auto t_bvp_prime_prime = torch::tensor(bvp_prime_prime).unsqueeze(0);

  std::string file_bp_model_cls = "ep-5_test_loss-1.1068_test_acc-0.4359.pt";
  std::string file_bp_model_low = "model=ResCNN1D_nclasses=3_pretrained=0_class0.pt";
  std::string file_bp_model_mid = "model=ResCNN1D_nclasses=3_pretrained=0_class1.pt";
  std::string file_bp_model_hig = "model=ResCNN1D_nclasses=3_pretrained=0_class2.pt";

  auto bp_model_cls = loadModel(file_bp_model_cls);
//  bp_model_cls.eval();
  torch::Tensor cls_tensor = bp_model_cls.forward({ t_bvp, t_bvp_prime, t_bvp_prime_prime }).toTensor();
  int clf = cls_tensor.argmax().item().toInt();

  int y = clf;
  if (use_high_bp_med) {
    y = 2;
  }

  std::string file_bp_model;
  if (y == 0) {
    file_bp_model = file_bp_model_low;
  }
  else if (y == 1) {
    file_bp_model = file_bp_model_mid;
  }
  else {
    file_bp_model = file_bp_model_hig;
  }

  auto bp_model = loadModel(file_bp_model);
//  bp_model.eval();

  torch::Tensor output_tensor = bp_model.forward({ t_bvp, t_bvp_prime, t_bvp_prime_prime }).toTensor();

  torch::Tensor flattened_output = output_tensor.view({ -1 });
  auto flattened_output_data = flattened_output.accessor<float, 1>();  // 1D accessor
  // for (int i = 0; i < flattened_output_data.size(0); ++i) {
  //   std::cout << "Element " << i << ": " << flattened_output_data[i] << std::endl;
  // }
  BPEval bpEval;
  bpEval.hbp = flattened_output_data[0];
  bpEval.lbp = flattened_output_data[1];
  bpEval.HR_BP = flattened_output_data[2];
  bpEval.clf = clf;
  return bpEval;
}

BPEval predict_bp_base_fea(const std::vector<double>& rppg, int age, int gender, double height, double weight) {
  std::vector<double> find_peak(rppg.begin(), rppg.begin() + 420);

  double bmi = weight / (height * height);

  std::string file_knn_hbp_model = "knn_model_HBP_06262023.sav";
  std::string file_knn_lbp_model = "knn_model_LBP_06262023.sav";
  std::string file_HBP_AGHW = "HBP_06302023__model=ResCNN1D3_nclasses=1_pretrained=0_loss15.12446915_epoch3.pt";
  std::string file_LBP_AGHW = "LBP_06302023__model=ResCNN1D3_nclasses=1_pretrained=0_loss10.89517215_epoch268.pt";

  // knn_hbp = self.models.knn_hbp_model.predict([[gender, age, bmi]] )[0]
  // knn_lbp = self.models.knn_lbp_model.predict([[gender, age, bmi]] )[0]

  // auto knn_hbp_model = loadModel(file_knn_hbp_model);
  // auto knn_lbp_model = loadModel(file_knn_lbp_model);
  // knn_hbp_model.predict();
  KNNModel knn_model;
  auto knn_bp = knn_model.predict(gender, age, bmi);

  float knn_hbp = (float)std::get<0>(knn_bp);
  float knn_lbp = (float)std::get<1>(knn_bp);

  std::vector<double> rppg_weight(rppg.size());
  std::vector<double> rppg_height(rppg.size());
  std::vector<double> rppg_bmi(rppg.size());
  for (auto v : rppg) {
    rppg_weight.push_back(v / weight);
    rppg_height.push_back(v / height);
    rppg_bmi.push_back(v / bmi);
  }

  std::vector<double> rppg_prime = gradient(rppg);
  std::vector<double> rppg_prime_prime = gradient(rppg_prime);

  auto t_rppg = torch::tensor(rppg).unsqueeze(0);
  auto t_rppg_prime = torch::tensor(rppg_prime).unsqueeze(0);
  auto t_rppg_prime_prime = torch::tensor(rppg_prime_prime).unsqueeze(0);

  auto t_rppg_weight = torch::tensor(rppg_weight).unsqueeze(0);
  auto t_rppg_height = torch::tensor(rppg_height).unsqueeze(0);
  auto t_rppg_bmi = torch::tensor(rppg_bmi).unsqueeze(0);

  auto t_age = torch::tensor(age).unsqueeze(0);
  auto t_gender = torch::tensor(gender).unsqueeze(0);
  auto t_weight = torch::tensor(weight).unsqueeze(0);
  auto t_height = torch::tensor(height).unsqueeze(0);
  auto t_bmi = torch::tensor(bmi).unsqueeze(0);

  auto t_knn_hbp = torch::tensor(knn_hbp).unsqueeze(0);
  auto t_knn_lbp = torch::tensor(knn_lbp).unsqueeze(0);

  std::vector<double> find_peak_prime = gradient(find_peak);
  std::vector<double> find_peak_prime_prime = gradient(find_peak_prime);

  // peaks
  PeakParams<double> params_height_0;
  params_height_0.height = 0;
  std::vector<int> peaks = findPeaks(find_peak, params_height_0);
  std::vector<int> peaks_prime = diff(peaks);
  double average_peak_time = mean(peaks_prime);
  int median_peak_time = median(peaks_prime);
  double sum = 0;
  for (auto i : peaks) {
    sum += find_peak[i];
  }
  double average_peak_height = sum / peaks.size();

  // dips
  std::vector<double> neg_find_peak(find_peak.size());
  std::transform(find_peak.begin(), find_peak.end(), neg_find_peak.begin(), [](double v) { return v * -1; });
  std::vector<int> dips = findPeaks(neg_find_peak, params_height_0);
  std::vector<int> dips_prime = diff(dips);
  double average_dips_time = mean(dips_prime);
  int median_dips_time = median(dips_prime);
  double average_dips_height = std::accumulate(dips.begin(), dips.end(), 0.0, [&find_peak](double sum, int i) { return sum + find_peak[i]; }) / dips.size();

  std::vector<int> peaks_2 = findPeaks(find_peak_prime_prime, params_height_0);
  std::vector<int> peaks_prime_2 = diff(peaks_2);
  double average_peak_time_2 = mean(peaks_prime_2);
  double median_peak_time_2 = median(peaks_prime_2);
  double average_peak_height_2 = std::accumulate(peaks_2.begin(), peaks_2.end(), 0.0, [&find_peak_prime_prime](double sum, int i) { return sum + find_peak_prime_prime[i]; }) / peaks_2.size();

  std::vector<double> neg_find_peak_prime_prime(find_peak_prime_prime.size());
  std::transform(find_peak_prime_prime.begin(), find_peak_prime_prime.end(), neg_find_peak_prime_prime.begin(), [](double v) { return v * -1; });
  std::vector<int> dips_2 = findPeaks(neg_find_peak_prime_prime, params_height_0);
  std::vector<int> dips_prime_2 = diff(dips_2);
  double average_dips_time_2 = mean(dips_prime_2);
  int median_dips_time_2 = median(dips_prime_2);
  double average_dips_height_2 = std::accumulate(dips_2.begin(), dips_2.end(), 0.0, [&find_peak_prime_prime](double sum, int i) { return sum + find_peak_prime_prime[i]; }) / dips_2.size();


  double peak_dips_height_ratio_2 = std::abs(average_peak_height_2 / average_dips_height_2);

  auto t_average_peak_time = torch::tensor(average_peak_time).unsqueeze(0);
  auto t_median_peak_time = torch::tensor(median_peak_time).unsqueeze(0);
  auto t_average_dips_time = torch::tensor(average_dips_time).unsqueeze(0);
  auto t_median_dips_time = torch::tensor(median_dips_time).unsqueeze(0);

  auto t_average_peak_time_2 = torch::tensor(average_peak_time_2).unsqueeze(0);
  auto t_median_peak_time_2 = torch::tensor(median_peak_time_2).unsqueeze(0);
  auto t_average_dips_time_2 = torch::tensor(average_dips_time_2).unsqueeze(0);
  auto t_median_dips_time_2 = torch::tensor(median_dips_time_2).unsqueeze(0);

  auto t_average_peak_height = torch::tensor(average_peak_height).unsqueeze(0);
  auto t_average_dips_height = torch::tensor(average_dips_height).unsqueeze(0);
  auto t_average_peak_height_2 = torch::tensor(average_peak_height_2).unsqueeze(0);
  auto t_average_dips_height_2 = torch::tensor(average_dips_height_2).unsqueeze(0);
  auto t_peak_dips_height_ratio_2 = torch::tensor(peak_dips_height_ratio_2).unsqueeze(0);

  auto HBP_AGHW = loadModel(file_HBP_AGHW);
//  HBP_AGHW.eval();
  auto pred_hbp = HBP_AGHW.forward({
    t_rppg, t_rppg_prime, t_rppg_prime_prime, t_rppg_weight, t_rppg_height, t_rppg_bmi, t_age, t_gender, t_weight, t_height, t_bmi,
    t_average_peak_time, t_median_peak_time, t_average_dips_time, t_median_dips_time,
    t_average_peak_time_2, t_median_peak_time_2, t_average_dips_time_2, t_median_dips_time_2,
    t_average_peak_height, t_average_dips_height, t_average_peak_height_2, t_average_dips_height_2, t_peak_dips_height_ratio_2, t_knn_hbp, t_knn_lbp
    });

  // std::cout << pred_hbp << std::endl;
  double hbp = pred_hbp.toTensor().item().toDouble();
  // std::cout << hbp << std::endl;

  auto LBP_AGHW = loadModel(file_LBP_AGHW);
  auto pred_lbp = LBP_AGHW.forward({
    t_rppg, t_rppg_prime, t_rppg_prime_prime, t_rppg_weight, t_rppg_height, t_rppg_bmi, t_age, t_gender, t_weight, t_height, t_bmi,
    t_average_peak_time, t_median_peak_time, t_average_dips_time, t_median_dips_time,
    t_average_peak_time_2, t_median_peak_time_2, t_average_dips_time_2, t_median_dips_time_2,
    t_average_peak_height, t_average_dips_height, t_average_peak_height_2, t_average_dips_height_2, t_peak_dips_height_ratio_2, t_knn_hbp, t_knn_lbp
    });

  // std::cout << pred_lbp << std::endl;
  double lbp = pred_lbp.toTensor().item().toDouble();
  // std::cout << lbp << std::endl;


  // std::cout << std::endl;
  // std::cout << "bp PP" << std::endl;
  // for (auto it : peaks) std::cout << it << ", ";
  // std::cout << std::endl;
  // for (auto it : dips) std::cout << it << ", ";
  // std::cout << std::endl;
  // for (auto it : peaks_2) std::cout << it << ", ";
  // std::cout << std::endl;
  // for (auto it : dips_2) std::cout << it << ", ";
  // std::cout << std::endl;

  // std::cout << average_peak_time << ", " << median_peak_time << ", " << average_peak_height << std::endl;
  // std::cout << average_dips_time << "," << median_dips_time << "," << average_dips_height << std::endl;
  // std::cout << average_peak_time_2 << "," << median_peak_time_2 << "," <<  average_peak_height_2 << std::endl;
  // std::cout << average_dips_time_2 << "," << median_dips_time_2 << "," << average_dips_height_2 << std::endl;
  // std::cout << "peak_dips_height_ratio_2: " << peak_dips_height_ratio_2 << std::endl;

  // TestHelperInstance->print_vector(find_peak, "bp_find_peak");


  BPEval bpEval;
  bpEval.hbp = hbp;
  bpEval.lbp = lbp;
  return bpEval;
}

std::tuple<double, double> bp_correction(int age, int gender, double height, double weight, double hbp_pred, double lbp_pred) {
  BPrefer1 bp_refer_v1;
  BPrefer2 bp_refer_v2;
  std::tuple<double, double> bp_ref_1 = bp_refer_v1.get_refer_bp(age, gender);
  std::tuple<double, double> bp_ref_2 = bp_refer_v2.get_refer_bp_bmi_gender(gender, height, weight);
  std::tuple<double, double> bp_ref_3 = bp_refer_v2.get_refer_bp_bmi_age(age, height, weight);

  double hbp_ref = 0, lbp_ref = 0;
  std::vector<std::tuple<double, double>> bp_refs = { bp_ref_1 , bp_ref_2, bp_ref_3 };
  for (const auto& bp_ref : bp_refs) {
    hbp_ref += std::get<0>(bp_ref);
    lbp_ref += std::get<1>(bp_ref);
  }
  hbp_ref /= bp_refs.size();
  lbp_ref /= bp_refs.size();

  double hbp_corr = hbp_pred;
  double lbp_corr = lbp_pred;
  if (hbp_pred <= hbp_ref - 10 || hbp_pred >= hbp_ref + 10) {
    hbp_corr = (hbp_pred + hbp_ref) / 2;
  }
  if (lbp_pred <= lbp_ref - 10 || lbp_pred >= lbp_ref + 10) {
    lbp_corr = (lbp_pred + lbp_ref) / 2;
  }
  return std::make_tuple(hbp_corr, lbp_corr);
}

BPEval predice_bp_v2(const std::vector<double>& pixels_g, double fps, std::optional<BaseFeature> base_fea) {
  BPEval bpEval;
  if (base_fea) {
    std::vector<double> rppg = merge_signal_base_fea(pixels_g, fps);
    bpEval = predict_bp_base_fea(rppg, base_fea->age, base_fea->gender, base_fea->height, base_fea->weight);
  } else {
    std::vector<double> bvp = merge_signal_clf(pixels_g, fps);
    bpEval = predict_bp_clf(bvp, false);
  }
  return bpEval;
}

} // namespace measure

} // namespace vitels