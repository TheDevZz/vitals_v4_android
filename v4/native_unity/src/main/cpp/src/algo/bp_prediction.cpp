#include <utility>

#include "bp_prediction.hpp"
#include "test_helper.hpp"
#include "rapidcsv.h"
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

class BPrefer1 {
  // 1: 'male', 0 : 'female'
  std::vector<std::vector<std::vector<double>>>group_gender_map = {
    //        /* 0 */    /* 1 */
    /* 1  */{ {110, 70}, {115, 73} },
    /* 2  */{ {110, 71}, {115, 73} },
    /* 3  */{ {112, 73}, {115, 75} },
    /* 4  */{ {114, 74}, {117, 76} },
    /* 5  */{ {116, 76}, {120, 80} },
    /* 6  */{ {122, 78}, {124, 81} },
    /* 7  */{ {128, 79}, {128, 82} },
    /* 8  */{ {134, 80}, {134, 84} },
    /* 9  */{ {139, 82}, {137, 84} },
    /* 10 */{ {145, 83}, {148, 86} },
  };
  
public:
  int get_age_group(int age) {
    int group;
    if (age <= 20) {
      group = 1;
    } else if (age >= 61) {
      group = 10;
    } else {
      group = (int)std::ceil((age - 20) / 5.0) + 1;
    }
    return group;
  }

  /*
    gender: 0: female, 1: male
    return: tuple: (hbp, lbp)
  */
  std::tuple<double, double> get_refer_bp(int age, int gender) {
    int age_group = get_age_group(age);
    std::vector<double> bp = group_gender_map[age_group][gender];
    return std::make_tuple(bp[0], bp[1]);
  }
};

class BPrefer2 {
  // rapidcsv::Document doc;

  struct GroupItem {
    std::string group;
    double bmi;
    double hbp;
    double lbp;

    GroupItem(std::string group, double bmi, double hbp, double lbp)
      : group(std::move(group)), bmi(bmi), hbp(hbp), lbp(lbp) {};
  };

  std::vector<GroupItem> group_map;

public:
  BPrefer2() {
    std::string csv_content = R"(
group,bmi,hbp,lbp
male,19,130.4,78.08
male,20,131.2,78.71
male,21,132.23,79.41
male,22,133.6,80.3
male,23,134.8,81.19
male,24,136.0,82.16
male,25,137.57,83.36
male,26,139.37,84.51
male,27,140.8,85.64
male,28,142.17,86.67
male,29,143.47,87.56
male,30,144.35,88.5
male,31,145.2,89.27
male,32,146.27,89.96
male,33,147.13,90.72
male,34,147.8,91.34
male,35,148.35,91.78
female,19,127.53,75.02
female,20,128.4,75.64
female,21,129.57,76.42
female,22,131.4,77.43
female,23,133.4,78.36
female,24,135.15,79.34
female,25,136.9,80.41
female,26,138.9,81.3
female,27,140.35,82.09
female,28,141.63,82.88
female,29,143.03,83.61
female,30,144.15,84.39
female,31,145.2,85.03
female,32,146.27,85.61
female,33,147.13,86.27
female,34,147.8,86.83
female,35,148.25,87.22
35-50,19,120.23,74.45
35-50,20,121.35,75.05
35-50,21,122.67,75.82
35-50,22,124.43,76.9
35-50,23,126.37,77.79
35-50,24,128.0,78.82
35-50,25,129.6,80.86
35-50,26,131.53,81.55
35-50,27,133.1,82.82
35-50,28,134.5,84.61
35-50,29,136.1,85.34
35-50,30,137.35,85.96
35-50,31,138.53,86.63
35-50,32,139.87,87.46
35-50,33,141.0,88.92
35-50,34,142.03,89.73
35-50,35,143.05,90.16
51-60,19,128.1,77.19
51-60,20,129.5,77.9
51-60,21,130.87,78.62
51-60,22,132.6,79.95
51-60,23,134.4,80.86
51-60,24,135.95,81.77
51-60,25,137.57,82.8
51-60,26,139.4,83.68
51-60,27,140.8,84.56
51-60,28,142.1,85.4
51-60,29,143.57,86.14
51-60,30,144.7,86.9
51-60,31,145.7,87.48
51-60,32,146.8,87.98
51-60,33,147.6,88.5
51-60,34,148.3,88.88
51-60,35,149.0,89.15
61-70,19,134.7,77.19
61-70,20,135.85,77.9
61-70,21,137.4,78.62
61-70,22,139.3,79.42
61-70,23,140.35,80.12
61-70,24,141.8,80.88
61-70,25,143.6,81.75
61-70,26,145.03,82.49
61-70,27,146.35,83.16
61-70,28,147.5,83.77
61-70,29,148.7,84.36
61-70,30,149.6,84.94
61-70,31,150.47,85.42
61-70,32,151.33,85.81
61-70,33,151.95,86.22
61-70,34,152.6,86.55
61-70,35,153.1,86.78
71-80,19,138.77,76.05
71-80,20,140.3,76.72
71-80,21,141.93,77.44
71-80,22,143.53,78.22
71-80,23,144.67,78.62
71-80,24,145.6,79.2
71-80,25,146.83,80.03
71-80,26,148.63,80.61
71-80,27,149.8,81.15
71-80,28,150.63,81.72
71-80,29,151.6,82.17
71-80,30,152.25,82.62
71-80,31,152.9,83.03
71-80,32,153.7,83.44
71-80,33,154.2,83.91
71-80,34,154.83,84.35
71-80,35,155.85,84.71
)";

    std::stringstream stream(csv_content);
    rapidcsv::Document doc(stream, rapidcsv::LabelParams(0, 0));
    // this->doc = doc;
    std::vector<std::string> groups = doc.GetColumn<std::string>(0);
    std::vector<double> bmis = doc.GetColumn<double>(1);
    std::vector<double> hbps = doc.GetColumn<double>(2);
    std::vector<double> lbps = doc.GetColumn<double>(3);
    size_t size = groups.size();
    for (int i = 0; i < size; ++i) {
      group_map.emplace_back(groups[i], bmis[i], hbps[i], lbps[i]);
    }
  }

  double get_bmi_group(double height, double weight) {
    double bmi = weight / height / height;
    bmi = std::round(bmi);
    bmi = std::max(19.0, bmi);
    bmi = std::min(35.0, bmi);
    return bmi;
  }

  std::tuple<double, double> get_refer_bp_bmi_gender(int gender, double height, double weight) {
    double bmi = get_bmi_group(height, weight);
    std::string gender_group = gender == 1 ? "male" : "female";

    double hbp = 0, lbp = 0;
    for (const auto& it : group_map) {
      if (it.group == gender_group && it.bmi == bmi) {
        hbp = it.hbp;
        lbp = it.lbp;
        break;
      }
    }
    return std::make_tuple(hbp, lbp);
  }

  std::tuple<double, double> get_refer_bp_bmi_age(int age, double height, double weight) {
    double bmi = get_bmi_group(height, weight);

    std::string age_group;
    if (age <= 50) {
      age_group = "35-50";
    } else if (age <= 60) {
      age_group = "51-60";
    } else if (age <= 70) {
      age_group = "61-70";
    } else {
      age_group = "71-80";
    }

    double hbp = 0, lbp = 0;
    for (const auto& it : group_map) {
      if (it.group == age_group && it.bmi == bmi) {
        hbp = it.hbp;
        lbp = it.lbp;
        break;
      }
    }
    return std::make_tuple(hbp, lbp);
  }
};

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