# CipherAML by CipherOS

CipherAML sends your phone's location to emergency services when you call
or text an emergency number. It's an open source implementation of Advanced
Mobile Location (AML), built for Android ROMs that ship without Google Mobile
Services.

Package: `com.cipheros.cipheraml`
Website: [cipheros.org.in](https://cipheros.org.in)

## Why we built this

If you call 112 from a regular Android phone in most of Europe (and a growing
list of other places), something happens that you never see. Within a few
seconds the phone turns on GPS and Wi-Fi location, works out where you are,
and quietly sends that location to the emergency centre handling your call.
The call taker sees you on a map, often to within a few metres, instead of
somewhere inside a cell tower's coverage area that can be kilometres wide.

That's AML. It started in the UK in 2014 (BT, EE and HTC worked it out
together), was standardised by ETSI as TS 103 625, and is now something EU
rules expect every smartphone to do. It's credited with saving lives, mostly
in the boring way: an ambulance turns into the right street first time.

On Android, AML is implemented by Google as the Emergency Location Service
(ELS), and it lives inside Google Play services. That's the problem. Take
Play services off the phone, which is exactly what CipherOS and plenty of
other privacy focused ROMs do, and ELS goes with it. The emergency call still
connects, but the location never arrives. Nobody tells the user. They just
end up a little less findable on the worst day of their life.

We didn't think "you chose privacy, so you get worse emergency response" was
an acceptable trade. So we wrote our own.

A few things we cared about while doing it:

- **It has to work without Google.** No Play services, no proprietary
  location backends, no network calls to anyone but the emergency service.
- **It has to be readable.** This is code that uses your location even
  when location is off, and sends it to a third party. You should be able to
  read exactly what it sends, when, and to whom. All of it is in this repo.
- **It has to follow the spec.** PSAPs (the emergency call centres) already
  receive AML from millions of phones. We build the messages exactly the
  way ETSI TS 103 625 describes them, so nothing on their side has to
  change.
- **It has to be easy for other ROMs to pick up.** One makefile line, see
  [Integrating it into your ROM](#integrating-it-into-your-rom).

## How it works

1. You call or text an emergency number. That covers the dialer, the
   emergency button on the lock screen, and anything else that goes through
   telephony.
2. Telephony tells CipherAML through the `OutgoingEmergencyCallListener` and
   `OutgoingEmergencySmsListener` system callbacks. The app runs as a
   persistent, direct boot aware process, so this works even right after a
   reboot before the phone has ever been unlocked.
3. CipherAML checks a few things: is AML switched on, does the country
   you're in have an AML endpoint, is the number you dialled one that should
   trigger it, and is there enough battery left (the voice call always comes
   first).
4. It asks GPS and the network location provider for fixes at the same
   time, ignoring the location setting for this one request. It keeps the
   best fix it gets and stops after 20 seconds (the spec calls this T1), or
   earlier if GPS gets a fix that's good to 25 metres.
5. It builds the AML message from that fix and sends it to every endpoint
   configured for the country: an SMS, an HTTPS POST, or both.
6. That's it. Nothing is stored. The SMS doesn't show up in your messages.

If no positioning method works at all, it still sends a message saying so,
because the spec asks for that and because "we tried and got nothing" is
useful information to a call taker.

Here's what an AML SMS looks like (on one line, wrapped here):

```
A"ML=1;lt=+54.76397;lg=-0.18305;rd=50;top=20130717141935;lc=90;pm=W;
si=234300000000000;ei=356708041746734;mcc=234;mnc=30;ml=128
```

Latitude and longitude, a radius in metres, when the fix was taken, how
confident we are that you're inside that circle, how we got the fix (G for
GPS, W for Wi-Fi, C for cell, N for nothing), and enough about the phone and
network for the PSAP to match the message to your call.

## Privacy

This is the part people ask about, so plainly:

- Your location is only sent during an emergency call or emergency SMS.
- It only goes to the emergency endpoint for the country you're in, and only
  if that country is in the endpoint table. It never goes to CipherOS or
  anyone else.
- CipherAML makes no other network requests. No analytics, no update
  checks, nothing.
- Nothing is kept on the phone. TS 103 625 actually requires that the user
  can't find a record of the AML message afterwards, so text SMS go out with
  `sendTextMessageWithoutPersisting()` and never land in your sent folder.
- Over SMS the IMSI is cut down to just the country and network codes, as the
  spec requires. Over HTTPS the spec wants the full IMSI and IMEI, but that
  goes straight to the emergency service over TLS.
- Logs record what happened (which method, how accurate, which endpoint,
  whether it went through) and never your coordinates or identifiers. The
  one exception is the adb dry run, where you asked to see the payload.
- You can turn it off in Settings > Location > Location services >
  CipherAML emergency location. Emergency calls work exactly the same either
  way.

