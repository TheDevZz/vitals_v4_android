cmake_minimum_required(VERSION 3.18.1)

# 设置路径
get_filename_component(TFLITE_ROOT_DIR ${CMAKE_CURRENT_SOURCE_DIR} ABSOLUTE)
set(TFLITE_HEADERS_DIR ${TFLITE_ROOT_DIR}/headers)
set(TFLITE_LIBS_DIR ${TFLITE_ROOT_DIR}/libs)
message("TensorFlow Lite root directory: ${TFLITE_ROOT_DIR}")

# 检查头文件目录
if(NOT EXISTS ${TFLITE_HEADERS_DIR})
    message(FATAL_ERROR "TensorFlow Lite headers directory not found: ${TFLITE_HEADERS_DIR}")
endif()

message("Configuring TensorFlow Lite for Android ABI: ${ANDROID_ABI}")

# =============================================================================
# TensorFlow Lite C API
# =============================================================================
set(TFLITE_C_LIB_PATH ${TFLITE_LIBS_DIR}/${ANDROID_ABI}/libtensorflowlite_c.so)

if(EXISTS ${TFLITE_C_LIB_PATH})
    # 指定 ABI 对应的 so 路径
#    find_library(TFLITE_LIBRARY tensorflowlite_c
#            PATHS ${TFLITE_LIBS_DIR}/${ANDROID_ABI}
#            NO_CMAKE_FIND_ROOT_PATH
#    )
#    # 定义一个 INTERFACE target 来封装
#    add_library(tensorflowlite_c INTERFACE)
#
#    # 链接真实的 so 文件
#    target_link_libraries(tensorflowlite_c INTERFACE ${TFLITE_LIBRARY})
#
#    # 自动传播头文件目录
#    target_include_directories(tensorflowlite_c INTERFACE
#            ${TFLITE_HEADERS_DIR}
#    )

    add_library(tensorflowlite_c SHARED IMPORTED GLOBAL)
    set_target_properties(tensorflowlite_c PROPERTIES
            IMPORTED_LOCATION ${TFLITE_C_LIB_PATH}
            INTERFACE_INCLUDE_DIRECTORIES ${TFLITE_HEADERS_DIR}
    )

    add_library(TensorFlowLite::C ALIAS tensorflowlite_c)
    message("  ✓ TensorFlow Lite C API: ${TFLITE_C_LIB_PATH}")
else()
    message("  ✗ TensorFlow Lite C API not found: ${TFLITE_C_LIB_PATH}")
endif()

# =============================================================================
# 导出信息
# =============================================================================
message("TensorFlow Lite configuration completed.")
