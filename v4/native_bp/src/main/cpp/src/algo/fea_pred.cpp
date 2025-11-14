#include "fea_pred.h"
#include <cmath>
#include <fstream>
#include <iostream>
#include <sstream>

#include "log.hpp"
#include "binary_data_manager.hpp"

// 定义宏控制模型加载方式
#ifndef PRED_LOAD_FROM_BUFFER
#define PRED_LOAD_FROM_BUFFER 1
#endif

using json = nlohmann::json;

// Landmark2Fea implementation
Eigen::VectorXd Landmark2Fea::get_dist_v2(const Eigen::MatrixXd &landmarks_array, int p1, int p2) {
  Eigen::MatrixXd point1 = landmarks_array.block(0, p1 * 2, landmarks_array.rows(), 2);
  Eigen::MatrixXd point2 = landmarks_array.block(0, p2 * 2, landmarks_array.rows(), 2);
  return (point1 - point2).rowwise().norm();
}

Eigen::MatrixXd Landmark2Fea::get_fea(const Eigen::MatrixXd &landmarks_array) {
  std::vector<int> inds = {
    10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288,
    397, 365, 379, 378, 400, 377, 152, 148, 176, 149, 150, 136,
    172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109
  };

  // Calculate base distances
  Eigen::MatrixXd h_dir = landmarks_array.block(0, 359 * 2, landmarks_array.rows(), 2) -
                          landmarks_array.block(0, 130 * 2, landmarks_array.rows(), 2);

  Eigen::VectorXd h_dir_dist = get_dist_v2(landmarks_array, 130, 359);
  Eigen::VectorXd h_base_dist = get_dist_v2(landmarks_array, 127, 356);
  Eigen::VectorXd v_base_dist = get_dist_v2(landmarks_array, 10, 152);

  int n_samples = landmarks_array.rows();
  int n_features = inds.size() * 2 + 2;
  Eigen::MatrixXd features = Eigen::MatrixXd::Zero(n_samples, n_features);

  // Set base distances
  features.col(0) = h_base_dist;
  features.col(1) = v_base_dist;

  // Calculate features for each landmark
  for (size_t idx = 0; idx < inds.size(); ++idx) {
    int i = inds[idx];
    Eigen::VectorXd dist = get_dist_v2(landmarks_array, i, 4);
    Eigen::MatrixXd temp_coords = landmarks_array.block(0, i * 2, landmarks_array.rows(), 2) -
                                  landmarks_array.block(0, 4 * 2, landmarks_array.rows(), 2);

    // Calculate angle
    Eigen::VectorXd prod = (temp_coords.array() * h_dir.array()).rowwise().sum();
    Eigen::VectorXd angle = prod.array() / (h_dir_dist.array() + 1e-10) / (dist.array() + 1e-10);

    // Normalize distance
    Eigen::VectorXd norm_dist = dist.array() / (v_base_dist.array() + 1e-10);

    // Store features
    features.col(2 + idx * 2) = norm_dist;
    features.col(2 + idx * 2 + 1) = angle;
  }

  return features;
}

Eigen::MatrixXd Landmark2Fea::gen_fea(const Eigen::MatrixXd &landmarks_array) {
  return get_fea(landmarks_array);
}

// Fea2Pred implementation
Fea2Pred::Fea2Pred(const std::string &model_fold) : model_fold(model_fold) {
  load_all_models();
}

void Fea2Pred::load_all_models() {
  load_merged_models("gender", gender_model_inds, gender_models);
  load_merged_models("age", age_model_inds, age_models);
  load_merged_models("bmi", bmi_model_inds, bmi_models);
}

json load_json_file(const std::string &file_path) {
#if PRED_LOAD_FROM_BUFFER
  std::vector<uint8_t> data;
  if (!vitals::BinaryDataManager::getInstance().getData(file_path, data)) {
    throw std::runtime_error("Failed to load json from BinaryDataManager with tag: " + file_path);
  }
  std::string json_str(data.begin(), data.end());
  return json::parse(json_str);
#else
  std::ifstream file(file_path);
    return json::parse(file);
#endif
}

