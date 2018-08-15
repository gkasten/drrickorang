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

#include "lb2/test_context.h"

#include <math.h>
#include <cmath>

AudioBufferView<sample_t> GlitchTestContext::getNextImpulse(size_t frameCount) {
    constexpr double TWO_PI = 2.0 * M_PI;
    auto sineBuffer = mSineBuffer.getView(0, frameCount);
    for (size_t i = 0; i < sineBuffer.getFrameCount(); ++i) {
        sample_t s = convertSampleType(std::sin(mPhaseRad) * SIGNAL_AMPLITUDE);
        sample_t *d = sineBuffer.getFrameAt(i);
        for (int j = 0; j < getChannelCount(); ++j) {
            *d++ = s;
        }

        mPhaseRad += TWO_PI * mPhaseIncrementPerFrame;
        while (mPhaseRad > TWO_PI) mPhaseRad -= TWO_PI;
    }
    return sineBuffer;
}
