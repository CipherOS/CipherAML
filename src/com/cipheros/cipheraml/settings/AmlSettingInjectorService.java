/*
 * Copyright (C) 2026 The CipherOS Project
 *
 * Licensed under the CipherOS License 2.0. See the LICENSE file in the
 * root of this repository for the full terms.
 */

package com.cipheros.cipheraml.settings;

import android.location.SettingInjectorService;

import com.cipheros.cipheraml.AmlSettings;
import com.cipheros.cipheraml.R;

/**
 * Puts a "CipherAML emergency location" row into Settings > Location >
 * Location services, which is where people already look for this kind
 * of thing. Settings only shows injected rows from system apps.
 *
 * <p>Both callbacks run on our main thread while Settings waits, so they
 * have to stay fast. Reading one shared preference is fine.
 */
public final class AmlSettingInjectorService extends SettingInjectorService {
    public AmlSettingInjectorService() {
        super("CipherAmlSettingInjector");
    }

    @Override
    protected String onGetSummary() {
        return getString(AmlSettings.isEnabled(this) ? R.string.summary_on : R.string.summary_off);
    }

    @Override
    protected boolean onGetEnabled() {
        return true;
    }
}
