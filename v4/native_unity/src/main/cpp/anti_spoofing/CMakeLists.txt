cmake_minimum_required(VERSION 3.4.1)

# ncnn 已在主 CMakeLists.txt 中通过 add_subdirectory 引入，这里不需要添加ncnn的依赖

# OpenCV 已在主 CMakeLists.txt 中通过 find_package 引入，这里不需要添加opencv的依赖

file(GLOB jni_srcs
        "*.cpp"
        "detection/*.cpp"
        "live/*.cpp"
)

add_library(engine STATIC ${jni_srcs})

target_link_libraries(engine log jnigraphics ncnn opencv_core opencv_imgproc opencv_imgcodecs android)

file(GLOB ENGINE_JNI_INTERFACE_SOURCE
        "*_jni.cpp"
)

# 将变量提升到父作用域，使主 CMakeLists.txt 可以访问
set(ENGINE_JNI_INTERFACE_SOURCE ${ENGINE_JNI_INTERFACE_SOURCE} PARENT_SCOPE)
