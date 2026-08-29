/*
 * Copyright (C) 2026 The CipherOS Project
 *
 * Licensed under the CipherOS License 2.0. See the LICENSE file in the
 * root of this repository for the full terms.
 */

package com.cipheros.cipheraml.settings;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.SettingInjectorService;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;

import com.cipheros.cipheraml.AmlSettings;
import com.cipheros.cipheraml.R;
import com.cipheros.cipheraml.config.CountryConfig;
import com.cipheros.cipheraml.config.EndpointConfig;
import com.cipheros.cipheraml.config.EndpointConfigLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The screen behind Settings > Location > Location services > CipherAML.
 *
 * <p>One switch, a plain language explanation, and a couple of status
 * lines so people can see whether AML would actually do anything where
 * they are right now.
 */
public final class AmlSettingsActivity extends Activity {
    private Switch mSwitch;
    private TextView mStatus;
    private TextView mPermissionWarning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setTitle(R.string.settings_title);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        mSwitch = findViewById(R.id.aml_switch);
        mStatus = findViewById(R.id.aml_status);
        mPermissionWarning = findViewById(R.id.aml_permission_warning);

        mSwitch.setChecked(AmlSettings.isEnabled(this));
        mSwitch.setOnCheckedChangeListener((view, checked) -> {
            AmlSettings.setEnabled(this, checked);
            // Let Settings know the On/Off summary changed.
            SettingInjectorService.refreshSettings(this);
        });

        mPermissionWarning.setOnClickListener(v -> startActivity(
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", getPackageName(), null))));

        TextView footer = findViewById(R.id.aml_footer);
        footer.setText(getString(R.string.settings_footer, versionName()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
        updatePermissionWarning();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateStatus() {
        TelephonyManager tm = getSystemService(TelephonyManager.class);
        String operator = null;
        String iso = null;
        try {
            if (tm != null) {
                operator = tm.getNetworkOperator();
                iso = tm.getNetworkCountryIso();
            }
        } catch (UnsupportedOperationException e) {
            // No radio. Falls through to the "not connected" message.
        }
        if (TextUtils.isEmpty(operator) && TextUtils.isEmpty(iso)) {
            mStatus.setText(R.string.settings_status_no_network);
            return;
        }
        String mcc = operator != null && operator.length() >= 3 ? operator.substring(0, 3) : null;
        EndpointConfig config = EndpointConfigLoader.load(this);
        CountryConfig country = config.find(mcc, iso);
        if (country != null && country.hasEndpoints()) {
            String name = !TextUtils.isEmpty(country.name()) ? country.name()
                    : (iso != null ? iso.toUpperCase(Locale.ROOT) : mcc);
            mStatus.setText(getString(R.string.settings_status_supported, name));
        } else {
            mStatus.setText(R.string.settings_status_unsupported);
        }
    }

    private void updatePermissionWarning() {
        List<String> missing = new ArrayList<>();
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)
                || !granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            missing.add(getString(R.string.permission_location));
        }
        if (!granted(Manifest.permission.SEND_SMS)) {
            missing.add(getString(R.string.permission_sms));
        }
        if (missing.isEmpty()) {
            mPermissionWarning.setVisibility(View.GONE);
        } else {
            mPermissionWarning.setText(getString(R.string.settings_permission_warning,
                    TextUtils.join(", ", missing)));
            mPermissionWarning.setVisibility(View.VISIBLE);
        }
    }

    private boolean granted(String permission) {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private String versionName() {
        try {
            String name = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return name != null ? name : "?";
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }
}
