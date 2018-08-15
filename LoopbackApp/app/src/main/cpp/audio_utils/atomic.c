/*
 * Copyright (C) 2015 The Android Open Source Project
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

#include "atomic.h"

#include <stdatomic.h>
#include <stdbool.h>

int32_t android_atomic_acquire_load(volatile const int32_t* addr) {
    volatile atomic_int_least32_t* a = (volatile atomic_int_least32_t*) addr;
    return atomic_load_explicit(a, memory_order_acquire);
}

void android_atomic_release_store(int32_t value, volatile int32_t* addr) {
    volatile atomic_int_least32_t* a = (volatile atomic_int_least32_t*) addr;
    atomic_store_explicit(a, value, memory_order_release);
}

int32_t android_atomic_exchange(int32_t value, volatile const int32_t* addr) {
    volatile atomic_int_least32_t* a = (volatile atomic_int_least32_t*) addr;
    return atomic_exchange(a, value);
}

bool android_atomic_compare_exchange(int32_t* expect, int32_t desire,
        volatile const int32_t* addr) {
    volatile atomic_int_least32_t* a = (volatile atomic_int_least32_t*) addr;
    return atomic_compare_exchange_weak(a, expect, desire);
}
