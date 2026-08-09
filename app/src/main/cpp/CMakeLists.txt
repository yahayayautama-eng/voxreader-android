cmake_minimum_required(VERSION 3.22.1)
project(voxleaf_kokoro)

add_library(kokoro_bridge SHARED kokoro_bridge.cpp)

add_library(babylon SHARED IMPORTED)
set_target_properties(babylon PROPERTIES IMPORTED_LOCATION
    "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libbabylon.so")

target_include_directories(kokoro_bridge PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/third_party/babylon/include"
    "${CMAKE_CURRENT_SOURCE_DIR}/third_party/babylon/submodules/onnxruntime/include/onnxruntime/core/session")

target_link_libraries(kokoro_bridge babylon log)
