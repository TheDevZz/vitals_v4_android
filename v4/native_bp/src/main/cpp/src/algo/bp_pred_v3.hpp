#ifndef VITALS_V4_BP_PRED_V3_HPP
#define VITALS_V4_BP_PRED_V3_HPP

#include <vector>
#include <string>
#include <utility>
#include <cmath>

#include "log.hpp"
#include "signal_processing.hpp"
#include "bp_refer.hpp"
#include "binary_data_manager.hpp"

// #include "torch/csrc/jit/mobile/import.h"
#include "torch/script.h"

// 定义宏控制模型加载方式
#ifndef BP_PRED_LOAD_FROM_BUFFER
#define BP_PRED_LOAD_FROM_BUFFER 1
#endif

namespace vitals {

  namespace measure {

    class Sig2Fea {
    public:
      std::vector<double> generate_timestamps(int frame_cnt, double fps) {
        std::vector<double> timestamps(frame_cnt);
        for (int i = 0; i < frame_cnt; ++i) {
          timestamps[i] = static_cast<double>(i) / fps;
        }
        return timestamps;
      }

      std::vector<double> sig_process(const std::vector<double> &df_sig2, double fps) {
        std::vector<double> t1 = generate_timestamps(df_sig2.size(), fps);
        double t_min = t1.front();
        double t_max = t1.back();
        double num = std::round(30 * (t_max - t_min) + 1);
        std::vector<double> t2 = vitals::signal::linspace(t_min, t_max, num);
        std::vector<double> y2 = vitals::signal::interp(t2, t1, df_sig2);

        // Calculate mean and standard deviation
        double y2_mean = vitals::signal::mean(y2);
        double y2_std = vitals::signal::std_dev(y2);

        // Normalize the signal
        std::vector<double> sig_new(y2.size());
        for (size_t i = 0; i < y2.size(); ++i) {
          sig_new[i] = (y2[i] - y2_mean) / (y2_std + 1e-10);
        }

        return sig_new;
      }

      std::pair<std::vector<size_t>, std::vector<double>>
      find_peaks(const std::vector<double> &d, double height = 0) {
        std::vector<size_t> peaks;
        std::vector<double> peak_values;

        // 需要至少3个点才能找到峰值
        if (d.size() < 3) {
          return {peaks, peak_values};
        }

        // 遍历所有点（除了第一个和最后一个点）
        for (size_t i = 1; i < d.size() - 1; ++i) {
          // 检查是否是峰值点：当前点大于等于相邻点且大于等于height
          if (d[i] >= d[i - 1] && d[i] >= d[i + 1] && d[i] >= height) {
            peaks.push_back(i);
            peak_values.push_back(d[i]);
          }
        }

        return {peaks, peak_values};
      }

      std::vector<double> get_sig_fea(const std::vector<double> &df_sig2, double fps) {
        // 处理信号
        std::vector<double> sig = sig_process(df_sig2, fps);

        // 计算梯度
        std::vector<double> sig_p = vitals::signal::gradient(sig);
        std::vector<double> sig_p_p = vitals::signal::gradient(sig_p);

        // 找到主波峰和谷
        // 找到主波峰和谷
        auto [peak, peak_values] = find_peaks(sig, 0);
        auto peak_p = vitals::signal::diff(peak);
        double peak_p_mean = vitals::signal::mean(peak_p);
        double peak_p_median = vitals::signal::median(peak_p);
        double peak_h_mean = vitals::signal::mean(vitals::signal::gather(sig, peak));

        // 找到谷值
        std::vector<double> neg_sig(sig.size());
        std::transform(sig.begin(), sig.end(), neg_sig.begin(), std::negate<double>());

        auto [dip, dip_values] = find_peaks(neg_sig, 0);
        auto dip_p = vitals::signal::diff(dip);
        double dip_p_mean = vitals::signal::mean(dip_p);
        double dip_p_median = vitals::signal::median(dip_p);
        double dip_h_mean = vitals::signal::mean(vitals::signal::gather(sig, dip));

        // 找到二阶导数的峰值
        auto [peak2, peak2_values] = find_peaks(sig_p_p, 0);
        auto peak2_p = vitals::signal::diff(peak2);
        double peak2_p_mean = vitals::signal::mean(peak2_p);
        double peak2_p_median = vitals::signal::median(peak2_p);
        double peak2_h_mean = vitals::signal::mean(vitals::signal::gather(sig_p_p, peak2));

        // 找到二阶导数的谷值
        std::vector<double> neg_sig_p_p(sig_p_p.size());
        std::transform(sig_p_p.begin(), sig_p_p.end(), neg_sig_p_p.begin(), std::negate<double>());

        auto [dip2, dip2_values] = find_peaks(neg_sig_p_p, 0);
        auto dip2_p = vitals::signal::diff(dip2);
        double dip2_p_mean = vitals::signal::mean(dip2_p);
        double dip2_p_median = vitals::signal::median(dip2_p);
        double dip2_h_mean = vitals::signal::mean(vitals::signal::gather(sig_p_p, dip2));

        // 计算峰谷比
        double peak_dip_h_mean_ratio = peak2_h_mean / dip2_h_mean;

        // 返回特征向量
        return {
          peak_p_mean, peak_p_median, dip_p_mean, dip_p_median,
          peak2_p_mean, peak2_p_median, dip2_p_mean, dip2_p_median,
          peak_h_mean, dip_h_mean, peak2_h_mean, dip2_h_mean,
          peak_dip_h_mean_ratio
        };
      }

