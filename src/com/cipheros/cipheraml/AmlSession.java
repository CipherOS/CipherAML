/*
 * Copyright (C) 2026 The CipherOS Project
 *
 * Licensed under the CipherOS License 2.0. See the LICENSE file in the
 * root of this repository for the full terms.
 */

package com.cipheros.cipheraml;

import android.content.Context;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import com.cipheros.cipheraml.config.CountryConfig;
import com.cipheros.cipheraml.config.HttpsEndpoint;
import com.cipheros.cipheraml.config.SmsEndpoint;
import com.cipheros.cipheraml.core.AmlHttpsEncoder;
import com.cipheros.cipheraml.core.AmlLocation;
import com.cipheros.cipheraml.core.AmlReport;
import com.cipheros.cipheraml.core.AmlSmsEncoder;
import com.cipheros.cipheraml.location.AmlClock;
import com.cipheros.cipheraml.location.FixConverter;
import com.cipheros.cipheraml.location.LocationCollector;
import com.cipheros.cipheraml.telephony.SubscriberSnapshot;
import com.cipheros.cipheraml.transport.HttpsTransport;
import com.cipheros.cipheraml.transport.SendListener;
import com.cipheros.cipheraml.transport.SmsTransport;

/**
 * One emergency, start to finish.
 *
 * <p>Holds a wake lock, gets the best fix it can before T1, builds one
 * report and hands it to every endpoint configured for the country.
 * When the last transport is done (or a hard timeout hits) it cleans up
 * and tells the controller.
 *
 * <p>Logging is deliberately boring. We log what happened, never the
 * coordinates or identifiers, except in a dry run where the person at
 * the adb shell asked to see the payload.
 */
final class AmlSession implements SendListener {
    private static final String TAG = "CipherAML";

    /**
     * Upper bound on how long a session can live after T1. Covers SMS
     * retries and a slow HTTPS server with time to spare.
     */
    private static final long SEND_BUDGET_MILLIS = 90_000;

    interface Listener {
        void onSessionFinished(AmlSession session);
    }

    /** What started the session. */
    static final class Trigger {
        final AmlReport.Source source;
        final String number;
        final int subId;
        final long startElapsedMillis;
        final long startUtcMillis;

        Trigger(AmlReport.Source source, String number, int subId) {
            this.source = source;
            this.number = number;
            this.subId = subId;
            this.startElapsedMillis = SystemClock.elapsedRealtime();
            this.startUtcMillis = AmlClock.nowUtcMillis();
        }

        @Override
        public String toString() {
            return source + " to " + number + " on sub " + subId;
        }
    }

    private final Context mContext;
    private final Handler mHandler;
    private final AmlTunables mTunables;
    private final CountryConfig mCountry;
    private final SubscriberSnapshot mSubscriber;
    private final Trigger mTrigger;
    private final boolean mDryRun;
    private final Listener mListener;
    private final PowerManager.WakeLock mWakeLock;
    private final LocationCollector mCollector;
    private final Runnable mHardTimeout = this::onHardTimeout;

    private SmsTransport mSmsTransport;
    private HttpsTransport mHttpsTransport;
    private int mPendingSends;
    private boolean mFinished;

    AmlSession(Context context, Handler handler, AmlTunables tunables, CountryConfig country,
            SubscriberSnapshot subscriber, Trigger trigger, boolean dryRun, Listener listener) {
        mContext = context;
        mHandler = handler;
        mTunables = tunables;
        mCountry = country;
        mSubscriber = subscriber;
        mTrigger = trigger;
        mDryRun = dryRun;
        mListener = listener;
        mWakeLock = context.getSystemService(PowerManager.class)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CipherAML:session");
        mWakeLock.setReferenceCounted(false);
        mCollector = new LocationCollector(context, handler, tunables,
                trigger.startElapsedMillis);
    }

    Trigger trigger() {
        return mTrigger;
    }

    void start() {
        long lifetime = mTunables.locationTimeoutMillis + SEND_BUDGET_MILLIS;
        // Handler delays stop counting while the CPU sleeps, and the screen
        // is usually off against the caller's ear. Keep the CPU up.
        mWakeLock.acquire(lifetime);
        mHandler.postDelayed(mHardTimeout, lifetime);

        Log.i(TAG, (mDryRun ? "Dry run" : "AML session") + " started: " + mTrigger + ", "
                + mSubscriber + ", country " + (mCountry != null ? mCountry.name() : "none")
                + ", " + mTunables);
        long deadline = mTrigger.startElapsedMillis + mTunables.locationTimeoutMillis;
        mCollector.start(deadline, this::onLocationResult);
    }

