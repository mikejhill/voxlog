// JNI bridge between WhisperNative.kt and whisper.cpp.
// Kept intentionally thin: model lifecycle plus one blocking transcribe call per audio chunk.

#include <jni.h>
#include <android/log.h>

#include <string>

#include "whisper.h"

namespace {

constexpr const char *kTag = "VoxLogWhisper";

struct ProgressBridge {
    JNIEnv *env;
    jobject listener;
    jmethodID onProgress;
};

void onWhisperProgress(whisper_context *, whisper_state *, int progress, void *userData) {
    auto *bridge = static_cast<ProgressBridge *>(userData);
    if (bridge->listener != nullptr) {
        bridge->env->CallVoidMethod(bridge->listener, bridge->onProgress, progress);
    }
}

std::string toStdString(JNIEnv *env, jstring value) {
    if (value == nullptr) {
        return {};
    }
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_mikejhill_voxlog_engine_whisper_WhisperNative_initContext(JNIEnv *env, jobject, jstring modelPath) {
    whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;
    const std::string path = toStdString(env, modelPath);
    whisper_context *context = whisper_init_from_file_with_params(path.c_str(), params);
    if (context == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "Failed to load model");
    }
    return reinterpret_cast<jlong>(context);
}

extern "C" JNIEXPORT void JNICALL
Java_com_mikejhill_voxlog_engine_whisper_WhisperNative_freeContext(JNIEnv *, jobject, jlong contextPtr) {
    whisper_free(reinterpret_cast<whisper_context *>(contextPtr));
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_mikejhill_voxlog_engine_whisper_WhisperNative_transcribe(
    JNIEnv *env, jobject, jlong contextPtr, jfloatArray samples, jstring language, jstring initialPrompt,
    jint threadCount, jobject progressListener) {
    auto *context = reinterpret_cast<whisper_context *>(contextPtr);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.no_context = false;
    params.n_threads = threadCount;

    const std::string languageValue = toStdString(env, language);
    params.language = languageValue.empty() ? "auto" : languageValue.c_str();
    const std::string promptValue = toStdString(env, initialPrompt);
    params.initial_prompt = promptValue.empty() ? nullptr : promptValue.c_str();

    ProgressBridge bridge{env, progressListener, nullptr};
    if (progressListener != nullptr) {
        jclass listenerClass = env->GetObjectClass(progressListener);
        bridge.onProgress = env->GetMethodID(listenerClass, "onProgress", "(I)V");
        params.progress_callback = onWhisperProgress;
        params.progress_callback_user_data = &bridge;
    }

    jsize sampleCount = env->GetArrayLength(samples);
    jfloat *sampleData = env->GetFloatArrayElements(samples, nullptr);
    const int status = whisper_full(context, params, sampleData, sampleCount);
    env->ReleaseFloatArrayElements(samples, sampleData, JNI_ABORT);

    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(2, stringClass, nullptr);
    if (status != 0) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "whisper_full failed: %d", status);
        return result;
    }

    std::string text;
    const int segmentCount = whisper_full_n_segments(context);
    for (int index = 0; index < segmentCount; ++index) {
        text += whisper_full_get_segment_text(context, index);
    }
    const char *detectedLanguage = whisper_lang_str(whisper_full_lang_id(context));

    env->SetObjectArrayElement(result, 0, env->NewStringUTF(text.c_str()));
    if (detectedLanguage != nullptr) {
        env->SetObjectArrayElement(result, 1, env->NewStringUTF(detectedLanguage));
    }
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mikejhill_voxlog_engine_whisper_WhisperNative_systemInfo(JNIEnv *env, jobject) {
    return env->NewStringUTF(whisper_print_system_info());
}
