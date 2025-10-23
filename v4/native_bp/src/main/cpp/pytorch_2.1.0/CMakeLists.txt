set(PYTORCH_DIR ${CMAKE_CURRENT_SOURCE_DIR})

set(PYTORCH_INCLUDE_DIRS "${PYTORCH_DIR}/headers")
set(PYTORCH_LINK_DIRS "${PYTORCH_DIR}/jni/${ANDROID_ABI}")
set(PYTORCH_LIBRARY_NAME "pytorch_jni")

if(NOT EXISTS ${PYTORCH_LINK_DIRS})
    message(FATAL_ERROR "PyTorch library directory not found: ${PYTORCH_LINK_DIRS}")
endif()

message("Configuring PyTorch Lite for Android ABI: ${ANDROID_ABI}")

find_library(PYTORCH_LIBRARY ${PYTORCH_LIBRARY_NAME}
        PATHS ${PYTORCH_LINK_DIRS}
        NO_CMAKE_FIND_ROOT_PATH)
find_library(FBJNI_LIBRARY fbjni
        PATHS ${PYTORCH_LINK_DIRS}
        NO_CMAKE_FIND_ROOT_PATH)

if (PYTORCH_LIBRARY)
    message("Found PyTorch library: ${PYTORCH_LIBRARY}")
else()
    message(FATAL_ERROR "Could not find PyTorch library: ${PYTORCH_LIBRARY_NAME} in ${PYTORCH_LINK_DIRS}")
endif ()

add_library(pytorch INTERFACE)
target_link_libraries(pytorch INTERFACE ${PYTORCH_LIBRARY} ${FBJNI_LIBRARY})
target_include_directories(pytorch INTERFACE ${PYTORCH_INCLUDE_DIRS})

# 提供一个仅链接的 target，不将 so 打包进 aar
add_library(pytorch_link_only SHARED IMPORTED GLOBAL)
set_target_properties(pytorch_link_only PROPERTIES
        IMPORTED_LOCATION ${PYTORCH_LIBRARY}
        INTERFACE_INCLUDE_DIRECTORIES ${PYTORCH_INCLUDE_DIRS}
        IMPORTED_NO_SONAME ON # 利用此属性来使so不打包进aar包
)

