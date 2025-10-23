#include <cmath>
#include <utility>
#include <vector>
#include <numeric>

#include "opencv2/core.hpp"
#include <opencv2/imgproc.hpp>

#include "core.h"
#include "inspect.h"

#ifndef M_PI
    #define M_PI    3.14159265358979323846
#endif

// ssd_anchors_calculator [START]
// <MOVE TO HEADER>
// struct Anchor
// {
//   float x_center;
//   float y_center;
//   float w;
//   float h;
// };
// </MOVE TO HEADER>

float CalculateScale(float min_scale, float max_scale, int stride_index,
                     int num_strides) {
  if (num_strides == 1) {
    return (min_scale + max_scale) * 0.5f;
  } else {
    return min_scale +
           (max_scale - min_scale) * 1.0 * stride_index / (num_strides - 1.0f);
  }
}

std::vector<Anchor> GenerateAnchors() {
  const size_t strides_size = 4;
  const int strides[strides_size] = { 8, 16, 16, 16 };
  const int num_layers = 4;

  const float min_scale = 0.1484375f;
  const float max_scale = 0.75f;

  const size_t options_aspect_ratios_size = 1;
  const float options_aspect_ratios[options_aspect_ratios_size] = { 1.0f };
  const float interpolated_scale_aspect_ratio = 1.0f;

  const int input_size_width = 128;
  const int input_size_height = 128;

  const float anchor_offset_x = 0.5f;
  const float anchor_offset_y = 0.5f;

  const bool fixed_anchor_size = true;

  std::vector<Anchor> anchors;

  int layer_id = 0;
  while (layer_id < num_layers) {
    std::vector<float> anchor_height;
    std::vector<float> anchor_width;
    std::vector<float> aspect_ratios;
    std::vector<float> scales;

    int last_same_stride_layer = layer_id;
    while (last_same_stride_layer < strides_size 
           && strides[last_same_stride_layer] == strides[layer_id]
    ){
      float scale = CalculateScale(min_scale, max_scale, 
                                   last_same_stride_layer + 1, strides_size);
      for (float aspect_ratio : options_aspect_ratios) {
        aspect_ratios.push_back(aspect_ratio);
        scales.push_back(scale);
      }
      if (interpolated_scale_aspect_ratio > 0.0f) {
        const float scale_next = 
          last_same_stride_layer ==  strides_size - 1
            ? 1.0f
            : CalculateScale(min_scale, max_scale,
                             last_same_stride_layer + 1,
                             strides_size);
        scales.push_back(std::sqrt(scale * scale_next));
        aspect_ratios.push_back(interpolated_scale_aspect_ratio);
      }
      last_same_stride_layer++;
    }

    for (int i = 0; i < aspect_ratios.size(); ++i) {
      const float ratio_sqrts = std::sqrt(aspect_ratios[i]);
      anchor_height.push_back(scales[i] / ratio_sqrts);
      anchor_width.push_back(scales[i] * ratio_sqrts);
    }

    const int stride = strides[layer_id];
    const float stride_f = static_cast<float>(stride);
    int feature_map_height = std::ceil(input_size_height / stride_f);
    int feature_map_width = std::ceil(input_size_width / stride_f);

    for (int y = 0; y < feature_map_height; ++y) {
      for (int x = 0; x < feature_map_width; ++x) {
        for (int anchor_id = 0; anchor_id < anchor_height.size(); ++anchor_id) {
          const float x_center =
              (x + anchor_offset_x) * 1.0f / feature_map_width;
          const float y_center =
              (y + anchor_offset_y) * 1.0f / feature_map_height;

          Anchor anchor;
          anchor.x_center = x_center;
          anchor.y_center = y_center;

          if (fixed_anchor_size) {
            anchor.w = 1.0f;
            anchor.h = 1.0f;
          } else {
            anchor.w = anchor_width[anchor_id];
            anchor.h = anchor_height[anchor_id];
          }
          anchors.push_back(anchor);
        }
      }
    }
    layer_id = last_same_stride_layer;
  }
  return anchors;
}

// ssd_anchors_calculator [END]

// image_to_tensor_calculator [START]

// <MOVE TO HEADER>
// struct RotatedRect {
//   float x_center;
//   float y_center;
//   float width;
//   float height;
//   float rotation; // radian
// };

// typedef RotatedRect NormalizedRect;
// </MOVE TO HEADER>