    /** Stops everything without sending. */
    void cancel() {
        Log.i(TAG, "AML session cancelled");
        mCollector.cancel();
        finish();
    }

    private void onLocationResult(Location best, boolean fresh) {
        if (mFinished) {
            return;
        }
        int confidence = mCountry != null && mCountry.confidencePercent() > 0
                ? mCountry.confidencePercent() : mTunables.confidencePercent;
        AmlReport report = buildReport(best, confidence);
        long elapsed = SystemClock.elapsedRealtime() - mTrigger.startElapsedMillis;
        if (report.hasLocation()) {
            AmlLocation loc = report.location();
            Log.i(TAG, "Got a " + loc.method() + " fix after " + elapsed + "ms, "
                    + Math.round(loc.radiusMeters()) + "m at " + confidence + "%"
                    + (fresh ? "" : " (cached)"));
        } else {
            Log.w(TAG, "No usable fix after " + elapsed + "ms, sending pm=N");
        }

        if (mDryRun) {
            dumpPayloads(report);
            finish();
            return;
        }

        if (mCountry != null) {
            for (SmsEndpoint endpoint : mCountry.smsEndpoints()) {
                if (mSmsTransport == null) {
                    mSmsTransport = new SmsTransport(mContext, mHandler, this);
                }
                mPendingSends++;
                mSmsTransport.send(mTrigger.subId, endpoint, report);
            }
            for (HttpsEndpoint endpoint : mCountry.httpsEndpoints()) {
                if (mHttpsTransport == null) {
                    mHttpsTransport = new HttpsTransport(mHandler, this);
                }
                mPendingSends++;
                mHttpsTransport.send(endpoint, report);
            }
        }
        if (mPendingSends == 0) {
            finish();
        }
    }

    private AmlReport buildReport(Location best, int confidence) {
        AmlReport.Builder b = new AmlReport.Builder()
                .setConfidencePercent(confidence)
                .setSource(mTrigger.source)
                .setCallTimeMillis(mTrigger.startUtcMillis)
                .setEmergencyNumber(mTrigger.number)
                .setImsi(mSubscriber.imsi)
                .setImei(mSubscriber.imei)
                .setNetworkMccMnc(mSubscriber.networkMcc, mSubscriber.networkMnc)
                .setHomeMccMnc(mSubscriber.homeMcc, mSubscriber.homeMnc)
                .setMsisdn(mSubscriber.msisdn)
                .setDeviceModel(Build.MODEL);
        if (best != null) {
            b.setLocation(FixConverter.toAmlLocation(best, confidence));
        } else {
            b.setFailureTimeMillis(AmlClock.nowUtcMillis());
        }
        return b.build();
    }

    private void dumpPayloads(AmlReport report) {
        String text = new AmlSmsEncoder(AmlSmsEncoder.MAX_TEXT_SMS_LENGTH).encode(report);
        Log.i(TAG, "Dry run SMS (" + text.length() + " chars): " + text);
        Log.i(TAG, "Dry run HTTPS body: " + AmlHttpsEncoder.encode(report));
        if (mCountry == null) {
            Log.i(TAG, "Dry run: no endpoint configured here, a real call would send nothing");
        } else {
            Log.i(TAG, "Dry run: a real call would go to " + mCountry.smsEndpoints() + " "
                    + mCountry.httpsEndpoints());
        }
    }

    @Override
    public void onSendFinished(String endpoint, boolean success) {
        Log.i(TAG, "Done with " + endpoint + ": " + (success ? "sent" : "failed"));
        if (--mPendingSends <= 0) {
            finish();
        }
    }

    private void onHardTimeout() {
        Log.w(TAG, "AML session hit its time limit, " + mPendingSends + " send(s) unfinished");
        mCollector.cancel();
        finish();
    }

    private void finish() {
        if (mFinished) {
            return;
        }
        mFinished = true;
        mHandler.removeCallbacks(mHardTimeout);
        if (mSmsTransport != null) {
            mSmsTransport.close();
        }
        if (mHttpsTransport != null) {
            mHttpsTransport.close();
        }
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        Log.i(TAG, "AML session finished");
        mListener.onSessionFinished(this);
    }
}
