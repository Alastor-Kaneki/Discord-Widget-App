#define DISCORDPP_IMPLEMENTATION
#include "discordpp.h"
#include <jni.h>
#include <atomic>
#include <chrono>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

static JavaVM* javaVm = nullptr;
static jobject bridgeObject = nullptr;
static std::shared_ptr<discordpp::Client> client;
static uint64_t applicationId = 0;
static std::atomic<bool> callbacksRunning{false};
static std::thread callbacksThread;
static std::mutex bridgeMutex;

static JNIEnv* envForThread(bool& attached) {
    attached = false;
    JNIEnv* env = nullptr;
    if (javaVm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        if (javaVm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            attached = true;
        }
    }
    return env;
}

static void callOneString(const char* methodName, const std::string& value) {
    bool attached;
    JNIEnv* env = envForThread(attached);
    if (env == nullptr || bridgeObject == nullptr) {
        return;
    }
    jclass type = env->GetObjectClass(bridgeObject);
    jmethodID method = env->GetMethodID(type, methodName, "(Ljava/lang/String;)V");
    jstring text = env->NewStringUTF(value.c_str());
    env->CallVoidMethod(bridgeObject, method, text);
    env->DeleteLocalRef(text);
    env->DeleteLocalRef(type);
    if (attached) {
        javaVm->DetachCurrentThread();
    }
}

static void callConversation(const std::string& userId, const std::string& preview, uint64_t messageId) {
    bool attached;
    JNIEnv* env = envForThread(attached);
    if (env == nullptr || bridgeObject == nullptr) {
        return;
    }
    jclass type = env->GetObjectClass(bridgeObject);
    jmethodID method = env->GetMethodID(type, "dispatchConversation", "(Ljava/lang/String;Ljava/lang/String;J)V");
    jstring user = env->NewStringUTF(userId.c_str());
    jstring text = env->NewStringUTF(preview.c_str());
    env->CallVoidMethod(bridgeObject, method, user, text, static_cast<jlong>(messageId));
    env->DeleteLocalRef(user);
    env->DeleteLocalRef(text);
    env->DeleteLocalRef(type);
    if (attached) {
        javaVm->DetachCurrentThread();
    }
}

static void callMessageSent(const std::string& userId, uint64_t messageId) {
    bool attached;
    JNIEnv* env = envForThread(attached);
    if (env == nullptr || bridgeObject == nullptr) {
        return;
    }
    jclass type = env->GetObjectClass(bridgeObject);
    jmethodID method = env->GetMethodID(type, "dispatchMessageSent", "(Ljava/lang/String;J)V");
    jstring user = env->NewStringUTF(userId.c_str());
    env->CallVoidMethod(bridgeObject, method, user, static_cast<jlong>(messageId));
    env->DeleteLocalRef(user);
    env->DeleteLocalRef(type);
    if (attached) {
        javaVm->DetachCurrentThread();
    }
}