#if CXX_17
RotatedRect GetRoi(int input_width, int input_height,
                   const std::optional<NormalizedRect>& norm_rect) {
  if (norm_rect) {
    return {/*center_x=*/norm_rect->x_center * input_width,
            /*center_y =*/norm_rect->y_center * input_height,
            /*width =*/norm_rect->width * input_width,
            /*height =*/norm_rect->height * input_height,
            /*rotation =*/norm_rect->rotation};
  } else {
    return {/*center_x=*/0.5f * input_width,
            /*center_y =*/0.5f * input_height,
            /*width =*/static_cast<float>(input_width),
            /*height =*/static_cast<float>(input_height),
            /*rotation =*/0};
    }
}
#else
RotatedRect GetRoi(int input_width, int input_height,
                   const NormalizedRect* norm_rect
                   ) {
    if (norm_rect) {
        return {/*center_x=*/norm_rect->x_center * input_width,
                /*center_y =*/norm_rect->y_center * input_height,
                /*width =*/norm_rect->width * input_width,
                /*height =*/norm_rect->height * input_height,
                /*rotation =*/norm_rect->rotation};
    } else {
        return {/*center_x=*/0.5f * input_width,
                /*center_y =*/0.5f * input_height,
                /*width =*/static_cast<float>(input_width),
                /*height =*/static_cast<float>(input_height),
                /*rotation =*/0};
    }
}
#endif

std::array<float, 4> PadRoi(int input_tensor_width,
                            int input_tensor_height,
                            bool keep_aspect_ratio,
                            RotatedRect* roi) {
  if (!keep_aspect_ratio) {
    return std::array<float, 4>{0.0f, 0.0f, 0.0f, 0.0f};
  }
  // RET_CHECK(input_tensor_width > 0 && input_tensor_height > 0)
  //   << "Input tensor width and height must be > 0.";
  // RET_CHECK(roi->width > 0 && roi->height > 0)
  //     << "ROI width and height must be > 0.";

  const float tensor_aspect_ratio =
      static_cast<float>(input_tensor_height) / input_tensor_width;
  const float roi_aspect_ratio = roi->height / roi->width;

  float vertical_padding = 0.0f;
  float horizontal_padding = 0.0f;
  float new_width;
  float new_height;
  if (tensor_aspect_ratio > roi_aspect_ratio) {
    new_width = roi->width;
    new_height = roi->width * tensor_aspect_ratio;
    vertical_padding = (1.0f - roi_aspect_ratio / tensor_aspect_ratio) / 2.0f;
  } else {
    new_width = roi->height / tensor_aspect_ratio;
    new_height = roi->height;
    horizontal_padding = (1.0f - tensor_aspect_ratio / roi_aspect_ratio) / 2.0f;
  }

  roi->width = new_width;
  roi->height = new_height;

  return std::array<float, 4>{horizontal_padding, vertical_padding,
                              horizontal_padding, vertical_padding};
}

cv::Mat convert_image(const cv::Mat& input, const RotatedRect& roi, 
                      const int output_width, const int output_height) {
  const cv::RotatedRect rotated_rect(cv::Point2f(roi.x_center, roi.y_center),
                                     cv::Size2f(roi.width, roi.height),
                                     roi.rotation * 180.f / M_PI);
  cv::Mat src_points;
  cv::boxPoints(rotated_rect, src_points);

  const float dst_width = output_width;
  const float dst_height = output_height;
  float dst_corners[8] = {0.0f,      dst_height,
                          0.0f,      0.0f,
                          dst_width, 0.0f,
                          dst_width, dst_height};
  cv::Mat dst_points = cv::Mat(4, 2, CV_32F, dst_corners);
  
  cv::Mat projection_matrix =
      cv::getPerspectiveTransform(src_points, dst_points);
  cv::Mat transformed;
  cv::warpPerspective(input, transformed, projection_matrix,
                      cv::Size(dst_width, dst_height),
                      /*flags=*/cv::INTER_LINEAR,
                      /*borderMode=*/cv::BORDER_CONSTANT);
  return transformed;
}

struct ValueTransformation {
  float scale;
  float offset;
};

ValueTransformation GetValueRangeTransformation(
    float from_range_min, float from_range_max,
    float to_range_min, float to_range_max
) {
  // RET_CHECK_LT(from_range_min, from_range_max)
  //     << "Invalid FROM range: min >= max.";
  // RET_CHECK_LT(to_range_min, to_range_max) << "Invalid TO range: min >= max.";
  const float scale =
      (to_range_max - to_range_min) / (from_range_max - from_range_min);
  const float offset = to_range_min - from_range_min * scale;
  return ValueTransformation{scale, offset};
}

