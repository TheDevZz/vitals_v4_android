cmake_minimum_required(VERSION 3.22)

project("vitals_native_lite")

set(CMAKE_CXX_STANDARD 17)

# MARK: - Build Options

option(BUILD_UNITY_SHARED "build unity shared lib. (default target)" ON)
option(ENABLE_BP "enable bp" OFF)
option(USE_PYTORCH_LINK_ONLY
    "Use PyTorch link only target, exclude pytorch_jni and fbjni so libraries in AAR"
    OFF
)
message("BUILD_UNITY_SHARED: ${BUILD_UNITY_SHARED}")
message("ENABLE_BP: ${ENABLE_BP}")
message("USE_PYTORCH_LINK_ONLY: ${USE_PYTORCH_LINK_ONLY}")

# MARK: - Import Dependencies

add_subdirectory(eigen)

add_subdirectory(tensorflowlite_c)

add_subdirectory(pytorch_2.1.0)

set(OpenCV_DIR opencv_4.8.0/sdk/native/jni)
set(OpenCV_SHARED OFF)
set(ANDROID_ARM_NEON ON)
find_package(OpenCV REQUIRED)

add_subdirectory(ncnn)

# 需要先引入opencv和ncnn后再引入anti_spoofing，因为其依赖opencv和ncnn
add_subdirectory(anti_spoofing)

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

set(ALGO_SOURCES
        # algo
        src/algo/port_jni.cpp
        src/algo/process.cpp
        src/algo/measure_vital.cpp
        src/algo/performance.cpp
        src/algo/signal_processing.cpp
        src/algo/test_helper.cpp
        src/algo/utils.cpp
        ${ENGINE_JNI_INTERFACE_SOURCE}
)

set(ALGO_LIBS
        Eigen3::Eigen
        opencv_imgproc
        z
        android
        log
        jnigraphics
        engine
)

if (ENABLE_BP)
    add_definitions(-DENABLE_BP)
    list(APPEND ALGO_SOURCES
            src/algo/bp_prediction.cpp
    )
    if (USE_PYTORCH_LINK_ONLY)
        list(APPEND ALGO_LIBS pytorch_link_only)
    else ()
        list(APPEND ALGO_LIBS pytorch)
    endif ()
endif ()

set(FACE_DEBUG_SOURCES)
set(FACE_DEBUG_LIBS)
if (${PROJECT_BUILD_TYPE} STREQUAL "test")
    set(FACE_DEBUG_SOURCES
            src/face/inspect_jni.cpp
    )
    set(FACE_DEBUG_LIBS
            opencv_imgcodecs
    )
endif ()

set(FACE_SOURCES
        # face
        src/face/face_landmarker_jni.cpp
        src/face/jni_utils.cpp
        ${FACE_DEBUG_SOURCES}
        # lib
        src/lib/core.cc
        src/lib/inspect.cc
)

set(FACE_LIBS
        ${FACE_DEBUG_LIBS}
        tensorflowlite_c
        opencv_imgproc
        z
        android
        log
        jnigraphics
)


if (BUILD_ALGO_SHARED)
    set(TARGET_ALGO "vitals_algo")
    add_library(${TARGET_ALGO} SHARED
            ${ALGO_SOURCES}
    )
    target_link_libraries(${TARGET_ALGO} PRIVATE
            ${ALGO_LIBS}
    )
    set_target_options(${TARGET_ALGO})
endif ()

if (BUILD_FACE_SHARED)
    set(TARGET_FACE "vitals_face")
    add_library(${TARGET_FACE} SHARED
            ${FACE_SOURCES}
    )
    target_link_libraries(${TARGET_FACE} PRIVATE
            ${FACE_LIBS}
    )
    set_target_options(${TARGET_FACE})
endif ()

if (BUILD_UNITY_SHARED)
    set(TARGET_UNITY "vitals_unity")
    add_library(${TARGET_UNITY} SHARED
            ${FACE_SOURCES}
            ${ALGO_SOURCES}
    )
    target_link_libraries(${TARGET_UNITY} PRIVATE
            ${FACE_LIBS}
            ${ALGO_LIBS}
            c++_shared
    )
    set_target_options(${TARGET_UNITY})
endif ()
