/*
 * Copyright (C) 2026 The CipherOS Project
 *
 * Licensed under the CipherOS License 2.0. See the LICENSE file in the
 * root of this repository for the full terms.
 */

package com.cipheros.cipheraml.core;

/**
 * A single location fix in the shape AML wants it: a WGS 84 circle.
 *
 * <p>{@link #radiusMeters()} is the radius at whatever confidence the
 * report is going out with, not the raw 68% figure Android hands us. The
 * caller does that conversion, see {@link Confidence}.
 */
public final class AmlLocation {
    private final double mLatitude;
    private final double mLongitude;
    private final double mRadiusMeters;
    private final long mTimeMillis;
    private final PositioningMethod mMethod;
    private final Double mAltitudeMeters;
    private final Double mVerticalAccuracyMeters;
    private final Double mBearingDegrees;
    private final Double mSpeedMetersPerSecond;

    private AmlLocation(Builder b) {
        mLatitude = b.mLatitude;
        mLongitude = b.mLongitude;
        mRadiusMeters = b.mRadiusMeters;
        mTimeMillis = b.mTimeMillis;
        mMethod = b.mMethod;
        mAltitudeMeters = b.mAltitudeMeters;
        mVerticalAccuracyMeters = b.mVerticalAccuracyMeters;
        mBearingDegrees = b.mBearingDegrees;
        mSpeedMetersPerSecond = b.mSpeedMetersPerSecond;
    }

    public double latitude() {
        return mLatitude;
    }

    public double longitude() {
        return mLongitude;
    }

    public double radiusMeters() {
        return mRadiusMeters;
    }

    /** UTC time the fix was taken, in milliseconds since the epoch. */
    public long timeMillis() {
        return mTimeMillis;
    }

    public PositioningMethod method() {
        return mMethod;
    }

    /** Altitude above the WGS 84 ellipsoid, or null if we do not have one. */
    public Double altitudeMeters() {
        return mAltitudeMeters;
    }

    public Double verticalAccuracyMeters() {
        return mVerticalAccuracyMeters;
    }

    public Double bearingDegrees() {
        return mBearingDegrees;
    }

    public Double speedMetersPerSecond() {
        return mSpeedMetersPerSecond;
    }

    public static final class Builder {
        private final double mLatitude;
        private final double mLongitude;
        private final double mRadiusMeters;
        private final long mTimeMillis;
        private final PositioningMethod mMethod;
        private Double mAltitudeMeters;
        private Double mVerticalAccuracyMeters;
        private Double mBearingDegrees;
        private Double mSpeedMetersPerSecond;

        public Builder(double latitude, double longitude, double radiusMeters,
                long timeMillis, PositioningMethod method) {
            if (method == null || method == PositioningMethod.NONE) {
                throw new IllegalArgumentException("a real fix needs a real method");
            }
            mLatitude = latitude;
            mLongitude = longitude;
            mRadiusMeters = radiusMeters;
            mTimeMillis = timeMillis;
            mMethod = method;
        }

        public Builder setAltitudeMeters(Double altitude) {
            mAltitudeMeters = altitude;
            return this;
        }

        public Builder setVerticalAccuracyMeters(Double accuracy) {
            mVerticalAccuracyMeters = accuracy;
            return this;
        }

        public Builder setBearingDegrees(Double bearing) {
            mBearingDegrees = bearing;
            return this;
        }

        public Builder setSpeedMetersPerSecond(Double speed) {
            mSpeedMetersPerSecond = speed;
            return this;
        }

        public AmlLocation build() {
            return new AmlLocation(this);
        }
    }
}
