#include <vector>
#include <queue>
#include <type_traits>
#include <optional>
#include <algorithm>
#include <numeric>

#include <Eigen/Dense>
#include <unsupported/Eigen/FFT>

#include "signal_processing.hpp"

namespace vitals {

namespace signal {

Eigen::VectorXcd fft(const Eigen::VectorXcd& signal) {
  Eigen::FFT<double> eigenFFT;
  Eigen::VectorXcd spectrum = eigenFFT.fwd(signal);
  return spectrum;
}

Eigen::VectorXcd rfft(const Eigen::VectorXcd& signal) {
  vitals::Timer timer("rfft");
  auto spectrum = fft(signal);
  return spectrum.segment(0, spectrum.size() / 2 + 1);
}

std::vector<double> rfftfreq(int n, double d) {
  vitals::Timer timer("rfftfreq");
  std::vector<double> freq(n / 2 + 1);
  double df = 1.0 / (n * d);
  for (int i = 0; i <= n / 2; ++i) {
    freq[i] = i * df;
  }
  return freq;
}

/*
p is pixels_cf (3, n_frame)
*/
Eigen::VectorXd pos(const Eigen::MatrixXd& mp, double fps) {
  // p shape(3, n)
  double eps = 1e-10;
  int l = (int)std::round(1.6 * fps);
  int frame_cnt = mp.cols();
  Eigen::Matrix<double, 2, 3> proj{ {0, 1, -1}, {-2, 1, 1} };
  Eigen::VectorXd h = Eigen::VectorXd::Zero(frame_cnt);
  for (int i = l; i < frame_cnt + 2; ++i) {
    int m = i - l;
    int ls = std::min(i, frame_cnt) - m;
    auto c = mp.block(0, m, mp.rows(), ls);
    Eigen::MatrixXd c_norm = c.array().colwise() / (c.rowwise().mean().array() + eps);
    auto s = proj * c_norm;
    auto temp_h = s.row(0) + s.row(1) *
      std::sqrt((s.row(0).array() - s.row(0).mean()).square().sum() / s.row(0).cols()) /
      (std::sqrt((s.row(1).array() - s.row(1).mean()).square().sum() / s.row(1).cols()) + eps);
    h.segment(m, ls).array() += temp_h.array() - temp_h.mean();
  }
  return h;
}


Eigen::VectorXd pos(const std::vector<std::vector<double>>& p, double fps) {
  // p shape(3, n)
  Eigen::MatrixXd mp(p.size(), p[0].size());
  for (int i = 0; i < p.size(); ++i) {
    mp.row(i) = Eigen::VectorXd::Map(&p[i][0], p[i].size());
  }
  return pos(mp, fps);
}

/*
p is pixels_cf (3, n_frame)
*/
Eigen::VectorXd pbv2(const std::vector<std::vector<double>>& p, double fps) {
  // p: (3, n_frame)

  // std::vector<std::vector<double>> p_norm(p.size());
  // for (int i = 0; i < p.size(); ++i) {
  //   double p_mean = mean(p[i]);
  //   auto& p_dst = p_norm[i];
  //   for (auto& it : p[i]) {
  //     p_dst.emplace_back(it / p_mean);
  //   }
  // }

  // Eigen::MatrixXd mp(p_norm.size(), p_norm[0].size());
  // for (int i = 0; i < p_norm.size(); ++i) {
  //   mp.row(i) = Eigen::VectorXd::Map(&p_norm[i][0], p_norm[i].size());
  // }

  // NumStats stats_p_norm_r = calculateStats(p_norm[0]);
  // NumStats stats_p_norm_g = calculateStats(p_norm[1]);
  // NumStats stats_p_norm_b = calculateStats(p_norm[2]);

  Eigen::MatrixXd p_norm(p.size(), p[0].size());
  auto p_size = p.size();
  for (int i = 0; i < p_size; ++i) {
    double p_mean = mean(p[i]);
    auto pi_size = p[i].size();
    for (int j = 0; j < pi_size; ++j) {
      p_norm(i, j) = p[i][j] / p_mean;
    }
  }
  Eigen::MatrixXd& mp = p_norm;

  NumStats stats_p_norm_r = calculateStats(p_norm.row(0).begin(), p_norm.row(0).end());
  NumStats stats_p_norm_g = calculateStats(p_norm.row(1).begin(), p_norm.row(1).end());
  NumStats stats_p_norm_b = calculateStats(p_norm.row(2).begin(), p_norm.row(2).end());

  double pbv_d = std::sqrt(stats_p_norm_r.variance + stats_p_norm_g.variance + stats_p_norm_b.variance); // () 标量

  // std::cout << "pbv_d: " << pbv_d << std::endl;

  Eigen::Vector3d pbv_vec; // (3, )
  pbv_vec << stats_p_norm_r.std_dev / pbv_d,
    stats_p_norm_g.std_dev / pbv_d,
    stats_p_norm_b.std_dev / pbv_d;

  // std::cout << "pbv_vec: " << pbv_vec << std::endl;

  int l = std::round(1.6 * fps);
  int frame_cnt = p[0].size();
  int end = frame_cnt + 2;
  Eigen::VectorXd p_new = Eigen::VectorXd::Zero(frame_cnt);
  for (int i = l; i < end; ++i) {
    int m = i - l;
    int ls = std::min(i, frame_cnt) - m;
    Eigen::MatrixXd c = mp.block(0, m, mp.rows(), ls); // (3, l)
    Eigen::MatrixXd q = c * c.transpose(); // (3, 3)
    Eigen::VectorXd w = np_linalg_solve(q, pbv_vec); // (3, )
    Eigen::VectorXd a = c.transpose() * w; // (l, )
    double b = pbv_vec.dot(w); // () 标量
    Eigen::VectorXd temp = a / b; // (l, )
    temp.array() -= temp.mean();
    // std::cout << "a: " << a << std::endl;
    // std::cout << "b: " << b << std::endl;
    // std::cout << "temp: " << temp << std::endl;
    p_new.segment(m, ls).array() += temp.array();
  }

  return p_new;
}

/*
p is pixels_cf (3, n_frame)
*/
Eigen::VectorXd omit2(const Eigen::MatrixXd& p) {
  int l = p.cols();
  Eigen::HouseholderQR<Eigen::MatrixXd> qr(p.transpose());
  Eigen::MatrixXd q = qr.householderQ(); // (length, 3), (3, 3)
  Eigen::VectorXd s = q.col(0); // (length, 1)
  Eigen::MatrixXd weight = Eigen::MatrixXd::Identity(l, l) - s * s.transpose(); // (length, length)
  Eigen::MatrixXd y = weight * p.transpose(); // (length, 3)
  return y.col(1); // (length, )
}

Eigen::VectorXd omit2(const std::vector<std::vector<double>>& p) {
  // Convert the input vector to an Eigen matrix
  Eigen::MatrixXd mp(p.size(), p[0].size());
  for (int i = 0; i < p.size(); ++i) {
    for (int j = 0; j < p[0].size(); ++j) {
      mp(i, j) = p[i][j];
    }
  }
  // Call the original omit2 function
  return omit2(mp);
}

/*
p is pixels_cf (3, n_frame)
return: (3, n_frame)
*/
Eigen::MatrixXd lgi(const Eigen::MatrixXd& p) {
  // p: (3, length)
  Eigen::JacobiSVD<Eigen::MatrixXd> svd(p, Eigen::ComputeThinU);
  Eigen::MatrixXd u = svd.matrixU(); // (3, 3)
  Eigen::VectorXd s = u.col(0); // (3, )
  Eigen::MatrixXd sst = s * s.transpose(); // (3, 3)
  Eigen::MatrixXd weight = Eigen::MatrixXd::Identity(3, 3) - sst; // (3, 3)
  Eigen::MatrixXd y = weight * p; // (3, length)
  return y;
}

Eigen::MatrixXd lgi(const std::vector<std::vector<double>>& p) {
  // Convert the input vector to an Eigen matrix
  Eigen::MatrixXd mp(p.size(), p[0].size());
  for (int i = 0; i < p.size(); ++i) {
    for (int j = 0; j < p[0].size(); ++j) {
      mp(i, j) = p[i][j];
    }
  }
  // Call the original omit2 function
  return lgi(mp);
}

Eigen::VectorXd lgi_1(const Eigen::MatrixXd& p) {
  return lgi(p).row(1);
}

Eigen::VectorXd lgi_1(const std::vector<std::vector<double>>& p) {
  return lgi(p).row(1);
}

// std::vector<int> find_peak(const std::vector<double>& d, int top_k, bool sort) {
//   auto end = d.size() - 1;
//   std::vector<int> ind;
//   for (int i = 1; i < end; ++i) {
//     if (d[i - 1] <= d[i] && d[i] >= d[i + 1]) {
//       ind.push_back(i);
//     }
//   }


//   // 使用std::greater<std::pair<double, int>>时，可实现相同的值选后出现的
//   // 该比较方法实现相同的值选先出现的
//   struct GreaterCompare {
//     bool operator()(const std::pair<double, int>& a, const std::pair<double, int>& b) {
//       return a.first > b.first || (a.first == b.first && a.second < b.second);
//     }
//   };

//   int sliceEnd = std::min(top_k, (int)d.size());
//   std::vector<int> p_ind;
//   if (sort) {
//     std::priority_queue<std::pair<double, int>, std::vector<std::pair<double, int>>, GreaterCompare> pq;
//     for (int i = ind.size() - 1; i >= 0; --i) {
//       pq.push(std::make_pair(d[ind[i]], ind[i]));
//       if (pq.size() > top_k) {
//         pq.pop();
//       }
//     }
//     while (!pq.empty()) {
//       p_ind.push_back(pq.top().second);
//       pq.pop();
//     }
//     std::reverse(p_ind.begin(), p_ind.end());
//   }
//   else {
//     p_ind = std::vector(ind.begin(), ind.begin() + sliceEnd);
//   }
//   return p_ind;
// }

std::vector<double> detrend_analysis(const std::vector<double>& g, double fps) {
  auto g_denoise = denoise(g);
  auto g_denoise_detrend = detrend(g_denoise, fps);
  auto g_denoise_detrend_maf = maf(g_denoise_detrend, 5.0);
  double m = mean(g_denoise_detrend_maf);
  double s = std_dev(g_denoise_detrend_maf);
  std::vector<double> g_denoise_detrend_maf_norm(g_denoise_detrend_maf.size());
  std::transform(g_denoise_detrend_maf.begin(), g_denoise_detrend_maf.end(), g_denoise_detrend_maf_norm.begin(),
    [&](double v) {
      return (v - m) / s;
    }
  );
  return g_denoise_detrend_maf_norm;
}

} // namespace signal

} // namespave vitals