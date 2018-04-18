LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE      := libloopback
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES   := \
    sles.cpp \
    byte_buffer.c \
    jni_native.c \
    loopback.c \
    audio_utils/atomic.c \
    audio_utils/fifo.c \
    audio_utils/roundup.c \
    lb2/loopback_test.cpp \
    lb2/sound_system_echo.cpp \
    lb2/test_context.cpp \
    lb2/loopback2.cpp \
    lb2/sound_system_aaudio.cpp \
    lb2/oboe/src/aaudio/AAudioLoader.cpp
LOCAL_C_INCLUDES := \
    frameworks/wilhelm/include

LOCAL_SHARED_LIBRARIES := \
    libOpenSLES \
    liblog \
    libandroid \
    libaaudio

LOCAL_LDLIBS += -lOpenSLES -llog -landroid
#LOCAL_PRELINK_MODULE := false

#LOCAL_LDFLAGS += -Wl,--hash-style=sysv
#LOCAL_CFLAGS := -DSTDC_HEADERS
LOCAL_CONLYFLAGS := -std=c11

include $(BUILD_SHARED_LIBRARY)
