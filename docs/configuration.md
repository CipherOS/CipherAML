# Configuring CipherAML

Most people never need to touch these. If you do, they live in
`res/values/config.xml` and can be overlaid:

| Resource | Default | What it does |
| --- | --- | --- |
| `config_amlEnabledByDefault` | `true` | Whether AML starts out on. |
| `config_amlLocationTimeoutMillis` | `20000` | T1, the longest we wait for a fix. |
| `config_amlEarlySendAccuracyMeters` | `25` | Send early once GPS is this good. 0 disables. |
| `config_amlConfidencePercent` | `68` | Confidence the radius is reported at. |
| `config_amlMinBatteryPercent` | `2` | Below this (and not charging) we stay out of the way. |
| `config_amlMaxCachedFixAgeMillis` | `60000` | Oldest cached fix we'll fall back on. |

Android reports accuracy at 68% confidence. If a country wants 90% or 95%,
set it per country in the endpoint table (see [endpoints.md](endpoints.md))
and the radius gets scaled up to match.
