# CipherAML by CipherOS

CipherAML sends your phone's location to emergency services when you call
or text an emergency number. It's an open source implementation of Advanced
Mobile Location (AML), built for Android ROMs that ship without Google Mobile
Services.

Package: `com.cipheros.cipheraml`
Website: [cipheros.org.in](https://cipheros.org.in)

> **This project is still in development.** The code runs end to end and the
> message format is tested against the ETSI spec, but CipherAML hasn't been
> certified by any emergency authority, hasn't been tested against a live
> emergency centre yet, and is only switched on in a couple of countries so
> far. Emergency calls always go through either way, CipherAML only adds the
> location on top. Please don't advertise it to users as finished yet.

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

This works the same way for CipherOS and for any other AOSP based OS or
ROM.

### 1. Get the source

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

### 2. Inherit the makefile

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

### 3. Endpoints

The app already ships a table with every country we could verify, see
[The endpoint table](#the-endpoint-table). You don't need to do anything
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

Then do a dry run, see [Testing](#testing).

### Partitions

Everything goes to `system_ext` by default. If you want it on `product`
instead, swap `system_ext_specific: true` for `product_specific: true` in
`Android.bp` and `TARGET_COPY_OUT_SYSTEM_EXT` for `TARGET_COPY_OUT_PRODUCT`
in `cipheraml.mk`. The privapp allowlist has to be on the same partition as
the app, so move them together.

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

Here's the thing, though: **governments don't publish where AML goes.**
There's no public list, no registry, nothing you can look up. Each country
set its endpoints up behind closed doors with its mobile networks, Google
and Apple, and that's where they stayed. EENA's yearly report will tell you
*who* runs AML in a country, but not *where* to send it.

We spent a good while digging through the ETSI spec, EU reports, EENA
documents and government pages. The UK and Slovakia are the only two where
an official source actually writes the destination down, so those are the
only two that are switched on.

And we're not going to guess. A wrong number in an emergency isn't a bug you
fix in the next release. So the rule is simple: a country only gets an
endpoint when an official or primary source says where AML goes, and every
entry cites that source right there in the XML.

Where things stand today:

| Country | Triggers | Location goes to | Status | Source |
| --- | --- | --- | --- | --- |
| United Kingdom | 999, 112 | SMS to 999 (BT) | On | ETSI TS 103 625 clause 6.3.4.1, EENA AML Report Card 2023 |
| Slovakia | 112, 150, 155, 158 | SMS to 112 (Ministry of Interior) | On | EENA AML Report Card 2023 |
| Germany | 112, 110 | Not public (ILS Freiburg) | Pending | EENA AML Report Card 2023, ILS Freiburg |
| India | 112 | Not public (UP 112 / ERSS) | Pending | MHA ERSS, UP 112 ELS launch |

"Pending" means the country is in the table with its trigger numbers but
has no endpoint yet, so nothing is sent and Settings says AML isn't set up
there. Adding the endpoint is a one line change once someone has it.

**Germany.** There's one national AML end-point, run by the Integrierte
Leitstelle Freiburg-Breisgau-Hochschwarzwald with a passive backup at the
Berliner Feuerwehr. It takes SMS to a short code and HTTPS, matches the two
on the IMEI, and covers roamers over HTTPS. What isn't public is the short
code and the URL. To switch Germany on we need access from ILS Freiburg.

**India.** Google's ELS went live with UP 112 in Uttar Pradesh in December
2025, integrated by UP Police with Pert Telecom Solutions, and other states
are expected to follow under ERSS. The endpoint is private. Please don't
point it at 112 as a guess: ERSS takes ordinary SMS to 112 from people, so
an AML message would show up in front of a call taker as gibberish. To
switch India on we need the endpoint from ERSS (MHA and C-DAC) or a state
112 service.

**Everyone else.** EENA counts 30 or so countries running AML, mostly in
Europe. Same story, nothing public. EENA does offer to put implementers in
touch with the people running AML in each country, and that's the route
we're taking.

### Know an endpoint? Send it in

If you know the AML endpoint for a country, and you got it the legit way,
please open a pull request. We'll review it and merge it.

"The legit way" means one of these:

- a public, official document that names it: a government or regulator
  page, a PSAP's published spec, an ETSI or EENA publication, that kind of
  thing, or
- written confirmation from the organisation that runs the endpoint saying
  it's fine for CipherAML to send there.

In the PR, add the `<country>` entry to `res/xml/aml_endpoints.xml` with a
comment that says where it came from: a link, the document name and section,
or who confirmed it and when. Tell us what you checked.

What we can't merge: numbers that were guessed, numbers pulled out of
Google's or Apple's private configs, or "my friend says it works". If we
can't trace it back to a real source, it doesn't go in. That's not us being
difficult. The person on the other end of that SMS is having the worst day
of their life, and we'd like to get it right.

If you run a PSAP or an AML endpoint yourself, we'd especially love to hear
from you. Open an issue, or get in touch through
[cipheros.org.in](https://cipheros.org.in).

### The format

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

- Only the UK and Slovakia are switched on. Germany and India are waiting
  on endpoint access, see [The endpoint table](#the-endpoint-table).
- CipherAML hasn't been certified by any emergency authority, and as far as
  we know it hasn't been tested against a live PSAP yet.
- One message per emergency. We don't send follow up updates if a better
  fix turns up later. Germany's end-point expects three (at 0, 15 and 30
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

## Further reading

- [ETSI TS 103 625](https://www.etsi.org/deliver/etsi_ts/103600_103699/103625/),
  the AML spec itself
- [EENA AML Report Card 2023](https://eena.org/knowledge-hub/documents/aml-report-card-2023-update),
  how each country runs AML
- [ECC Report 368](https://docdb.cept.org/download/4814), on SMS to PSAPs
  and AML for roamers
- [Advanced Mobile Location on Wikipedia](https://en.wikipedia.org/wiki/Advanced_Mobile_Location)
- [How Google's ELS works](https://crisisresponse.google/emergencylocationservice/how-it-works/),
  for comparison

## License

CipherAML is released under the CipherOS License 2.0. See [LICENSE](LICENSE).

© 2019-2026 The CipherOS Project.