void Fea2Pred::load_models(
  const std::string &base_name,
  const std::vector<int> &model_inds,
  std::map<int, ModelParams> &models
) {
  std::string norm_path = model_fold + "/" + base_name + "_norm.json";
  json norm = load_json_file(norm_path);

  for (int ind: model_inds) {
    std::string model_path =
      model_fold + "/" + base_name + "_model_" + std::to_string(ind) + ".json";
    json params = load_json_file(model_path);

    ModelParams model_params;


    // 动态获取 dense1 weight 形状
    auto &dense1_weight = params["dense1.weight"];
    int dense1_in = dense1_weight.size(); // 推荐: 74
    int dense1_out = dense1_weight[0].size(); // 推荐: 64
    model_params.dense1_weight = Eigen::MatrixXd(dense1_in, dense1_out); // 74 x 64
    for (int i = 0; i < dense1_in; ++i)
      for (int j = 0; j < dense1_out; ++j)
        model_params.dense1_weight(i, j) = dense1_weight[i][j];

    // dense1 bias
    auto &dense1_bias = params["dense1.bias"];
    int dense1_bias_len = dense1_bias.size(); // 推荐: 64
    model_params.dense1_bias = Eigen::VectorXd(dense1_bias_len);
    for (int i = 0; i < dense1_bias_len; ++i)
      model_params.dense1_bias(i) = dense1_bias[i];

    // dense2 weight
    auto &dense2_weight = params["dense2.weight"];
    int dense2_in = dense2_weight.size(); // 推荐: 64
    int dense2_out = dense2_weight[0].size(); // 推荐: 32
    model_params.dense2_weight = Eigen::MatrixXd(dense2_in, dense2_out); // 64 x 32
    for (int i = 0; i < dense2_in; ++i)
      for (int j = 0; j < dense2_out; ++j)
        model_params.dense2_weight(i, j) = dense2_weight[i][j];

    // dense2 bias
    auto &dense2_bias = params["dense2.bias"];
    int dense2_bias_len = dense2_bias.size(); // 推荐: 32
    model_params.dense2_bias = Eigen::VectorXd(dense2_bias_len);
    for (int i = 0; i < dense2_bias_len; ++i)
      model_params.dense2_bias(i) = dense2_bias[i];

    // dense3 weight
    auto &dense3_weight = params["dense3.weight"];
    int dense3_in = dense3_weight.size(); // 推荐: 32
    int dense3_out = dense3_weight[0].size(); // 推荐: 3(bmi)/2(gender)/1(age)
    int output_dim = dense3_out;
    model_params.dense3_weight = Eigen::MatrixXd(dense3_in, dense3_out);
    for (int i = 0; i < dense3_in; ++i)
      for (int j = 0; j < dense3_out; ++j)
        model_params.dense3_weight(i, j) = dense3_weight[i][j];

    // dense3 bias
    auto &dense3_bias = params["dense3.bias"];
    int dense3_bias_len = dense3_bias.size(); // 推荐: 3/2/1
    model_params.dense3_bias = Eigen::VectorXd(dense3_bias_len);
    for (int i = 0; i < dense3_bias_len; ++i)
      model_params.dense3_bias(i) = dense3_bias[i];

    // Load normalization parameters
    const auto &mean_json = norm["mean"][std::to_string(ind)];
    const auto &std_json = norm["std"][std::to_string(ind)];

    model_params.mean = Eigen::VectorXd(mean_json.size());
    model_params.std = Eigen::VectorXd(std_json.size());

    for (int i = 0; i < mean_json.size(); ++i) {
      model_params.mean(i) = mean_json[i];
      model_params.std(i) = std_json[i];
    }

    models[ind] = model_params;
  }
}

