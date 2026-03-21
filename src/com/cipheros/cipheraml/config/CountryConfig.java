/*
 * Copyright (C) 2026 The CipherOS Project
 *
 * Licensed under the CipherOS License 2.0. See the LICENSE file in the
 * root of this repository for the full terms.
 */

package com.cipheros.cipheraml.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * AML setup for one country: which numbers trigger it and where the
 * location goes.
 */
public final class CountryConfig {
    private final String mName;
    private final Set<String> mMccs;
    private final Set<String> mIsos;
    private final Set<String> mTriggers;
    private final List<SmsEndpoint> mSmsEndpoints;
    private final List<HttpsEndpoint> mHttpsEndpoints;
    private final int mConfidencePercent;

    private CountryConfig(Builder b) {
        mName = b.mName;
        mMccs = Collections.unmodifiableSet(new HashSet<>(b.mMccs));
        mIsos = Collections.unmodifiableSet(new HashSet<>(b.mIsos));
        mTriggers = Collections.unmodifiableSet(new HashSet<>(b.mTriggers));
        mSmsEndpoints = Collections.unmodifiableList(new ArrayList<>(b.mSmsEndpoints));
        mHttpsEndpoints = Collections.unmodifiableList(new ArrayList<>(b.mHttpsEndpoints));
        mConfidencePercent = b.mConfidencePercent;
    }

    public String name() {
        return mName;
    }

    public Set<String> mccs() {
        return mMccs;
    }

    public Set<String> isos() {
        return mIsos;
    }

    public List<SmsEndpoint> smsEndpoints() {
        return mSmsEndpoints;
    }

    public List<HttpsEndpoint> httpsEndpoints() {
        return mHttpsEndpoints;
    }

    /** Per country confidence override, or 0 to use the global default. */
    public int confidencePercent() {
        return mConfidencePercent;
    }

    public boolean hasEndpoints() {
        return !mSmsEndpoints.isEmpty() || !mHttpsEndpoints.isEmpty();
    }

    /**
     * Whether a call to this number should start AML. With no trigger list
     * every number the modem considers an emergency number counts.
     */
    public boolean isTrigger(String number) {
        if (mTriggers.isEmpty()) {
            return true;
        }
        return number != null && mTriggers.contains(normalizeNumber(number));
    }

    static String normalizeNumber(String number) {
        StringBuilder sb = new StringBuilder(number.length());
        for (int i = 0; i < number.length(); i++) {
            char c = number.charAt(i);
            if ((c >= '0' && c <= '9') || c == '+' || c == '*' || c == '#') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "country{" + mName + " mcc=" + mMccs + " iso=" + mIsos + " triggers="
                + (mTriggers.isEmpty() ? "any" : mTriggers) + " " + mSmsEndpoints + " "
                + mHttpsEndpoints + "}";
    }

    public static final class Builder {
        private String mName = "";
        private final List<String> mMccs = new ArrayList<>();
        private final List<String> mIsos = new ArrayList<>();
        private final List<String> mTriggers = new ArrayList<>();
        private final List<SmsEndpoint> mSmsEndpoints = new ArrayList<>();
        private final List<HttpsEndpoint> mHttpsEndpoints = new ArrayList<>();
        private int mConfidencePercent;

        public Builder setName(String name) {
            mName = name == null ? "" : name;
            return this;
        }

        public Builder addMcc(String mcc) {
            mMccs.add(mcc);
            return this;
        }

        public Builder addIso(String iso) {
            mIsos.add(iso.toLowerCase(Locale.ROOT));
            return this;
        }

        public Builder addTrigger(String number) {
            mTriggers.add(normalizeNumber(number));
            return this;
        }

        public Builder addSmsEndpoint(SmsEndpoint endpoint) {
            mSmsEndpoints.add(endpoint);
            return this;
        }

        public Builder addHttpsEndpoint(HttpsEndpoint endpoint) {
            mHttpsEndpoints.add(endpoint);
            return this;
        }

        public Builder setConfidencePercent(int percent) {
            mConfidencePercent = percent;
            return this;
        }

        public CountryConfig build() {
            return new CountryConfig(this);
        }
    }
}
