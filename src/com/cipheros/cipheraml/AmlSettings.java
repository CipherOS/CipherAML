/*
 * Copyright (C) 2026 The CipherOS Project
 *
 * Licensed under the CipherOS License 2.0. See the LICENSE file in the
 * root of this repository for the full terms.
 */

package com.cipheros.cipheraml;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The user facing on/off switch.
 *
 * <p>Stored in device protected storage so we can read it before the
 * user unlocks for the first time after boot.
 */
public final class AmlSettings {
    private static final String PREFS_NAME = "cipheraml";
    private static final String KEY_ENABLED = "enabled";

    private AmlSettings() {}

    public static boolean isEnabled(Context context) {
        boolean byDefault = context.getResources().getBoolean(R.bool.config_amlEnabledByDefault);
        return prefs(context).getBoolean(KEY_ENABLED, byDefault);
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.createDeviceProtectedStorageContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
