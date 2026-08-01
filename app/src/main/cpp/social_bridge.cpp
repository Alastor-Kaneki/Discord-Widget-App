#define DISCORDPP_IMPLEMENTATION
#include "discordpp.h"
#include <jni.h>
#include <atomic>
#include <chrono>
#include <memory>
#include <mutex>
#include <sstream>
#include <string>
#include <thread>
#include <vector>

static JavaVM* javaVm = nullptr;
static jobject bridgeObject = nullptr;
static std::shared_ptr<discordpp::Client> client;
static uint64_t applicationId = 0;
static std::atomic<bool> callbacksRunning{false};
static std::atomic<bool> ready{false};
static std::thread callbacksThread;
static std::mutex bridgeMutex;
static std::mutex tokenMutex;
static std::string currentRefreshToken;

static int64_t nowMillis() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::now().time_since_epoch()
    ).count();
}

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

static std::string toString(JNIEnv* env, jstring value) {
    if (value == nullptr) {
        return "";
    }
    const char* raw = env->GetStringUTFChars(value, nullptr);
    std::string result(raw == nullptr ? "" : raw);
    if (raw != nullptr) {
        env->ReleaseStringUTFChars(value, raw);
    }
    return result;
}

static std::string jsonEscape(const std::string& value) {
    std::ostringstream output;
    for (unsigned char character : value) {
        switch (character) {
            case '\"': output << "\\\""; break;
            case '\\': output << "\\\\"; break;
            case '\b': output << "\\b"; break;
            case '\f': output << "\\f"; break;
            case '\n': output << "\\n"; break;
            case '\r': output << "\\r"; break;
            case '\t': output << "\\t"; break;
            default:
                if (character < 0x20) {
                    const char hex[] = "0123456789abcdef";
                    output << "\\u00" << hex[(character >> 4) & 0x0f] << hex[character & 0x0f];
                } else {
                    output << static_cast<char>(character);
                }
        }
    }
    return output.str();
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

static void callTwoStrings(
    const char* methodName,
    const std::string& first,
    const std::string& second
) {
    bool attached;
    JNIEnv* env = envForThread(attached);
    if (env == nullptr || bridgeObject == nullptr) {
        return;
    }
    jclass type = env->GetObjectClass(bridgeObject);
    jmethodID method = env->GetMethodID(
        type,
        methodName,
        "(Ljava/lang/String;Ljava/lang/String;)V"
    );
    jstring firstValue = env->NewStringUTF(first.c_str());
    jstring secondValue = env->NewStringUTF(second.c_str());
    env->CallVoidMethod(bridgeObject, method, firstValue, secondValue);
    env->DeleteLocalRef(firstValue);
    env->DeleteLocalRef(secondValue);
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

static void callTokens(
    const std::string& accessToken,
    const std::string& refreshToken,
    int64_t expiresAtMillis
) {
    bool attached;
    JNIEnv* env = envForThread(attached);
    if (env == nullptr || bridgeObject == nullptr) {
        return;
    }
    jclass type = env->GetObjectClass(bridgeObject);
    jmethodID method = env->GetMethodID(
        type,
        "dispatchTokens",
        "(Ljava/lang/String;Ljava/lang/String;J)V"
    );
    jstring access = env->NewStringUTF(accessToken.c_str());
    jstring refresh = env->NewStringUTF(refreshToken.c_str());
    env->CallVoidMethod(
        bridgeObject,
        method,
        access,
        refresh,
        static_cast<jlong>(expiresAtMillis)
    );
    env->DeleteLocalRef(access);
    env->DeleteLocalRef(refresh);
    env->DeleteLocalRef(type);
    if (attached) {
        javaVm->DetachCurrentThread();
    }
}

static void connectWithAccessToken(const std::string& accessToken) {
    if (!client || accessToken.empty()) {
        callOneString("dispatchError", "Discord access token is unavailable");
        return;
    }
    client->UpdateToken(
        discordpp::AuthorizationTokenType::Bearer,
        accessToken,
        [](discordpp::ClientResult result) {
            if (!result.Successful()) {
                callOneString("dispatchError", "Discord token update failed");
                return;
            }
            client->Connect();
        }
    );
}

static void storeTokensAndConnect(
    const std::string& accessToken,
    const std::string& refreshToken,
    int32_t expiresIn
) {
    int64_t expiresAtMillis = nowMillis() + static_cast<int64_t>(expiresIn) * 1000;
    {
        std::lock_guard<std::mutex> lock(tokenMutex);
        currentRefreshToken = refreshToken;
    }
    callTokens(accessToken, refreshToken, expiresAtMillis);
    connectWithAccessToken(accessToken);
}

static void refreshCurrentToken() {
    if (!client) {
        callOneString("dispatchError", "Social SDK client is not initialized");
        return;
    }
    std::string refreshToken;
    {
        std::lock_guard<std::mutex> lock(tokenMutex);
        refreshToken = currentRefreshToken;
    }
    if (refreshToken.empty()) {
        callOneString("dispatchError", "Discord refresh token is unavailable");
        return;
    }
    client->RefreshToken(
        applicationId,
        refreshToken,
        [](
            discordpp::ClientResult result,
            std::string accessToken,
            std::string refreshTokenValue,
            discordpp::AuthorizationTokenType,
            int32_t expiresIn,
            std::string
        ) {
            if (!result.Successful()) {
                callOneString("dispatchError", "Discord token refresh failed");
                return;
            }
            storeTokensAndConnect(accessToken, refreshTokenValue, expiresIn);
        }
    );
}

static void dispatchHistory(
    uint64_t userId,
    const std::vector<discordpp::MessageHandle>& messages
) {
    std::ostringstream json;
    json << '[';
    bool first = true;
    for (auto iterator = messages.rbegin(); iterator != messages.rend(); ++iterator) {
        const auto& message = *iterator;
        if (!first) {
            json << ',';
        }
        first = false;
        std::string authorName;
        auto author = message.Author();
        if (author.has_value()) {
            authorName = author->DisplayName();
        }
        bool outgoing = message.AuthorId() != userId;
        json << '{'
             << "\"id\":\"" << message.Id() << "\","
             << "\"authorId\":\"" << message.AuthorId() << "\","
             << "\"authorName\":\"" << jsonEscape(authorName) << "\","
             << "\"content\":\"" << jsonEscape(message.Content()) << "\","
             << "\"timestamp\":" << message.SentTimestamp() << ','
             << "\"outgoing\":" << (outgoing ? "true" : "false")
             << '}';
    }
    json << ']';
    callTwoStrings("dispatchMessageHistory", std::to_string(userId), json.str());
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
    if (bridgeObject == nullptr) {
        bridgeObject = env->NewGlobalRef(bridge);
    }
    if (client) {
        return JNI_TRUE;
    }
    client = std::make_shared<discordpp::Client>();
    client->SetStatusChangedCallback([](
        discordpp::Client::Status status,
        discordpp::Client::Error error,
        int32_t errorDetail
    ) {
        ready.store(status == discordpp::Client::Status::Ready);
        if (error != discordpp::Client::Error::None) {
            callOneString("dispatchError", "Discord Social SDK error " + std::to_string(errorDetail));
            return;
        }
        callOneString("dispatchStatus", discordpp::Client::StatusToString(status));
    });
    client->SetTokenExpirationCallback([] {
        refreshCurrentToken();
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
Java_com_alastorkaneki_discordwidget_DiscordSocialBridge_nativeExchangeAuthorizationCode(
    JNIEnv* env,
    jclass,
    jstring codeValue,
    jstring verifierValue,
    jstring redirectUriValue
) {
    if (!client) {
        callOneString("dispatchError", "Social SDK client is not initialized");
        return;
    }
    std::string code = toString(env, codeValue);
    std::string verifier = toString(env, verifierValue);
    std::string redirectUri = toString(env, redirectUriValue);
    if (code.empty() || verifier.empty() || redirectUri.empty()) {
        callOneString("dispatchError", "Discord authorization callback is incomplete");
        return;
    }
    client->GetToken(
        applicationId,
        code,
        verifier,
        redirectUri,
        [](
            discordpp::ClientResult tokenResult,
            std::string accessToken,
            std::string refreshToken,
            discordpp::AuthorizationTokenType,
            int32_t expiresIn,
            std::string
        ) {
            if (!tokenResult.Successful()) {
                callOneString("dispatchError", "Discord token exchange failed");
                return;
            }
            storeTokensAndConnect(accessToken, refreshToken, expiresIn);
        }
    );
}

extern "C" JNIEXPORT void JNICALL
Java_com_alastorkaneki_discordwidget_DiscordSocialBridge_nativeRestoreSession(
    JNIEnv* env,
    jclass,
    jstring accessTokenValue,
    jstring refreshTokenValue,
    jlong expiresAtMillis
) {
    std::string accessToken = toString(env, accessTokenValue);
    std::string refreshToken = toString(env, refreshTokenValue);
    {
        std::lock_guard<std::mutex> lock(tokenMutex);
        currentRefreshToken = refreshToken;
    }
    if (!accessToken.empty() && static_cast<int64_t>(expiresAtMillis) > nowMillis() + 60'000) {
        connectWithAccessToken(accessToken);
        return;
    }
    refreshCurrentToken();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_alastorkaneki_discordwidget_DiscordSocialBridge_nativeIsReady(
    JNIEnv*,
    jclass
) {
    return ready.load() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_alastorkaneki_discordwidget_DiscordSocialBridge_nativeRefreshDirectMessages(
    JNIEnv*,
    jclass
) {
    if (!client || !ready.load()) {
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
Java_com_alastorkaneki_discordwidget_DiscordSocialBridge_nativeFetchDirectMessageHistory(
    JNIEnv* env,
    jclass,
    jstring userIdValue,
    jint limit
) {
    if (!client || !ready.load()) {
        callOneString("dispatchError", "Discord is reconnecting");
        return;
    }
    uint64_t userId = 0;
    try {
        userId = std::stoull(toString(env, userIdValue));
    } catch (...) {
        callOneString("dispatchError", "Invalid Discord user ID");
        return;
    }
    int32_t safeLimit = static_cast<int32_t>(limit);
    if (safeLimit < 1) {
        safeLimit = 1;
    }
    if (safeLimit > 200) {
        safeLimit = 200;
    }
    client->GetUserMessagesWithLimit(
        userId,
        safeLimit,
        [userId](
            const discordpp::ClientResult& result,
            const std::vector<discordpp::MessageHandle>& messages
        ) {
            if (!result.Successful()) {
                callTwoStrings("dispatchMessageHistory", std::to_string(userId), "[]");
                callOneString(
                    "dispatchError",
                    "Discord could not return this DM history. Both people must have used this application, and only the last 72 hours are available."
                );
                return;
            }
            dispatchHistory(userId, messages);
        }
    );
}

extern "C" JNIEXPORT void JNICALL
Java_com_alastorkaneki_discordwidget_DiscordSocialBridge_nativeSendDirectMessage(
    JNIEnv* env,
    jclass,
    jstring userIdValue,
    jstring messageValue
) {
    if (!client || !ready.load()) {
        callOneString("dispatchError", "Discord is reconnecting");
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
