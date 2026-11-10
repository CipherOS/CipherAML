# Working on CipherAML

Notes for anyone hacking on the code itself. To test your changes, see
[testing.md](testing.md).

## What's where

```
Android.bp                 App module and host tests
AndroidManifest.xml
cipheraml.mk               Product makefile: PRODUCT_PACKAGES and PRODUCT_COPY_FILES
etc/
  permissions/             privapp-permissions allowlist
  default-permissions/     Runtime permissions granted on first boot
  sysconfig/               allow-ignore-location-settings entry
res/
  values/config.xml        Overlayable tunables (T1, accuracy, confidence...)
  values/overlayable.xml   What an RRO is allowed to change
  xml/aml_endpoints.xml    The per country endpoint table, see below
src/com/cipheros/cipheraml/
  core/                    Pure Java message encoders, no Android imports
  config/                  Endpoint table model, parser and loader
  location/                Location collection with the settings bypass
  telephony/               IMSI, IMEI, MCC/MNC lookup
  transport/               SMS and HTTPS senders
  settings/                Settings screen and Location services entry
  AmlController.java       Decides whether to start a session
  AmlSession.java          One emergency, start to finish
  EmergencyListener.java   Hooks into telephony
tests/host/                JUnit tests for core/, run on the host
```

## Known gaps

Things we know aren't there yet. We'd rather you hear it from us:

- Only the UK and Slovakia are switched on. Germany and India are waiting
  on endpoint access, see
  [endpoints.md](endpoints.md).
- CipherAML hasn't been certified by any emergency authority, and as far as
  we know it hasn't been tested against a live PSAP yet.
- One message per emergency. We don't send follow up updates if a better
  fix turns up later. Germany's endpoint expects three (at 0, 15 and 30
  seconds), so this needs fixing before Germany goes live.
- No custom SMSC address yet. Belgium's roaming setup (the AML SMS goes
  through SMSC +32112) needs it.
- When the network location provider doesn't say whether a fix came from
  Wi-Fi or cell towers, we guess from the accuracy.
- Data SMS uses the standard little endian 7-bit packing. A few legacy
  receivers expect big endian and won't decode it.
- With no SIM, or in limited service on another operator's network, SMS
  can't be sent at all. HTTPS still works if there's Wi-Fi.
- International roaming depends on how the visited network routes SMS. The
  spec talks about this (GSMA NG.119) and there's not much the phone can do
  about it.
