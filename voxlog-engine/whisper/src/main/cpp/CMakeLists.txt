# Builds whisper.cpp (pinned git submodule) from source together with VoxLog's JNI bridge.
# Everything is compiled from source so F-Droid can reproduce the native libraries.
cmake_minimum_required(VERSION 3.22)
project(voxlog_whisper LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Deterministic, portable build: no host CPU detection, no optional backends, static libs.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_ALL_WARNINGS OFF CACHE BOOL "" FORCE)

add_subdirectory(whisper.cpp)

add_library(voxlog_whisper SHARED voxlog_whisper_jni.cpp)
target_link_libraries(voxlog_whisper PRIVATE whisper android log)
target_compile_options(voxlog_whisper PRIVATE -Wall -Wextra -Werror -ffile-prefix-map=${CMAKE_SOURCE_DIR}=.)

if(NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_compile_options(voxlog_whisper PRIVATE -O3 -fvisibility=hidden)
    target_link_options(voxlog_whisper PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL -Wl,--build-id=none)
endif()
