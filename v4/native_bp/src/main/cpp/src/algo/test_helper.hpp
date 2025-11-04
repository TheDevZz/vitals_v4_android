#pragma once

#include <vector>
#include <fstream>
#include <ostream>
#include <memory>
#include <iomanip>
#include <endian.h>

#include <Eigen/Core>

#include "signal_processing.hpp"
#include "utils.hpp"

namespace vitals {

using namespace signal;

namespace csv {

// 辅助函数：将列数据转换成字符串
// template<typename T>
// std::string to_string(const T& value) {
//   std::ostringstream oss;
//   oss << std::fixed << std::setprecision(15) << value;
//   std::cout << std::fixed << std::setprecision(15) << value << ",";
//   return oss.str();
// }

// std::string to_string(const double& value) {
//   std::ostringstream oss;
//   oss << std::fixed << std::setprecision(15) << value;
//   return oss.str();
// }

// 特化：将std::string直接返回
// std::string to_string(const std::string& value) {
//   return value;
// }

// 辅助函数：写入一行CSV
inline void write_csv_row(std::ostream& os, const std::vector<std::string>& row) {
  for (size_t i = 0; i < row.size(); ++i) {
    if (i != 0) {
      os << ",";
    }
    os << row[i];
  }
  os << "\n";
}

// 处理每个列的数据
template<typename... Args>
void process_columns(std::ostream& os, const std::vector<std::string>& header, const std::vector<std::vector<std::string>>& columns) {
  write_csv_row(os, header);

  size_t row_count = 0;
  for (auto& vals : columns) {
    row_count = std::max(row_count, vals.size());
  }

  for (size_t i = 0; i < row_count; ++i) {
    std::vector<std::string> row;
    for (const auto& column : columns) {
      if (i < column.size()) {
        row.push_back(column[i]);
      }
      else {
        row.push_back("");
      }
    }
    write_csv_row(os, row);
  }
}

template <typename ... Ts>
void to_csv(std::ostream& os, const std::pair<const char*, std::vector<Ts>>& ... args) {
  // 收集列名
  std::vector<std::string> header = { args.first... };

  // 收集列数据并转换为字符串形式
  std::vector<std::vector<std::string>> columns = { std::vector<std::string>(args.second.size())... };

  int index = 0;
  (void)std::initializer_list<int>{
          (std::transform(args.second.begin(), args.second.end(), columns[index++].begin(), [](const auto& value) {
              // return to_string(value);
              std::ostringstream oss;
              oss << std::setprecision(17) << value;
              return oss.str();
          }), 0)...
  };

  // 写入CSV文件
  process_columns(os, header, columns);
}

template <typename ... Ts>
inline void to_csv(const std::string& csv_path, const std::pair<const char*, std::vector<Ts>>& ... args) {
  std::ofstream ofs(csv_path);
//  if (!ofs.is_open()) {
//    throw std::runtime_error("Could not open file for writing");
//  }

  to_csv(ofs, args...);

  ofs.close();
}

template <typename ... Ts>
inline std::string to_csv(const std::pair<const char*, std::vector<Ts>>& ... args) {
  std::ostringstream oss;

  to_csv(oss, args...);

  return oss.str();
}

} // namespace csv

struct OutputPair {
    std::string filename;
    std::string content;

    OutputPair(std::string filename, std::string content)
      : filename(std::move(filename)), content(std::move(content)) {};
};

class TestHelper
{
private:
  /* data */
public:
  std::vector<OutputPair> outputs;

  TestHelper() {}
  ~TestHelper() {}

  virtual void update_sig(const Eigen::VectorXd& sig) {};
  virtual void update_sig(const std::vector<double>& sig) {};
  virtual void update_fft(const Eigen::VectorXcd& fft, int frame_cnt, double fps) {};
  virtual void update_hrv_data(const std::vector<double>& p_tar, const std::vector<int>& peak_ind, const std::vector<double>& rr_interval) {};
  virtual void update_rr_interval_filter(const std::vector<double>& rr_interval_filter) {};
  virtual void update_hrv_bandpass(const Eigen::VectorXcd& fft, int type) {};
  virtual void update_sdnns(const std::vector<double>& sdnns) {};
  virtual void update_rr_preds(double pbv2, double omit2, double lgi_1) {};

