LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE      := libloopback
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES   := \
	sles.cpp \
	jni_sles.c \
	audio_utils/atomic.c \
	audio_utils/fifo.c \
	audio_utils/roundup.c
LOCAL_C_INCLUDES := \
        frameworks/wilhelm/include

LOCAL_SHARED_LIBRARIES := \
	libutils \
	libcutils \
	libOpenSLES \
	libnbaio \
	liblog
LOCAL_PRELINK_MODULE := false

LOCAL_LDFLAGS := -Wl,--hash-style=sysv
LOCAL_CFLAGS := -DSTDC_HEADERS

include $(BUILD_SHARED_LIBRARY)
