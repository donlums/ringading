package uk.co.lumsdon.ringading;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.net.Uri;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/** Hosted Blink sign-in page, embedded for remote-control-only setup. */
public final class SignInActivity extends Activity {
    private String verifier, expectedState, hardwareId;
    private WebView browser;
    private TextView prompt;
    private long started;
    private boolean consumed = false;
    private final SecureRandom random = new SecureRandom();
    private String token(int bytes) {
        byte[] b = new byte[bytes]; random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().getDecorView().setBackgroundColor(Color.rgb(16, 23, 37));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 25, 40, 25);
        prompt = new TextView(this);
        prompt.setTextSize(18); prompt.setTextColor(Color.WHITE);
        prompt.setText("Secure Blink sign-in · Use the TV remote. Your password is entered on Blink's page.");
        root.addView(prompt);
        browser = new WebView(this);
        browser.setBackgroundColor(Color.WHITE);
        browser.getSettings().setJavaScriptEnabled(true);
        browser.getSettings().setDomStorageEnabled(true);
        browser.getSettings().setAllowFileAccess(false);
        browser.getSettings().setAllowContentAccess(false);
        browser.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleCallback(request.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleCallback(Uri.parse(url));
            }
            @Override public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                prompt.setText("Sign-in page error. Check TV internet or WebView. " + description);
            }
        });
        root.addView(browser, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        try {
            expectedState = token(32);
            verifier = token(64);
            hardwareId = UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT);
            String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
            Uri url = Uri.parse("https://api.oauth.blink.com/oauth/v2/authorize").buildUpon()
                .appendQueryParameter("client_id", "android")
                .appendQueryParameter("redirect_uri", "https://applinks.blink.com/signin/callback")
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("scope", "client")
                .appendQueryParameter("state", expectedState)
                .appendQueryParameter("code_challenge", challenge)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("prompt", "login")
                .appendQueryParameter("hardware_id", hardwareId)
                .appendQueryParameter("app_brand", "blink")
                .appendQueryParameter("app_version", "Version 60.0")
                .appendQueryParameter("device_brand", "Google")
                .appendQueryParameter("device_model", "Chromecast Google TV")
                .appendQueryParameter("device_os_version", "Android TV")
                .appendQueryParameter("dark_mode", "false").build();
            started = System.currentTimeMillis();
            browser.loadUrl(url.toString());
        } catch (Exception e) { prompt.setText("Cannot start secure sign-in: " + e.getMessage()); }
    }
    private boolean handleCallback(Uri uri) {
        if (!"https".equals(uri.getScheme()) || !"applinks.blink.com".equals(uri.getHost())
             || !"/signin/callback".equals(uri.getPath())) return false;
        if (consumed) return true;
        if (System.currentTimeMillis() - started > 15 * 60 * 1000L) {
            prompt.setText("Sign-in timed out. Back out and reconnect."); return true;
        }
        String state = uri.getQueryParameter("state");
        String code = uri.getQueryParameter("code");
        if (!expectedState.equals(state) || code == null || code.isEmpty() || uri.getQueryParameter("error") != null) {
            prompt.setText("Blink sign-in was cancelled or callback state did not match."); return true;
        }
        consumed = true;
        browser.stopLoading();
        browser.setVisibility(View.GONE);
        prompt.setText("Blink authorised. Linking doorbell push notifications to this TV…");
        ChimeApp.IO.execute(() -> {
            try {
                BlinkCloud.exchange(getApplicationContext(), code, verifier, hardwareId);
                runOnUiThread(() -> { setResult(RESULT_OK); finish(); });
            } catch (Exception e) {
                Status.write(this, "Blink link failed: " + e.getMessage());
                runOnUiThread(() -> prompt.setText("Blink link failed: " + e.getMessage()
                    + "\nPress Back and retry later. Do not repeatedly reauthenticate."));
            }
        });
        return true;
    }
    @Override protected void onDestroy() {
        if (browser != null) {
            browser.stopLoading(); browser.loadUrl("about:blank"); browser.destroy(); browser = null;
        }
        super.onDestroy();
    }
}