/*
 * Copyright (C) 2026 The CipherOS Project
 *
 * Licensed under the CipherOS License 2.0. See the LICENSE file in the
 * root of this repository for the full terms.
 */

package com.cipheros.cipheraml.core;

/**
 * How a location was worked out, as defined by ETSI TS 103 625.
 *
 * <p>Each value carries both spellings the spec uses: the single letter
 * for the SMS {@code pm} attribute (annex A) and the word for the HTTPS
 * {@code location_source} field (annex B).
 */
public enum PositioningMethod {
    GNSS("G", "gps"),
    WIFI("W", "wifi"),
    CELL("C", "cell"),
    NONE("N", "unknown");

    private final String mSmsCode;
    private final String mHttpsName;

    PositioningMethod(String smsCode, String httpsName) {
        mSmsCode = smsCode;
        mHttpsName = httpsName;
    }

    /** Value for the SMS {@code pm} attribute. */
    public String smsCode() {
        return mSmsCode;
    }

    /** Value for the HTTPS {@code location_source} field. */
    public String httpsName() {
        return mHttpsName;
    }
}