      std::tuple<double, double> get_bp_refer(int age, int gender, double bmi) {
        BPrefer1 bp_refer_v1;
        BPrefer2 bp_refer_v2;
        std::tuple<double, double> bp_ref_1 = bp_refer_v1.get_refer_bp(age, gender);
        std::tuple<double, double> bp_ref_2 = bp_refer_v2.get_refer_bp_bmi_gender(gender, bmi);
        std::tuple<double, double> bp_ref_3 = bp_refer_v2.get_refer_bp_bmi_age(age, bmi);

        // LOGD("bp_ref_1: %.2f/%.2f", std::get<0>(bp_ref_1), std::get<1>(bp_ref_1));
        // LOGD("bp_ref_2: %.2f/%.2f", std::get<0>(bp_ref_2), std::get<1>(bp_ref_2));
        // LOGD("bp_ref_3: %.2f/%.2f", std::get<0>(bp_ref_3), std::get<1>(bp_ref_3));

        // 计算三组血压参考值的平均值
        double hbp_mean =
          (std::get<0>(bp_ref_1) + std::get<0>(bp_ref_2) + std::get<0>(bp_ref_3)) / 3.0;
        double lbp_mean =
          (std::get<1>(bp_ref_1) + std::get<1>(bp_ref_2) + std::get<1>(bp_ref_3)) / 3.0;

        return std::make_tuple(hbp_mean, lbp_mean);
      }

      std::vector<double> get_fea(
        const std::vector<double> &df_sig2,
        double fps,
        int age,
        int gender,
        double bmi,
        double hr_pred,
        double peak_ratio,
        double hbp_refer = 0.0,
        double lbp_refer = 0.0
      ) {

        // 如果未提供血压参考值，则计算参考值
        if (hbp_refer == 0.0 || lbp_refer == 0.0) {
          auto [hbp_ref, lbp_ref] = get_bp_refer(age, gender, bmi);
          hbp_refer = hbp_ref;
          lbp_refer = lbp_ref;
        }

        // 获取信号特征
        std::vector<double> sig_features = get_sig_fea(df_sig2, fps);

        // 构建完整特征向量
        std::vector<double> fea;
        fea.reserve(20); // 7 + 13 特征

        // 添加基本特征
        fea.push_back(static_cast<double>(age));
        fea.push_back(static_cast<double>(gender));
        fea.push_back(bmi);
        fea.push_back(hr_pred);
        fea.push_back(peak_ratio);
        fea.push_back(hbp_refer);
        fea.push_back(lbp_refer);

        // 添加信号特征
        fea.insert(fea.end(), sig_features.begin(), sig_features.end());

        return fea;
      }
    };


