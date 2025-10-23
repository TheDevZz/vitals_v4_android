#pragma once

#include "tensorflow/lite/c/c_api.h"

#include "opencv2/core.hpp"
#include "opencv2/imgproc.hpp"

#include "../lib/core.h"
#include "../lib/inspect.h"

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
        TfLiteInterpreterOptionsSetNumThreads(options, 2);

        face_detection_model = TfLiteModelCreateFromFile(face_detection_model_path);
        // Create the interpreter.
        face_detection = TfLiteInterpreterCreate(face_detection_model, options);
        // Allocate tensors and populate the input tensor data.
        TfLiteInterpreterAllocateTensors(face_detection);

        face_landmarker_model = TfLiteModelCreateFromFile(face_landmarker_model_path);
        // Create the interpreter.
        face_landmarker = TfLiteInterpreterCreate(face_landmarker_model, options);
        // Allocate tensors and populate the input tensor data.
        TfLiteInterpreterAllocateTensors(face_landmarker);

        TfLiteInterpreterOptionsDelete(options);
    }

    std::vector<Detection> invoke_face_detect(const cv::Mat& img) {
      std::array<float, 4> padding;
      TfLiteTensor* input_tensor =
              TfLiteInterpreterGetInputTensor(face_detection, 0);
      auto input_tensor_data = (float*)TfLiteTensorData(input_tensor);
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
      TfLiteInterpreterInvoke(face_detection);

      // Extract the output tensor data.
      const TfLiteTensor* output_tensor_location =
              TfLiteInterpreterGetOutputTensor(face_detection, 0);
      const TfLiteTensor* output_tensor_score =
              TfLiteInterpreterGetOutputTensor(face_detection, 1);

      std::vector<Detection> detections =
          decode_tensors_to_detections(
              (float*)TfLiteTensorData(output_tensor_location),
              (float*)TfLiteTensorData(output_tensor_score),
              ssd_anchors,
              padding
          );
      return detections;
    }

    std::vector<NormalizedLandmark> invoke_face_landmark(
        const cv::Mat& img, const NormalizedRect* roi
    ) {
      TfLiteTensor* input_tensor =
              TfLiteInterpreterGetInputTensor(face_landmarker, 0);
      auto input_tensor_data = (float*)TfLiteTensorData(input_tensor);
      image_to_tensor(img,
#if CXX_17
                      roi ? std::make_optional(*roi) : std::nullopt,
#else
                      roi,
#endif
                      192, 192, 0.0f, 1.0f,
                      input_tensor_data,
                      false, nullptr);
      TfLiteInterpreterInvoke(face_landmarker);

      const TfLiteTensor* output_tensor_landmark =
              TfLiteInterpreterGetOutputTensor(face_landmarker, 0);
      const TfLiteTensor* output_tensor_score =
              TfLiteInterpreterGetOutputTensor(face_landmarker, 1);

      float score = ((float*)TfLiteTensorData(output_tensor_score))[0];
      score = Sigmoid(score);
      if (score > 0.5f) {
        float* landmark_tensor_data = (float*)TfLiteTensorData(output_tensor_landmark);
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
        TfLiteInterpreterDelete(face_detection);
        TfLiteModelDelete(face_detection_model);

        TfLiteInterpreterDelete(face_landmarker);
        TfLiteModelDelete(face_landmarker_model);
    }
};