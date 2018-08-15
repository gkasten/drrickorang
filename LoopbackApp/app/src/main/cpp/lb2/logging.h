/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef LB2_LOGGING_H_
#define LB2_LOGGING_H_

#ifndef LOG_TAG
#define LOG_TAG "lb2"
#endif

#include <android/log.h>
#define ALOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define ALOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define ALOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define ALOGF(...) __android_log_assert("assert", LOG_TAG, __VA_ARGS__)

#include <android/trace.h>
#define PASTE(x, y) x ## y
#define ATRACE_NAME(name) ScopedTrace PASTE(___tracer, __LINE__) (name)
#define ATRACE_CALL() ATRACE_NAME(__func__)

struct ScopedTrace {
    ScopedTrace(const char* name) {
#if __ANDROID_API__ >= 23
        ATrace_beginSection(name);
#else
        (void)name;
#endif
    }
    ScopedTrace(const ScopedTrace&) = delete;
    ScopedTrace& operator=(const ScopedTrace&) = delete;
    ~ScopedTrace() {
#if __ANDROID_API__ >= 23
        ATrace_endSection();
#endif
    }
};

#endif  // LB2_LOGGING_H_
