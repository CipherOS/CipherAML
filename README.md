# CipherAML by CipherOS

CipherAML sends your phone's location to emergency services when you call
or text an emergency number, so help can find you faster. It's an open
source implementation of Advanced Mobile Location (AML), the same standard
Google and Apple phones already use, built for Android ROMs that ship
without Google.

Package: `com.cipheros.cipheraml`
Website: [cipheros.org.in](https://cipheros.org.in)

> **CipherAML is in development.** It works end to end in code and the
> message format is tested against the ETSI spec, but it hasn't been
> certified by any emergency authority, hasn't been tested against a real
> emergency centre, and is only switched on in two countries so far. It can
> fail, and it comes with no warranty. Please read
> [Risks and disclaimer](#risks-and-disclaimer) before you put it on anyone's
> phone. Emergency calls always go through either way; CipherAML only tries
> to add your location on top.

## Why we built this

When you call an emergency number from a regular Android phone or iPhone,
something happens that you never see. Within seconds the phone works out
where you are from GPS and Wi-Fi and sends that location to the emergency
centre handling your call. The call taker sees you on a map, often within a
few metres, instead of somewhere in a cell tower's coverage area that can be
kilometres wide.

That's AML. It started in the UK in 2014, was standardised by ETSI as
TS 103 625, and now runs in more than 30 countries, from Brazil to India to
New Zealand. It's credited with saving lives, mostly in the boring way: the
ambulance turns into the right street first time.

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
  [docs/integration.md](docs/integration.md).

## How it works

Here's what happens, step by step, when someone dials for help:

1. You call or text an emergency number. It doesn't matter how: the dialer,
   the emergency button on the lock screen, anything that goes through the
   phone's telephony stack.
2. Android lets CipherAML know, through two system callbacks
   (`OutgoingEmergencyCallListener` and `OutgoingEmergencySmsListener`).
   CipherAML is always running and starts before you unlock, so this works
   even straight after a reboot.
3. CipherAML checks a few things: is AML switched on, does the country
   you're in have an AML endpoint, is the number you dialled one that should
   trigger it, and is there enough battery left (the voice call always comes
   first).
4. It asks GPS and the network location provider for a fix at the same
   time, even if you've switched location off. That switch is ignored for
   this one request and nothing else. It keeps the best fix it gets and
   stops after 20 seconds (the spec calls this T1), or sooner if GPS gets
   within 25 metres.
5. It builds the AML message from that fix and sends it to every endpoint
   configured for the country: an SMS, an HTTPS POST, or both.
6. That's it. Nothing is saved, and the SMS never shows up in your
   messages.

If nothing manages to get a fix, it still sends a message saying so. The
spec asks for that, and "we tried and got nothing" is still useful to a call
taker.

Here's what an AML SMS looks like (on one line, wrapped here):

```
A"ML=1;lt=+54.76397;lg=-0.18305;rd=50;top=20130717141935;lc=90;pm=W;
si=234300000000000;ei=356708041746734;mcc=234;mnc=30;ml=128
```

It's not pretty, but it's all there: latitude and longitude, a radius in
metres, when the fix was taken, how sure we are that you're inside that
circle, how we got the fix (G for GPS, W for Wi-Fi, C for cell, N for
nothing), and just enough about the phone and network for the emergency
centre to match the message to your call.

## Where it works

AML is set up country by country, and **governments don't publish where AML
goes**. Each country agreed its endpoints privately with its mobile networks,
Google and Apple. We only switch a country on when an official source says
where to send, and we won't guess.

| Country | Status | Location goes to |
| --- | --- | --- |
| United Kingdom | On | SMS to 999 |
| Slovakia | On | SMS to 112 |
| Lithuania | On | SMS to 112 |
| Germany | Pending | Not public yet |
| India | Pending | Not public yet |

Know an endpoint and got it the legit way? We'll happily merge it. Sources,
details on the pending countries, the rules for contributing and the table
format are all in [docs/endpoints.md](docs/endpoints.md).

## Finding it in Settings

Settings > Location > Location services > **CipherAML emergency location**,
the same spot as Google's Emergency Location Service.

## Privacy

This is the part people ask about most, so here it is plainly:

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

## Risks and disclaimer

CipherAML is in development, and it deals with emergencies, so here's the
honest version of what can go wrong.

**It might not send anything.** The country might not be in the endpoint
table. There might be no SIM, no signal for the SMS, or no data for HTTPS.
Someone might have revoked a permission, the battery might be nearly flat,
or there might just be a bug we haven't found yet.

**It might send a location that's off.** GPS is weak indoors. Wi-Fi and
cell fixes can be wrong by hundreds of metres, sometimes more. If nothing
fresh comes in, we fall back on a fix that can be up to a minute old.

**The other side might not use it.** No emergency centre has formally agreed
to receive AML from CipherAML. For SMS endpoints that usually doesn't matter,
since our SMS looks just like everyone else's, but nobody has promised us
anything. Endpoints can also change or be switched off without notice, and
our table won't know.

**Nobody has certified it.** Not a regulator, not an emergency service, not
a test lab. It also hasn't been tried against a live emergency centre.

**It sends personal data on purpose.** Your location, your phone number
(through the SMS itself), your IMEI and, over HTTPS, your IMSI go to the
emergency service. That's the whole point, but you should know.

**It can cost money in some cases.** AML SMS is free in the countries we
support, but while roaming, or anywhere it isn't zero rated, the SMS or the
HTTPS request might be charged.

**It shouldn't run next to Google's ELS.** On a build with Play services
you'd send two messages per call, which can confuse the receiving side.

So, please:

- **Don't rely on it.** If you can, always tell the call taker where you
  are. Think of CipherAML as a bonus, not a guarantee.
- **If you ship it in a ROM, that part is on you.** Test it on your devices,
  check that it's allowed where your users live (some countries have rules
  about who may send AML and how), get the emergency service's okay where
  that's needed, and tell your users what it does.

**The legal bit.** CipherAML is provided "as is", without warranty of any
kind, express or implied. The CipherOS Project and its contributors aren't
liable for any damage or loss that comes from using it, building it,
shipping it, or it failing to work when someone needed it, to the fullest
extent the law allows. Sections 5 and 6 of the [LICENSE](LICENSE) are what
actually count here; this section is just the plain English version. By
using, building or distributing CipherAML you accept these terms.

## Putting it in your ROM

One line in your product makefile:

```make
$(call inherit-product, packages/apps/CipherAML/cipheraml.mk)
```

The full guide covers requirements, the checkout path, what lands where,
checking it worked, partitions and licensing. It's in
[docs/integration.md](docs/integration.md).

## Docs

- [Integrating it into your ROM](docs/integration.md)
- [Permissions, and why we need them](docs/permissions.md)
- [AML endpoints, country status and contributing](docs/endpoints.md)
- [Configuration](docs/configuration.md)
- [Testing](docs/testing.md)
- [Working on the code, and known gaps](docs/development.md)

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

CipherAML is released under the CipherOS License 2.0, see [LICENSE](LICENSE).
Questions about licensing go to legal@cipheros.org.in.

© 2019-2026 The CipherOS Project.
