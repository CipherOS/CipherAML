/*
 * Copyright (C) 2026 The CipherOS Project
 *
 * Licensed under the CipherOS License 2.0. See the LICENSE file in the
 * root of this repository for the full terms.
 */

package com.cipheros.cipheraml;

import android.content.Context;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.telephony.emergency.EmergencyNumber;
import android.util.Log;

/**
 * Hears about outgoing emergency calls and SMS from telephony.
 *
 * <p>These callbacks are system APIs guarded by READ_ACTIVE_EMERGENCY_SESSION
 * and fire for every subscription, which is exactly what we want. They
 * cover the dialer, the lock screen emergency button and anything else
 * that goes through telephony, without us having to hook each one.
 */
final class EmergencyListener extends TelephonyCallback
        implements TelephonyCallback.OutgoingEmergencyCallListener,
        TelephonyCallback.OutgoingEmergencySmsListener {
    private static final String TAG = "CipherAML";

    private final AmlController mController;

    private EmergencyListener(AmlController controller) {
        mController = controller;
    }

    /** Registers with telephony. Safe to call once per process. */
    static void register(Context context) {
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        if (tm == null) {
            Log.w(TAG, "No telephony on this device, AML stays idle");
            return;
        }
        try {
            tm.registerTelephonyCallback(context.getMainExecutor(),
                    new EmergencyListener(AmlController.get(context)));
            Log.i(TAG, "Listening for emergency calls");
        } catch (SecurityException e) {
            // Almost always a missing privapp-permissions entry. Loud on
            // purpose, this means AML is dead on this build.
            Log.wtf(TAG, "Cannot listen for emergency calls, check the privapp allowlist", e);
        }
    }

    @Override
    public void onOutgoingEmergencyCall(EmergencyNumber number, int subscriptionId) {
        mController.onEmergencyCall(number.getNumber(), subscriptionId);
    }

    @Override
    public void onOutgoingEmergencySms(EmergencyNumber number, int subscriptionId) {
        mController.onEmergencySms(number.getNumber(), subscriptionId);
    }
}