void Fea2Pred::load_merged_models(
  const std::string &base_name,
  const std::vector<int> &model_inds,
  std::map<int, ModelParams> &models
) {
  for (int ind: model_inds) {
    std::string merged_path =
      model_fold + "/" + base_name + "_merged_model_" + std::to_string(ind) + ".json";
    json merged_data = load_json_file(merged_path);

    ModelParams model_params;

    auto &params = merged_data["params"];

    // 动态获取 dense1 weight 形状
    auto &dense1_weight = params["dense1.weight"];
    int dense1_in = dense1_weight.size(); // 推荐: 74
    int dense1_out = dense1_weight[0].size(); // 推荐: 64
    model_params.dense1_weight = Eigen::MatrixXd(dense1_in, dense1_out); // 74 x 64
    for (int i = 0; i < dense1_in; ++i)
      for (int j = 0; j < dense1_out; ++j)
        model_params.dense1_weight(i, j) = dense1_weight[i][j];

    // dense1 bias
    auto &dense1_bias = params["dense1.bias"];
    int dense1_bias_len = dense1_bias.size(); // 推荐: 64
    model_params.dense1_bias = Eigen::VectorXd(dense1_bias_len);
    for (int i = 0; i < dense1_bias_len; ++i)
      model_params.dense1_bias(i) = dense1_bias[i];

    // dense2 weight
    auto &dense2_weight = params["dense2.weight"];
    int dense2_in = dense2_weight.size(); // 推荐: 64
    int dense2_out = dense2_weight[0].size(); // 推荐: 32
    model_params.dense2_weight = Eigen::MatrixXd(dense2_in, dense2_out); // 64 x 32
    for (int i = 0; i < dense2_in; ++i)
      for (int j = 0; j < dense2_out; ++j)
        model_params.dense2_weight(i, j) = dense2_weight[i][j];

    // dense2 bias
    auto &dense2_bias = params["dense2.bias"];
    int dense2_bias_len = dense2_bias.size(); // 推荐: 32
    model_params.dense2_bias = Eigen::VectorXd(dense2_bias_len);
    for (int i = 0; i < dense2_bias_len; ++i)
      model_params.dense2_bias(i) = dense2_bias[i];

    // dense3 weight
    auto &dense3_weight = params["dense3.weight"];
    int dense3_in = dense3_weight.size(); // 推荐: 32
    int dense3_out = dense3_weight[0].size(); // 推荐: 3(bmi)/2(gender)/1(age)
    int output_dim = dense3_out;
    model_params.dense3_weight = Eigen::MatrixXd(dense3_in, dense3_out);
    for (int i = 0; i < dense3_in; ++i)
      for (int j = 0; j < dense3_out; ++j)
        model_params.dense3_weight(i, j) = dense3_weight[i][j];

    // dense3 bias
    auto &dense3_bias = params["dense3.bias"];
    int dense3_bias_len = dense3_bias.size(); // 推荐: 3/2/1
    model_params.dense3_bias = Eigen::VectorXd(dense3_bias_len);
    for (int i = 0; i < dense3_bias_len; ++i)
      model_params.dense3_bias(i) = dense3_bias[i];

    // Load normalization parameters
    const auto &mean_json = merged_data["mean"];
    const auto &std_json = merged_data["std"];

    model_params.mean = Eigen::VectorXd(mean_json.size());
    model_params.std = Eigen::VectorXd(std_json.size());

    for (int i = 0; i < mean_json.size(); ++i) {
      model_params.mean(i) = mean_json[i];
      model_params.std(i) = std_json[i];
    }

    models[ind] = model_params;
  }
}

Eigen::MatrixXd Fea2Pred::leaky_relu(const Eigen::MatrixXd &x, double alpha) {
  return (x.array() >= 0.0).select(x, alpha * x);
}

