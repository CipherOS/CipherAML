/*
 * Copyright (C) 2026 The CipherOS Project
 *
 * Licensed under the CipherOS License 2.0. See the LICENSE file in the
 * root of this repository for the full terms.
 */

package com.cipheros.cipheraml.core;

/**
 * Packs text into GSM 7-bit septets (3GPP TS 23.038, 6.1.2.1.1).
 *
 * <p>Clause 6.3.2 of TS 103 625 asks for the AML payload of a data SMS to
 * be septet packed even though the SMS itself is sent with the 8-bit data
 * coding scheme. That is how 152 characters fit in 133 bytes.
 *
 * <p>We only accept characters whose GSM default alphabet code is the
 * same as their ASCII code. That covers everything an AML message can
 * contain and means we do not need a translation table.
 *
 * <p>Some legacy AML receivers expect big endian packing. New
 * deployments are told to use the standard little endian layout, which
 * is what this does.
 */
public final class Gsm7Packer {
    private static final int CR = 0x0D;

    private Gsm7Packer() {}

    public static byte[] pack(String text) {
        int septets = text.length();
        byte[] out = new byte[(septets * 7 + 7) / 8];
        int bit = 0;
        for (int i = 0; i < septets; i++) {
            int code = toGsm(text.charAt(i));
            int index = bit / 8;
            int shift = bit % 8;
            out[index] |= (byte) (code << shift);
            if (shift > 1) {
                out[index + 1] |= (byte) (code >> (8 - shift));
            }
            bit += 7;
        }
        // With 8n-1 characters the last octet has 7 spare bits, which a
        // receiver could read as a trailing '@'. TS 23.038 says to fill
        // them with CR instead.
        if (septets % 8 == 7) {
            out[out.length - 1] |= (byte) (CR << 1);
        }
        return out;
    }

    static int toGsm(char c) {
        boolean same = (c >= 0x20 && c <= 0x3F && c != '$')
                || (c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z');
        if (!same) {
            throw new IllegalArgumentException(
                    "char 0x" + Integer.toHexString(c) + " has no 1:1 GSM 7-bit mapping");
        }
        return c;
    }
}
