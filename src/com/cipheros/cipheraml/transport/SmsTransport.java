/*
 * Copyright (C) 2026 The CipherOS Project
 *
 * Licensed under the CipherOS License 2.0. See the LICENSE file in the
 * root of this repository for the full terms.
 */

package com.cipheros.cipheraml.transport;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.util.Log;
import android.util.SparseArray;

import com.cipheros.cipheraml.config.SmsEndpoint;
import com.cipheros.cipheraml.core.AmlReport;
import com.cipheros.cipheraml.core.AmlSmsEncoder;
import com.cipheros.cipheraml.core.Gsm7Packer;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sends the AML SMS.
 *
 * <p>TS 103 625 says the user must never see a record of the AML
 * message. Text messages go out through sendTextMessageWithoutPersisting()
 * so they are not written to the SMS provider, and data messages are
 * never stored to begin with.
 *
 * <p>Failed sends are retried a couple of times. During an emergency call
 * the radio is busy and a first attempt failing is not unusual.
 */
public final class SmsTransport {
    private static final String TAG = "CipherAML";

    static final String ACTION_SMS_SENT = "com.cipheros.cipheraml.action.SMS_SENT";
    static final String EXTRA_ATTEMPT_ID = "attempt_id";

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MILLIS = 5000;

    /**
     * Doubles as the PendingIntent request code, so it has to be unique for
     * the whole process. Otherwise a second session could get handed a
     * stale PendingIntent from the first one.
     */
    private static final AtomicInteger sNextId = new AtomicInteger();

    private final Context mContext;
    private final Handler mHandler;
    private final SendListener mListener;
    private final SparseArray<Pending> mPending = new SparseArray<>();
    private boolean mReceiverRegistered;

    private final BroadcastReceiver mSentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            onSent(intent.getIntExtra(EXTRA_ATTEMPT_ID, -1), getResultCode());
        }
    };

    private static final class Pending {
        final int subId;
        final SmsEndpoint endpoint;
        final String message;
        int attempts;

        Pending(int subId, SmsEndpoint endpoint, String message) {
            this.subId = subId;
            this.endpoint = endpoint;
            this.message = message;
        }
    }

    public SmsTransport(Context context, Handler handler, SendListener listener) {
        mContext = context;
        mHandler = handler;
        mListener = listener;
    }

    /** Encodes and sends one AML SMS. Must be called on the handler thread. */
    public void send(int subId, SmsEndpoint endpoint, AmlReport report) {
        int max = endpoint.type() == SmsEndpoint.Type.DATA
                ? AmlSmsEncoder.MAX_DATA_SMS_LENGTH : AmlSmsEncoder.MAX_TEXT_SMS_LENGTH;
        String message;
        try {
            message = new AmlSmsEncoder(max)
                    .setIncludeOptionalFields(endpoint.includeOptionalFields())
                    .setPartialImei(endpoint.partialImei())
                    .encode(report);
        } catch (RuntimeException e) {
            Log.e(TAG, "Could not encode AML SMS for " + endpoint, e);
            mListener.onSendFinished(endpoint.toString(), false);
            return;
        }
        registerReceiverIfNeeded();
        attempt(new Pending(subId, endpoint, message));
    }

    /** Stops listening for results. Anything still in flight is dropped. */
    public void close() {
        mHandler.removeCallbacksAndMessages(this);
        if (mReceiverRegistered) {
            mContext.unregisterReceiver(mSentReceiver);
            mReceiverRegistered = false;
        }
        mPending.clear();
    }

    private void registerReceiverIfNeeded() {
        if (mReceiverRegistered) {
            return;
        }
        mContext.registerReceiver(mSentReceiver, new IntentFilter(ACTION_SMS_SENT), null,
                mHandler, Context.RECEIVER_NOT_EXPORTED);
        mReceiverRegistered = true;
    }

    private void attempt(Pending pending) {
        pending.attempts++;
        int id = sNextId.getAndIncrement();
        mPending.put(id, pending);

        Intent intent = new Intent(ACTION_SMS_SENT)
                .setPackage(mContext.getPackageName())
                .putExtra(EXTRA_ATTEMPT_ID, id);
        PendingIntent sent = PendingIntent.getBroadcast(mContext, id, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT);

        SmsEndpoint endpoint = pending.endpoint;
        Log.i(TAG, "Sending AML SMS to " + endpoint + ", attempt " + pending.attempts);
        try {
            SmsManager sms = smsManagerFor(pending.subId);
            if (endpoint.type() == SmsEndpoint.Type.DATA) {
                sms.sendDataMessage(endpoint.destination(), null, (short) endpoint.port(),
                        Gsm7Packer.pack(pending.message), sent, null);
            } else {
                sms.sendTextMessageWithoutPersisting(endpoint.destination(), null,
                        pending.message, sent, null);
            }
        } catch (RuntimeException e) {
            // Usually no SIM or no SMS capable subscription at all. Retrying
            // will not fix that.
            Log.e(TAG, "SmsManager refused AML SMS to " + endpoint, e);
            mPending.remove(id);
            mListener.onSendFinished(endpoint.toString(), false);
        }
    }

    private void onSent(int id, int resultCode) {
        Pending pending = mPending.get(id);
        if (pending == null) {
            return;
        }
        mPending.remove(id);
        String endpoint = pending.endpoint.toString();
        if (resultCode == Activity.RESULT_OK) {
            Log.i(TAG, "AML SMS to " + endpoint + " accepted by the network");
            mListener.onSendFinished(endpoint, true);
            return;
        }
        Log.w(TAG, "AML SMS to " + endpoint + " failed, result " + resultCode);
        if (pending.attempts < MAX_ATTEMPTS) {
            mHandler.postDelayed(() -> attempt(pending), this, RETRY_DELAY_MILLIS);
        } else {
            mListener.onSendFinished(endpoint, false);
        }
    }

    private SmsManager smsManagerFor(int subId) {
        SmsManager base = mContext.getSystemService(SmsManager.class);
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            subId = SmsManager.getDefaultSmsSubscriptionId();
        }
        return SubscriptionManager.isValidSubscriptionId(subId)
                ? base.createForSubscriptionId(subId) : base;
    }
}
