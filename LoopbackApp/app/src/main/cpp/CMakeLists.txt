#
# Copyright (C) The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

cmake_minimum_required(VERSION 3.4.1)
project(loopback LANGUAGES C CXX)

# set up common compile options
add_library(${PROJECT_NAME} SHARED
    sles.cpp
    byte_buffer.c
    jni_native.c
    loopback.c
    audio_utils/atomic.c
    audio_utils/fifo.c
    audio_utils/roundup.c
    lb2/loopback_test.cpp
    lb2/sound_system_echo.cpp
    lb2/test_context.cpp
    lb2/loopback2.cpp
    lb2/sound_system_aaudio.cpp
    lb2/oboe/src/aaudio/AAudioLoader.cpp )

target_include_directories(${PROJECT_NAME} PRIVATE ${CMAKE_SOURCE_DIR} frameworks/wilhelm/include)

set_target_properties(${PROJECT_NAME}
  PROPERTIES
    CXX_STANDARD 11
    CXX_STANDARD_REQUIRED YES
    CXX_EXTENSIONS NO
)

target_compile_options(${PROJECT_NAME} PRIVATE -fexceptions -Wall)
#target_compile_options(${PROJECT_NAME} -DSTDC_HEADERS)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -std=c11")

# set(CMAKE_SHARED_LINKER_FLAGS, "${CMAKE_SHARED_LINKER_FLAGS} --hash-style=sysv")
# Export ANativeActivity_onCreate(),
# Refer to: https://github.com/android-ndk/ndk/issues/381.
# set_target_properties(${PROJECT_NAME}
#   PROPERTIES  LINK_FLAGS "-u ANativeActivity_onCreate")

# add lib dependencies
target_link_libraries(${PROJECT_NAME} PRIVATE  OpenSLES dl log android)
