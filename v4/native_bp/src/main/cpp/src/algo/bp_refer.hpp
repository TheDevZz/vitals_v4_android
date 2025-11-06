#pragma once

#include <string>
#include <vector>
#include <tuple>
#include <cmath>
#include <sstream>

#include "rapidcsv.h"

namespace vitals {

  namespace measure {

    class BPrefer1 {
      // 1: 'male', 0 : 'female'
      std::vector<std::vector<std::vector<double>>> group_gender_map = {
        //        /* 0 */    /* 1 */
        /* 1  */ {{110, 70}, {115, 73}},
        /* 2  */ {{110, 71}, {115, 73}},
        /* 3  */ {{112, 73}, {115, 75}},
        /* 4  */ {{114, 74}, {117, 76}},
        /* 5  */ {{116, 76}, {120, 80}},
        /* 6  */ {{122, 78}, {124, 81}},
        /* 7  */ {{128, 79}, {128, 82}},
        /* 8  */ {{134, 80}, {134, 84}},
        /* 9  */ {{139, 82}, {137, 84}},
        /* 10 */ {{145, 83}, {148, 86}},
      };
      /**
       * python code:
       * self.group_gender_map = {
       *   1:  {1: [115, 73], 0: [110, 70]},
       *   2:  {1: [115, 73], 0: [110, 71]},
       *   3:  {1: [115, 75], 0: [112, 73]},
       *   4:  {1: [117, 76], 0: [114, 74]},
       *   5:  {1: [120, 80], 0: [116, 76]},
       *   6:  {1: [124, 81], 0: [122, 78]},
       *   7:  {1: [128, 82], 0: [128, 79]},
       *   8:  {1: [134, 84], 0: [134, 80]},
       *   9:  {1: [137, 84], 0: [139, 82]},
       *   10: {1: [148, 86], 0: [145, 83]},
       * }
       * map类型改为vector，age_group的key需要减一得到索引值
       * female的值为0，所以其数据得放在索引为0的位置
       */

    public:
      int get_age_group(int age) {
        int group;
        if (age <= 20) {
          group = 1;
        } else if (age >= 61) {
          group = 10;
        } else {
          group = (int) std::ceil((age - 20) / 5.0) + 1;
        }
        return group;
      }

      /*
        gender: 0: female, 1: male
        return: tuple: (hbp, lbp)
      */
      std::tuple<double, double> get_refer_bp(int age, int gender) {
        int age_group = get_age_group(age);
        int age_group_index = age_group - 1;
        std::vector<double> bp = group_gender_map[age_group_index][gender];
        return std::make_tuple(bp[0], bp[1]);
      }
    };

    class BPrefer2 {
      // rapidcsv::Document doc;

      struct GroupItem {
        std::string group;
        double bmi;
        double hbp;
        double lbp;

        GroupItem(std::string group, double bmi, double hbp, double lbp)
          : group(std::move(group)), bmi(bmi), hbp(hbp), lbp(lbp) {};
      };

      std::vector<GroupItem> group_map;