void convert_image_to_tensor(
    const cv::Mat& input, const RotatedRect& roi,
    const int tensor_width, const int tensor_height,
    float range_min, float range_max,
    float* dst_buffer
) {
  cv::Mat transformed = convert_image(input, roi, tensor_width, tensor_height);
  get_inspector()->update_transformed_image(transformed);
  cv::Mat dst = cv::Mat(tensor_height, tensor_width, CV_32FC3, dst_buffer);
  ValueTransformation transform = 
      GetValueRangeTransformation(0.0f, 255.0f, range_min, range_max);
  transformed.convertTo(dst, CV_32FC3, transform.scale, transform.offset);
}


#if CXX_17
void image_to_tensor(
    const cv::Mat& input,
    const std::optional<NormalizedRect>& norm_rect,
    const int tensor_width, const int tensor_height,
    float range_min, float range_max,
    float* tensor_data,
    bool keep_aspect_ratio,
    std::array<float, 4>* output_padding
) {
  auto img_w = input.cols;
  auto img_h = input.rows;
  RotatedRect roi = GetRoi(img_w, img_h, norm_rect);
  std::array<float, 4> padding = PadRoi(tensor_width, tensor_height,
                                        keep_aspect_ratio, &roi);
  convert_image_to_tensor(input, roi,
                          tensor_width, tensor_height,
                          range_min, range_max,
                          tensor_data);
  if (output_padding != nullptr) {
    *output_padding = std::move(padding);
  }
}
#else
void image_to_tensor(
        const cv::Mat& input,
        const NormalizedRect* norm_rect,
        const int tensor_width, const int tensor_height,
        float range_min, float range_max,
        float* tensor_data,
        bool keep_aspect_ratio,
        std::array<float, 4>* output_padding
) {
    auto img_w = input.cols;
    auto img_h = input.rows;
    RotatedRect roi = GetRoi(img_w, img_h, norm_rect);
    std::array<float, 4> padding = PadRoi(tensor_width, tensor_height,
                                          keep_aspect_ratio, &roi);
    convert_image_to_tensor(input, roi,
                            tensor_width, tensor_height,
                            range_min, range_max,
                            tensor_data);
    if (output_padding != nullptr) {
        *output_padding = std::move(padding);
    }
}
#endif
// image_to_tensor_calculator [END]

// tensors_to_detections [START]
// tensors_to_detections_calculator [START]

// <MOVE TO HEADER>
// struct Box {
//   float x_center;
//   float y_center;
//   float w;
//   float h;
//   float l;
//   float r;
//   float t;
//   float b;
// };

// struct Keypoint {
//   float x;
//   float y;

//   Keypoint(float x, float y): x(x), y(y) {}
// };

// struct Detection {
//   Box box;
//   std::vector<Keypoint> keypoints;
//   float score;
// };
// </MOVE TO HEADER>

Box create_box_from_center_xywh(float x_center, float y_center,
                                float w, float h) {
  Box box;
  box.x_center = x_center;
  box.y_center = y_center;
  box.w = w;
  box.h = h;
  box.l = x_center - w/2.0f;
  box.r = x_center + w/2.0f;
  box.t = y_center - h/2.0f;
  box.b = y_center + h/2.0f;
  return box;
}

Box create_box_from_ltrb(float l, float t, float r, float b) {
  Box box;
  box.l = l;
  box.r = r;
  box.t = t;
  box.b = b;
  box.w = r - l;
  box.h = b - t;
  box.x_center = (r + l)/2.0f;
  box.y_center = (b + t)/2.0f;
  return box;
}

struct TensorToDetectionsOptions {
  size_t num_boxes;
  size_t num_coords;
  size_t num_keypoints;
  size_t num_values_per_keypoint;
  int box_coord_offset;
  int keypoint_coord_offset;

  float x_scale;
  float y_scale;
  float w_scale;
  float h_scale;

  float min_score_thresh;
};

const size_t face_detection_num_boxes = 896;
const size_t face_detection_num_coords = 16;
const size_t face_detection_num_keypoints = 2; // 实际有六个，但只需要前两个
const size_t face_detection_num_values_per_keypoint = 2;
const int face_detection_box_coord_offset = 0;
const int face_detection_keypoint_coord_offset = 4;