## What's in the repo

```
Android.bp                 App module, host tests, and the three XML modules
AndroidManifest.xml
cipher.aml.mk              Product makefile, the one line integrators need
etc/
  permissions/             privapp-permissions allowlist
  default-permissions/     Runtime permissions granted on first boot
  sysconfig/               allow-ignore-location-settings entry
res/
  values/config.xml        Overlayable tunables (T1, accuracy, confidence...)
  values/overlayable.xml   What an RRO is allowed to change
  xml/aml_endpoints.xml    The per country endpoint table (empty, see below)
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

## Requirements

- Android 14 (API 34) or newer. `LOCATION_BYPASS` showed up in 14. Older
  releases gate the same feature behind `WRITE_SECURE_SETTINGS`, which we'd
  rather not hand out, so we don't support them.
- A Soong based tree. The app builds against `system_current`, not hidden
  APIs, so it should drop into most AOSP derived trees without patches.
- GPS, obviously. A network location provider (for Wi-Fi and cell fixes) is
  optional but helps a lot indoors. Anything that registers as the `network`
  provider works, including microG's UnifiedNlp.

## Integrating it into your ROM

### 1. Get the source

Add it to your local manifest. Point `remote` at wherever you mirror this
repo:

```xml
<project path="packages/apps/CipherAML"
         name="packages_apps_CipherAML"
         remote="cipheros"
         revision="main" />
```

### 2. Inherit the makefile

In your device or vendor product makefile:

```make
$(call inherit-product, packages/apps/CipherAML/cipher.aml.mk)
```

That pulls in four things:

| What | Installed to |
| --- | --- |
| `CipherAML` (the app) | `/system_ext/priv-app/CipherAML/` |
| `privapp-permissions-com.cipheros.cipheraml.xml` | `/system_ext/etc/permissions/` |
| `default-permissions-com.cipheros.cipheraml.xml` | `/system_ext/etc/default-permissions/` |
| `cipheraml-sysconfig.xml` | `/system_ext/etc/sysconfig/cipheraml.xml` |

If you'd rather not use the makefile, adding those four names to
`PRODUCT_PACKAGES` yourself does the same thing. Actually just `CipherAML`
is enough, since the app module lists the other three as `required`.

**If your build ships Google apps, don't include CipherAML.** Google's ELS
is already there and you'd end up sending two AML messages per call.
Something like this works:

```make
ifneq ($(WITH_GMS),true)
$(call inherit-product, packages/apps/CipherAML/cipher.aml.mk)
endif
```

### 3. Add your endpoint table

Out of the box the endpoint table is **empty**, so CipherAML does nothing
until you fill it in. That's on purpose; see
[The endpoint table](#the-endpoint-table) for why and for the format.

Once you have a table, the easiest way to ship it is to set
`CIPHER_AML_ENDPOINTS` before the inherit line:

```make
CIPHER_AML_ENDPOINTS := device/acme/phone/aml/aml_endpoints.xml
$(call inherit-product, packages/apps/CipherAML/cipher.aml.mk)
```

That copies it to `/product/etc/cipheraml/aml_endpoints.xml`, which the app
reads in preference to its built in table. A path that doesn't exist fails
the build instead of quietly giving you a phone that sends nothing.

You can also replace `res/xml/aml_endpoints.xml` with a runtime resource
overlay, or drop a file at `/system_ext/etc/cipheraml/aml_endpoints.xml`.
The lookup order is `/product`, then `/system_ext`, then the (possibly
overlaid) resource.

### 4. Build and check

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

Then do a dry run, below.

### Partitions

Everything goes to `system_ext` by default. If you want it on `product`
instead, switch `system_ext_specific: true` to `product_specific: true` on
all four modules in `Android.bp`. The privapp allowlist has to be on the same
partition as the app, so move them together.

### The privileged permissions, and why

| Permission | Why |
| --- | --- |
| `READ_ACTIVE_EMERGENCY_SESSION` | To be told when an emergency call or SMS goes out. |
| `LOCATION_BYPASS` | To get a fix when the user has location turned off. |
| `READ_PRIVILEGED_PHONE_STATE` | IMEI, IMSI and phone number for the message. |
| `MODIFY_PHONE_STATE` | To send the SMS without it landing in the sent folder. |

On builds with `ro.control_privapp_permissions=enforce` the device won't
boot if any of these are missing from the allowlist, so if you change the
manifest, change the XML too.

The runtime ones (location, background location, `SEND_SMS`) are granted on
first boot through the default-permissions file. They aren't fixed, so a
user can revoke them, and the settings screen will tell them AML may not
work if they do.

### Licensing for other ROMs

CipherAML is under the CipherOS License 2.0. You're free to build it, study
it and test it, but shipping it in another distributed project or a
commercial product needs written permission from the CipherOS Founder
first. Email legal@cipheros.org.in, tell us what you're building, and we'll
sort it out. We want this in as many phones as possible, we just want to
know where it's going.

## The endpoint table

AML is set up country by country. Each country decides which emergency
numbers trigger it and where the location goes: an SMS short code, a long
number, an HTTPS server, or a mix.

We ship the table empty, deliberately. These endpoints are run by national
emergency services, they change, and some of them only accept messages from
OS vendors they have an agreement with. Guessing a number and getting it
wrong means someone's location goes nowhere at the worst possible moment.
So please only add entries you got from an official source (the national
regulator, the PSAP operator, or EENA's AML deployment information) and
that you're allowed to use.

The format is documented at the top of `res/xml/aml_endpoints.xml`. A full
entry looks like this:

```xml
<aml-endpoints version="1">
    <country name="Example" mcc="001" iso="xx" confidence="90">
        <trigger number="112" />
        <trigger number="999" />
        <sms destination="999" />
        <https url="https://aml.example.invalid/v1" />
    </country>
