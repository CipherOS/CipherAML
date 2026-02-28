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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AmlHttpsEncoderTest {
    @Test
    public void encode_fullReport() {
        AmlLocation loc = new AmlLocation.Builder(55.85732, -4.26325, 10.4, 1476189444435L,
                PositioningMethod.GNSS)
                .setAltitudeMeters(0.0)
                .build();
        AmlReport report = new AmlReport.Builder()
                .setLocation(loc)
                .setConfidencePercent(68)
                .setSource(AmlReport.Source.CALL)
                .setCallTimeMillis(1476189440000L)
                .setEmergencyNumber("112")
                .setImsi("234159176307582")
                .setImei("354773072099116")
                .setNetworkMccMnc("234", "15")
                .setHomeMccMnc("234", "15")
                .setMsisdn("+447477593102")
                .setDeviceModel("Cipher Phone 1")
                .build();

        String expected = "v=1"
                + "&location_latitude=55.8573200"
                + "&location_longitude=-4.2632500"
                + "&location_accuracy=10.4"
                + "&location_time=1476189444435"
                + "&location_confidence=0.68"
                + "&location_source=gps"
                + "&device_imsi=234159176307582"
                + "&device_imei=354773072099116"
                + "&cell_network_mcc=234"
                + "&cell_network_mnc=15"
                + "&location_altitude=0.0"
                + "&time=1476189440000"
                + "&emergency_number=112"
                + "&source=CALL"
                + "&device_number=%2B447477593102"
                + "&device_model=Cipher+Phone+1"
                + "&cell_home_mcc=234"
                + "&cell_home_mnc=15";
        assertEquals(expected, AmlHttpsEncoder.encode(report));
    }

    @Test
    public void encode_noLocation_zeroesTheFix() {
        AmlReport report = new AmlReport.Builder()
                .setFailureTimeMillis(1476189464435L)
                .setImei("354773072099116")
                .build();
        String body = AmlHttpsEncoder.encode(report);
        assertTrue(body, body.startsWith("v=1"
                + "&location_latitude=%2B00.00000"
                + "&location_longitude=%2B000.00000"
                + "&location_accuracy=0"
                + "&location_time=1476189464435"
                + "&location_confidence=0"
                + "&location_source=unknown"
                + "&device_imsi="
                + "&device_imei=354773072099116"
                + "&cell_network_mcc="
                + "&cell_network_mnc="
                + "&location_altitude=0"));
    }

    @Test
    public void encode_skipsUnknownOptionalFields() {
        AmlLocation loc = new AmlLocation.Builder(1, 2, 3, 4, PositioningMethod.WIFI).build();
        String body = AmlHttpsEncoder.encode(new AmlReport.Builder().setLocation(loc).build());
        assertFalse(body, body.contains("location_altitude"));
        assertFalse(body, body.contains("device_number"));
        assertFalse(body, body.contains("&time="));
        assertTrue(body, body.contains("&location_source=wifi&"));
    }
}
