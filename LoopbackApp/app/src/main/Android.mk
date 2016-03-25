LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests

LOCAL_SRC_FILES := $(call all-subdir-java-files)

LOCAL_JNI_SHARED_LIBRARIES := libloopback

LOCAL_PACKAGE_NAME := Loopback

LOCAL_CERTIFICATE := platform

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res \
    frameworks/support/v7/appcompat/res

LOCAL_AAPT_FLAGS := --auto-add-overlay \
    --extra-packages android.support.v4

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-support-v4

include $(BUILD_PACKAGE)

include $(call all-makefiles-under,$(LOCAL_PATH))
