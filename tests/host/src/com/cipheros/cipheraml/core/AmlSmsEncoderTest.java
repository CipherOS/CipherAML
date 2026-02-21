/*
 * Copyright (C) 2026 The CipherOS Project
 *
 * Licensed under the CipherOS License 2.0. See the LICENSE file in the
 * root of this repository for the full terms.
 */

package com.cipheros.cipheraml.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Instant;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AmlSmsEncoderTest {
    private static final long TOP = Instant.parse("2013-07-17T14:19:35Z").toEpochMilli();
    private static final long TOC = Instant.parse("2013-07-17T14:19:20Z").toEpochMilli();

    private static AmlReport.Builder specExample() {
        AmlLocation loc = new AmlLocation.Builder(54.76397, -0.18305, 50, TOP,
                PositioningMethod.WIFI).build();
        return new AmlReport.Builder()
                .setLocation(loc)
                .setConfidencePercent(90)
                .setImsi("234301234567890")
                .setHomeMccMnc("234", "30")
                .setImei("1234567890123456")
                .setNetworkMccMnc("234", "30");
    }

    @Test
    public void encode_matchesSpecFigure2() {
        // Figure 2 of TS 103 625, except si is masked the way annex A says
        // it should be. Same length, so ml stays at 128.
        String expected = "A\"ML=1;lt=+54.76397;lg=-0.18305;rd=50;top=20130717141935;"
                + "lc=90;pm=W;si=234300000000000;ei=1234567890123456;mcc=234;mnc=30;ml=128";
        String actual = new AmlSmsEncoder(AmlSmsEncoder.MAX_TEXT_SMS_LENGTH)
                .encode(specExample().build());
        assertEquals(expected, actual);
        assertEquals(128, actual.length());
    }

    @Test
    public void encode_noLocation_usesAnnexAPlaceholders() {
        AmlReport report = new AmlReport.Builder()
                .setFailureTimeMillis(TOP)
                .setImsi("234301234567890")
                .setHomeMccMnc("234", "30")
                .setImei("356708041746734")
                .setNetworkMccMnc("234", "30")
                .build();
        String actual = new AmlSmsEncoder(AmlSmsEncoder.MAX_TEXT_SMS_LENGTH).encode(report);
        assertTrue(actual, actual.startsWith(
                "A\"ML=1;lt=+00.00000;lg=+000.00000;rd=N;top=20130717141935;lc=0;pm=N;"));
        assertMlIsLength(actual);
    }

    @Test
    public void encode_westernLongitude_hasNoZeroPadding() {
        AmlLocation loc = new AmlLocation.Builder(55.743171, -4.268814, 12.2, TOP,
                PositioningMethod.GNSS).build();
        String actual = new AmlSmsEncoder(AmlSmsEncoder.MAX_TEXT_SMS_LENGTH)
                .encode(new AmlReport.Builder().setLocation(loc).build());
        assertTrue(actual, actual.contains(";lt=+55.74317;lg=-4.26881;rd=13;"));
        assertTrue(actual, actual.contains(";lc=68;pm=G;"));
    }

    @Test
    public void encode_optionalFields_addedWhenAskedFor() {
        AmlLocation loc = new AmlLocation.Builder(54.76397, -0.18305, 50, TOP,
                PositioningMethod.GNSS).setAltitudeMeters(4.04).build();
        AmlReport report = specExample().setLocation(loc).setCallTimeMillis(TOC).build();

        String plain = new AmlSmsEncoder(AmlSmsEncoder.MAX_TEXT_SMS_LENGTH).encode(report);
        assertFalse(plain, plain.contains("al="));
        assertFalse(plain, plain.contains("toc="));

        String full = new AmlSmsEncoder(AmlSmsEncoder.MAX_TEXT_SMS_LENGTH)
                .setIncludeOptionalFields(true)
                .encode(report);
        assertTrue(full, full.contains(";al=4.0;toc=20130717141920;ml="));
        assertMlIsLength(full);
    }

    @Test
    public void encode_optionalFields_droppedWhenTooLong() {
        AmlLocation loc = new AmlLocation.Builder(54.76397, -0.18305, 50, TOP,
                PositioningMethod.GNSS).setAltitudeMeters(4.0).build();
        AmlReport report = specExample().setLocation(loc).setCallTimeMillis(TOC).build();

        // 128 for the mandatory part, 7 for al, not enough for toc.
        String actual = new AmlSmsEncoder(140).setIncludeOptionalFields(true).encode(report);
        assertTrue(actual, actual.contains(";al=4.0;ml="));
        assertFalse(actual, actual.contains("toc="));
        assertMlIsLength(actual);
    }

    @Test
    public void encode_partialImei_keepsOnlyTac() {
        AmlReport report = specExample().setImei("356708041746734").build();
        String actual = new AmlSmsEncoder(AmlSmsEncoder.MAX_TEXT_SMS_LENGTH)
                .setPartialImei(true)
                .encode(report);
        assertTrue(actual, actual.contains(";ei=356708040000000;"));
    }

    @Test
    public void partialImsi_threeDigitMnc() {
        AmlReport report = new AmlReport.Builder()
                .setImsi("310260123456789")
                .setHomeMccMnc("310", "260")
                .build();
        assertEquals("310260000000000", AmlSmsEncoder.partialImsi(report));
    }

    @Test
    public void partialImsi_unknownOperator_keepsFiveDigits() {
        AmlReport report = new AmlReport.Builder().setImsi("234301234567890").build();
        assertEquals("234300000000000", AmlSmsEncoder.partialImsi(report));
    }

    @Test
    public void formatRadius_roundsUpAndClamps() {
        assertEquals("1", AmlSmsEncoder.formatRadius(0.2));
        assertEquals("13", AmlSmsEncoder.formatRadius(12.01));
        assertEquals("99999", AmlSmsEncoder.formatRadius(250000));
    }

    @Test
    public void withLength_handlesDigitRollover() {
        assertEquals("x;ml=6", AmlSmsEncoder.withLength("x;"));
        String body94 = "a".repeat(94);
        assertEquals(99, AmlSmsEncoder.withLength(body94).length());
        String body95 = "a".repeat(95);
        assertEquals(body95 + "ml=101", AmlSmsEncoder.withLength(body95));
    }

    private static void assertMlIsLength(String message) {
        int idx = message.lastIndexOf(";ml=");
        int ml = Integer.parseInt(message.substring(idx + 4));
        assertEquals(message, message.length(), ml);
    }
}
