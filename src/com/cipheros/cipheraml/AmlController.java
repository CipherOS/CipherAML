/*
 * Copyright (C) 2026 The CipherOS Project
 *
 * Licensed under the CipherOS License 2.0. See the LICENSE file in the
 * root of this repository for the full terms.
 */

package com.cipheros.cipheraml;

import android.content.Context;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.ArrayMap;
import android.util.Log;

import com.cipheros.cipheraml.config.CountryConfig;
import com.cipheros.cipheraml.config.EndpointConfig;
import com.cipheros.cipheraml.config.EndpointConfigLoader;
import com.cipheros.cipheraml.config.SmsEndpoint;
import com.cipheros.cipheraml.core.AmlReport;
import com.cipheros.cipheraml.telephony.SubscriberSnapshot;

import java.util.Map;

/**
 * Decides whether an emergency call or SMS should start AML, and makes
 * sure only one session runs at a time.
 *
 * <p>All the real work happens on a single background thread. Callers
 * can poke us from anywhere.
 */
public final class AmlController implements AmlSession.Listener {
    private static final String TAG = "CipherAML";

    /**
     * If our own AML SMS goes to an emergency number, the framework reports
     * it as an outgoing emergency SMS, which would start AML again. Ignore
     * emergency SMS to a number we just used as an AML destination.
     */
    private static final long OWN_SMS_ECHO_WINDOW_MILLIS = 5 * 60 * 1000;

    private static AmlController sInstance;

    private final Context mContext;
    private final Handler mHandler;
    private final Map<String, Long> mRecentSmsDestinations = new ArrayMap<>();
    private AmlSession mActive;

    public static synchronized AmlController get(Context context) {
        if (sInstance == null) {
            sInstance = new AmlController(context.getApplicationContext());
        }
        return sInstance;
    }

    private AmlController(Context context) {
        mContext = context;
        HandlerThread thread = new HandlerThread("CipherAML");
        thread.start();
        mHandler = new Handler(thread.getLooper());
    }

    /** An emergency call was just placed. */
    public void onEmergencyCall(String number, int subId) {
        AmlSession.Trigger trigger = new AmlSession.Trigger(AmlReport.Source.CALL, number, subId);
        mHandler.post(() -> maybeStart(trigger, false));
    }

    /** An emergency SMS was just sent. */
    public void onEmergencySms(String number, int subId) {
        AmlSession.Trigger trigger = new AmlSession.Trigger(AmlReport.Source.SMS, number, subId);
        mHandler.post(() -> {
            if (isOwnSmsEcho(number)) {
                Log.d(TAG, "Ignoring emergency SMS to " + number + ", that was us");
                return;
            }
            maybeStart(trigger, false);
        });
    }

    /**
     * Runs a full session but logs the payloads instead of sending them.
     * Ignores the user switch, battery and trigger rules so it is useful
     * for checking a device before it ships.
     */
    public void startDryRun(String number, int subId) {
        AmlSession.Trigger trigger = new AmlSession.Trigger(AmlReport.Source.CALL, number, subId);
        mHandler.post(() -> maybeStart(trigger, true));
    }

    private void maybeStart(AmlSession.Trigger trigger, boolean dryRun) {
        if (mActive != null) {
            Log.i(TAG, "AML already running for " + mActive.trigger() + ", ignoring " + trigger);
            return;
        }
        if (!dryRun && !AmlSettings.isEnabled(mContext)) {
            Log.i(TAG, "AML is switched off, not doing anything for " + trigger);
            return;
        }
        AmlTunables tunables = AmlTunables.from(mContext);
        if (!dryRun && isBatteryTooLow(tunables)) {
            Log.w(TAG, "Battery below " + tunables.minBatteryPercent
                    + "%, leaving it all to the voice call");
            return;
        }

        SubscriberSnapshot subscriber = SubscriberSnapshot.capture(mContext, trigger.subId);
        EndpointConfig config = EndpointConfigLoader.load(mContext);
        CountryConfig country = config.find(subscriber.networkMcc, subscriber.networkCountryIso);
        if (!dryRun) {
            if (country == null || !country.hasEndpoints()) {
                Log.i(TAG, "No AML endpoint for mcc " + subscriber.networkMcc + " iso "
                        + subscriber.networkCountryIso + " (table: " + config.source() + ")");
                return;
            }
            if (!country.isTrigger(trigger.number)) {
                Log.i(TAG, trigger.number + " does not trigger AML in " + country.name());
                return;
            }
        }

        if (!dryRun && country != null) {
            long now = SystemClock.elapsedRealtime();
            for (SmsEndpoint endpoint : country.smsEndpoints()) {
                mRecentSmsDestinations.put(
                        CountryConfig.normalizeNumber(endpoint.destination()), now);
            }
        }
        mActive = new AmlSession(mContext, mHandler, tunables, country, subscriber, trigger,
                dryRun, this);
        mActive.start();
    }

    @Override
    public void onSessionFinished(AmlSession session) {
        if (mActive == session) {
            mActive = null;
        }
    }

    private boolean isOwnSmsEcho(String number) {
        if (number == null) {
            return false;
        }
        Long sentAt = mRecentSmsDestinations.get(CountryConfig.normalizeNumber(number));
        return sentAt != null
                && SystemClock.elapsedRealtime() - sentAt < OWN_SMS_ECHO_WINDOW_MILLIS;
    }

    private boolean isBatteryTooLow(AmlTunables tunables) {
        BatteryManager battery = mContext.getSystemService(BatteryManager.class);
        if (battery == null || battery.isCharging()) {
            return false;
        }
        int level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        // Integer.MIN_VALUE means the property is not supported. Do not block
        // AML on a missing fuel gauge.
        return level >= 0 && level < tunables.minBatteryPercent;
    }
}