  template <typename T>
  void print_vector(const std::vector<T> vec, std::string label = "") {
    std::cout << label << "\n";
    std::cout << "[";
    for (const auto& it : vec) {
      std::cout << it << ", ";
    }
    std::cout << "]" << std::endl;
  };
};

class TestHelperImp : public TestHelper
{
private:
public:
  std::vector<double> sig2_oval_pos_0;
  std::vector<double> fft_oval_pos_0;
  std::vector<int> ind;
  std::vector<double> freq;
  std::vector<double> hr_pred;

  std::vector<double> p_tar;
  std::vector<int> peak_ind;
  std::vector<double> rr_interval;
  std::vector<double> rr_interval_filter;
  std::vector<std::complex<double>> hrv_bandpass_fft;
  std::vector<std::complex<double>> hrv_bandpass_fft_filter;

  std::ostringstream oss;

  TestHelperImp() {};
  ~TestHelperImp() {};

  // 检测本地字节序是否为小端序
  static bool isLittleEndian() {
    uint16_t num = 0x0001;
    return (*reinterpret_cast<uint8_t *>(&num) == 0x01);
  }

  // 将 std::vector<T> 数据以二进制形式写入文件（支持 double 和 float 类型）
  // 参数说明：
  // - data: 要写入的数据向量（支持 double 和 float 类型）
  // - file_path: 目标文件路径
  // - convert_endian: 是否转换为大端序（默认启用）
  //    - true: 将数据转换为大端序后写入文件（适用于跨平台或网络传输）
  //    - false: 直接以本地字节序写入文件（适用于同平台使用）
  // 注意：
  // - 大端序（Big-Endian）：高位字节在前，低位字节在后（如网络字节序）
  // - 小端序（Little-Endian）：低位字节在前，高位字节在后（如 x86/ARM 架构）
  template<typename T>
  void write_binary_data(
    const std::vector<T> &data, const std::string &file_path, bool convert_endian = true
  ) {
    std::ofstream ofs(file_path, std::ios::binary);
    if (!ofs.is_open()) {
      throw std::runtime_error("Could not open file for writing");
    }

    for (const auto &value: data) {
      if (convert_endian) {
        if constexpr (sizeof(T) == sizeof(uint64_t)) {
          // 将 64 位数据转换为大端序
          uint64_t big_endian_value = htobe64(*reinterpret_cast<const uint64_t *>(&value));
          ofs.write(reinterpret_cast<const char *>(&big_endian_value), sizeof(big_endian_value));
        } else if constexpr (sizeof(T) == sizeof(uint32_t)) {
          // 将 32 位数据转换为大端序
          uint32_t big_endian_value = htobe32(*reinterpret_cast<const uint32_t *>(&value));
          ofs.write(reinterpret_cast<const char *>(&big_endian_value), sizeof(big_endian_value));
        }
      } else {
        // 直接以本地字节序写入
        ofs.write(reinterpret_cast<const char *>(&value), sizeof(value));
      }
    }
    ofs.close();
  }

