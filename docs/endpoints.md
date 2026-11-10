# AML endpoints

AML is set up country by country. Each country decides which emergency
numbers trigger it and where the location goes: an SMS short code, a long
number, an HTTPS server, or a mix.

Here's the thing, though: **governments don't publish where AML goes.**
There's no public list, no registry, nothing you can look up. Each country
set its endpoints up behind closed doors with its mobile networks, Google
and Apple, and that's where they stayed. EENA's yearly report will tell you
*who* runs AML in a country, but not *where* to send it.

We spent a good while digging through the ETSI spec, EU reports, EENA
documents and government pages. Only a handful of countries have an official
source that actually writes the destination down, and those are the ones
that are switched on.

And we're not going to guess. A wrong number in an emergency isn't a bug you
fix in the next release. So the rule is simple: a country only gets an
endpoint when an official or primary source says where AML goes, and every
entry cites that source right there in the XML.

Where things stand today:

| Country | Triggers | Location goes to | Status | Source |
| --- | --- | --- | --- | --- |
| United Kingdom | 999, 112 | SMS to 999 (BT) | On | ETSI TS 103 625 clause 6.3.4.1, EENA AML Report Card 2023 |
| Slovakia | 112, 150, 155, 158 | SMS to 112 (Ministry of Interior) | On | EENA AML Report Card 2023 |
| Lithuania | 112 | SMS to 112 (Emergency Response Centre) | On | 112.lt, "Locating the caller" |
| Australia | 000, 112 | SMS to 1262612626 (Telstra) | On | AS/CA S042.1:2022 clause 5.2.2.1(g) |
| Croatia | 112 | SMS to 112 | On | HAKOM decision of 5 March 2024 |
| Greece | 112 | SMS to 112 (112 Communication Centre) | On | General Secretariat for Civil Protection, 5 Aug 2020 |
| Moldova | 112 | SMS to 112 (SNUAU 112) | On | ANRCETI draft decision (HCA 31/2016 amendment), SNUAU 112 via EENA |
| Germany | 112, 110 | Not public (ILS Freiburg) | Pending | EENA AML Report Card 2023, ILS Freiburg |
| India | 112 | Not public (UP 112 / ERSS) | Pending | MHA ERSS, UP 112 ELS launch |

"Pending" means the country is in the table with its trigger numbers but
has no endpoint yet, so nothing is sent and Settings says AML isn't set up
there. Adding the endpoint is a one line change once someone has it.

## The pending countries

**Germany.** There's one national AML endpoint, run by the Integrierte
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

## Everywhere else we looked

In November 2026 we went through regulators, laws, tenders, PSAP pages and
slides, EENA, ETSI, CEPT, GSMA and EU Help112 documents, and the open source
world (AOSP, Sailfish OS, OpenHarmony, microG, /e/OS and others) looking for
more endpoints. Here's what came back for countries that are still off, so
nobody has to repeat it:

- **Belgium.** Close, but not clear enough. GSMA NG.119 (v6.0) describes the
  roaming setup: Android phones use SMSC `+32112` so the SMS stays in the
  visited network. The same document says the destination stays the
  emergency number, but also that Orange Belgium visitors may only send to
  `8112`, which is also Belgium's emergency SMS line for people. We won't
  pick one until Astrid or BIPT says which.
- **Switzerland.** The AML number is a "SMS-Large Account" that, by OFCOM
  regulation (SR 784.101.113/1.3, section 4.3.4), "ne peut pas être rendu
  public". Getting it means talking to Swisscom, which runs the endpoint.
- **Spain.** The regulator CNMC confirms it's an SMS to a long number plus
  HTTPS, handled by Vodafone's platform, but doesn't give the number.
- **Romania.** A 2019 emergency ordinance named 114, but the Constitutional
  Court struck the whole ordinance down in 2020. Don't use 114.
- **United States and Canada.** Handset location goes through partner
  pipelines (RapidSOS, Bell, TELUS) that only take data from Google and
  Apple. Not something we can send to.
- **Poland.** AML isn't running yet. The digital ministry plans it for 2027.
- **Not public:** Denmark, Sweden, Finland, Norway, Estonia, Latvia, Iceland,
  Ireland, Netherlands, Austria, France, Portugal, Italy, Czech Republic,
  Slovenia, Bulgaria, Hungary, Luxembourg, Malta, New Zealand, Brazil,
  Mexico, Malaysia and the UAE all run some form of AML, but none of them
  publishes where it goes. The EENA report cards tell you who runs each
  endpoint, which is the place to start asking.

## Know an endpoint? Send it in

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

## The format

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
- By default the SMS is only sent when the SIM is from the country you're
  in. A foreign SIM sends SMS through its home network, so 112 would end up
  at the home country's 112, and a national number like Australia's could
  reach a stranger's phone back home. Set `roaming-sms="true"` on the
  `<country>` only when a source documents how roamers' AML SMS is routed.
  HTTPS endpoints are not affected.
