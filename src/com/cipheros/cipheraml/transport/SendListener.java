/*
 * Copyright (C) 2026 The CipherOS Project
 *
 * Licensed under the CipherOS License 2.0. See the LICENSE file in the
 * root of this repository for the full terms.
 */

package com.cipheros.cipheraml.transport;

/** Tells the session when a transport is done with one endpoint. */
public interface SendListener {
    /**
     * Called once per endpoint, after the last attempt, on the handler the
     * transport was created with.
     *
     * @param endpoint short description for logs, never contains payload data
     * @param success whether any attempt got through
     */
    void onSendFinished(String endpoint, boolean success);
}
