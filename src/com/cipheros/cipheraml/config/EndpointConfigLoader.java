/*
 * Copyright (C) 2026 The CipherOS Project
 *
 * Licensed under the CipherOS License 2.0. See the LICENSE file in the
 * root of this repository for the full terms.
 */

package com.cipheros.cipheraml.config;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.util.Log;
import android.util.Xml;

import com.cipheros.cipheraml.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Finds the endpoint table to use.
 *
 * <p>A plain XML file on the product or system_ext partition wins, so a
 * ROM can ship its own table with a single PRODUCT_COPY_FILES line. If
 * there is none we fall back to res/xml/aml_endpoints.xml, which can in
 * turn be replaced with a resource overlay.
 */
public final class EndpointConfigLoader {
    private static final String TAG = "CipherAML";

    static final String[] OVERRIDE_PATHS = {
        "/product/etc/cipheraml/aml_endpoints.xml",
        "/system_ext/etc/cipheraml/aml_endpoints.xml",
    };

    private EndpointConfigLoader() {}

    public static EndpointConfig load(Context context) {
        for (String path : OVERRIDE_PATHS) {
            File file = new File(path);
            if (!file.isFile()) {
                continue;
            }
            try (InputStream in = new FileInputStream(file)) {
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(in, StandardCharsets.UTF_8.name());
                return EndpointConfigParser.parse(parser, path);
            } catch (IOException | XmlPullParserException | RuntimeException e) {
                Log.e(TAG, "Could not read " + path + ", trying the next one", e);
            }
        }

        try (XmlResourceParser parser = context.getResources().getXml(R.xml.aml_endpoints)) {
            return EndpointConfigParser.parse(parser, "res/xml/aml_endpoints.xml");
        } catch (IOException | XmlPullParserException | RuntimeException e) {
            Log.e(TAG, "Bundled AML endpoint table is broken, AML is off", e);
            return EndpointConfig.EMPTY;
        }
    }
}
