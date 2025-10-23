#pragma once

#if defined __cplusplus && __cplusplus >= 201703L
    #define CXX_17 1
#else
    #define CXX_17 0
#endif

#if CXX_17
#include <optional>
#endif

#include "opencv2/core.hpp"


// ===
inline float Sigmoid(float value) { return 1.0f / (1.0f + std::exp(-value)); }
// ===
struct Anchor
{
  float x_center;
  float y_center;
  float w;
  float h;
};

std::vector<Anchor> GenerateAnchors();

// ===
struct RotatedRect {
  float x_center;
  float y_center;
  float width;
  float height;
  float rotation; // radian
};

typedef RotatedRect NormalizedRect;

#if CXX_17
void image_to_tensor(
    const cv::Mat& input,
    const std::optional<NormalizedRect>& norm_rect,
    const int tensor_width, const int tensor_height,
    float range_min, float range_max,
    float* tensor_data,
    bool keep_aspect_ratio = true,
    std::array<float, 4>* output_padding = nullptr
);
#else
void image_to_tensor(
    const cv::Mat& input,
    const NormalizedRect* norm_rect,
    const int tensor_width, const int tensor_height,
    float range_min, float range_max,
    float* tensor_data,
    bool keep_aspect_ratio = true,
    std::array<float, 4>* output_padding = nullptr
);
#endif

// ===
struct Box {
  float x_center;
  float y_center;
  float w;
  float h;
  float l;
  float r;
  float t;
  float b;
};

struct Keypoint {
  float x;
  float y;

  Keypoint(float x, float y): x(x), y(y) {}
};

struct Detection {
  Box box;
  std::vector<Keypoint> keypoints;
  float score;
};

typedef Detection NormalizedDetection;

Box create_box_from_center_xywh(float x_center, float y_center,
                                float w, float h);
Box create_box_from_ltrb(float l, float t, float r, float b);

std::vector<NormalizedDetection> decode_tensors_to_detections(
    float* raw_box_tensor_data, float* raw_score_tensor_data,
    const std::vector<Anchor>& anchors,
    const std::array<float, 4>& padding
);

//===
NormalizedRect face_detection_to_roi(const Detection& detection,
                                     int image_width, int image_height
);

struct Landmark {
  float x;
  float y;
  // float z;

  Landmark(float x, float y): x(x), y(y) {}
};

typedef Landmark NormalizedLandmark;
typedef std::vector<std::vector<NormalizedLandmark>> MultiFaceLandmarks;

// std::vector<NormalizedLandmark> tensors_to_landmarks(
//     const float* landmark_tensor_data, 
//     const size_t num_landmarks, const size_t num_dimensions,
//     const int input_image_width, const int input_image_height
// );

std::vector<NormalizedLandmark> decode_tensors_to_landmarks(
    const float* landmark_tensor_data, 
    const size_t num_landmarks, const size_t num_dimensions,
    const int input_image_width, const int input_image_height,
    const NormalizedRect* roi
);
