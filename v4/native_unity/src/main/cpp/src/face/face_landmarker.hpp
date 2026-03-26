#pragma once

#include "tensorflow/lite/c/c_api.h"

#include "opencv2/core.hpp"
#include "opencv2/imgproc.hpp"

#include "../lib/core.h"
#include "../lib/inspect.h"
#include "log.hpp"

class FaceLandmarker {

private:
    TfLiteModel* face_detection_model = nullptr;
    TfLiteModel* face_landmarker_model = nullptr;
    TfLiteInterpreter* face_detection = nullptr;
    TfLiteInterpreter* face_landmarker = nullptr;

    const std::vector<Anchor> ssd_anchors = GenerateAnchors();

public:
    int num_faces = 1;

    FaceLandmarker(
            const char* face_detection_model_path,
            const char* face_landmarker_model_path
    ) {
        TfLiteInterpreterOptions* options = TfLiteInterpreterOptionsCreate();
        if (options == nullptr) {
            LOGE("FaceLandmarker: Failed to create interpreter options");
            return;
        }
        TfLiteInterpreterOptionsSetNumThreads(options, 2);

        face_detection_model = TfLiteModelCreateFromFile(face_detection_model_path);
        if (face_detection_model == nullptr) {
            LOGE("FaceLandmarker: Failed to load face detection model from %s", face_detection_model_path);
            TfLiteInterpreterOptionsDelete(options);
            return;
        }

        // Create the interpreter.
        face_detection = TfLiteInterpreterCreate(face_detection_model, options);
        if (face_detection == nullptr) {
            LOGE("FaceLandmarker: Failed to create face detection interpreter");
            TfLiteInterpreterOptionsDelete(options);
            return;
        }

        // Allocate tensors and populate the input tensor data.
        if (TfLiteInterpreterAllocateTensors(face_detection) != kTfLiteOk) {
            LOGE("FaceLandmarker: Failed to allocate tensors for face detection");
        }

        face_landmarker_model = TfLiteModelCreateFromFile(face_landmarker_model_path);
        if (face_landmarker_model == nullptr) {
            LOGE("FaceLandmarker: Failed to load face landmarker model from %s", face_landmarker_model_path);
            TfLiteInterpreterOptionsDelete(options);
            return;
        }

        // Create the interpreter.
        face_landmarker = TfLiteInterpreterCreate(face_landmarker_model, options);
        if (face_landmarker == nullptr) {
            LOGE("FaceLandmarker: Failed to create face landmarker interpreter");
            TfLiteInterpreterOptionsDelete(options);
            return;
        }

        // Allocate tensors and populate the input tensor data.
        if (TfLiteInterpreterAllocateTensors(face_landmarker) != kTfLiteOk) {
            LOGE("FaceLandmarker: Failed to allocate tensors for face landmarker");
        }

        TfLiteInterpreterOptionsDelete(options);
    }

    std::vector<Detection> invoke_face_detect(const cv::Mat& img) {
      if (face_detection == nullptr) {
          // LOGE("FaceLandmarker: face_detection interpreter is null");
          return std::vector<Detection>();
      }

      std::array<float, 4> padding;
      TfLiteTensor* input_tensor =
              TfLiteInterpreterGetInputTensor(face_detection, 0);
      if (input_tensor == nullptr) {
          // LOGE("FaceLandmarker: Failed to get face detection input tensor");
          return std::vector<Detection>();
      }

      auto input_tensor_data = (float*)TfLiteTensorData(input_tensor);
      if (input_tensor_data == nullptr) {
          // LOGE("FaceLandmarker: Failed to get face detection input tensor data");
          return std::vector<Detection>();
      }

#if CXX_17
      auto nullopt = std::nullopt;
#else
      auto nullopt = nullptr;
#endif
      image_to_tensor(img, nullopt,
                      128, 128, -1.0f, 1.0f,
                      input_tensor_data,
                      true, &padding);
      // Execute inference.
      if (TfLiteInterpreterInvoke(face_detection) != kTfLiteOk) {
          // LOGE("FaceLandmarker: Failed to invoke face detection");
          return std::vector<Detection>();
      }

      // Extract the output tensor data.
      const TfLiteTensor* output_tensor_location =
              TfLiteInterpreterGetOutputTensor(face_detection, 0);
      const TfLiteTensor* output_tensor_score =
              TfLiteInterpreterGetOutputTensor(face_detection, 1);

      if (output_tensor_location == nullptr || output_tensor_score == nullptr) {
          // LOGE("FaceLandmarker: Failed to get face detection output tensors");
          return std::vector<Detection>();
      }

      float* location_data = (float*)TfLiteTensorData(output_tensor_location);
      float* score_data = (float*)TfLiteTensorData(output_tensor_score);

      if (location_data == nullptr || score_data == nullptr) {
          // LOGE("FaceLandmarker: Failed to get face detection output tensor data");
          return std::vector<Detection>();
      }

      std::vector<Detection> detections =
          decode_tensors_to_detections(
              location_data,
              score_data,
              ssd_anchors,
              padding
          );
      return detections;
    }

