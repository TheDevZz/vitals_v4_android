#pragma once

#ifndef VITALS_STRUCT_H
#define VITALS_STRUCT_H

#include <iostream>
#include <iomanip>
#include <sstream>
namespace vitals {

namespace measure {

struct BaseFeature {
    int age = 0;
    int gender = 0; // 0: female, 1: male
    double height = 0; // m
    double weight = 0; // kg
    double bmi = 0;
    bool smoke = false;

    // 默认构造函数
    BaseFeature() = default;

    // 传入age, gender, height, weight的构造函数，自动计算bmi
    BaseFeature(int age, int gender, double height, double weight)
            : age(age), gender(gender), height(height), weight(weight) {
        updateBMI();
    }

    // 传入age, gender, height, weight, smoke的构造函数，自动计算bmi
    BaseFeature(int age, int gender, double height, double weight, bool smoke)
            : age(age), gender(gender), height(height), weight(weight), smoke(smoke) {
        updateBMI();
    }

    friend std::ostream& operator<<(std::ostream& os, const BaseFeature& feature) {
        os << "Age: " << feature.age
           << ", Gender: " << (feature.gender == 0 ? "Female(0)" : "Male(1)")
           << ", Height: " << feature.height
           << ", Weight: " << feature.weight
           << ", BMI: " << feature.bmi
           << ", Smoke: " << (feature.smoke ? "Yes" : "No");
        return os;
    }

private:
    // 计算并更新bmi
    void updateBMI() {
        if (height > 0) {
            bmi = weight / (height * height);
        } else {
            bmi = 0;
        }
    }
};

} // namespace measure

} // namespace vitels
#endif //VITALS_STRUCT_H
