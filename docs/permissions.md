# Permissions

CipherAML is a privileged system app. Here's every permission that needs
special handling, and why we ask for it.

## Privileged permissions

These are granted through `etc/permissions/privapp-permissions-com.cipheros.cipheraml.xml`.

| Permission | Why |
| --- | --- |
| `READ_ACTIVE_EMERGENCY_SESSION` | To be told when an emergency call or SMS goes out. |
| `LOCATION_BYPASS` | To get a fix when the user has location turned off. |
| `READ_PRIVILEGED_PHONE_STATE` | IMEI, IMSI and phone number for the message. |
| `MODIFY_PHONE_STATE` | To send the SMS without it landing in the sent folder. |

On builds with `ro.control_privapp_permissions=enforce` the device won't
boot if any of these are missing from the allowlist, so if you change the
manifest, change the XML too.

## Runtime permissions

The runtime ones (location, background location, `SEND_SMS`) are granted on
first boot through the default-permissions file. They aren't fixed, so a
user can revoke them, and the settings screen will tell them AML may not
work if they do.
