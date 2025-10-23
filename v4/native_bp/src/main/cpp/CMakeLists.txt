cmake_minimum_required(VERSION 3.22)

project("vitals_native_lite")

set(CMAKE_CXX_STANDARD 17)

# MARK: - Build Options

option(USE_PYTORCH_LINK_ONLY
    "Use PyTorch link only target, exclude pytorch_jni and fbjni so libraries in AAR"
    OFF
)
message("USE_PYTORCH_LINK_ONLY: ${USE_PYTORCH_LINK_ONLY}")

# MARK: - Import Dependencies

add_subdirectory(eigen)
add_subdirectory(pytorch_2.1.0)

# MARK: - Build Configuration

if (NOT DEFINED PROJECT_BUILD_TYPE)
    set(PROJECT_BUILD_TYPE "release")
endif ()
message("PROJECT_BUILD_TYPE: ${PROJECT_BUILD_TYPE}")

# 定义一个函数，传入 target 名称
function(set_target_options target)
    if (TARGET ${target})  # 确认这个 target 已经被 add_library / add_executable 定义
        if (NOT ${PROJECT_BUILD_TYPE} STREQUAL "debug")
            target_compile_options(${target} PRIVATE
                    -fvisibility=hidden
                    -fvisibility-inlines-hidden
                    -fexceptions
            )
            target_link_options(${target} PRIVATE
                    -Wl,--exclude-libs,ALL
                    -Wl,--strip-all
            )
        endif ()
    endif ()
endfunction()

# MARK: - Build Targets

add_library("vitals_bp" SHARED
        src/algo/bp_jni.cpp
        src/algo/bp_prediction.cpp

        src/algo/measure_vital.cpp
        src/algo/performance.cpp
        src/algo/signal_processing.cpp
        src/algo/test_helper.cpp
        src/algo/utils.cpp
)

target_link_libraries("vitals_bp"
        Eigen3::Eigen
        z
        android
        log
        pytorch
)

add_definitions(-DENABLE_BP)

set_target_options("vitals_bp")
