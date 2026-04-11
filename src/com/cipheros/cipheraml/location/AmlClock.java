/*
 * Copyright (C) 2026 The CipherOS Project
 *
 * Licensed under the CipherOS License 2.0. See the LICENSE file in the
 * root of this repository for the full terms.
 */

package com.cipheros.cipheraml.location;

import android.location.Location;
import android.location.LocationManager;
import android.os.SystemClock;

import java.time.DateTimeException;

/**
 * Wall clock time for AML timestamps.
 *
 * <p>PSAPs use the time of positioning to throw away messages that look
 * too old or come from the future, so it matters. TS 103 625 asks for
 * network (NTP) time first, then GNSS time, and only then the handset
 * clock, which the user may have set to anything.
 */
public final class AmlClock {
    private AmlClock() {}

    /** Best guess at the current UTC time in milliseconds. */
    public static long nowUtcMillis() {
        Long network = networkTimeMillis();
        return network != null ? network : System.currentTimeMillis();
    }

    /** UTC time a fix was taken, following the spec's order of preference. */
    public static long fixTimeUtcMillis(Location location) {
        long ageMillis = location.getElapsedRealtimeAgeMillis();
        Long network = networkTimeMillis();
        if (network != null) {
            return network - ageMillis;
        }
        if (LocationManager.GPS_PROVIDER.equals(location.getProvider())) {
            // Straight from the satellites, so it is UTC regardless of what
            // the handset clock says.
            return location.getTime();
        }
        return System.currentTimeMillis() - ageMillis;
    }

    private static Long networkTimeMillis() {
        try {
            return SystemClock.currentNetworkTimeClock().millis();
        } catch (DateTimeException e) {
            return null;
        }
    }
}
