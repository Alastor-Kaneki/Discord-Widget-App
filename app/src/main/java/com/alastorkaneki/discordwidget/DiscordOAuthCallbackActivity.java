package com.alastorkaneki.discordwidget;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public final class DiscordOAuthCallbackActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        Uri data = intent == null ? null : intent.getData();
        Intent main = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        if (data == null) {
            main.putExtra(
                    MainActivity.EXTRA_OAUTH_ERROR,
                    getString(R.string.oauth_callback_missing)
            );
            startActivity(main);
            finish();
            return;
        }

        String error = data.getQueryParameter("error");
        if (error != null && !error.isEmpty()) {
            String description = data.getQueryParameter("error_description");
            main.putExtra(
                    MainActivity.EXTRA_OAUTH_ERROR,
                    description == null || description.isEmpty() ? error : description
            );
            startActivity(main);
            finish();
            return;
        }

        String code = data.getQueryParameter("code");
        String state = data.getQueryParameter("state");
        DiscordOAuthManager.PendingAuthorization pending = DiscordOAuthManager.consume(this, state);
        if (code == null || code.isEmpty() || pending == null) {
            main.putExtra(
                    MainActivity.EXTRA_OAUTH_ERROR,
                    getString(R.string.oauth_callback_invalid)
            );
            startActivity(main);
            finish();
            return;
        }

        main.putExtra(MainActivity.EXTRA_OAUTH_CODE, code);
        main.putExtra(MainActivity.EXTRA_OAUTH_VERIFIER, pending.verifier);
        main.putExtra(MainActivity.EXTRA_OAUTH_REDIRECT_URI, pending.redirectUri);
        startActivity(main);
        finish();
    }
}
