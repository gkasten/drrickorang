LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := samples

# Only compile source java files in this apk.
LOCAL_SRC_FILES := $(call all-java-files-under, app/src/main/java)

LOCAL_SHARED_LIBRARIES := libloopback
LOCAL_JNI_SHARED_LIBRARIES := libloopback

LOCAL_PACKAGE_NAME := LoopbackApp

LOCAL_PROGUARD_FLAG_FILES := proguard.cfg

LOCAL_SDK_VERSION := 21

include $(BUILD_PACKAGE)

# Use the following include to make our test apk.
include $(call all-makefiles-under, app/src/main/jni)
include $(call all-makefiles-under,$(LOCAL_PATH))


