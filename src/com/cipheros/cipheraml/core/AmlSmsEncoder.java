/*
 * Copyright (C) 2026 The CipherOS Project
 *
 * Licensed under the CipherOS License 2.0. See the LICENSE file in the
 * root of this repository for the full terms.
 */

package com.cipheros.cipheraml.core;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Builds the AML SMS body described in ETSI TS 103 625 annex A.
 *
 * <p>A typical message looks like this:
 *
 * <pre>
 * A"ML=1;lt=+54.76397;lg=-0.18305;rd=50;top=20130717141935;lc=90;pm=W;
 * si=234300000000000;ei=356708041746734;mcc=234;mnc=30;ml=128
 * </pre>
 *
 * (on one line, wrapped here for reading). Mandatory attributes go out in
 * the order the spec lists them. {@code ml} is always last and counts the
 * whole message including itself.
 */
public final class AmlSmsEncoder {
    /** One GSM 7-bit text SMS. */
    public static final int MAX_TEXT_SMS_LENGTH = 160;

    /** 133 bytes of user data left after the port UDH, packed as septets. */
    public static final int MAX_DATA_SMS_LENGTH = 152;

    static final String HEADER = "A\"ML=1";
    static final String NO_LATITUDE = "+00.00000";
    static final String NO_LONGITUDE = "+000.00000";
    static final int MAX_RADIUS = 99999;
    static final int TAC_LENGTH = 8;

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("uuuuMMddHHmmss", Locale.ROOT)
                    .withZone(ZoneOffset.UTC);

    private final int mMaxLength;
    private boolean mIncludeOptionalFields;
    private boolean mPartialImei;

    public AmlSmsEncoder(int maxLength) {
        mMaxLength = maxLength;
    }

    /**
     * Adds the optional {@code al} and {@code toc} attributes when they fit.
     * Off by default because older receivers were written against version
     * 1 of the format and some of them are picky.
     */
    public AmlSmsEncoder setIncludeOptionalFields(boolean include) {
        mIncludeOptionalFields = include;
        return this;
    }

    /**
     * Sends only the TAC part of the IMEI. The spec allows this when SMS is
     * the only transport in use.
     */
    public AmlSmsEncoder setPartialImei(boolean partial) {
        mPartialImei = partial;
        return this;
    }

    public String encode(AmlReport report) {
        String mandatory = mandatoryFields(report);
        if (mIncludeOptionalFields) {
            String altitude = altitudeField(report);
            String toc = tocField(report);
            // Try everything first, then shed fields from the bottom of the
            // spec's list until it fits.
            String[] attempts = {
                mandatory + altitude + toc,
                mandatory + altitude,
            };
            for (String body : attempts) {
                String message = withLength(body);
                if (message.length() <= mMaxLength) {
                    return message;
                }
            }
        }
        String message = withLength(mandatory);
        if (message.length() > mMaxLength) {
            throw new IllegalStateException("AML message is " + message.length()
                    + " chars, limit is " + mMaxLength);
        }
        return message;
    }

    private String mandatoryFields(AmlReport report) {
        AmlLocation loc = report.location();
        StringBuilder sb = new StringBuilder(160);
        sb.append(HEADER).append(';');
        if (loc != null) {
            sb.append("lt=").append(formatCoordinate(loc.latitude())).append(';');
            sb.append("lg=").append(formatCoordinate(loc.longitude())).append(';');
            sb.append("rd=").append(formatRadius(loc.radiusMeters())).append(';');
            sb.append("top=").append(formatTime(loc.timeMillis())).append(';');
            sb.append("lc=").append(report.confidencePercent()).append(';');
            sb.append("pm=").append(loc.method().smsCode()).append(';');
        } else {
            // Annex A spells out exactly what a failed attempt looks like.
            sb.append("lt=").append(NO_LATITUDE).append(';');
            sb.append("lg=").append(NO_LONGITUDE).append(';');
            sb.append("rd=N;");
            sb.append("top=").append(formatTime(report.failureTimeMillis())).append(';');
            sb.append("lc=0;");
            sb.append("pm=").append(PositioningMethod.NONE.smsCode()).append(';');
        }
        sb.append("si=").append(partialImsi(report)).append(';');
        String imei = nullToEmpty(report.imei());
        sb.append("ei=").append(mPartialImei ? partialImei(imei) : imei).append(';');
        sb.append("mcc=").append(nullToEmpty(report.networkMcc())).append(';');
        sb.append("mnc=").append(nullToEmpty(report.networkMnc())).append(';');
        return sb.toString();
    }

    private static String altitudeField(AmlReport report) {
        AmlLocation loc = report.location();
        if (loc == null) {
            return "al=0;";
        }
        if (loc.altitudeMeters() == null) {
            return "";
        }
        return "al=" + String.format(Locale.ROOT, "%.1f", loc.altitudeMeters()) + ";";
    }

    private static String tocField(AmlReport report) {
        if (report.callTimeMillis() <= 0) {
            return "";
        }
        return "toc=" + formatTime(report.callTimeMillis()) + ";";
    }

    /**
     * Appends {@code ml=N} where N is the length of the finished message,
     * including the ml attribute itself. Adding digits can push the length
     * over a power of ten, so loop until it settles.
     */
    static String withLength(String body) {
        int length = body.length() + "ml=".length() + 1;
        while (true) {
            int candidate = body.length() + "ml=".length() + Integer.toString(length).length();
            if (candidate == length) {
                return body + "ml=" + length;
            }
            length = candidate;
        }
    }

    /** Signed decimal degrees with five places and no zero padding. */
    static String formatCoordinate(double degrees) {
        return String.format(Locale.ROOT, "%+.5f", degrees);
    }

    /** Whole metres, rounded up so the circle never shrinks. */
    static String formatRadius(double meters) {
        long rounded = (long) Math.ceil(meters);
        if (rounded < 1) {
            rounded = 1;
        }
        if (rounded > MAX_RADIUS) {
            rounded = MAX_RADIUS;
        }
        return Long.toString(rounded);
    }

    static String formatTime(long utcMillis) {
        return TIME_FORMAT.format(java.time.Instant.ofEpochMilli(utcMillis));
    }

    /**
     * Keeps MCC and MNC and zeroes the rest, as annex A asks. The MSISDN
     * already travels in the SMS header, so the PSAP does not need the
     * subscriber part of the IMSI.
     */
    static String partialImsi(AmlReport report) {
        String imsi = report.imsi();
        if (imsi == null || imsi.isEmpty()) {
            return "";
        }
        int keep = nullToEmpty(report.homeMcc()).length() + nullToEmpty(report.homeMnc()).length();
        if (keep < 5) {
            // No SIM operator to go by. MCC is 3 digits and MNC at least 2.
            keep = 5;
        }
        return maskAfter(imsi, keep);
    }

    /** First eight digits are the TAC, which only identifies the model. */
    static String partialImei(String imei) {
        if (imei.isEmpty()) {
            return imei;
        }
        return maskAfter(imei, TAC_LENGTH);
    }

    private static String maskAfter(String digits, int keep) {
        if (digits.length() <= keep) {
            return digits;
        }
        StringBuilder sb = new StringBuilder(digits.length());
        sb.append(digits, 0, keep);
        for (int i = keep; i < digits.length(); i++) {
            sb.append('0');
        }
        return sb.toString();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
