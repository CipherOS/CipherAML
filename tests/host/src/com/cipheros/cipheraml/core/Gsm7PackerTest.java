/*
 * Copyright (C) 2026 The CipherOS Project
 *
 * Licensed under the CipherOS License 2.0. See the LICENSE file in the
 * root of this repository for the full terms.
 */

package com.cipheros.cipheraml.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class Gsm7PackerTest {
    @Test
    public void pack_knownVector() {
        // The classic example from the GSM docs.
        byte[] expected = {
            (byte) 0xE8, 0x32, (byte) 0x9B, (byte) 0xFD, 0x46, (byte) 0x97,
            (byte) 0xD9, (byte) 0xEC, 0x37,
        };
        assertArrayEquals(expected, Gsm7Packer.pack("hellohello"));
    }

    @Test
    public void pack_eightChars_fillsSevenBytesExactly() {
        assertEquals(7, Gsm7Packer.pack("ABCDEFGH").length);
    }

    @Test
    public void pack_sevenChars_padsWithCarriageReturn() {
        byte[] packed = Gsm7Packer.pack("1234567");
        assertEquals(7, packed.length);
        assertEquals(0x0D << 1, packed[6] & 0xFE);
    }

    @Test
    public void pack_fullAmlMessage_fitsInDataSms() {
        String msg = "A\"ML=1;lt=+54.76397;lg=-0.18305;rd=50;top=20130717141935;"
                + "lc=90;pm=W;si=234300000000000;ei=1234567890123456;mcc=234;mnc=30;ml=128";
        // 128 septets is exactly 112 bytes, well under the 133 left after
        // the port header.
        assertEquals(112, Gsm7Packer.pack(msg).length);
    }

    @Test(expected = IllegalArgumentException.class)
    public void pack_rejectsDollar() {
        Gsm7Packer.pack("$");
    }

    @Test(expected = IllegalArgumentException.class)
    public void pack_rejectsAt() {
        // '@' is 0x00 in GSM, not 0x40.
        Gsm7Packer.pack("@");
    }
}