TensorToDetectionsOptions get_face_detections_options() {
  TensorToDetectionsOptions options;
  options.num_boxes = 896;
  options.num_coords = 16;
  options.num_keypoints = 2; // 实际有六个，但只需要前两个
  options.num_values_per_keypoint = 2;
  options.box_coord_offset = 0;
  options.keypoint_coord_offset = 4;
  options.x_scale = 128.0f;
  options.y_scale = 128.0f;
  options.w_scale = 128.0f;
  options.h_scale = 128.0f;
  options.min_score_thresh = 0.5f;
  return options;
}

Detection decode_box(const float* raw_box_data, const Anchor& anchor,
                     const TensorToDetectionsOptions& options
) {
  float x_center = raw_box_data[0];
  float y_center = raw_box_data[1];
  float w = raw_box_data[2];
  float h = raw_box_data[3];

  x_center = x_center / options.x_scale * anchor.w + anchor.x_center;
  y_center = y_center / options.y_scale * anchor.h + anchor.y_center;

  h = h / options.h_scale * anchor.h;
  w = w / options.w_scale * anchor.w;

  Detection detection;
  detection.box = create_box_from_center_xywh(x_center, y_center, w, h);

  for (int k = 0; k < options.num_keypoints; ++k) {
    const int offset = options.keypoint_coord_offset + 
                       k * options.num_values_per_keypoint;
    float keypoint_x = raw_box_data[offset];
    float keypoint_y = raw_box_data[offset + 1];
    keypoint_x = keypoint_x / options.x_scale * anchor.w + anchor.x_center;
    keypoint_y = keypoint_y / options.y_scale * anchor.h + anchor.y_center;
    detection.keypoints.emplace_back(keypoint_x, keypoint_y);
    // detection.keypoints.push_back(Keypoint{keypoint_x, keypoint_y});
  }
  return detection;
}

std::vector<Detection> tensors_to_detections(
    float* raw_box_tensor_data, float* raw_score_tensor_data,
    const std::vector<Anchor>& anchors,
    const TensorToDetectionsOptions& options
) {
  float* raw_boxes = raw_box_tensor_data;
  float* raw_scores = raw_score_tensor_data;

  // const bool sigmoid_score = true;
  // const int score_clipping_thresh = 100;
  std::vector<int> filter_inds;
  float orig_score_thresh = -std::log(1.0f / options.min_score_thresh - 1.0f);
  for (int i = 0; i < options.num_boxes; ++i) {
    if (raw_scores[i] >= orig_score_thresh) {
      filter_inds.push_back(i);
    }
  }
  std::vector<Detection> filter_detections;
  filter_detections.reserve(filter_inds.size());
  for (const int& ind : filter_inds) {
    float score = raw_scores[ind];
    float sigmoid_score = 1.0f / (1.0f + std::exp(-score));

    float* raw_box_data = raw_boxes + ind * options.num_coords + options.box_coord_offset;
    Detection detection = decode_box(raw_box_data, anchors[ind], options);
    detection.score = sigmoid_score;
    filter_detections.emplace_back(detection);
  }

  return filter_detections;
}

float OverlapSimilarity(const Box& box1, const Box& box2) {
  float l = std::max(box1.l, box2.l);
  float t = std::max(box1.t, box2.t);
  float r = std::min(box1.r, box2.r);
  float b = std::min(box1.b, box2.b);
  if (l > r || t > b) {
    return 0.0f;
  }
  float intersection_area = (r - l) * (b - t);
  float area1 = box1.w * box1.h;
  float area2 = box2.w * box2.h;
  float normalization = area1 + area2 - intersection_area;
  if (normalization > 0.0f) {
    return intersection_area / normalization;
  } else {
    return 0.0f;
  }
}

