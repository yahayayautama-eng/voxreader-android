#include <jni.h>
#include <string>
#include "babylon.h"

namespace {
std::string jstring_to_string(JNIEnv* env, jstring value) {
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_tts_KokoroNativeEngine_nativeInitialize(
    JNIEnv* env,
    jobject,
    jstring phonemizerPath,
    jstring dictionaryPath,
    jstring modelPath
) {
    const auto phonemizer = jstring_to_string(env, phonemizerPath);
    const auto dictionary = jstring_to_string(env, dictionaryPath);
    const auto model = jstring_to_string(env, modelPath);
    babylon_g2p_options_t options{dictionary.c_str(), 1};
    return babylon_g2p_init(phonemizer.c_str(), options) == 0 &&
        babylon_kitten_init(model.c_str()) == 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_tts_KokoroNativeEngine_nativeSynthesize(
    JNIEnv* env,
    jobject,
    jstring text,
    jstring voicePath,
    jfloat speed,
    jstring outputPath
) {
    const auto input = jstring_to_string(env, text);
    const auto voice = jstring_to_string(env, voicePath);
    const auto output = jstring_to_string(env, outputPath);
    // Babylon's kittenTTS entry point performs its own English phonemization.
    // Passing already-phonemized IPA here causes a second conversion and garbled speech.
    babylon_kitten_tts(input.c_str(), voice.c_str(), speed, output.c_str());
    return JNI_TRUE;
}
