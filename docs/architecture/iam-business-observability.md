# IAM and business observability

Status: **DESIGN / PLANNED**. Existing semantic runtime metrics are not a complete business analytics implementation. Determine availability by actual source and product version.

## Boundaries and sources

Report identity-service use and reliability, not all downstream business activity. Prefer native aggregate event metrics; separately authorize granular analytics for unique identities/journeys. No arbitrary log/profile ingestion or automatic event-listener SPI deployment.

Keycloak describes per-instance event counters that reset on restart, aggregation across replicas and optional client/IdP dimensions with cardinality costs. Verify support/configuration on the actual RHBK release. [Keycloak event metrics](https://www.keycloak.org/observability/event-metrics), [RHBK 26.4 Observability Guide](https://docs.redhat.com/en/documentation/red_hat_build_of_keycloak/26.4/pdf/observability_guide/Red_Hat_build_of_Keycloak-26.4-Observability_Guide-en-US.pdf).

## Indicator dictionary

| Indicator | Meaning / calculation | Evidence and caveats |
|---|---|---|
| Login success/failure | Reset-aware successful/failed event increases; success ratio = success/(success+failure) | Define events/error-label semantics per version; aggregate replicas. Zero denominator -> NO_ACTIVITY, not 100%. Scrape gaps -> PARTIAL. Attempts are not unique users/journeys. |
| Most-used applications | Successful login events ranked by realm/client/window | Requires client dimension and series budget. SSO/token reuse may not create a new login. Client-to-business-application mapping is owner-supplied/versioned. |
| DAU/WAU/MAU | Distinct eligible human subjects with qualifying successful event over explicit daily/7-day/30-day window | Counters/current sessions cannot establish unique users. Requires approved event source, pseudonymous stable key, timezone/exclusions and deduplication. No raw IDs in exports. |
| MFA policy | Eligible inspected applications requiring an approved additional factor / eligible inspected applications | Use one analysis unit (application) per KPI; enumerate included clients/exclusions and evaluate effective flow bindings/conditions. A flow-level KPI is separate. Required action alone does not establish enforcement. |
| MFA enrollment | Eligible inspected users with approved factor / eligible inspected users | Separately authorized aggregation; never credential material. Federation/partial scans must be visible. |
| MFA actual usage | Eligible completed journeys using approved factor / eligible journeys observed | Requires actual journey/factor instrumentation; neither enrollment nor generic login events suffice. Otherwise NOT_SUPPORTED. |
| Availability/impact | Good eligible observations / all eligible observations, failure windows, mapped affected clients and error-budget use | Owner defines SLI/SLO. Service error, bad credentials and HTTP availability differ. Telemetry gaps during outage make impact uncertain. |
| Estimated affected attempts | Explicit baseline traffic × affected interval model with assumptions | Label ESTIMATED, not observed users. Financial impact requires approved external inputs/model. |

Every result includes target/scope/source, start/end/window, unit, denominator, method version, freshness/coverage and availability: AVAILABLE, NO_ACTIVITY, PARTIAL, UNAVAILABLE, NOT_SUPPORTED or NOT_AUTHORIZED. Missing labels are not an empty business population.

Availability KPIs are separate: request SLI = good eligible requests/all eligible requests; probe SLI = successful probes/executed probes; time availability = verified available time/observed eligible time. Do not substitute one for another. Error budget = (1 − agreed SLO) × eligible observations in the agreed period; burn rate = observed bad fraction/(1 − SLO). Missing observation time remains unknown; define maintenance exclusions with the owner before calculation.

The version/source capability catalog also covers account creation/deletion/lockout/password-reset, federation and IdP errors, sessions/token issuance/refresh/client-credentials, and administrative/security events. These are separate bounded datasets with availability and privacy gates; native user-event metrics do not imply administrative-event coverage. No universal “everything happening” collection guarantee.

## Alerts and unusual behavior

First implement deterministic threshold, minimum samples/duration, recovery threshold, deduplication, cooldown, owner and expiring maintenance silence. Link alert evaluations to evidence. Examples: sustained service-authentication errors, replica loss, latency SLO breach, drift and provider-data loss. Credential errors need separate interpretation.

Statistical anomaly detection requires enough history, target-specific baseline and daily/weekly seasonality. Separate anomaly from incident; calibrate low-volume cases and measure false positives/omissions before notifications. LLM interpretation is not a detector. Delivery is opt-in, authorized, sanitized, idempotent and recoverable; alerting never authorizes remediation.

## Privacy, retention and scale

- Aggregate first; no emails, usernames, IPs, tokens or raw event payloads in model context by default.
- Approve purpose/access/retention before event ingestion; define raw/aggregate retention, deletion and pseudonymization. No automatic legal-compliance claim.
- Scope and bound every query by target/authorized entity, time, samples and cardinality. No unbounded analytics queries.
- Reuse Prometheus and an approved analytics source. PostgreSQL operational history is not an unrestricted event/metrics warehouse.
- Timestamp correlation between infrastructure and IAM is not proof of causality.

## Validation

Fixtures: success/failure, zero traffic, missing client/error labels, scrape gap, counter reset, multiple replicas, target denial, high cardinality, duplicate events, timezone edge, service accounts versus humans and configured-but-unused MFA. Live tests record actual RHBK release and event-metric capability without secrets. Publish each KPI only after its source and semantics have passed these gates.
