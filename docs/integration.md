# Integrating CipherAML into your ROM

Whether you're building CipherOS or your own ROM, it's the same four
steps. It should take you about as long as adding any other system app.

What each permission is for is covered in [permissions.md](permissions.md),
and the knobs you can overlay are in [configuration.md](configuration.md).

## Requirements

- Android 14 (API 34) or newer. `LOCATION_BYPASS` showed up in 14. Older
  releases gate the same feature behind `WRITE_SECURE_SETTINGS`, which we'd
  rather not hand out, so we don't support them.
- An AOSP based tree that builds with Soong, which is pretty much any
  modern ROM. The app only uses `system_current` APIs, nothing hidden, so it
  should drop in without patches.
- GPS, obviously. A network location provider (for Wi-Fi and cell fixes) is
  optional but helps a lot indoors. Anything that registers as the `network`
  provider works, including microG's UnifiedNlp.

## 1. Get the source

Add it to your local manifest. Point `remote` at wherever you mirror this
repo:

```xml
<project path="packages/apps/CipherAML"
         name="packages_apps_CipherAML"
         remote="cipheros"
         revision="main" />
```

Keep the path as `packages/apps/CipherAML`. The makefile copies files from
there, so if you check it out somewhere else, fix the paths in
`cipheraml.mk` to match.

## 2. Inherit the makefile

In your device or vendor product makefile (`device.mk`, `common.mk`, or
whatever your tree uses):

```make
$(call inherit-product, packages/apps/CipherAML/cipheraml.mk)
```

`cipheraml.mk` is a plain AOSP style product makefile. It builds the app
and copies the three XMLs it needs onto the device:

| What | How | Installed to |
| --- | --- | --- |
| `CipherAML` (the app) | `PRODUCT_PACKAGES` | `/system_ext/priv-app/CipherAML/` |
| `privapp-permissions-com.cipheros.cipheraml.xml` | `PRODUCT_COPY_FILES` | `/system_ext/etc/permissions/` |
| `default-permissions-com.cipheros.cipheraml.xml` | `PRODUCT_COPY_FILES` | `/system_ext/etc/default-permissions/` |
| `cipheraml.xml` (sysconfig) | `PRODUCT_COPY_FILES` | `/system_ext/etc/sysconfig/` |

If you'd rather not inherit it, copy its lines into your own makefile. Just
don't add `CipherAML` to `PRODUCT_PACKAGES` on its own: without the privapp
allowlist next to it, an enforcing build won't boot.

**If your build ships Google apps, don't include CipherAML.** Google's ELS
is already there and you'd end up sending two AML messages per call.
Something like this works:

```make
ifneq ($(WITH_GMS),true)
$(call inherit-product, packages/apps/CipherAML/cipheraml.mk)
endif
```

## 3. Endpoints

The app already ships a table with every country we could verify, see
[endpoints.md](endpoints.md). You don't need to do anything
for those.

If you have endpoints of your own, for example from your own agreement with
a PSAP, ship a table next to the app:

```make
PRODUCT_COPY_FILES += \
    device/acme/phone/aml/aml_endpoints.xml:$(TARGET_COPY_OUT_PRODUCT)/etc/cipheraml/aml_endpoints.xml
```

The app reads `/product/etc/cipheraml/aml_endpoints.xml` first, then
`/system_ext/etc/cipheraml/aml_endpoints.xml`, then its built in table
(which you can also replace with a runtime resource overlay). The first one
it finds wins outright, so start from a copy of our table rather than an
empty file, or you'll lose the countries we already cover.

## 4. Build and check

```
m CipherAML
```

After flashing, check that the privileged permissions were granted:

```
adb shell dumpsys package com.cipheros.cipheraml | grep -E 'EMERGENCY_SESSION|LOCATION_BYPASS|MODIFY_PHONE_STATE'
```

and that the listener came up:

```
adb logcat -s CipherAML
# CipherAML starting
# Listening for emergency calls
```

If you see `Cannot listen for emergency calls, check the privapp allowlist`
instead, the permissions XML didn't make it onto the device or is on the
wrong partition.

Then do a dry run, see [testing.md](testing.md).

## Partitions

Everything goes to `system_ext` by default. If you want it on `product`
instead, swap `system_ext_specific: true` for `product_specific: true` in
`Android.bp` and `TARGET_COPY_OUT_SYSTEM_EXT` for `TARGET_COPY_OUT_PRODUCT`
in `cipheraml.mk`. The privapp allowlist has to be on the same partition as
the app, so move them together.

## Licensing for other ROMs

CipherAML is under the CipherOS License 2.0. You're free to build it, study
it and test it, but shipping it in another distributed project or a
commercial product needs written permission from the CipherOS Founder
first. Email legal@cipheros.org.in, tell us what you're building, and we'll
sort it out. We want this in as many phones as possible, we just want to
know where it's going.
