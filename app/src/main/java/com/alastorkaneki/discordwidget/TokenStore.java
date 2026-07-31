package com.alastorkaneki.discordwidget;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class TokenStore {
    private static final String PREFS = "discord_oauth_secure";
    private static final String KEY_PAYLOAD = "payload";
    private static final String KEY_IV = "iv";
    private static final String KEY_ALIAS = "discord_widget_oauth_tokens";

    public static final class Session {
        public final String accessToken;
        public final String refreshToken;
        public final long expiresAtMillis;

        private Session(String accessToken, String refreshToken, long expiresAtMillis) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresAtMillis = expiresAtMillis;
        }
    }

    private TokenStore() {
    }

    public static synchronized void save(
            Context context,
            String accessToken,
            String refreshToken,
            long expiresAtMillis
    ) {
        try {
            JSONObject object = new JSONObject();
            object.put("access", accessToken);
            object.put("refresh", refreshToken);
            object.put("expires", expiresAtMillis);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] encrypted = cipher.doFinal(object.toString().getBytes(StandardCharsets.UTF_8));

            prefs(context).edit()
                    .putString(KEY_PAYLOAD, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .putString(KEY_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                    .apply();
        } catch (Exception error) {
            clear(context);
        }
    }

    public static synchronized Session load(Context context) {
        SharedPreferences preferences = prefs(context);
        String payload = preferences.getString(KEY_PAYLOAD, "");
        String iv = preferences.getString(KEY_IV, "");
        if (payload.isEmpty() || iv.isEmpty()) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    getOrCreateKey(),
                    new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            );
            byte[] decrypted = cipher.doFinal(Base64.decode(payload, Base64.NO_WRAP));
            JSONObject object = new JSONObject(new String(decrypted, StandardCharsets.UTF_8));
            String accessToken = object.optString("access", "");
            String refreshToken = object.optString("refresh", "");
            long expiresAtMillis = object.optLong("expires", 0L);
            if (accessToken.isEmpty() || refreshToken.isEmpty()) {
                clear(context);
                return null;
            }
            return new Session(accessToken, refreshToken, expiresAtMillis);
        } catch (Exception error) {
            clear(context);
            return null;
        }
    }

    public static synchronized boolean hasSession(Context context) {
        return load(context) != null;
    }

    public static synchronized void clear(Context context) {
        prefs(context).edit().clear().apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null)).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }
}
