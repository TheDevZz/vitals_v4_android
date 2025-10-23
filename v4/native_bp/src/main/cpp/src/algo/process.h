#ifndef VITALS_PROCESS_H
#define VITALS_PROCESS_H

#include "signal_processing.hpp"
//#include "bp_prediction.hpp"
#include "measure_vital.hpp"

namespace vitals {

using namespace std;

struct PixelsResult {
    vector<vector<double>> pixels;
    vector<size_t> shape;
};

//measure::MeasureResult processPixelsV2(double* p_rfcPixels, const std::vector<size_t>& shape, double fps);
measure::MeasureResult processPixelsV2(const double* p, const int* p_shape, double fps, const string& models_dir, int age, int gender, double height, double weight);

} // namespace vitals


#endif //VITALS_PROCESS_H

/*

frame roi channel

roi channel frame

frc -> rcf
fc -> cf

[ frame[ roi[ r, g, b ]... ]... ]
[ roi[ r[ frame... ], g[ frame... ], b[ frame... ] ]... ]

[
  [
    [
      r, g, b
    ] // roi
  ] // frame
]

[
  [
    [] // r
    [] // g
    [] // b
  ] // roi
]

*/