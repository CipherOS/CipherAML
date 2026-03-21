/*
 * Copyright (C) 2026 The CipherOS Project
 *
 * Licensed under the CipherOS License 2.0. See the LICENSE file in the
 * root of this repository for the full terms.
 */

package com.cipheros.cipheraml.config;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** The full table of AML countries we know about. */
public final class EndpointConfig {
    public static final EndpointConfig EMPTY = new EndpointConfig(Collections.emptyList(), "none");

    private final List<CountryConfig> mCountries;
    private final String mSource;

    public EndpointConfig(List<CountryConfig> countries, String source) {
        mCountries = Collections.unmodifiableList(countries);
        mSource = source;
    }

    public List<CountryConfig> countries() {
        return mCountries;
    }

    /** Where this table came from, for dumps and logs. */
    public String source() {
        return mSource;
    }

    /**
     * Finds the country we are in. The network MCC is the most reliable
     * signal, the country ISO is a fallback for when the modem has not
     * told us the MCC yet, which happens in limited service.
     */
    public CountryConfig find(String networkMcc, String countryIso) {
        if (networkMcc != null && !networkMcc.isEmpty()) {
            for (CountryConfig c : mCountries) {
                if (c.mccs().contains(networkMcc)) {
                    return c;
                }
            }
        }
        if (countryIso != null && !countryIso.isEmpty()) {
            String iso = countryIso.toLowerCase(Locale.ROOT);
            for (CountryConfig c : mCountries) {
                if (c.isos().contains(iso)) {
                    return c;
                }
            }
        }
        return null;
    }
}