    public:
      BPrefer2() {
        std::string csv_content = R"(group,bmi,hbp,lbp
male,19,130.4,78.08
male,20,131.2,78.71
male,21,132.23,79.41
male,22,133.6,80.3
male,23,134.8,81.19
male,24,136.0,82.16
male,25,137.57,83.36
male,26,139.37,84.51
male,27,140.8,85.64
male,28,142.17,86.67
male,29,143.47,87.56
male,30,144.35,88.5
male,31,145.2,89.27
male,32,146.27,89.96
male,33,147.13,90.72
male,34,147.8,91.34
male,35,148.35,91.78
female,19,127.53,75.02
female,20,128.4,75.64
female,21,129.57,76.42
female,22,131.4,77.43
female,23,133.4,78.36
female,24,135.15,79.34
female,25,136.9,80.41
female,26,138.9,81.3
female,27,140.35,82.09
female,28,141.63,82.88
female,29,143.03,83.61
female,30,144.15,84.39
female,31,145.2,85.03
female,32,146.27,85.61
female,33,147.13,86.27
female,34,147.8,86.83
female,35,148.25,87.22
35-50,19,120.23,74.45
35-50,20,121.35,75.05
35-50,21,122.67,75.82
35-50,22,124.43,76.9
35-50,23,126.37,77.79
35-50,24,128.0,78.82
35-50,25,129.6,80.86
35-50,26,131.53,81.55
35-50,27,133.1,82.82
35-50,28,134.5,84.61
35-50,29,136.1,85.34
35-50,30,137.35,85.96
35-50,31,138.53,86.63
35-50,32,139.87,87.46
35-50,33,141.0,88.92
35-50,34,142.03,89.73
35-50,35,143.05,90.16
51-60,19,128.1,77.19
51-60,20,129.5,77.9
51-60,21,130.87,78.62
51-60,22,132.6,79.95
51-60,23,134.4,80.86
51-60,24,135.95,81.77
51-60,25,137.57,82.8
51-60,26,139.4,83.68
51-60,27,140.8,84.56
51-60,28,142.1,85.4
51-60,29,143.57,86.14
51-60,30,144.7,86.9
51-60,31,145.7,87.48
51-60,32,146.8,87.98
51-60,33,147.6,88.5
51-60,34,148.3,88.88
51-60,35,149.0,89.15
61-70,19,134.7,77.19
61-70,20,135.85,77.9
61-70,21,137.4,78.62
61-70,22,139.3,79.42
61-70,23,140.35,80.12
61-70,24,141.8,80.88
61-70,25,143.6,81.75
61-70,26,145.03,82.49
61-70,27,146.35,83.16
61-70,28,147.5,83.77
61-70,29,148.7,84.36
61-70,30,149.6,84.94
61-70,31,150.47,85.42
61-70,32,151.33,85.81
61-70,33,151.95,86.22
61-70,34,152.6,86.55
61-70,35,153.1,86.78
71-80,19,138.77,76.05
71-80,20,140.3,76.72
71-80,21,141.93,77.44
71-80,22,143.53,78.22
71-80,23,144.67,78.62
71-80,24,145.6,79.2
71-80,25,146.83,80.03
71-80,26,148.63,80.61
71-80,27,149.8,81.15
71-80,28,150.63,81.72
71-80,29,151.6,82.17
71-80,30,152.25,82.62
71-80,31,152.9,83.03
71-80,32,153.7,83.44
71-80,33,154.2,83.91
71-80,34,154.83,84.35
71-80,35,155.85,84.71)";

        std::stringstream stream(csv_content);
        rapidcsv::Document doc(stream, rapidcsv::LabelParams(0, -1));
        // this->doc = doc;
        std::vector<std::string> groups = doc.GetColumn<std::string>("group");
        std::vector<double> bmis = doc.GetColumn<double>("bmi");
        std::vector<double> hbps = doc.GetColumn<double>("hbp");
        std::vector<double> lbps = doc.GetColumn<double>("lbp");
        size_t size = groups.size();
        for (int i = 0; i < size; ++i) {
          group_map.emplace_back(groups[i], bmis[i], hbps[i], lbps[i]);
        }
      }

      static double calc_bmi(double height, double weight) {
        return weight / height / height;
      }

      double get_bmi_group(double height, double weight) {
        return get_bmi_group(calc_bmi(height, weight));
      }

      double get_bmi_group(double bmi) {
        bmi = std::round(bmi);
        bmi = std::max(19.0, bmi);
        bmi = std::min(35.0, bmi);
        return bmi;
      }

      std::tuple<double, double> get_refer_bp_bmi_gender(int gender, double bmi) {
        bmi = get_bmi_group(bmi);
        std::string gender_group = gender == 1 ? "male" : "female";

        double hbp = 0, lbp = 0;
        for (const auto &it: group_map) {
          if (it.group == gender_group && it.bmi == bmi) {
            hbp = it.hbp;
            lbp = it.lbp;
            break;
          }
        }
        return std::make_tuple(hbp, lbp);
      }

      std::tuple<double, double> get_refer_bp_bmi_gender(int gender, double height, double weight) {
        return get_refer_bp_bmi_gender(gender, calc_bmi(height, weight));
      }

      std::tuple<double, double> get_refer_bp_bmi_age(int age, double bmi) {
        bmi = get_bmi_group(bmi);

        std::string age_group;
        if (age <= 50) {
          age_group = "35-50";
        } else if (age <= 60) {
          age_group = "51-60";
        } else if (age <= 70) {
          age_group = "61-70";
        } else {
          age_group = "71-80";
        }

        double hbp = 0, lbp = 0;
        for (const auto &it: group_map) {
          if (it.group == age_group && it.bmi == bmi) {
            hbp = it.hbp;
            lbp = it.lbp;
            break;
          }
        }
        return std::make_tuple(hbp, lbp);
      }

      std::tuple<double, double> get_refer_bp_bmi_age(int age, double height, double weight) {
        return get_refer_bp_bmi_age(age, calc_bmi(height, weight));
      }
    };

  } // namespace measure

} // namespace vitels