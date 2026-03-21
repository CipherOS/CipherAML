/*
 * Copyright (C) 2026 The CipherOS Project
 *
 * Licensed under the CipherOS License 2.0. See the LICENSE file in the
 * root of this repository for the full terms.
 */

package com.cipheros.cipheraml.config;

import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads the AML endpoint table. See res/xml/aml_endpoints.xml for the
 * format.
 *
 * <p>We run in a persistent process, so a typo in someone's overlay must
 * never take us down. Bad entries are logged and skipped, the rest of the
 * file still loads.
 */
public final class EndpointConfigParser {
    private static final String TAG = "CipherAML";

    static final String TAG_COUNTRY = "country";
    static final String TAG_TRIGGER = "trigger";
    static final String TAG_SMS = "sms";
    static final String TAG_HTTPS = "https";

    private EndpointConfigParser() {}

    public static EndpointConfig parse(XmlPullParser parser, String source)
            throws XmlPullParserException, IOException {
        List<CountryConfig> countries = new ArrayList<>();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (type == XmlPullParser.START_TAG && TAG_COUNTRY.equals(parser.getName())) {
                CountryConfig country = parseCountry(parser);
                if (country != null) {
                    countries.add(country);
                }
            }
        }
        return new EndpointConfig(countries, source);
    }

    private static CountryConfig parseCountry(XmlPullParser p)
            throws XmlPullParserException, IOException {
        CountryConfig.Builder b = new CountryConfig.Builder();
        String name = p.getAttributeValue(null, "name");
        b.setName(name);
        List<String> mccs = splitList(p.getAttributeValue(null, "mcc"));
        List<String> isos = splitList(p.getAttributeValue(null, "iso"));
        boolean valid = !isos.isEmpty();
        for (String mcc : mccs) {
            if (!mcc.matches("\\d{3}")) {
                Log.w(TAG, "Skipping bad mcc '" + mcc + "' in " + name);
                continue;
            }
            b.addMcc(mcc);
            valid = true;
        }
        for (String iso : isos) {
            b.addIso(iso);
        }
        String confidence = p.getAttributeValue(null, "confidence");
        if (confidence != null) {
            int value = parseInt(confidence, 0);
            if (value >= 1 && value <= 99) {
                b.setConfidencePercent(value);
            } else {
                Log.w(TAG, "Ignoring bad confidence '" + confidence + "' in " + name);
            }
        }

        int depth = p.getDepth();
        int type;
        while ((type = p.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || p.getDepth() > depth)) {
            if (type != XmlPullParser.START_TAG) {
                continue;
            }
            switch (p.getName()) {
                case TAG_TRIGGER:
                    String number = p.getAttributeValue(null, "number");
                    if (number != null && !number.isEmpty()) {
                        b.addTrigger(number);
                    }
                    break;
                case TAG_SMS:
                    SmsEndpoint sms = parseSms(p, name);
                    if (sms != null) {
                        b.addSmsEndpoint(sms);
                    }
                    break;
                case TAG_HTTPS:
                    HttpsEndpoint https = parseHttps(p, name);
                    if (https != null) {
                        b.addHttpsEndpoint(https);
                    }
                    break;
                default:
                    Log.w(TAG, "Unknown tag <" + p.getName() + "> in " + name);
                    break;
            }
        }

        if (!valid) {
            Log.w(TAG, "Country '" + name + "' has neither mcc nor iso, skipping it");
            return null;
        }
        return b.build();
    }

    private static SmsEndpoint parseSms(XmlPullParser p, String country) {
        String destination = p.getAttributeValue(null, "destination");
        if (destination == null || destination.isEmpty()) {
            Log.w(TAG, "<sms> without destination in " + country);
            return null;
        }
        String typeName = p.getAttributeValue(null, "type");
        SmsEndpoint.Type type = SmsEndpoint.Type.TEXT;
        int port = 0;
        if ("data".equals(typeName)) {
            type = SmsEndpoint.Type.DATA;
            port = parseInt(p.getAttributeValue(null, "port"), -1);
            if (port < 1 || port > 65535) {
                Log.w(TAG, "Data <sms> needs a port between 1 and 65535 in " + country);
                return null;
            }
        } else if (typeName != null && !"text".equals(typeName)) {
            Log.w(TAG, "Unknown sms type '" + typeName + "' in " + country);
            return null;
        }
        boolean optional = Boolean.parseBoolean(p.getAttributeValue(null, "optional-fields"));
        boolean partialImei = Boolean.parseBoolean(p.getAttributeValue(null, "partial-imei"));
        return new SmsEndpoint(destination, type, port, optional, partialImei);
    }

    private static HttpsEndpoint parseHttps(XmlPullParser p, String country) {
        String url = p.getAttributeValue(null, "url");
        if (url == null || !url.toLowerCase(Locale.ROOT).startsWith("https://")) {
            // Location data never goes out in the clear.
            Log.w(TAG, "Refusing non https AML url in " + country);
            return null;
        }
        return new HttpsEndpoint(url);
    }

    private static List<String> splitList(String value) {
        List<String> out = new ArrayList<>();
        if (value == null) {
            return out;
        }
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