static std::string toString(JNIEnv* env, jstring value) {
    const char* raw = env->GetStringUTFChars(value, nullptr);
    std::string result(raw == nullptr ? "" : raw);
    if (raw != nullptr) {
        env->ReleaseStringUTFChars(value, raw);
    }
    return result;
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    javaVm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_alastorkaneki_discordwidget_DiscordSocialBridge_nativeInitialize(
    JNIEnv* env,
    jclass,
    jlong appId,
    jobject bridge
) {
    std::lock_guard<std::mutex> lock(bridgeMutex);
    applicationId = static_cast<uint64_t>(appId);
    if (bridgeObject != nullptr) {
        env->DeleteGlobalRef(bridgeObject);
    }
    bridgeObject = env->NewGlobalRef(bridge);
    client = std::make_shared<discordpp::Client>();
    client->SetStatusChangedCallback([](
        discordpp::Client::Status status,
        discordpp::Client::Error error,
        int32_t errorDetail
    ) {
        if (error != discordpp::Client::Error::None) {
            callOneString("dispatchError", "Discord Social SDK error " + std::to_string(errorDetail));
            return;
        }
        callOneString("dispatchStatus", discordpp::Client::StatusToString(status));
    });
    client->SetMessageCreatedCallback([](uint64_t messageId) {
        if (!client) {
            return;
        }
        auto message = client->GetMessageHandle(messageId);
        if (!message.has_value()) {
            return;
        }
        uint64_t userId = message->RecipientId();
        if (userId == 0) {
            return;
        }
        callConversation(std::to_string(userId), message->Content(), message->Id());
    });
    callbacksRunning.store(true);
    callbacksThread = std::thread([] {
        while (callbacksRunning.load()) {
            discordpp::RunCallbacks();
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
        }
    });
    callbacksThread.detach();
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_alastorkaneki_discordwidget_DiscordSocialBridge_nativeConnect(
    JNIEnv*,
    jclass
) {
    if (!client) {
        callOneString("dispatchError", "Social SDK client is not initialized");
        return;
    }
    auto verifier = client->CreateAuthorizationCodeVerifier();
    discordpp::AuthorizationArgs args{};
    args.SetClientId(applicationId);
    args.SetScopes(discordpp::Client::GetDefaultCommunicationScopes());
    args.SetCodeChallenge(verifier.Challenge());

    client->Authorize(
        args,
        [verifier](
            discordpp::ClientResult result,
            std::string code,
            std::string redirectUri
        ) mutable {
            if (!result.Successful()) {
                callOneString("dispatchError", "Discord authorization failed");
                return;
            }
            client->GetToken(
                applicationId,
                code,
                verifier.Verifier(),
                redirectUri,
                [](
                    discordpp::ClientResult tokenResult,
                    std::string accessToken,
                    std::string,
                    discordpp::AuthorizationTokenType,
                    int32_t,
                    std::string
                ) {
                    if (!tokenResult.Successful()) {
                        callOneString("dispatchError", "Discord token exchange failed");
                        return;
                    }
                    client->UpdateToken(
                        discordpp::AuthorizationTokenType::Bearer,
                        accessToken,
                        [](discordpp::ClientResult updateResult) {
                            if (!updateResult.Successful()) {
                                callOneString("dispatchError", "Discord token update failed");
                                return;
                            }
                            client->Connect();
                        }
                    );
                }
            );
        }
    );
}

extern "C" JNIEXPORT void JNICALL
Java_com_alastorkaneki_discordwidget_DiscordSocialBridge_nativeRefreshDirectMessages(
    JNIEnv*,
    jclass
) {
    if (!client) {
        callOneString("dispatchError", "Social SDK client is not initialized");
        return;
    }
    client->GetUserMessageSummaries([](
        const discordpp::ClientResult& result,
        const std::vector<discordpp::UserMessageSummary>& summaries
    ) {
        if (!result.Successful()) {
            callOneString("dispatchError", "Unable to load Discord conversations");
            return;
        }
        for (const auto& summary : summaries) {
            uint64_t userId = summary.UserId();
            uint64_t lastMessageId = summary.LastMessageId();
            client->GetUserMessagesWithLimit(
                userId,
                1,
                [userId, lastMessageId](
                    const discordpp::ClientResult& messagesResult,
                    const std::vector<discordpp::MessageHandle>& messages
                ) {
                    if (!messagesResult.Successful() || messages.empty()) {
                        callConversation(std::to_string(userId), "", lastMessageId);
                        return;
                    }
                    callConversation(
                        std::to_string(userId),
                        messages.front().Content(),
                        lastMessageId
                    );
                }
            );
        }
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_alastorkaneki_discordwidget_DiscordSocialBridge_nativeSendDirectMessage(
    JNIEnv* env,
    jclass,
    jstring userIdValue,
    jstring messageValue
) {
    if (!client) {
        callOneString("dispatchError", "Social SDK client is not initialized");
        return;
    }
    std::string userIdText = toString(env, userIdValue);
    std::string message = toString(env, messageValue);
    uint64_t userId = 0;
    try {
        userId = std::stoull(userIdText);
    } catch (...) {
        callOneString("dispatchError", "Invalid Discord user ID");
        return;
    }
    client->SendUserMessage(
        userId,
        message,
        [userId](discordpp::ClientResult result, uint64_t messageId) {
            if (!result.Successful()) {
                callOneString("dispatchError", "Discord message send failed");
                return;
            }
            callMessageSent(std::to_string(userId), messageId);
        }
    );
}