  // 从文件中读取二进制数据（支持 double 和 float 类型）
  // 参数说明：
  // - file_path: 文件路径
  // - convert_endian: 是否从大端序转换回本地字节序（默认启用）
  //    - true: 假设文件中的数据是大端序，转换为本地字节序
  //    - false: 直接读取为本地字节序
  // 返回值：
  // - 包含读取数据的向量
  template<typename T>
  std::vector<T> read_binary_data(const std::string &file_path, bool convert_endian = true) {
    std::ifstream ifs(file_path, std::ios::binary | std::ios::ate);
    if (!ifs.is_open()) {
      throw std::runtime_error("Could not open file for reading");
    }

    std::streamsize size = ifs.tellg();
    ifs.seekg(0, std::ios::beg);

    std::vector<T> data(size / sizeof(T));
    ifs.read(reinterpret_cast<char *>(data.data()), size);

    if (convert_endian) {
      for (auto &value: data) {
        if constexpr (sizeof(T) == sizeof(uint64_t)) {
          // 将 64 位大端序数据转换为本地字节序
          uint64_t big_endian_value = *reinterpret_cast<uint64_t *>(&value);
          uint64_t host_endian_value = be64toh(big_endian_value);
          value = *reinterpret_cast<T *>(&host_endian_value);
        } else if constexpr (sizeof(T) == sizeof(uint32_t)) {
          // 将 32 位大端序数据转换为本地字节序
          uint32_t big_endian_value = *reinterpret_cast<uint32_t *>(&value);
          uint32_t host_endian_value = be32toh(big_endian_value);
          value = *reinterpret_cast<T *>(&host_endian_value);
        }
      }
    }

    ifs.close();
    return data;
  }

  void update_sig(const Eigen::VectorXd& sig) override {
    update_sig(EigenVectorToStdVector(sig));
  };

  void update_sig(const std::vector<double>& sig) override {
    this->sig2_oval_pos_0 = sig;
  };

  void update_fft(const Eigen::VectorXcd& fft_data, int frame_cnt, double fps) override {
    auto fsize = fft_data.size();
    std::vector<double> abs_fft_data(fsize);
    std::vector<int> ind(fsize);
    std::vector<double> freq(fsize);
    std::vector<double> hr_preds(fsize);
    for (int i = 0; i < fsize; ++i) {
      auto amplitude = std::abs(fft_data[i]);
      double hr_pred = (double)i / frame_cnt * fps * 60;
      ind[i] = i;
      freq[i] = (double)i / frame_cnt * fps;
      hr_preds[i] = hr_pred;
      abs_fft_data[i] = amplitude;
    }
    this->ind = ind;
    this->freq = freq;
    this->hr_pred = hr_preds;
    this->fft_oval_pos_0 = abs_fft_data;
  }

  void update_hrv_data(const std::vector<double>& p_tar, const std::vector<int>& peak_ind, const std::vector<double>& rr_interval) override {
    this->p_tar = p_tar;
    this->peak_ind = peak_ind;
    this->rr_interval = rr_interval;
  }

  void update_rr_interval_filter(const std::vector<double>& rr_interval_filter) override {
    this->rr_interval_filter = rr_interval_filter;
  }

  virtual void update_hrv_bandpass(const Eigen::VectorXcd& fft, int type) override {
    if (type == 0) {
      hrv_bandpass_fft.resize(fft.size());
      std::copy(fft.begin(), fft.end(), hrv_bandpass_fft.begin());
    } else if (type == 1) {
      hrv_bandpass_fft_filter.resize(fft.size());
      std::copy(fft.begin(), fft.end(), hrv_bandpass_fft_filter.begin());
    }
//    auto size = fft.size();
//    std::vector<double> abs_fft(size);
//    std::vector<double> fft_real(size);
//    std::vector<double> fft_imag(size);
//    for (int i = 0; i < size; ++i) {
//      double abs = std::abs(fft[i]);
//      abs_fft[i] = abs;
//      fft_real[i] = fft[i].real();
//      fft_imag[i] = fft[i].imag();
//    }
//    if (type == 0) {
//      outputs.emplace_back("test_hrv_bandpass.csv",
//        csv::to_csv(
//            std::make_pair("fft", abs_fft),
//            std::make_pair("fft_real", fft_real),
//            std::make_pair("fft_imag", fft_imag)
//        )
//      );
//    } else if (type == 1) {
//      outputs.emplace_back("test_hrv_bandpass_fft_filter.csv",
//        csv::to_csv(
//            std::make_pair("fft_filter", abs_fft)
//        )
//      );
//    }
  }

