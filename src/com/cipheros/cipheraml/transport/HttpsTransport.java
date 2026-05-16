/*
 * Copyright (C) 2026 The CipherOS Project
 *
 * Licensed under the CipherOS License 2.0. See the LICENSE file in the
 * root of this repository for the full terms.
 */

package com.cipheros.cipheraml.transport;

import android.os.Handler;
import android.util.Log;

import com.cipheros.cipheraml.config.HttpsEndpoint;
import com.cipheros.cipheraml.core.AmlHttpsEncoder;
import com.cipheros.cipheraml.core.AmlReport;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;

/**
 * Posts the annex B form to an AML reception server.
 *
 * <p>Plain HTTP is never used, the URL check happens both when the table
 * is parsed and here. Certificates are checked against the system trust
 * store like any other connection, the spec requires the PSAP to present
 * a properly signed one.
 *
 * <p>Network IO runs on its own thread so a slow server can not hold up
 * the SMS side. Results are posted back to the session handler.
 */
public final class HttpsTransport {
    private static final String TAG = "CipherAML";

    private static final int CONNECT_TIMEOUT_MILLIS = 10000;
    private static final int READ_TIMEOUT_MILLIS = 10000;
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MILLIS = 3000;

    private final Handler mHandler;
    private final SendListener mListener;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "CipherAML-https"));

    public HttpsTransport(Handler handler, SendListener listener) {
        mHandler = handler;
        mListener = listener;
    }

    public void send(HttpsEndpoint endpoint, AmlReport report) {
        byte[] body = AmlHttpsEncoder.encode(report).getBytes(StandardCharsets.UTF_8);
        mExecutor.execute(() -> {
            boolean ok = postWithRetries(endpoint, body);
            mHandler.post(() -> mListener.onSendFinished(endpoint.toString(), ok));
        });
    }

    /** Lets queued posts finish, then lets the thread die. */
    public void close() {
        mExecutor.shutdown();
    }

    private static boolean postWithRetries(HttpsEndpoint endpoint, byte[] body) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Log.i(TAG, "Posting AML to " + endpoint + ", attempt " + attempt);
            try {
                int code = post(endpoint.url(), body);
                if (code >= 200 && code < 300) {
                    Log.i(TAG, "AML server accepted the post (" + code + ")");
                    return true;
                }
                if (code >= 400 && code < 500) {
                    // Bad format or auth. Sending the same bytes again will
                    // get the same answer.
                    Log.e(TAG, "AML server rejected the post (" + code + "), giving up");
                    return false;
                }
                Log.w(TAG, "AML server error (" + code + ")");
            } catch (IOException e) {
                Log.w(TAG, "AML post failed: " + e);
            }
            if (attempt < MAX_ATTEMPTS) {
                try {
                    Thread.sleep(RETRY_DELAY_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    private static int post(String url, byte[] body) throws IOException {
        URLConnection raw = new URL(url).openConnection();
        if (!(raw instanceof HttpsURLConnection)) {
            throw new IOException("not an https url");
        }
        HttpsURLConnection conn = (HttpsURLConnection) raw;
        try {
            conn.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            conn.setReadTimeout(READ_TIMEOUT_MILLIS);
            conn.setInstanceFollowRedirects(false);
            conn.setUseCaches(false);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", AmlHttpsEncoder.CONTENT_TYPE);
            conn.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(body);
            }
            return conn.getResponseCode();
        } finally {
            conn.disconnect();
        }
    }
}
