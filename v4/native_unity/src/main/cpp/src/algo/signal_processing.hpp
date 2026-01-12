#pragma once

#ifndef SIGNAL_PROCESSING_H
#define SIGNAL_PROCESSING_H

// #define EIGEN_USE_MKL_ALL

#include <vector>
#include <queue>
#include <type_traits>
#include <optional>
#include <algorithm>
#include <numeric>
#include <iterator>

#include <Eigen/Dense>
#include <unsupported/Eigen/FFT>

#include "performance.hpp"


namespace vitals {

namespace signal {

template <typename T>
std::vector<T> convolveValid(const std::vector<T> &signal, const std::vector<T> &kernel)
{
  static_assert(std::is_arithmetic<T>::value, "Type must be numeric");

  int signalLength = signal.size();
  int kernelLength = kernel.size();
  int outputLength = signalLength - kernelLength + 1;

  std::vector<T> output(outputLength, 0.0);

  for (int i = 0; i < outputLength; i++)
  {
    for (int j = 0; j < kernelLength; j++)
    {
      // output[i] += signal[i + j] * kernel[j];
      output[i] += signal[i + j] * kernel[kernelLength - 1 - j]; // 注意卷积核的翻转
    }
  }

  return output;
}

template <typename T>
std::vector<T> maf(const std::vector<T> &signal, int window = 5)
{
  vitals::Timer timer("maf");
  std::vector<T> kernel(window, (1.0 / window));
  return convolveValid(signal, kernel);
}

template <typename T>
std::vector<T> denoise(const std::vector<T> &x, T v = 1)
{
  static_assert(std::is_arithmetic<T>::value, "Type must be numeric");
  vitals::Timer timer("denoise");

  int len_x = x.size();
  int i = 0;
  T offset = 0;
  std::vector<T> new_x{x[0]};
  int size = len_x - 1;
  while (i < size)
  {
    T diff = x[i + 1] - x[i];
    if (std::abs(diff) > v)
    {
      offset += diff;
    }
    new_x.push_back(x[i + 1] - offset);
    i += 1;
  }
  return new_x;
}

template <typename T>
struct PeakParams
{
  std::optional<T> height;
  T threshold = 0;
  double distance = 1;
};

template <typename T>
std::vector<int> findPeaks(const std::vector<T> &data, const PeakParams<T> &params = {})
{
  static_assert(std::is_arithmetic_v<T>, "Template type must be an arithmetic type");

  if (params.distance < 1) {
    throw std::invalid_argument("'distance' should be greater than or equal to 1.");
    return {};
  }
  double distance = params.distance;

  double threshold = params.threshold;
  if (threshold < 0) {
    threshold = 0;
  }

  std::vector<int> peaks;

  // int last_peak_idx = -1;

  int i = 1;
  int i_max = data.size() - 1;

  while (i < i_max) {
    if (!params.height || data[i] >= params.height.value()) {
      T left_diff = data[i] - data[i - 1];
      if (left_diff > 0 && left_diff >= threshold && data[i] - data[i + 1] >= threshold) {
        int i_ahead = i + 1;

        while (i_ahead < i_max && data[i_ahead] == data[i]) {
          i_ahead += 1;
        }

        T right_diff = data[i] - data[i_ahead];

        if (right_diff >= threshold) {
          int peak_idx = (i + i_ahead - 1) / 2;
          i = i_ahead;

          peaks.push_back(peak_idx);
        }
      }
    }
    i += 1;
  }

  if (distance > 1) {
    int peaks_size = peaks.size();

    // argsort start: priority_to_position = np.argsort(data[peaks])
    // 创建一个索引向量并使用std::iota生成递增序列
    std::vector<int> priority_to_position(peaks_size);
    std::iota(priority_to_position.begin(), priority_to_position.end(), 0);

    // 创建一个新的向量来存储data[peaks[i]]的值，减少查找次数
    std::vector<T> values(peaks_size);
    std::transform(priority_to_position.begin(), priority_to_position.end(), values.begin(),
        [&](int i){ return data[peaks[i]]; });

    // 根据 values 的值对索引向量进行排序
    std::sort(priority_to_position.begin(), priority_to_position.end(),
        [&](int i1, int i2) { return values[i1] < values[i2]; });
    // argsort end

    for (int i = peaks_size - 1; i >= 0; --i) {
      int j = priority_to_position[i];
      if (peaks[j] == -1) {
        continue;
      }

      int k = j - 1;
      while (0 <= k && peaks[j] - peaks[k] < distance) {
        peaks[k] = -1;
        k -= 1;
      }

      k = j + 1;
      while(k < peaks_size && peaks[k] - peaks[j] < distance) {
        peaks[k] = -1;
        k += 1;
      }
    }
    peaks.erase(std::remove(peaks.begin(), peaks.end(), -1), peaks.end());
  }

  return peaks;
}

template <typename T>
std::vector<int> find_peak(const std::vector<T>& d, int top_k = 10, bool sort = true) {
  auto end = d.size() - 1;
  std::vector<int> ind;
  for (int i = 1; i < end; ++i) {
    if (d[i - 1] <= d[i] && d[i] >= d[i + 1]) {
      ind.push_back(i);
    }
  }

  // 使用std::greater<std::pair<T, int>>时，可实现相同的值选后出现的
  // 该比较方法实现相同的值选先出现的
  struct GreaterCompare {
    bool operator()(const std::pair<T, int>& a, const std::pair<T, int>& b) {
      return a.first > b.first || (a.first == b.first && a.second < b.second);
    }
  };

  std::vector<int> p_ind;
  if (sort) {
    std::priority_queue<std::pair<T, int>, std::vector<std::pair<T, int>>, GreaterCompare> pq;
    for (int i = ind.size() - 1; i >= 0; --i) {
      pq.push(std::make_pair(d[ind[i]], ind[i]));
      if (pq.size() > top_k) {
        pq.pop();
      }
    }
    while (!pq.empty()) {
      p_ind.push_back(pq.top().second);
      pq.pop();
    }
    std::reverse(p_ind.begin(), p_ind.end());
  }
  else {
    int sliceEnd = std::min(top_k, (int)ind.size());
    p_ind = std::vector(ind.begin(), ind.begin() + sliceEnd);
  }
  return p_ind;
}

template<typename T>
std::vector<T> detrend(const std::vector<T>& x, T reg = 30) {
  vitals::Timer timer("detrend");
  int n = x.size();
  Eigen::MatrixX<T> i = Eigen::MatrixX<T>::Identity(n, n);
  Eigen::MatrixX<T> d2 = Eigen::MatrixX<T>::Zero(n - 2, n);

  for (int j = 0; j < n - 2; ++j) {
    d2(j, j) = 1;
    d2(j, j + 1) = -2;
    d2(j, j + 2) = 1;
  }

  // std::cout << "d2\n" << d2 << std::endl;
  // std::cout << "d2.T\n" << d2.transpose() << std::endl;

  Eigen::MatrixX<T> temp = i + reg * reg * (d2.transpose() * d2);

  // std::cout << "d2 dot\n" << (d2.transpose() * d2) << std::endl;
  // std::cout << "**\n" << temp << std::endl;
  // std::cout << "inv\n" << temp.inverse() << std::endl;

  // auto d3 = i - temp.inverse();
  // std::cout << "d3\n" << d3 << std::endl;
  // auto d4 = Eigen::Map<const Eigen::VectorX<T>>(x.data(), x.size());
  // Eigen::MatrixX<T> new_x = (d3) * d4;
  Eigen::MatrixX<T> new_x = (i - temp.inverse()) * Eigen::Map<const Eigen::VectorX<T>>(x.data(), x.size());
  return std::vector<T>(new_x.data(), new_x.data() + new_x.size());
}

template <typename T>
double mean(const std::vector<T>& v) {
  if (v.empty()) {
    return 0;
  }
  return std::accumulate(v.begin(), v.end(), 0.0) / v.size();
}

template <typename T>
double variance(const std::vector<T>& v) {
  if (v.size() < 2) {
    std::cerr << "Error: Cannot compute standard deviation with less than 2 elements.\n";
    return 0;
  }
  double m = mean(v);

  double sq_diff_sum = 0;
  for (const auto& val : v) {
    double diff = static_cast<double>(val) - m;
    sq_diff_sum += diff * diff;
  }
  return sq_diff_sum / v.size();
}

template <typename T>
double std_dev(const std::vector<T>& v) {
  return std::sqrt(variance(v));
}

template<typename T>
struct NumStats {
  T sum;
  double mean;
  double variance;
  double std_dev;
};

template<typename T>
NumStats<T> calculateStats(const std::vector<T>& v) {
  if (v.empty()) {
    throw std::invalid_argument("Input vector must not be empty");  // 抛出异常
  }

  T sum = std::accumulate(v.begin(), v.end(), T(0));
  double mean = static_cast<double>(sum) / v.size();
  double sq_diff_sum = 0;
  for (const auto& val : v) {
    double diff = static_cast<double>(val) - mean;
    sq_diff_sum += diff * diff;
  }
  double variance = sq_diff_sum / v.size();
  double std_dev = std::sqrt(variance);

  return NumStats<T>{sum, mean, variance, std_dev};
}

// Overloaded function to calculate stats from iterators
template<typename Iterator>
auto calculateStats(Iterator begin, Iterator end) -> NumStats<typename std::iterator_traits<Iterator>::value_type> {
  using T = typename std::iterator_traits<Iterator>::value_type;

  if (begin == end) {
    throw std::invalid_argument("Input range must not be empty");
  }

  T sum = std::accumulate(begin, end, T(0));
  auto size = std::distance(begin, end);
  double mean = static_cast<double>(sum) / size;
  double sq_sum = std::inner_product(begin, end, begin, 0.0);
  double variance = sq_sum / size - mean * mean;
  double std_dev = std::sqrt(variance);

  return NumStats<T>{sum, mean, variance, std_dev};
}

// Overloaded function to calculate stats from pointers
template<typename T>
NumStats<T> calculateStats(const T* begin, const T* end) {
  if (begin == end) {
    throw std::invalid_argument("Input range must not be empty");
  }

  T sum = std::accumulate(begin, end, T(0));
  auto size = std::distance(begin, end);
  double mean = static_cast<double>(sum) / size;
  double sq_sum = std::inner_product(begin, end, begin, 0.0);
  double variance = sq_sum / size - mean * mean;
  double std_dev = std::sqrt(variance);

  return NumStats<T>{sum, mean, variance, std_dev};
}

enum BVPType {
  HR,
  RR,
  SPO2,
};

template<typename T>
std::vector<T> extractBVP(const std::vector<T>& p_signal, BVPType type, T frame_rate = 30) {
  static_assert(std::is_arithmetic_v<T>, "Template type must be an arithmetic type");
  vitals::Timer timer("extractBVP");
  auto p = denoise(p_signal, 1.0);
  if (type == SPO2) {
    p = maf(p, 5);
  } else {
    if (type == HR) {
      p = detrend(p, frame_rate);
    }
    p = maf(p, 5);

    // std::cout << "maf: [";
    // for (auto& v : p) {
    //   std::cout << v << ", ";
    // }
    // std::cout << "]" << std::endl;

    auto m = mean(p);
    auto s = std_dev(p);

    // std::cout << "m, s: " << m << ", " << s << std::endl;

    size_t p_size = p.size();
    for (int i = 0; i < p_size; ++i) {
      p[i] = (p[i] - m) / s;
    }
  }
  return p;
}

// 将Eigen::VectorX转换为std::vector的模板函数
template<typename T>
std::vector<T> EigenVectorToStdVector(const Eigen::Matrix<T, Eigen::Dynamic, 1>& eigen_vector) {
    std::vector<T> std_vector(eigen_vector.data(), eigen_vector.data() + eigen_vector.size());
    return std_vector;
}

// 将std::vector转换为Eigen::VectorX的模板函数
template<typename T>
Eigen::Matrix<T, Eigen::Dynamic, 1> StdVectorToEigenVector(const std::vector<T>& std_vector) {
    Eigen::Matrix<T, Eigen::Dynamic, 1> eigen_vector = Eigen::Map<Eigen::Matrix<T, Eigen::Dynamic, 1>>(std_vector.data(), std_vector.size());
    return eigen_vector;
}

std::vector<double> rfftfreq(int n, double d = 1.0);

Eigen::VectorXcd fft(const Eigen::VectorXcd& signal);

Eigen::VectorXcd rfft(const Eigen::VectorXcd& signal);

template <typename T>
Eigen::VectorXcd vector2vectorXcd(const std::vector<T>& signal) {
  Eigen::VectorXcd eigenVector(signal.size());
  size_t size = signal.size();
  for (int i = 0; i < size; ++i) {
    eigenVector(i) = std::complex<double>(signal[i], 0.0);
  }
  return eigenVector;
}

template <typename T>
Eigen::VectorXcd fft(const std::vector<T>& signal) {
  return fft(vector2vectorXcd(signal));
}

template <typename T>
Eigen::VectorXcd rfft(const std::vector<T>& signal) {
  return rfft(vector2vectorXcd(signal));
}

template<typename T>
std::vector<int> argsort(const std::vector<T>& v) {
  // 初始化索引向量
  std::vector<int> idx(v.size());
  // for (size_t i = 0; i != idx.size(); ++i) idx[i] = i;
  std::iota(idx.begin(), idx.end(), 0);

  // 通过比较v中的值对索引向量进行排序
  std::sort(idx.begin(), idx.end(), [&v](int i1, int i2){ return v[i1] < v[i2]; } );

  return idx;
}

struct HRFreqInfo {
  double HR;
  double power;
  double CR;
  double SNR;
};

struct FFTInfo {
  Eigen::VectorXcd fdata;
  std::vector<double> freq;
};

template<typename T>
FFTInfo getFFT(const std::vector<T>& p, double frame_rate = 30) {
  auto fft_data = rfft(p);
  auto freq = rfftfreq(p.size(), 1.0 / frame_rate);
  FFTInfo fftInfo;
  fftInfo.fdata = fft_data;
  fftInfo.freq = freq;
  return fftInfo;
}

inline HRFreqInfo getHR(const FFTInfo& fftInfo, double frame_rate = 30, double minFreq = 0.7, double maxFreq = 2.5) {
  vitals::Timer timer("getHR");
  auto& fft_data = fftInfo.fdata;
  // freq = np.fft.rfftfreq(p_len, 1. / self.frame_rate) # Frequency data
  auto& freq = fftInfo.freq;
  // inds = np.where((freq < self.minFreq) | (freq > self.maxFreq))[0]
  std::vector<int> freq_ids;
  for (int i = 0; i < freq.size(); ++i) {
    auto f = freq[i];
    // if (f > minFreq && f < maxFreq) {
    //   freq_ids.push_back(i);
    // }
    if (f < minFreq) {
      continue;
    }
    if (f > maxFreq) {
      break;
    }
    freq_ids.push_back(i);
  }

  // fft_data = np.abs(fft_data)
  // fft_data[inds] = 0
  // bps_freq = 60.0 * freq
  // sorted_arr = np.argsort(fft_data)
  // max_index = sorted_arr[-1]
  // HR = bps_freq[max_index]
  int maxAmplitudeIdx = 0;
  int secondMaxAmplitudeIdx = 0;
  double maxAmplitude = 0;
  double secondMaxAmplitude = 0;
  std::printf("---\n");
  std::vector<double> fft_amplitudes(fft_data.size(), 0);
  for (auto& i : freq_ids) {
    auto amplitude = std::abs(fft_data[i]);
    fft_amplitudes[i] = amplitude;
    std::printf("# %d: %f  ", i, amplitude);
    std::cout << fft_data[i] << std::endl;
    if (amplitude > maxAmplitude) {
      secondMaxAmplitude = maxAmplitude;
      secondMaxAmplitudeIdx = maxAmplitudeIdx;
      maxAmplitude = amplitude;
      maxAmplitudeIdx = i;
    } else if (amplitude > secondMaxAmplitude) {
      secondMaxAmplitude = amplitude;
      secondMaxAmplitudeIdx = i;
    }
  }
  std::printf("---\n");
  std::printf("maxAmp: %f , secMaxAmp: %f, maxIdx: %d , secMaxIdx: %d\n", maxAmplitude, secondMaxAmplitude, maxAmplitudeIdx, secondMaxAmplitudeIdx);
  auto HR = freq[maxAmplitudeIdx] * 60;
  auto CR = maxAmplitude / secondMaxAmplitude;

  int binary_window_fund = 2;
  int fund_begin = maxAmplitudeIdx - binary_window_fund;
  int fund_end = maxAmplitudeIdx + binary_window_fund + 1;
  if (fund_begin < 0) {
    fund_begin = 0;
  }
  if (fund_end > fft_amplitudes.size()) {
    fund_end = fft_amplitudes.size();
  }

  std::vector<double> fund_freq_spect(fft_amplitudes.begin() + fund_begin, fft_amplitudes.begin() + fund_end);

  double signal = 0, noise = 0;
  for (int i = fund_begin; i < fund_end; ++i) {
    auto amplitude = fft_amplitudes[i];
    auto amplitude_norm = amplitude / maxAmplitude;
    signal += std::pow(amplitude_norm, 2);
    noise += std::pow(1 - amplitude_norm, 2);
  }
  auto SNR = 10 * std::log10(signal / noise);

  HRFreqInfo hrFreqInfo;
  hrFreqInfo.HR = HR;
  hrFreqInfo.power = maxAmplitude;
  hrFreqInfo.CR = CR;
  hrFreqInfo.SNR = SNR;
  return hrFreqInfo;
}

template<typename T>
HRFreqInfo getHR(const std::vector<T>& p, double frame_rate = 30, double minFreq = 0.7, double maxFreq = 2.5) {
  return getHR(getFFT(p, frame_rate), frame_rate, minFreq, maxFreq);
}

inline double getRR(const FFTInfo& fftInfo, double frame_rate = 30, double minFreq = 0.15, double maxFreq = 0.35) {
  auto& fft_data = fftInfo.fdata;
  auto& freq = fftInfo.freq;
  std::vector<int> freq_ids;
  for (int i = 0; i < freq.size(); ++i) {
    auto f = freq[i];
    if (f < minFreq) {
      continue;
    }
    if (f > maxFreq) {
      break;
    }
    freq_ids.push_back(i);
  }
  int maxAmplitudeIdx = 0;
  double maxAmplitude = 0;
  for (auto& i : freq_ids) {
    auto amplitude = std::abs(fft_data[i]);
    if (amplitude > maxAmplitude) {
      maxAmplitude = amplitude;
      maxAmplitudeIdx = i;
    }
  }
  auto RR = freq[maxAmplitudeIdx] * 60;
  return RR;
}

template<typename T>
double getRR(const std::vector<T>& p, double frame_rate = 30, double minFreq = 0.15, double maxFreq = 0.35) {
  return getRR(getFFT(p, frame_rate), frame_rate, minFreq, maxFreq);
}

template<typename T>
std::vector<T> gradient(const std::vector<T>& arr) {
  std::vector<T> grad(arr.size());
  int n = arr.size();

  // Handle the first element
  if (n > 0) {
    grad[0] = arr[1] - arr[0];
  }

  // Handle elements in the middle
  for (int i = 1; i < n - 1; ++i) {
    grad[i] = (arr[i + 1] - arr[i - 1]) / 2.0;
  }

  // Handle the last element
  if (n > 1) {
    grad[n - 1] = arr[n - 1] - arr[n - 2];
  }

  return grad;
}

template <typename T>
T median(std::vector<T> v) {
  size_t n = v.size();
  if (n == 0) {
    throw std::domain_error("median of an empty vector");
  }

  // nth_element会将第n个元素放在它在排序后应该在的位置，左边的元素都比它小，右边的元素都比它大
  std::nth_element(v.begin(), v.begin() + n / 2, v.end());

  if (n % 2 == 0) { // 如果元素数量是偶数，那么中位数是中间两个数的平均值
    T a = v[n / 2];
    std::nth_element(v.begin(), v.begin() + n / 2 - 1, v.end());
    T b = v[n / 2 - 1];
    return (a + b) / 2.0; // 使用静态转换避免整数除法的问题
  }
  else { // 如果元素数量是奇数，那么中位数就是中间的那个数
    return v[n / 2];
  }
}

template<typename T>
std::vector<T> diff(const std::vector<T>& vec) {
    std::vector<T> result;
    for (size_t i = 1; i < vec.size(); ++i) {
        result.push_back(vec[i] - vec[i-1]);
    }
    return result;
}

Eigen::VectorXd pos(const Eigen::MatrixXd& mp, double fps);
Eigen::VectorXd pos(const std::vector<std::vector<double>>& p, double fps);

Eigen::VectorXd pbv2(const std::vector<std::vector<double>>& p, double fps);

Eigen::VectorXd omit2(const Eigen::MatrixXd& p);
Eigen::VectorXd omit2(const std::vector<std::vector<double>>& p);

Eigen::MatrixXd lgi(const Eigen::MatrixXd& p);
Eigen::MatrixXd lgi(const std::vector<std::vector<double>>& p);
Eigen::VectorXd lgi_1(const Eigen::MatrixXd& p);
Eigen::VectorXd lgi_1(const std::vector<std::vector<double>>& p);

std::vector<double> detrend_analysis(const std::vector<double>& g, double fps = 30);

inline Eigen::VectorXd np_linalg_solve(const Eigen::MatrixXd& A, const Eigen::MatrixXd& B) {
  return A.colPivHouseholderQr().solve(B);
}

inline std::tuple<Eigen::MatrixXd, Eigen::MatrixXd> np_linalg_qr(const Eigen::MatrixXd& A) {
  Eigen::HouseholderQR<Eigen::MatrixXd> qr = A.householderQr();
  Eigen::MatrixXd Q = qr.householderQ();
  Eigen::MatrixXd R = qr.matrixQR().template triangularView<Eigen::Upper>();
  return std::make_tuple(Q, R);
}

} // namespace signal

} // namespave vitals

#endif // SIGNAL_PROCESSING_H