  std::string print_hrv_bandpass() {
    auto size = hrv_bandpass_fft.size();
    std::vector<double> abs_fft(size);
    std::vector<double> fft_real(size);
    std::vector<double> fft_imag(size);
    for (int i = 0; i < size; ++i) {
      double abs = std::abs(hrv_bandpass_fft[i]);
      abs_fft[i] = abs;
      fft_real[i] = hrv_bandpass_fft[i].real();
      fft_imag[i] = hrv_bandpass_fft[i].imag();
    }
    size = hrv_bandpass_fft_filter.size();
    std::vector<double> abs_fft_filter(size);
    for (int i = 0; i < size; ++i) {
      double abs = std::abs(hrv_bandpass_fft_filter[i]);
      abs_fft_filter[i] = abs;
    }
    return csv::to_csv(
        std::make_pair("fft", abs_fft),
        std::make_pair("fft_real", fft_real),
        std::make_pair("fft_imag", fft_imag),
        std::make_pair("fft_filter", abs_fft_filter)
    );
  }

  void update_sdnns(const std::vector<double>& sdnns) override {
    oss << "sdnns: " << vectorToString(sdnns);
  }

  void update_rr_preds(double pbv2, double omit2, double lgi_1) override {
    oss << "rr_pred_pbv2: " << pbv2 << std::endl;
    oss << "rr_pred_omit2: " << omit2 << std::endl;
    oss << "rr_pred_lgi1: " << lgi_1 << std::endl;
  }
  
};

class TestHelperAndroid : public TestHelperImp {

public:
    std::string test_dir;
    std::ofstream ofs;

  explicit TestHelperAndroid(const std::string& test_dir) {
    this->test_dir = join_path(test_dir, ""); // 添加路径分隔符在末尾
    ofs.open(join_path(this->test_dir, "temp_test.txt"));
  };

  ~TestHelperAndroid() {
    ofs.close();
  }

  void update_hrv_bandpass(const Eigen::VectorXcd& fft, int type) override {
    auto size = fft.size();
    std::vector<double> abs_fft(size);
    std::vector<double> fft_real(size);
    std::vector<double> fft_imag(size);
    for (int i = 0; i < size; ++i) {
      double abs = std::abs(fft[i]);
      abs_fft[i] = abs;
      fft_real[i] = fft[i].real();
      fft_imag[i] = fft[i].imag();
    }
    if (type == 0) {
      csv::to_csv(test_dir + "tmp_test_hrv_bandpass.csv",
        std::make_pair("fft", abs_fft),
        std::make_pair("fft_real", fft_real),
        std::make_pair("fft_imag", fft_imag)
      );
    } else if (type == 1) {
      csv::to_csv(test_dir + "tmp_test_hrv_bandpass_fft_filter.csv",
        std::make_pair("fft_filter", abs_fft)
      );
    }
  }

  void update_sdnns(const std::vector<double>& sdnns) override {
    ofs << "sdnns: " << vectorToString(sdnns) << std::endl;
  }

  void update_rr_preds(double pbv2, double omit2, double lgi_1) override {
    ofs << "rr_pred_pbv2: " << pbv2 << std::endl;
    ofs << "rr_pred_omit2: " << omit2 << std::endl;
    ofs << "rr_pred_lgi1: " << lgi_1 << std::endl;
  }

  void print_sig(const std::vector<std::vector<double>>& pixels_cf, double fps) {
    int frame_cnt = pixels_cf[0].size();
    std::vector<double> t(frame_cnt);
    for (int i = 0; i < frame_cnt; ++i) {
      t[i] = i / fps;
    }
    csv::to_csv(test_dir + "sig_test.csv",
        std::make_pair("t", t),
        std::make_pair("oval_r_mean", pixels_cf[0]),
        std::make_pair("oval_g_mean", pixels_cf[1]),
        std::make_pair("oval_b_mean", pixels_cf[2])
    );
  }
};

extern std::unique_ptr<TestHelper> TestHelperInstance;

} // namespace vitals

