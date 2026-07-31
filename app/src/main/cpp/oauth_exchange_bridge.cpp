#include "social_bridge.cpp"

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
        callOneString("dispatchError", "Discord OAuth callback is incomplete");
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