std::vector<Detection> WeightedNonMaxSuppression(
    const std::vector<Detection>& input_detections,
    float min_suppression_threshold
) {
  std::vector<Detection> output_detections;
  if (input_detections.empty()) {
    return output_detections;
  }

  std::vector<int> inds(input_detections.size());
  std::iota(inds.begin(), inds.end(), 0); // 用 std::iota 初始化索引向量
  std::sort(inds.begin(), inds.end(), [&input_detections](const int a, const int b) {
      return input_detections[a].score > input_detections[b].score;
  });

  std::vector<int> remained_inds = std::move(inds);

  std::vector<int> remained;
  std::vector<int> candidates;
  while (!remained_inds.empty()) {
    const size_t original_inds_size = remained_inds.size();
    const auto& detection = input_detections[remained_inds[0]];
    remained.clear();
    candidates.clear();
    for (const int& ind : remained_inds) {
      const auto& rest_detection = input_detections[ind];
      float similarity = OverlapSimilarity(rest_detection.box, detection.box);
      if (similarity > min_suppression_threshold) {
        candidates.push_back(ind);
      } else {
        remained.push_back(ind);
      }
    }
    auto weigthed_detection = detection;
    if (!candidates.empty()) {
      const int num_keypoints = detection.keypoints.size();
      std::vector<float> keypoints(num_keypoints * 2);
      float w_xmin = 0.0f;
      float w_ymin = 0.0f;
      float w_xmax = 0.0f;
      float w_ymax = 0.0f;
      float total_score = 0.0f;
      for (const auto& candidate_ind : candidates) {
        const auto& candidate = input_detections[candidate_ind];
        const auto& bbox = candidate.box;
        const auto& score = candidate.score;
        total_score += score;
        w_xmin += bbox.l * score;
        w_ymin += bbox.t * score;
        w_xmax += bbox.r * score;
        w_ymax += bbox.b * score;

        for (int i = 0; i < num_keypoints; ++i) {
          keypoints[i * 2] +=  candidate.keypoints[i].x * score;
          keypoints[i * 2 + 1] +=  candidate.keypoints[i].y * score;
        } 
      }
      w_xmin /= total_score;
      w_ymin /= total_score;
      w_xmax /= total_score;
      w_ymax /= total_score;
      weigthed_detection.box = create_box_from_ltrb(w_xmin, w_ymin, w_xmax, w_ymax);
      for (int i = 0; i < num_keypoints; ++i) {
        weigthed_detection.keypoints[i].x = keypoints[i * 2] / total_score;
        weigthed_detection.keypoints[i].y = keypoints[i * 2 + 1] / total_score;
      }
    }
    output_detections.push_back(weigthed_detection);
    if (original_inds_size == remained.size()) {
      break;
    } else {
      remained_inds = std::move(remained);
    }
  }
  return output_detections;
}
// tensors_to_detections_calculator [END]

// detection_projection [START]
std::vector<Detection> sample_detection_projection(
    const std::vector<Detection>& detections,
    const float norm_horizontal_padding,
    const float norm_vertical_padding
    // const std::array<float, 16>& project_mat
    // const float x_scale, const float y_scale,
    // const float x_offset, const float y_offset
) {
  std::vector<Detection> output_detections;
  for (const auto& detection : detections) {
    Detection output_detection;
    float new_norm_width = 1.0f - 2.0f * norm_horizontal_padding;
    float new_norm_height = 1.0f - 2.0f * norm_vertical_padding;

    float nx_center = detection.box.x_center - norm_horizontal_padding;
    float ny_center = detection.box.y_center - norm_vertical_padding;
    nx_center /= new_norm_width;
    ny_center /= new_norm_height;

    float nw = detection.box.w / new_norm_width;
    float nh = detection.box.h / new_norm_height;

    output_detection.box = create_box_from_center_xywh(nx_center, ny_center, nw, nh);

    for (const auto& keypoint : detection.keypoints) {
      float x = (keypoint.x - norm_horizontal_padding) / new_norm_width;
      float y = (keypoint.y - norm_vertical_padding) / new_norm_height;
      output_detection.keypoints.emplace_back(x, y);
    }

    output_detections.push_back(std::move(output_detection));
  }
  return output_detections;
}
// detection_projection [END]

std::vector<NormalizedDetection> decode_tensors_to_detections(
    float* raw_box_tensor_data, float* raw_score_tensor_data,
    const std::vector<Anchor>& anchors,
    const std::array<float, 4>& padding
) {
  TensorToDetectionsOptions options = get_face_detections_options();
  std::vector<Detection> filter_detections = 
      tensors_to_detections(raw_box_tensor_data, raw_score_tensor_data,
                            anchors, options);
  std::vector<Detection> suppressed_detections = 
      WeightedNonMaxSuppression(filter_detections, 0.3f);
  std::vector<Detection> output_detections = 
      sample_detection_projection(suppressed_detections,
                                  padding[0], padding[1]);
  return output_detections;
}
// tensors_to_detections [END]

// FaceDetectionFrontDetectionToRoi [START]
// detections_to_rects_calculator [START]
void DetectionToNormalizedRect(const Detection& detection, 
                               NormalizedRect* rect) {
  const Box& norm_box = detection.box;
  rect->x_center = norm_box.x_center;
  rect->y_center = norm_box.y_center;
  rect->width = norm_box.w;
  rect->height = norm_box.h;
}

