/*
 * Copyright (C) 2026 The CipherOS Project
 *
 * Licensed under the CipherOS License 2.0. See the LICENSE file in the
 * root of this repository for the full terms.
 */

package com.cipheros.cipheraml;

import android.app.Application;
import android.util.Log;

/**
 * Entry point for the persistent CipherAML process.
 *
 * <p>The system starts us at boot because we are marked persistent. Keep
 * this class small, Settings also binds to us and onCreate() runs on the
 * main thread.
 */
public class CipherAmlApp extends Application {
    static final String TAG = "CipherAML";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "CipherAML starting");
    }
}
