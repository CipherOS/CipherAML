/*
 * Copyright (C) 2026 The CipherOS Project
 *
 * Licensed under the CipherOS License 2.0. See the LICENSE file in the
 * root of this repository for the full terms.
 */

package com.cipheros.cipheraml.telephony;

import android.content.Context;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import java.util.function.Supplier;

/**
 * The phone and network identifiers AML needs, read once at the start of
 * a session.
 *
 * <p>Any of these can be missing. No SIM, limited service and dual SIM
 * phones with a dead slot are all normal during an emergency, so every
 * lookup is allowed to fail on its own without taking the others down.
 * That includes the UnsupportedOperationException newer releases throw
 * when the device lacks a telephony feature.
 */
public final class SubscriberSnapshot {
    private static final String TAG = "CipherAML";

    public final int subId;
    public final String imsi;
    public final String imei;
    public final String networkMcc;
    public final String networkMnc;
    public final String homeMcc;
    public final String homeMnc;
    public final String msisdn;
    public final String networkCountryIso;

    private SubscriberSnapshot(int subId, String imsi, String imei, String networkOperator,
            String simOperator, String msisdn, String networkCountryIso) {
        this.subId = subId;
        this.imsi = emptyToNull(imsi);
        this.imei = emptyToNull(imei);
        this.networkMcc = mcc(networkOperator);
        this.networkMnc = mnc(networkOperator);
        this.homeMcc = mcc(simOperator);
        this.homeMnc = mnc(simOperator);
        this.msisdn = emptyToNull(msisdn);
        this.networkCountryIso = emptyToNull(networkCountryIso);
    }

    public static SubscriberSnapshot capture(Context context, int subId) {
        TelephonyManager base = context.getSystemService(TelephonyManager.class);
        boolean validSub = SubscriptionManager.isValidSubscriptionId(subId);
        if (base == null) {
            return new SubscriberSnapshot(subId, null, null, null, null, null, null);
        }
        TelephonyManager tm = validSub ? base.createForSubscriptionId(subId) : base;

        String networkOperator = safe("network operator", tm::getNetworkOperator);
        if (TextUtils.isEmpty(networkOperator) && tm != base) {
            networkOperator = safe("network operator", base::getNetworkOperator);
        }

        String imsi = validSub ? safe("IMSI", tm::getSubscriberId) : null;
        String simOperator = validSub ? safe("SIM operator", tm::getSimOperator) : null;

        int slot = validSub ? SubscriptionManager.getSlotIndex(subId)
                : SubscriptionManager.INVALID_SIM_SLOT_INDEX;
        String imei = safe("IMEI", () -> slot >= 0 ? base.getImei(slot) : base.getImei());

        String msisdn = null;
        SubscriptionManager sm = context.getSystemService(SubscriptionManager.class);
        if (validSub && sm != null) {
            msisdn = safe("MSISDN", () -> sm.getPhoneNumber(subId));
        }

        return new SubscriberSnapshot(subId, imsi, imei, networkOperator, simOperator, msisdn,
                networkCountryIso(base, tm));
    }

    private static String networkCountryIso(TelephonyManager base, TelephonyManager tm) {
        String iso = safe("network country", tm::getNetworkCountryIso);
        if (!TextUtils.isEmpty(iso)) {
            return iso;
        }
        // Without a SIM there is no subscription to ask, but every modem
        // still knows which country it is camped in.
        Integer modems = safeInt(base::getActiveModemCount);
        for (int slot = 0; modems != null && slot < modems; slot++) {
            int s = slot;
            iso = safe("network country", () -> base.getNetworkCountryIso(s));
            if (!TextUtils.isEmpty(iso)) {
                return iso;
            }
        }
        return null;
    }

    private static String safe(String what, Supplier<String> lookup) {
        try {
            return lookup.get();
        } catch (RuntimeException e) {
            // SecurityException, UnsupportedOperationException,
            // IllegalArgumentException for a bad slot... none of them are
            // worth failing the whole session over.
            Log.w(TAG, "Could not read " + what + ": " + e);
            return null;
        }
    }

    private static Integer safeInt(Supplier<Integer> lookup) {
        try {
            return lookup.get();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String mcc(String operator) {
        return operator != null && operator.length() >= 5 ? operator.substring(0, 3) : null;
    }

    private static String mnc(String operator) {
        return operator != null && operator.length() >= 5 ? operator.substring(3) : null;
    }

    private static String emptyToNull(String s) {
        return TextUtils.isEmpty(s) ? null : s;
    }

    /** Safe for logs: no identifiers, just what we have and where we are. */
    @Override
    public String toString() {
        return "sub=" + subId + " net=" + networkMcc + "/" + networkMnc + " home=" + homeMcc
                + "/" + homeMnc + " iso=" + networkCountryIso + " imsi=" + (imsi != null)
                + " imei=" + (imei != null) + " msisdn=" + (msisdn != null);
    }
}