Eigen::MatrixXd Fea2Pred::softmax(const Eigen::MatrixXd &x) {
  Eigen::MatrixXd exp_x = x.array().exp();
  if (exp_x.cols() == 1) {
    exp_x.transpose();
  }
  Eigen::VectorXd sums = exp_x.rowwise().sum();
  return exp_x.array().colwise() / sums.array();
}

Eigen::MatrixXd Fea2Pred::apply_model(
  int model_ind,
  const Eigen::MatrixXd &fea,
  const std::map<int, ModelParams> &models
) {
  const auto &model = models.at(model_ind);

  // Normalize input
  // Eigen::MatrixXd x = fea;
  // // 将 mean 和 std 转换为适当的维度进行广播
  // Eigen::MatrixXd mean_broadcasted = model.mean.replicate(1, fea.cols());
  // Eigen::MatrixXd std_broadcasted = model.std.replicate(1, fea.cols());

  // LOGD("x shape: %d x %d", x.rows(), x.cols());
  // LOGD("mean shape: %d x %d", model.mean.rows(), model.mean.cols());
  // LOGD("std shape: %d x %d", model.std.rows(), model.std.cols());
  // LOGD("mean_broadcasted shape: %d x %d", mean_broadcasted.rows(), mean_broadcasted.cols());
  // LOGD("std_broadcasted shape: %d x %d", std_broadcasted.rows(), std_broadcasted.cols());

  // x = ((x.array() - mean_broadcasted.array()) / (std_broadcasted.array() + 1e-10)).matrix();

  // 转置 mean 和 std 为行向量 (1, 74)
  Eigen::MatrixXd mean_row = model.mean.transpose(); // shape: (1, 74)
  Eigen::MatrixXd std_row = model.std.transpose();   // shape: (1, 74)

  // 直接使用转置后的 mean 和 std，无需复制
  Eigen::MatrixXd x = ((fea.array() - mean_row.array()) / (std_row.array() + 1e-10)).matrix();

    // LOGD("x shape: %d x %d", x.rows(), x.cols());
    // LOGD("mean_row shape: %d x %d", mean_row.rows(), mean_row.cols());
    // LOGD("std_row shape: %d x %d", std_row.rows(), std_row.cols());

  // First dense layer
  x = (x * model.dense1_weight).rowwise() + model.dense1_bias.transpose();
  x = leaky_relu(x, 0.1);

  // Second dense layer
  x = (x * model.dense2_weight).rowwise() + model.dense2_bias.transpose();
  x = leaky_relu(x, 0.1);

  // Third dense layer
  return (x * model.dense3_weight).rowwise() + model.dense3_bias.transpose();
}

Eigen::VectorXd Fea2Pred::gender_forward(int model_ind, const Eigen::MatrixXd &fea) {
  Eigen::MatrixXd score = apply_model(model_ind, fea, gender_models);
  return softmax(score).col(1);
}

double Fea2Pred::age_forward(int model_ind, const Eigen::MatrixXd &fea) {
  return apply_model(model_ind, fea, age_models)(0, 0) + 45.0;
}

double Fea2Pred::bmi_forward(int model_ind, const Eigen::MatrixXd &fea) {
  return apply_model(model_ind, fea, bmi_models)(0, 2) + 23.8;
}

std::tuple<int, double, double> Fea2Pred::predict(const Eigen::MatrixXd &fea) {
  double gender_score = 0.0;
  for (int ind: gender_model_inds) {
    gender_score += gender_forward(ind, fea)(0);
  }
  gender_score /= gender_model_inds.size();

  double age_pred = 0.0;
  for (int ind: age_model_inds) {
    age_pred += age_forward(ind, fea);
  }
  age_pred /= age_model_inds.size();

  double bmi_pred = 0.0;
  for (int ind: bmi_model_inds) {
    bmi_pred += bmi_forward(ind, fea);
  }
  bmi_pred /= bmi_model_inds.size();

  return std::make_tuple(gender_score >= 0.5 ? 1 : 0, age_pred, bmi_pred);
}