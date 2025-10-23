#pragma once

#include <iostream>
#include <chrono>

namespace vitals {

class Timer {
public:
  Timer(std::string functionName);
  ~Timer();

private:
  std::string functionName;
  std::chrono::high_resolution_clock::time_point start_time;
};

} // namespace vitals
