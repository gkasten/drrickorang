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

#ifndef LB2_UTIL_H_
#define LB2_UTIL_H_

// TODO: move all to audio utilities

constexpr int MS_PER_SECOND = 1000;

// Assuming the arguments to be positive numbers, returns
// a value 'm' such that 'part' * 'm' >= 'whole'.
inline int wholeMultiplier(int whole, int part) {
    // part * ((whole - 1) / part + 1) = whole - 1 + part >= whole, if part > 0
    return (whole - 1) / part + 1;
}

#endif  // LB2_UTIL_H_
