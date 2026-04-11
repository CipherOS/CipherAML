/*
 * Copyright (C) 2026 The CipherOS Project
 *
 * Licensed under the CipherOS License 2.0. See the LICENSE file in the
 * root of this repository for the full terms.
 */

package com.cipheros.cipheraml.location;

import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;

import com.cipheros.cipheraml.core.AmlLocation;
import com.cipheros.cipheraml.core.Confidence;
import com.cipheros.cipheraml.core.PositioningMethod;

/** Turns an Android {@link Location} into what we put on the wire. */
public final class FixConverter {
    /**
     * Some network location providers (UnifiedNlp backends among them) tag
     * fixes with this extra. If it is missing we have to guess.
     */
    static final String EXTRA_NETWORK_LOCATION_TYPE = "networkLocationType";

    /**
     * Without a hint from the provider, anything tighter than this from
     * the network provider is almost certainly Wi-Fi rather than cell.
     */
    static final float WIFI_GUESS_MAX_ACCURACY_METERS = 200f;

    private FixConverter() {}

    public static AmlLocation toAmlLocation(Location loc, int confidencePercent) {
        double radius = Confidence.scaleRadius(loc.getAccuracy(), confidencePercent);
        AmlLocation.Builder b = new AmlLocation.Builder(loc.getLatitude(), loc.getLongitude(),
                radius, AmlClock.fixTimeUtcMillis(loc), methodOf(loc));
        if (loc.hasAltitude()) {
            b.setAltitudeMeters(loc.getAltitude());
        }
        if (loc.hasVerticalAccuracy()) {
            b.setVerticalAccuracyMeters((double) loc.getVerticalAccuracyMeters());
        }
        if (loc.hasBearing()) {
            b.setBearingDegrees((double) loc.getBearing());
        }
        if (loc.hasSpeed()) {
            b.setSpeedMetersPerSecond((double) loc.getSpeed());
        }
        return b.build();
    }

    static PositioningMethod methodOf(Location loc) {
        if (LocationManager.GPS_PROVIDER.equals(loc.getProvider())) {
            return PositioningMethod.GNSS;
        }
        Bundle extras = loc.getExtras();
        if (extras != null) {
            String type = extras.getString(EXTRA_NETWORK_LOCATION_TYPE);
            if ("wifi".equals(type)) {
                return PositioningMethod.WIFI;
            }
            if ("cell".equals(type)) {
                return PositioningMethod.CELL;
            }
        }
        return loc.getAccuracy() <= WIFI_GUESS_MAX_ACCURACY_METERS
                ? PositioningMethod.WIFI : PositioningMethod.CELL;
    }
}
