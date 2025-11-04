#include <jni.h>
#include <string>

// extern "C"
// JNIEXPORT jboolean JNICALL
// Java_com_vitals_sdk_bp_Native_verifyCredential(JNIEnv *env, jobject thiz, jlong timestamp,
//                                                jstring sign) {
//     return JNI_TRUE;
// }

extern "C"
JNIEXPORT jstring JNICALL
Java_com_vitals_sdk_bp_Native_nativeGetKeyHash(JNIEnv *env, jobject thiz, jstring app_id_hash) {
  const char *app_id_hash_chars = env->GetStringUTFChars(app_id_hash, nullptr);
  std::string app_id_hash_str(app_id_hash_chars);
  env->ReleaseStringUTFChars(app_id_hash, app_id_hash_chars);
  std::string key = "";
  if (app_id_hash_str == "b178ffb63bfcc199374b4306edc8425c4065e10db7aef5ca58c05f29c2c60166")
    key = "d8da05c6a6eb0c615c009df4f6b42680149810fd1f7a980799f349209f04f3d0";
  return env->NewStringUTF(key.c_str());
}
extern "C"
JNIEXPORT jstring JNICALL
Java_com_vitals_sdk_bp_Native_nativeGetKey(JNIEnv *env, jobject thiz, jstring app_id_hash) {
  const char *app_id_hash_chars = env->GetStringUTFChars(app_id_hash, nullptr);
  std::string app_id_hash_str(app_id_hash_chars);
  env->ReleaseStringUTFChars(app_id_hash, app_id_hash_chars);
  std::string key = "";
  if (app_id_hash_str == "b178ffb63bfcc199374b4306edc8425c4065e10db7aef5ca58c05f29c2c60166")
    key = "255afad56fc64068dcc4d30dc6d660075931de288abda60019e0e71493443cb9";
  return env->NewStringUTF(key.c_str());
}