    class Fea2Pred {
    private:
      torch::jit::Module model;
      std::vector<double> norm_mean = {
        53.85913775, 0.54679803, 23.53778122, 66.87372978, 0.41929929,
        134.88804269, 80.73653202, 6.28976438, 3.93448276, 6.24019495,
        3.93743842, 4.35582916, 4.08275862, 4.27250808, 4.04581281,
        0.85080547, -0.89611475, 0.25290241, -0.24011541, -1.0529718,
      };
      std::vector<double> norm_std = {
        14.60831652, 0.49805053, 2.93103535, 11.77989114, 0.16783374,
        6.98614921, 2.80879393, 2.35938911, 1.7338699, 2.30482152,
        1.73497419, 0.43129322, 0.43858329, 0.44601456, 0.44146487,
        0.09084726, 0.098785, 0.07288665, 0.06850576, 0.06340448,
      };

    public:
      Fea2Pred(const std::string &model_path) {
#if BP_PRED_LOAD_FROM_BUFFER
        std::vector<uint8_t> model_data;
        if (!BinaryDataManager::getInstance().getData(model_path, model_data)) {
          throw std::runtime_error(
            "Failed to load model from BinaryDataManager with tag: " + model_path);
        }

        // 创建内存缓冲区
        torch::jit::script::ExtraFilesMap extra_files;
        std::istringstream model_stream(std::string(model_data.begin(), model_data.end()));
        model = torch::jit::load(model_stream, torch::kCPU, extra_files);
        model.eval();
#else
        model = torch::jit::load(model_path, torch::kCPU);
#endif
      }

      ~Fea2Pred() = default;

      std::pair<double, double> predict_bp(const std::vector<double> &fea) {
        // 归一化特征
        std::vector<float> norm_fea(fea.size()); // 输入层必须为float类型
        for (size_t i = 0; i < fea.size(); ++i) {
          norm_fea[i] = (fea[i] - norm_mean[i]) / norm_std[i];
        }

        // norm_fea = {0.35191339419307693, 0.9099517874220513, 0.4988744949800761, 3.3775735627336987, -1.5419473076287422, 0.35669969751476344, 0.9435133771692078, 1.6573084971134748, 0.6145312517392454, 0.14887850549630965, 0.32424780912735085, -1.2666680697558625, -2.468763960432692, -0.3749763732046383, -0.10377453136871377, 6.153064040824958, 0.16829858103212866, -1.5414056685351945, 1.4059218361128587, 1.1915966301889076};

        // 转换为Tensor
        auto input = torch::from_blob(norm_fea.data(), {1, static_cast<int64_t>(norm_fea.size())},
                                      torch::kFloat);

        // 前向传播
        auto output = model.forward({input}).toTensor();

        // 提取预测值
        double hbp_pred = output[0][0].item<double>();
        double lbp_pred = output[0][1].item<double>();

        // 偏置修正
        hbp_pred += 120.0;
        lbp_pred += 75.0;

        return std::make_pair(hbp_pred, lbp_pred);
      }
    };


    class BPPredV3 {
    private:
      Sig2Fea sig2fea;
      Fea2Pred fea2pred;
    public:
      BPPredV3(std::string model_path) : fea2pred(model_path) {

      };

      ~BPPredV3() = default;

      std::pair<double, double> predict_bp_v3(
        const std::vector<double> &df_sig2,
        double fps,
        int age,
        int gender,
        double bmi,
        double hr_pred,
        double peak_ratio,
        double hbp_refer = 0.0,
        double lbp_refer = 0.0
      ) {
        std::vector<double> fea = sig2fea.get_fea(
          df_sig2, fps,
          age, gender, bmi, hr_pred, peak_ratio,
          hbp_refer, lbp_refer
        );

        // std::string fea_str = "Features: ";
        // for (size_t i = 0; i < fea.size(); ++i) {
        //     fea_str += std::to_string(fea[i]);
        //     if (i < fea.size() - 1) fea_str += ", ";
        // }
        // LOGD("%s", fea_str.c_str());

        // fea = {59, 1, 25, 106.66117866634494, 0.1605085064777376, 137.38, 83.38666666666667, 10.2, 5.0, 6.583333333333333, 4.5, 3.8095238095238093, 3.0, 4.105263157894737, 4.0, 1.4097944787134755, -0.8794893746727411, 0.1405545145294593, -0.14380166611649317, -0.9774192352931199};

        return fea2pred.predict_bp(fea);
      }
    };


  } // namespace measure

} // namespace vitals

#endif // VITALS_V4_BP_PRED_V3_HPP