#ifndef RPPG_NATIVE_H
#define RPPG_NATIVE_H

#include <Eigen/Dense>
#include <vector>
#include <string>
#include <map>

#include "json.hpp"

class Landmark2Fea {
public:
  Eigen::MatrixXd get_fea(const Eigen::MatrixXd &landmarks_array);

  Eigen::MatrixXd gen_fea(const Eigen::MatrixXd &landmarks_array);

private:
  Eigen::VectorXd get_dist_v2(const Eigen::MatrixXd &landmarks_array, int p1, int p2);
};

class Fea2Pred {
public:
  struct ModelParams {
    Eigen::MatrixXd dense1_weight;
    Eigen::VectorXd dense1_bias;
    Eigen::MatrixXd dense2_weight;
    Eigen::VectorXd dense2_bias;
    Eigen::MatrixXd dense3_weight;
    Eigen::VectorXd dense3_bias;
    Eigen::VectorXd mean;
    Eigen::VectorXd std;
  };

  Fea2Pred(const std::string &model_fold);

  std::tuple<int, double, double> predict(const Eigen::MatrixXd &fea);

private:
  std::string model_fold;
  std::map<int, ModelParams> gender_models;
  std::map<int, ModelParams> age_models;
  std::map<int, ModelParams> bmi_models;
  std::vector<int> gender_model_inds{0};
  std::vector<int> age_model_inds{0};
  std::vector<int> bmi_model_inds{0};

  void load_all_models();

  void load_models(
    const std::string &base_name,
    const std::vector<int> &model_inds,
    std::map<int, ModelParams> &models
  );

  void load_merged_models(
    const std::string &base_name,
    const std::vector<int> &model_inds,
    std::map<int, ModelParams> &models
  );

  Eigen::MatrixXd leaky_relu(const Eigen::MatrixXd &x, double alpha = 0.1);

  Eigen::MatrixXd softmax(const Eigen::MatrixXd &x);

  Eigen::MatrixXd apply_model(
    int model_ind,
    const Eigen::MatrixXd &fea,
    const std::map<int, ModelParams> &models
  );

  Eigen::VectorXd gender_forward(int model_ind, const Eigen::MatrixXd &fea);

  double age_forward(int model_ind, const Eigen::MatrixXd &fea);

  double bmi_forward(int model_ind, const Eigen::MatrixXd &fea);

};

#endif // RPPG_NATIVE_H