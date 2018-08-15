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

#ifndef LB2_SAMPLE_H_
#define LB2_SAMPLE_H_

#include <cmath>
#include <limits>

using sample_t = int16_t;  // For flexibility. May change to a float type if needed.

static_assert(std::is_integral<sample_t>::value,
        "FULL_SAMPLE_SCALE assumes sample values are of integer type");
// FIXME: Would we plan to use floats, the maximum value will be 1.0.
constexpr double FULL_SAMPLE_SCALE = std::numeric_limits<sample_t>::max() + 1;

inline double convertSampleType(sample_t s) {
    static_assert(std::numeric_limits<sample_t>::is_signed, "sample value is assumed to be signed");
    return s / FULL_SAMPLE_SCALE;
}

inline sample_t convertSampleType(double d) {
    return std::trunc(d * FULL_SAMPLE_SCALE);
}

#endif  // LB2_SAMPLE_H_
