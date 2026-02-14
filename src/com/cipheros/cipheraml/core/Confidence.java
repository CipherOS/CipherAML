/*
 * Copyright (C) 2026 The CipherOS Project
 *
 * Licensed under the CipherOS License 2.0. See the LICENSE file in the
 * root of this repository for the full terms.
 */

package com.cipheros.cipheraml.core;

/**
 * Converts Android's horizontal accuracy into a radius at another
 * confidence level.
 *
 * <p>Android defines {@code Location#getAccuracy()} as the radius of 68%
 * confidence. Some PSAPs prefer 90% or 95%. Treating the horizontal error
 * as a circular 2D normal distribution, the chance of being inside radius
 * r is {@code 1 - exp(-r^2 / 2 sigma^2)}, so the radius for probability p
 * is {@code sigma * sqrt(-2 ln(1 - p))}. We back out sigma from the 68%
 * radius and plug in the target.
 */
public final class Confidence {
    /** What Android's getAccuracy() is defined against. */
    public static final int ANDROID_DEFAULT_PERCENT = 68;

    private Confidence() {}

    /** Scales a 68% radius to the given confidence percentage. */
    public static double scaleRadius(double radius68, int targetPercent) {
        if (targetPercent < 1 || targetPercent > 99) {
            throw new IllegalArgumentException("confidence must be 1..99, got " + targetPercent);
        }
        if (targetPercent == ANDROID_DEFAULT_PERCENT) {
            return radius68;
        }
        return radius68 * factor(targetPercent) / factor(ANDROID_DEFAULT_PERCENT);
    }

    private static double factor(int percent) {
        return Math.sqrt(-2.0 * Math.log(1.0 - percent / 100.0));
    }
}
