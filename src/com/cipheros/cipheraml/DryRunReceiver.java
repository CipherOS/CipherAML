/*
 * Copyright (C) 2026 The CipherOS Project
 *
 * Licensed under the CipherOS License 2.0. See the LICENSE file in the
 * root of this repository for the full terms.
 */

package com.cipheros.cipheraml;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SubscriptionManager;

/**
 * Runs a full AML session that logs instead of sends.
 *
 * <p>Please never test AML by calling an emergency number. Use this:
 *
 * <pre>
 * adb shell am broadcast -a com.cipheros.cipheraml.action.DRY_RUN \
 *     -p com.cipheros.cipheraml [--es number 112] [--ei sub_id 1]
 * adb logcat -s CipherAML
 * </pre>
 *
 * The receiver is guarded by android.permission.DUMP, so only the shell
 * and the system can trigger it.
 */
public final class DryRunReceiver extends BroadcastReceiver {
    static final String ACTION_DRY_RUN = "com.cipheros.cipheraml.action.DRY_RUN";
    static final String EXTRA_NUMBER = "number";
    static final String EXTRA_SUB_ID = "sub_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_DRY_RUN.equals(intent.getAction())) {
            return;
        }
        String number = intent.getStringExtra(EXTRA_NUMBER);
        if (number == null || number.isEmpty()) {
            number = "112";
        }
        int subId = intent.getIntExtra(EXTRA_SUB_ID,
                SubscriptionManager.getDefaultVoiceSubscriptionId());
        AmlController.get(context).startDryRun(number, subId);
    }
}
