/*
 * Copyright (C) 2026 The CipherOS Project
 *
 * Licensed under the CipherOS License 2.0. See the LICENSE file in the
 * root of this repository for the full terms.
 */

package com.cipheros.cipheraml.config;

/** An AML reception server that takes annex B HTTPS posts. */
public final class HttpsEndpoint {
    private final String mUrl;

    public HttpsEndpoint(String url) {
        mUrl = url;
    }

    public String url() {
        return mUrl;
    }

    @Override
    public String toString() {
        return "https{" + mUrl + "}";
    }
}
