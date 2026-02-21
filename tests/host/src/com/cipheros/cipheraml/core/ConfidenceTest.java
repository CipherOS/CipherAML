/*
 * Copyright (C) 2026 The CipherOS Project
 *
 * Licensed under the CipherOS License 2.0. See the LICENSE file in the
 * root of this repository for the full terms.
 */

package com.cipheros.cipheraml.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ConfidenceTest {
    @Test
    public void scaleRadius_sameConfidence_isIdentity() {
        assertEquals(20.0, Confidence.scaleRadius(20.0, 68), 0.0);
    }

    @Test
    public void scaleRadius_to95_matchesRayleighRatio() {
        // sqrt(-2 ln 0.05) / sqrt(-2 ln 0.32) is about 1.6214.
        assertEquals(16.214, Confidence.scaleRadius(10.0, 95), 0.001);
    }

    @Test
    public void scaleRadius_to90_isBetween68And95() {
        double r90 = Confidence.scaleRadius(10.0, 90);
        assertEquals(14.216, r90, 0.001);
    }

    @Test(expected = IllegalArgumentException.class)
    public void scaleRadius_rejects100() {
        Confidence.scaleRadius(10.0, 100);
    }
}
