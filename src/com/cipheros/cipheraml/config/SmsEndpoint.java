/*
 * Copyright (C) 2026 The CipherOS Project
 *
 * Licensed under the CipherOS License 2.0. See the LICENSE file in the
 * root of this repository for the full terms.
 */

package com.cipheros.cipheraml.config;

/** Where and how to send the AML SMS for one country. */
public final class SmsEndpoint {
    public enum Type {
        /** Regular text SMS, sent without being written to the SMS provider. */
        TEXT,
        /** Port addressed data SMS with a septet packed payload. */
        DATA
    }

    private final String mDestination;
    private final Type mType;
    private final int mPort;
    private final boolean mIncludeOptionalFields;
    private final boolean mPartialImei;

    public SmsEndpoint(String destination, Type type, int port, boolean includeOptionalFields,
            boolean partialImei) {
        mDestination = destination;
        mType = type;
        mPort = port;
        mIncludeOptionalFields = includeOptionalFields;
        mPartialImei = partialImei;
    }

    public String destination() {
        return mDestination;
    }

    public Type type() {
        return mType;
    }

    /** Destination port, only meaningful for {@link Type#DATA}. */
    public int port() {
        return mPort;
    }

    public boolean includeOptionalFields() {
        return mIncludeOptionalFields;
    }

    public boolean partialImei() {
        return mPartialImei;
    }

    @Override
    public String toString() {
        return "sms{" + mDestination + ", " + mType + (mType == Type.DATA ? ":" + mPort : "")
                + "}";
    }
}
