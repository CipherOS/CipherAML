/*
 * Copyright (C) 2026 The CipherOS Project
 *
 * Licensed under the CipherOS License 2.0. See the LICENSE file in the
 * root of this repository for the full terms.
 */

package com.cipheros.cipheraml.core;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Builds the application/x-www-form-urlencoded body from ETSI TS 103 625
 * annex B.
 *
 * <p>Mandatory keys are always written, even if we have nothing to put
 * in them, since the spec says receivers must cope with empty values but
 * does not promise they cope with missing keys. Optional keys are only
 * written when we actually know the value.
 *
 * <p>Note the spec is not consistent about {@code location_confidence}.
 * Table B.1 (normative) says it is a fraction between 0 and 1, while the
 * informative example in 6.4.7.3 shows a whole percentage. We follow the
 * table.
 */
public final class AmlHttpsEncoder {
    public static final String CONTENT_TYPE = "application/x-www-form-urlencoded";

    static final String VERSION = "1";

    private AmlHttpsEncoder() {}

    public static String encode(AmlReport report) {
        Form form = new Form();
        AmlLocation loc = report.location();

        form.put("v", VERSION);
        if (loc != null) {
            form.put("location_latitude", format("%.7f", loc.latitude()));
            form.put("location_longitude", format("%.7f", loc.longitude()));
            form.put("location_accuracy", format("%.1f", loc.radiusMeters()));
            form.put("location_time", Long.toString(loc.timeMillis()));
            form.put("location_confidence", format("%.2f", report.confidencePercent() / 100.0));
            form.put("location_source", loc.method().httpsName());
        } else {
            form.put("location_latitude", AmlSmsEncoder.NO_LATITUDE);
            form.put("location_longitude", AmlSmsEncoder.NO_LONGITUDE);
            form.put("location_accuracy", "0");
            form.put("location_time", Long.toString(report.failureTimeMillis()));
            form.put("location_confidence", "0");
            form.put("location_source", PositioningMethod.NONE.httpsName());
        }
        form.put("device_imsi", report.imsi());
        form.put("device_imei", report.imei());
        form.put("cell_network_mcc", report.networkMcc());
        form.put("cell_network_mnc", report.networkMnc());

        // Optional from here on.
        if (loc == null) {
            form.putOptional("location_altitude", "0");
        } else if (loc.altitudeMeters() != null) {
            form.putOptional("location_altitude", format("%.1f", loc.altitudeMeters()));
        }
        if (report.callTimeMillis() > 0) {
            form.putOptional("time", Long.toString(report.callTimeMillis()));
        }
        form.putOptional("emergency_number", report.emergencyNumber());
        form.putOptional("source", report.source() == null ? null : report.source().name());
        if (loc != null) {
            if (loc.verticalAccuracyMeters() != null) {
                form.putOptional("location_vertical_accuracy",
                        format("%.1f", loc.verticalAccuracyMeters()));
            }
            if (loc.bearingDegrees() != null) {
                form.putOptional("location_bearing", format("%.1f", loc.bearingDegrees()));
            }
            if (loc.speedMetersPerSecond() != null) {
                form.putOptional("location_speed", format("%.1f", loc.speedMetersPerSecond()));
            }
        }
        form.putOptional("device_number", report.msisdn());
        form.putOptional("device_model", report.deviceModel());
        form.putOptional("cell_home_mcc", report.homeMcc());
        form.putOptional("cell_home_mnc", report.homeMnc());
        return form.toString();
    }

    private static String format(String pattern, double value) {
        return String.format(Locale.ROOT, pattern, value);
    }

    private static final class Form {
        private final StringBuilder mBody = new StringBuilder(512);

        void put(String key, String value) {
            if (mBody.length() > 0) {
                mBody.append('&');
            }
            mBody.append(key).append('=');
            if (value != null) {
                mBody.append(URLEncoder.encode(value, StandardCharsets.UTF_8));
            }
        }

        void putOptional(String key, String value) {
            if (value != null && !value.isEmpty()) {
                put(key, value);
            }
        }

        @Override
        public String toString() {
            return mBody.toString();
        }
    }
}
