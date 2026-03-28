/*
 * Copyright (C) 2026 The CipherOS Project
 *
 * Licensed under the CipherOS License 2.0. See the LICENSE file in the
 * root of this repository for the full terms.
 */

package com.cipheros.cipheraml;

import android.content.Context;
import android.content.res.Resources;

/** Snapshot of the overlayable values in res/values/config.xml. */
public final class AmlTunables {
    public final long locationTimeoutMillis;
    public final int earlySendAccuracyMeters;
    public final int confidencePercent;
    public final int minBatteryPercent;
    public final long maxCachedFixAgeMillis;

    private AmlTunables(Resources res) {
        locationTimeoutMillis = clamp(res.getInteger(R.integer.config_amlLocationTimeoutMillis),
                1000, 60000);
        earlySendAccuracyMeters = Math.max(0,
                res.getInteger(R.integer.config_amlEarlySendAccuracyMeters));
        confidencePercent = clamp(res.getInteger(R.integer.config_amlConfidencePercent), 1, 99);
        minBatteryPercent = clamp(res.getInteger(R.integer.config_amlMinBatteryPercent), 0, 100);
        maxCachedFixAgeMillis = Math.max(0,
                res.getInteger(R.integer.config_amlMaxCachedFixAgeMillis));
    }

    public static AmlTunables from(Context context) {
        return new AmlTunables(context.getResources());
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public String toString() {
        return "T1=" + locationTimeoutMillis + "ms early<=" + earlySendAccuracyMeters + "m lc="
                + confidencePercent + "% minBattery=" + minBatteryPercent + "% maxCachedAge="
                + maxCachedFixAgeMillis + "ms";
    }
}
