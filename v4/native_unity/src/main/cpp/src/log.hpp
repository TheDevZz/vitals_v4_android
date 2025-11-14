#pragma once

#ifdef __ANDROID__

#include <android/log.h>

#define LOG_TAG "vitals"

// 日志开关控制宏：设置为 1 启用日志，0 禁用日志
#ifndef ENABLE_LOG
#define ENABLE_LOG 0
#endif

// 根据 ENABLE_LOG 宏定义日志输出宏
#if ENABLE_LOG
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))
#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__))
#define LOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))
#else
#define LOGE(...) ((void)0)
#define LOGI(...) ((void)0)
#define LOGD(...) ((void)0)
#endif

#endif

