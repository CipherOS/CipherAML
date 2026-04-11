/*
 * Copyright (C) 2026 The CipherOS Project
 *
 * Licensed under the CipherOS License 2.0. See the LICENSE file in the
 * root of this repository for the full terms.
 */

package com.cipheros.cipheraml.location;

import static android.location.LocationManager.GPS_PROVIDER;
import static android.location.LocationManager.NETWORK_PROVIDER;

import android.content.Context;
import android.location.LastLocationRequest;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationRequest;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import com.cipheros.cipheraml.AmlTunables;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Gets the best fix it can before a deadline.
 *
 * <p>GNSS and the network provider are asked in parallel, with the
 * location settings bypass set so this works even if the user has
 * location turned off. That is the one thing a privileged emergency
 * service is allowed to do that nothing else is, and only for the length
 * of the emergency session.
 *
 * <p>We stop at the deadline (T1) or as soon as GNSS gives us something
 * good enough, whichever comes first. If no fresh fix shows up we fall
 * back to a recent cached one. Everything runs on the handler passed in.
 */
public final class LocationCollector {
    private static final String TAG = "CipherAML";

    private static final String[] PROVIDERS = {GPS_PROVIDER, NETWORK_PROVIDER};
    private static final long UPDATE_INTERVAL_MILLIS = 1000;

    /**
     * Network providers sometimes hand out a fix computed just before we
     * asked. A few seconds of slack keeps those from being treated as
     * stale.
     */
    private static final long FRESH_SLACK_MILLIS = 5000;

    public interface Callback {
        /**
         * Called exactly once on the collector's handler.
         *
         * @param best the fix to report, or null if we have nothing usable
         * @param fresh whether {@code best} was obtained during this session
         */
        void onLocationResult(Location best, boolean fresh);
    }

    private final LocationManager mLocationManager;
    private final Handler mHandler;
    private final Executor mExecutor;
    private final AmlTunables mTunables;
    private final long mStartElapsedMillis;
    private final List<LocationListener> mListeners = new ArrayList<>();
    private final Runnable mTimeout = this::finish;

    private Callback mCallback;
    private Location mBestFresh;
    private Location mBestCached;
    private boolean mDone;

    public LocationCollector(Context context, Handler handler, AmlTunables tunables,
            long startElapsedMillis) {
        mLocationManager = context.getSystemService(LocationManager.class);
        mHandler = handler;
        mExecutor = handler::post;
        mTunables = tunables;
        mStartElapsedMillis = startElapsedMillis;
    }

    public void start(long deadlineElapsedMillis, Callback callback) {
        mCallback = callback;
        for (String provider : PROVIDERS) {
            if (!mLocationManager.hasProvider(provider)) {
                Log.i(TAG, "No " + provider + " provider on this device");
                continue;
            }
            considerCached(lastKnown(provider));
            request(provider, deadlineElapsedMillis);
        }
        long delay = Math.max(0, deadlineElapsedMillis - SystemClock.elapsedRealtime());
        mHandler.postDelayed(mTimeout, delay);
    }

    /** Stops listening without reporting anything. */
    public void cancel() {
        mCallback = null;
        finish();
    }

    private void request(String provider, long deadlineElapsedMillis) {
        // One listener per provider. Registering the same listener twice
        // replaces the first registration instead of adding to it.
        LocationListener listener = this::onLocation;
        long duration = Math.max(1, deadlineElapsedMillis - SystemClock.elapsedRealtime())
                + UPDATE_INTERVAL_MILLIS;
        try {
            mLocationManager.requestLocationUpdates(provider, buildRequest(duration, true),
                    mExecutor, listener);
        } catch (SecurityException e) {
            Log.w(TAG, "Settings bypass refused for " + provider + ", trying without it", e);
            try {
                mLocationManager.requestLocationUpdates(provider, buildRequest(duration, false),
                        mExecutor, listener);
            } catch (SecurityException | IllegalArgumentException e2) {
                Log.e(TAG, "Cannot get updates from " + provider, e2);
                return;
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Cannot get updates from " + provider, e);
            return;
        }
        mListeners.add(listener);
    }

    private static LocationRequest buildRequest(long durationMillis, boolean bypass) {
        return new LocationRequest.Builder(UPDATE_INTERVAL_MILLIS)
                .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
                .setMinUpdateIntervalMillis(0)
                .setDurationMillis(durationMillis)
                .setLocationSettingsIgnored(bypass)
                .build();
    }

    private Location lastKnown(String provider) {
        try {
            return mLocationManager.getLastKnownLocation(provider,
                    new LastLocationRequest.Builder().setLocationSettingsIgnored(true).build());
        } catch (SecurityException e) {
            try {
                return mLocationManager.getLastKnownLocation(provider);
            } catch (SecurityException e2) {
                return null;
            }
        }
    }

    private void onLocation(Location loc) {
        if (mDone || !isUsable(loc)) {
            return;
        }
        if (loc.getElapsedRealtimeMillis() < mStartElapsedMillis - FRESH_SLACK_MILLIS) {
            considerCached(loc);
            return;
        }
        if (mBestFresh == null || loc.getAccuracy() < mBestFresh.getAccuracy()) {
            mBestFresh = loc;
        }
        int earlySend = mTunables.earlySendAccuracyMeters;
        if (earlySend > 0 && GPS_PROVIDER.equals(loc.getProvider())
                && loc.getAccuracy() <= earlySend) {
            finish();
        }
    }

    private void considerCached(Location loc) {
        if (!isUsable(loc) || loc.getElapsedRealtimeAgeMillis() > mTunables.maxCachedFixAgeMillis) {
            return;
        }
        if (mBestCached == null || loc.getAccuracy() < mBestCached.getAccuracy()) {
            mBestCached = loc;
        }
    }

    private static boolean isUsable(Location loc) {
        if (loc == null || !loc.hasAccuracy() || !(loc.getAccuracy() > 0)) {
            return false;
        }
        double lat = loc.getLatitude();
        double lon = loc.getLongitude();
        if (!Double.isFinite(lat) || !Double.isFinite(lon)) {
            return false;
        }
        // Null Island. A broken provider, not a caller in the Gulf of Guinea.
        return lat != 0.0 || lon != 0.0;
    }

    private void finish() {
        if (mDone) {
            return;
        }
        mDone = true;
        mHandler.removeCallbacks(mTimeout);
        for (LocationListener listener : mListeners) {
            mLocationManager.removeUpdates(listener);
        }
        mListeners.clear();

        Callback callback = mCallback;
        mCallback = null;
        if (callback == null) {
            return;
        }
        if (mBestFresh != null) {
            callback.onLocationResult(mBestFresh, true);
        } else {
            callback.onLocationResult(mBestCached, false);
        }
    }
}