</aml-endpoints>
```

- `mcc` is matched against the network you're on (not your SIM), so it
  works for roamers. `iso` is a fallback for limited service, when the
  modem knows the country but not the network code yet. Both take comma
  separated lists.
- No `<trigger>` lines means any number the modem treats as an emergency
  number starts AML.
- `<sms type="data" port="...">` sends a port addressed data SMS instead of
  a text SMS. Only use that if the receiving side has agreed a port with you.
- `<sms optional-fields="true">` also sends altitude and time of call when
  they fit. `partial-imei="true"` sends just the model part of the IMEI,
  which the spec allows when SMS is the only transport.
- `<https>` only accepts `https://` URLs. Plain HTTP entries are dropped.

## Tuning

These live in `res/values/config.xml` and can be overlaid:

| Resource | Default | What it does |
| --- | --- | --- |
| `config_amlEnabledByDefault` | `true` | Whether AML starts out on. |
| `config_amlLocationTimeoutMillis` | `20000` | T1, the longest we wait for a fix. |
| `config_amlEarlySendAccuracyMeters` | `25` | Send early once GPS is this good. 0 disables. |
| `config_amlConfidencePercent` | `68` | Confidence the radius is reported at. |
| `config_amlMinBatteryPercent` | `2` | Below this (and not charging) we stay out of the way. |
| `config_amlMaxCachedFixAgeMillis` | `60000` | Oldest cached fix we'll fall back on. |

Android reports accuracy at 68% confidence. If a country wants 90% or 95%,
set it per country in the endpoint table and the radius gets scaled up to
match.

## Testing

**Please never test this by calling an emergency number.** Those lines are
for people who need them.

Unit tests for the message encoders run on the host, no device needed:

```
atest CipherAMLHostTests
```

They check the output against the example in the ETSI spec, the "no fix"
placeholders, optional fields, the length field, IMSI masking, GSM 7-bit
packing and the confidence maths.

On a device, use the dry run. It does everything a real session does (real
location, real SIM info, real endpoint lookup) but logs the SMS and HTTPS
payloads instead of sending them:

```
adb shell am broadcast -a com.cipheros.cipheraml.action.DRY_RUN \
    -p com.cipheros.cipheraml --es number 112
adb logcat -s CipherAML
```

It needs the `DUMP` permission, which normal apps can't get, so in
practice only adb and the system can trigger it.

If you want to test end to end with a real PSAP, talk to them first. Some
countries have test procedures for exactly this.

## Known gaps

We'd rather you hear these from us:

- No endpoints ship by default, see above.
- CipherAML hasn't been certified by any emergency authority, and as far as
  we know it hasn't been tested against a live PSAP yet.
- One message per emergency. We don't send follow up updates if a better
  fix turns up later.
- When the network location provider doesn't say whether a fix came from
  Wi-Fi or cell towers, we guess from the accuracy.
- Data SMS uses the standard little endian 7-bit packing. A few legacy
  receivers expect big endian and won't decode it.
- With no SIM, or in limited service on another operator's network, SMS
  can't be sent at all. HTTPS still works if there's Wi-Fi.
- International roaming depends on how the visited network routes SMS. The
  spec talks about this (GSMA NG.119) and there's not much the phone can do
  about it.

## Further reading

- [ETSI TS 103 625](https://www.etsi.org/deliver/etsi_ts/103600_103699/103625/),
  the AML spec itself
- [Advanced Mobile Location on Wikipedia](https://en.wikipedia.org/wiki/Advanced_Mobile_Location)
- [How Google's ELS works](https://crisisresponse.google/emergencylocationservice/how-it-works/),
  for comparison

## License

CipherAML is released under the CipherOS License 2.0. See [LICENSE](LICENSE).

© 2019-2026 The CipherOS Project.