    std::vector<NormalizedLandmark> invoke_face_landmark(
        const cv::Mat& img, const NormalizedRect* roi
    ) {
      if (face_landmarker == nullptr) {
          // LOGE("FaceLandmarker: face_landmarker interpreter is null");
          return std::vector<NormalizedLandmark>();
      }

      TfLiteTensor* input_tensor =
              TfLiteInterpreterGetInputTensor(face_landmarker, 0);
      if (input_tensor == nullptr) {
          // LOGE("FaceLandmarker: Failed to get face landmarker input tensor");
          return std::vector<NormalizedLandmark>();
      }

      auto input_tensor_data = (float*)TfLiteTensorData(input_tensor);
      if (input_tensor_data == nullptr) {
          // LOGE("FaceLandmarker: Failed to get face landmarker input tensor data");
          return std::vector<NormalizedLandmark>();
      }

      image_to_tensor(img,
#if CXX_17
                      roi ? std::make_optional(*roi) : std::nullopt,
#else
                      roi,
#endif
                      192, 192, 0.0f, 1.0f,
                      input_tensor_data,
                      false, nullptr);

      if (TfLiteInterpreterInvoke(face_landmarker) != kTfLiteOk) {
          // LOGE("FaceLandmarker: Failed to invoke face landmarker");
          return std::vector<NormalizedLandmark>();
      }

      const TfLiteTensor* output_tensor_landmark =
              TfLiteInterpreterGetOutputTensor(face_landmarker, 0);
      const TfLiteTensor* output_tensor_score =
              TfLiteInterpreterGetOutputTensor(face_landmarker, 1);

      if (output_tensor_landmark == nullptr || output_tensor_score == nullptr) {
          // LOGE("FaceLandmarker: Failed to get face landmarker output tensors");
          return std::vector<NormalizedLandmark>();
      }

      float* score_ptr = (float*)TfLiteTensorData(output_tensor_score);
      if (score_ptr == nullptr) {
          // LOGE("FaceLandmarker: Failed to get face landmarker score data");
          return std::vector<NormalizedLandmark>();
      }

      float score = score_ptr[0];
      score = Sigmoid(score);
      if (score > 0.5f) {
        float* landmark_tensor_data = (float*)TfLiteTensorData(output_tensor_landmark);
        if (landmark_tensor_data == nullptr) {
            // LOGE("FaceLandmarker: Failed to get face landmarker landmark data");
            return std::vector<NormalizedLandmark>();
        }
        std::vector<NormalizedLandmark> landmarks =
            decode_tensors_to_landmarks(
                landmark_tensor_data,
                468, 3, 192, 192,
                roi
            );
        return landmarks;
      } else {
        return std::vector<NormalizedLandmark>();
      }
    }

    MultiFaceLandmarks detect(const cv::Mat& img) {
      std::vector<Detection> detections = invoke_face_detect(img);
      int img_w = img.cols;
      int img_h = img.rows;

      MultiFaceLandmarks multi_face_landmarks;
      int id = 0;
      int size = std::min(num_faces, (int) detections.size());
      for (int i = 0; i < size; ++i) {
        const auto& detection = detections[i];
        NormalizedRect roi = face_detection_to_roi(detection, img_w, img_h);
        std::vector<NormalizedLandmark> landmarks =
            invoke_face_landmark(img, &roi);
        get_inspector()->stage_transformed_image(
          "roi_" + get_inspector()->current_batch_tag + "_" + std::to_string(id++));
        multi_face_landmarks.push_back(std::move(landmarks));
      }
      return multi_face_landmarks;
    }

//     void detect(const char* bytes, const int width, const int height) {
//         cv::Mat orig(height, width, CV_8UC3, (void*) bytes);
//     }

    ~FaceLandmarker() {
        // Dispose of the model and interpreter objects.
        if (face_detection) TfLiteInterpreterDelete(face_detection);
        if (face_detection_model) TfLiteModelDelete(face_detection_model);

        if (face_landmarker) TfLiteInterpreterDelete(face_landmarker);
        if (face_landmarker_model) TfLiteModelDelete(face_landmarker_model);
    }
};
