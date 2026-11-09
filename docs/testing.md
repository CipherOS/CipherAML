# Testing CipherAML

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
