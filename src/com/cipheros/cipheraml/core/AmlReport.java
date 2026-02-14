/*
 * Copyright (C) 2026 The CipherOS Project
 *
 * Licensed under the CipherOS License 2.0. See the LICENSE file in the
 * root of this repository for the full terms.
 */

package com.cipheros.cipheraml.core;

/**
 * Everything that goes into one AML message, independent of transport.
 *
 * <p>A report with no location is still a valid report. The spec wants a
 * message sent even when every positioning method failed, so the PSAP at
 * least knows we tried.
 */
public final class AmlReport {
    /** What kicked off the emergency session. */
    public enum Source {
        CALL,
        SMS
    }

    private final AmlLocation mLocation;
    private final long mFailureTimeMillis;
    private final int mConfidencePercent;
    private final Source mSource;
    private final long mCallTimeMillis;
    private final String mEmergencyNumber;
    private final String mImsi;
    private final String mImei;
    private final String mNetworkMcc;
    private final String mNetworkMnc;
    private final String mHomeMcc;
    private final String mHomeMnc;
    private final String mMsisdn;
    private final String mDeviceModel;

    private AmlReport(Builder b) {
        mLocation = b.mLocation;
        mFailureTimeMillis = b.mFailureTimeMillis;
        mConfidencePercent = b.mConfidencePercent;
        mSource = b.mSource;
        mCallTimeMillis = b.mCallTimeMillis;
        mEmergencyNumber = b.mEmergencyNumber;
        mImsi = b.mImsi;
        mImei = b.mImei;
        mNetworkMcc = b.mNetworkMcc;
        mNetworkMnc = b.mNetworkMnc;
        mHomeMcc = b.mHomeMcc;
        mHomeMnc = b.mHomeMnc;
        mMsisdn = b.mMsisdn;
        mDeviceModel = b.mDeviceModel;
    }

    /** The fix to report, or null if we could not get one. */
    public AmlLocation location() {
        return mLocation;
    }

    public boolean hasLocation() {
        return mLocation != null;
    }

    /** Time the location attempt was given up on. Only meaningful without a fix. */
    public long failureTimeMillis() {
        return mFailureTimeMillis;
    }

    /** Confidence the radius is reported at, 1 to 99. */
    public int confidencePercent() {
        return mConfidencePercent;
    }

    public Source source() {
        return mSource;
    }

    /** UTC time the emergency call or SMS was started, or 0 if unknown. */
    public long callTimeMillis() {
        return mCallTimeMillis;
    }

    public String emergencyNumber() {
        return mEmergencyNumber;
    }

    public String imsi() {
        return mImsi;
    }

    public String imei() {
        return mImei;
    }

    public String networkMcc() {
        return mNetworkMcc;
    }

    public String networkMnc() {
        return mNetworkMnc;
    }

    public String homeMcc() {
        return mHomeMcc;
    }

    public String homeMnc() {
        return mHomeMnc;
    }

    public String msisdn() {
        return mMsisdn;
    }

    public String deviceModel() {
        return mDeviceModel;
    }

    public static final class Builder {
        private AmlLocation mLocation;
        private long mFailureTimeMillis;
        private int mConfidencePercent = Confidence.ANDROID_DEFAULT_PERCENT;
        private Source mSource = Source.CALL;
        private long mCallTimeMillis;
        private String mEmergencyNumber;
        private String mImsi;
        private String mImei;
        private String mNetworkMcc;
        private String mNetworkMnc;
        private String mHomeMcc;
        private String mHomeMnc;
        private String mMsisdn;
        private String mDeviceModel;

        public Builder setLocation(AmlLocation location) {
            mLocation = location;
            return this;
        }

        public Builder setFailureTimeMillis(long time) {
            mFailureTimeMillis = time;
            return this;
        }

        public Builder setConfidencePercent(int percent) {
            if (percent < 1 || percent > 99) {
                throw new IllegalArgumentException("confidence must be 1..99, got " + percent);
            }
            mConfidencePercent = percent;
            return this;
        }

        public Builder setSource(Source source) {
            mSource = source;
            return this;
        }

        public Builder setCallTimeMillis(long time) {
            mCallTimeMillis = time;
            return this;
        }

        public Builder setEmergencyNumber(String number) {
            mEmergencyNumber = number;
            return this;
        }

        public Builder setImsi(String imsi) {
            mImsi = imsi;
            return this;
        }

        public Builder setImei(String imei) {
            mImei = imei;
            return this;
        }

        public Builder setNetworkMccMnc(String mcc, String mnc) {
            mNetworkMcc = mcc;
            mNetworkMnc = mnc;
            return this;
        }

        public Builder setHomeMccMnc(String mcc, String mnc) {
            mHomeMcc = mcc;
            mHomeMnc = mnc;
            return this;
        }

        public Builder setMsisdn(String msisdn) {
            mMsisdn = msisdn;
            return this;
        }

        public Builder setDeviceModel(String model) {
            mDeviceModel = model;
            return this;
        }

        public AmlReport build() {
            return new AmlReport(this);
        }
    }
}
