#include <iostream>
#include <chrono>
#include <utility>

#include "../log.hpp"

#include "performance.hpp"

namespace vitals {

Timer::Timer(std::string functionName)
  : functionName(std::move(functionName)), start_time(std::chrono::high_resolution_clock::now()) {
}

Timer::~Timer() {
  auto end_time = std::chrono::high_resolution_clock::now();
  auto duration = std::chrono::duration_cast<std::chrono::microseconds>(end_time - start_time);

#ifdef __ANDROID__
  LOGD(" /////////////// %s 运行耗时: %f.2  毫秒", functionName.c_str(), (double)duration.count() / 1000);
#else
  std::cout << " /////////////// " << functionName << " 运行耗时: " << (double)duration.count() / 1000 << " 毫秒" << std::endl;
#endif
}


} // namespace vitals
