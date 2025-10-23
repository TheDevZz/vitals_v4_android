#pragma once

#include "core.h"

class Inspector {
public:
  std::string current_batch_tag;
  virtual void update_transformed_image(const cv::Mat& transformed) {};
  virtual void stage_transformed_image(const std::string& tag) {};
};

void setup_inspector(Inspector* inspector);
Inspector* get_inspector();
void remove_inspector();
