#include "test_helper.hpp"

namespace vitals {

std::unique_ptr<TestHelper> TestHelperInstance = std::make_unique<TestHelper>();

// TestHelper fackTestHelper = TestHelper();
// TestHelper& TestHelperInstance = fackTestHelper;

// void TestHelperImp::update_sig(const Eigen::VectorXd& sig) {
//   update_sig(EigenVectorToStdVector(sig));
// };

// void TestHelperImp::update_sig(const std::vector<double>& sig) {
//   this->sig = sig;
// };

// void TestHelperImp::update_fft(const Eigen::VectorXcd& fft_data, int frame_cnt, double fps) {
//   auto fsize = fft_data.size();
//   std::vector<double> abs_fft_data(fsize);
//   std::vector<int> ind(fsize);
//   std::vector<double> freq(fsize);
//   std::vector<double> hr_preds(fsize);
//   for (int i = 0; i < fsize; ++i) {
//     auto amplitude = std::abs(fft_data[i]);
//     double hr_pred = (double)i / frame_cnt * fps * 60;
//     ind[i] = i;
//     freq[i] = (double)i / frame_cnt * fps;
//     abs_fft_data[i] = amplitude;
//     hr_preds[i] = hr_pred;
//   }
//   this->oval_pos_0 = abs_fft_data;
//   this->ind = ind;
//   this->freq = freq;
//   this->hr_pred = hr_preds;
// }

} // namespace vitals