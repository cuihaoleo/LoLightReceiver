ifneq ($(TARGET_SIMULATOR),true)

LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

OPENCV_LIB_TYPE := static
include $(LOCAL_PATH)/opencv-native/jni/OpenCV.mk

LOCAL_MODULE := screen_detector
LOCAL_SRC_FILES:= hough.cpp
TARGET_OUT=src/main/assets/$(TARGET_ARCH_ABI)

LOCAL_CPP_FEATURES := exceptions
LOCAL_CFLAGS += -Wall -std=c++11 -O2
LOCAL_LDLIBS := -Wl,-unresolved-symbols=ignore-in-shared-libs -L$(LOCAL_PATH)/lib -llog -lm -lz
LOCAL_C_INCLUDES += bionic
LOCAL_C_INCLUDES += $(LOCAL_PATH)/include

include $(BUILD_EXECUTABLE)

endif  # TARGET_SIMULATOR != true