static inline float NormalizeRadians(float angle) {
  return angle - 2 * M_PI * std::floor((angle - (-M_PI)) / (2 * M_PI));
}

float ComputeRotation(
    const Keypoint& norm_start_keypoint,
    const Keypoint& norm_end_keypoint,
    const int img_w, const int img_h,
    const float target_angle = 0.0f
) {
  float x0 = norm_start_keypoint.x * img_w;
  float y0 = norm_start_keypoint.y * img_h;
  float x1 = norm_end_keypoint.x * img_w;
  float y1 = norm_end_keypoint.y * img_h;
  float rotation = NormalizeRadians(target_angle - std::atan2(-(y1 - y0), x1 - x0));
  return rotation;
}

NormalizedRect detection_to_rect(
   const Detection& detection,
   const int img_w, const int img_h
) {
  NormalizedRect rect;
  DetectionToNormalizedRect(detection, &rect);
  const auto& keypoint_left_eye = detection.keypoints[0];
  const auto& keypoint_right_eye = detection.keypoints[1];
  rect.rotation = ComputeRotation(keypoint_left_eye, keypoint_right_eye,
                                  img_w, img_h);
  return rect;
}
// detections_to_rects_calculator [END]

// rect_transformation_calculator [START]
void TransformNormalizedRect(NormalizedRect* rect,
                             int image_width, int image_height,
                             float scale_x, float scale_y
) {
  // bool square_long = true;
  float width = rect->width;
  float height = rect->height;
  const float long_side =
      std::max(width * image_width, height * image_height);
  width = long_side / image_width;
  height = long_side / image_height;
  rect->width = width * scale_x;
  rect->height = height * scale_y;
}

void rect_transformation(NormalizedRect* rect,
                         int image_width, int image_height) {
  TransformNormalizedRect(rect, image_width, image_height, 1.5, 1.5);
}
// rect_transformation_calculator [END]

NormalizedRect face_detection_to_roi(const Detection& detection,
                                     int image_width, int image_height
) {
  NormalizedRect rect = detection_to_rect(detection, image_width, image_height);
  rect_transformation(&rect, image_width, image_height);
  return rect;
}
// FaceDetectionFrontDetectionToRoi [END]

// tensors_to_landmarks [START]
// tensors_to_landmarks_calculator [START]
std::vector<NormalizedLandmark> tensors_to_landmarks(
    const float* landmark_tensor_data, 
    const size_t num_landmarks, const size_t num_dimensions,
    const int input_image_width, const int input_image_height
) {
  const float* raw_landmarks = landmark_tensor_data;
  std::vector<NormalizedLandmark> landmarks;
  for (int ld = 0; ld < num_landmarks; ++ld) {
    const int offset = ld * num_dimensions;
    float x = raw_landmarks[offset];
    float y = raw_landmarks[offset + 1];
    x /= input_image_width;
    y /= input_image_height;
    landmarks.emplace_back(x, y);
  }
  return landmarks;
}
// tensors_to_landmarks_calculator [END]

// landmark_projection_calculator [START]
void project_landmark(
    const NormalizedLandmark& landmark,
    NormalizedLandmark* new_landmark,
    const NormalizedRect& norm_rect
) {
  float x = landmark.x - 0.5f;
  float y = landmark.y - 0.5f;
  float angle = norm_rect.rotation;
  float new_x = std::cos(angle) * x - std::sin(angle) * y;
  float new_y = std::sin(angle) * x + std::cos(angle) * y;

  new_x = new_x * norm_rect.width + norm_rect.x_center;
  new_y = new_y * norm_rect.height + norm_rect.y_center;

  new_landmark->x = new_x;
  new_landmark->y = new_y;
}

void landmark_projection(std::vector<NormalizedLandmark>* landmarks,
                         const NormalizedRect& norm_rect
) {
  for (auto& landmark : *landmarks) {
    project_landmark(landmark, &landmark, norm_rect);
  }
}
// landmark_projection_calculator [END]

std::vector<NormalizedLandmark> decode_tensors_to_landmarks(
    const float* landmark_tensor_data, 
    const size_t num_landmarks, const size_t num_dimensions,
    const int input_image_width, const int input_image_height,
    const NormalizedRect* roi
) {
  std::vector<NormalizedLandmark> landmarks =
      tensors_to_landmarks(landmark_tensor_data,
                           num_landmarks, num_dimensions,
                           input_image_width, input_image_height);
  if (roi != nullptr) {
    landmark_projection(&landmarks, *roi);
  }
  return landmarks;
}
// tensors_to_landmarks [END]