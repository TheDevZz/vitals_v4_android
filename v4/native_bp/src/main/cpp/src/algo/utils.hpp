#pragma once

#include <iostream>
#include <chrono>
#include <ctime>
#include <sstream>

namespace vitals {

#ifdef __ANDROID__
#include <unistd.h>
inline bool is_exists(const std::string& path)  {
    return access(path.c_str(), F_OK) == 0;
}

inline std::string join_path(const std::string& parent, const std::string& child) {
    if (parent.back() == '/') {
        return parent + child;
    } else {
        return parent + "/" + child;
    }
}
#else
#include <filesystem>
namespace fs = std::filesystem;

template <typename T>
inline bool is_exists(const T& path)  {
  return fs::exists(path);
}

inline std::string join_path(const fs::path& parent, const fs::path& child) {
  return (parent / child).string();
}
#endif

template <typename T>
std::string vectorToString(const std::vector<T> &vec)
{
    std::stringstream ss;

    ss << "[";
    for (const auto &elem : vec)
    {
        ss << elem << ", ";
    }
    ss << "]";

    return ss.str();
}

//template<typename T>
//std::vector<T> flatten(const T& input) {
//    return {input};
//}
//
//template<typename T>
//std::vector<T> flatten(const std::vector<T>& input) {
//    std::vector<T> flattened;
//    for (const auto& elem : input) {
//        auto flattened_elem = flatten(elem);
//        flattened.insert(flattened.end(), flattened_elem.begin(), flattened_elem.end());
//    }
//    return flattened;
//}

class TimeUtils {
public:
    // 获取毫秒级时间戳
    static long long getTimestampMs() {
        std::chrono::time_point<std::chrono::system_clock> now = std::chrono::system_clock::now();
        std::chrono::milliseconds duration = std::chrono::duration_cast<std::chrono::milliseconds>(
                now.time_since_epoch());
        return duration.count();
    }

    // 获取秒级时间戳
    static long long getTimestampSec() {
        std::chrono::time_point<std::chrono::system_clock> now = std::chrono::system_clock::now();
        std::chrono::seconds duration = std::chrono::duration_cast<std::chrono::seconds>(
                now.time_since_epoch());
        return duration.count();
    }

    // 获取毫秒级时间戳的字符串类型
    static std::string getTimestampMsStr() {
        return std::to_string(getTimestampMs());
    }

    // 获取秒级时间戳的字符串类型
    static std::string getTimestampSecStr() {
        return std::to_string(getTimestampSec());
    }
};

} // namespace vitals

